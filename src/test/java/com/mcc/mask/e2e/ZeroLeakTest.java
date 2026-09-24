package com.mcc.mask.e2e;

import com.mcc.mask.codebook.Codebook;
import com.mcc.mask.codebook.CodebookJson;
import com.mcc.mask.rw.Ctx;
import com.mcc.mask.rw.Rw;
import com.mcc.mask.rw.RwFactory;
import com.mcc.mask.rules.RuleTable;
import com.mcc.mask.util.Summary;
import org.apache.poi.hslf.usermodel.HSLFSlide;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hslf.usermodel.HSLFTextBox;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 零泄漏矩阵：各格式 mask 产物中，原始敏感值一个都不许出现。 */
class ZeroLeakTest {

    private static final String BODY = "车牌 苏E·D1234 电话 13812345678 客户 昆山精密五金厂 证 320583199003074516";

    private final RuleTable rules = RuleTable.defaultTable();

    /** 自由文本里"公司名"无列上下文可依据（设计如此，需 --rules 或列名命中）；结构化三件套任何格式都必须消失。 */
    private static final List<String> STRUCT_SECRETS = List.of("苏E·D1234", "13812345678", "320583199003074516");

    private void assertNoLeak(Path produced, List<String> extra) throws Exception {
        String all = textOf(produced);
        for (String s : STRUCT_SECRETS) {
            assertFalse(all.contains(s), produced.getFileName() + " 泄漏: " + s);
        }
        for (String s : extra) {
            assertFalse(all.contains(s), produced.getFileName() + " 泄漏(列命中项): " + s);
        }
    }

    private String textOf(Path p) throws Exception {
        String n = p.getFileName().toString().toLowerCase();
        if (n.endsWith(".xlsx") || n.endsWith(".xlsm")) {
            try (var is = Files.newInputStream(p); Workbook wb = new XSSFWorkbook(is)) {
                return wbText(wb);
            }
        }
        if (n.endsWith(".xls")) {
            try (var is = Files.newInputStream(p); Workbook wb = new HSSFWorkbook(is)) {
                return wbText(wb);
            }
        }
        if (n.endsWith(".docx")) {
            try (var is = Files.newInputStream(p); XWPFDocument d = new XWPFDocument(is)) {
                StringBuilder sb = new StringBuilder();
                d.getParagraphs().forEach(pp -> sb.append(pp.getText()));
                return sb.toString();
            }
        }
        if (n.endsWith(".pptx")) {
            try (var is = Files.newInputStream(p); XMLSlideShow s = new XMLSlideShow(is)) {
                StringBuilder sb = new StringBuilder();
                s.getSlides().forEach(sl -> sl.getShapes().forEach(sh -> {
                    if (sh instanceof org.apache.poi.xslf.usermodel.XSLFTextShape ts) {
                        sb.append(ts.getText());
                    }
                }));
                return sb.toString();
            }
        }
        if (n.endsWith(".ppt")) {
            return com.mcc.mask.rw.PptRw.extractText(p);
        }
        if (n.endsWith(".doc")) {
            return com.mcc.mask.rw.DocRw.extractText(p);
        }
        if (n.endsWith(".mv.db")) {
            String u = "jdbc:h2:" + p.toAbsolutePath();
            if (u.toLowerCase().endsWith(".mv.db")) {
                u = u.substring(0, u.length() - ".mv.db".length());
            }
            try (var c = DriverManager.getConnection(u, "sa", "");
                    var st = c.createStatement();
                    var r = st.executeQuery("SELECT plate, note FROM t")) {
                StringBuilder sb = new StringBuilder();
                while (r.next()) {
                    sb.append(r.getString(1)).append('|').append(r.getString(2)).append('\n');
                }
                return sb.toString();
            }
        }
        return Files.readString(p, StandardCharsets.UTF_8);
    }

    private static String wbText(Workbook wb) {
        StringBuilder sb = new StringBuilder();
        for (Sheet sh : wb) {
            for (Row r : sh) {
                for (org.apache.poi.ss.usermodel.Cell c : r) {
                    if (c.getCellType() == org.apache.poi.ss.usermodel.CellType.STRING) {
                        sb.append(c.getStringCellValue()).append('|');
                    }
                }
            }
        }
        return sb.toString();
    }

    private void mask(Path in, Path out) {
        Codebook book = new Codebook(new CodebookJson());
        Summary sum = new Summary();
        Rw rw = RwFactory.byPath(in);
        Rw.RwResult r = rw.process(in, out, Ctx.mask(book, rules, sum));
        assertTrue(r.hits() >= 1, in + " 未命中任何敏感值: " + r.hits());
    }

    @Test
    void allFormatsLeakNothing(@TempDir Path dir) throws Exception {
        // 文本类
        Path txt = dir.resolve("a.txt");
        Files.writeString(txt, BODY, StandardCharsets.UTF_8);
        Path csv = dir.resolve("a.csv");
        Files.writeString(csv, "车牌,电话,客户,身份证\n苏E·D1234,13812345678,昆山精密五金厂,320583199003074516\n",
                StandardCharsets.UTF_8);
        Path sql = dir.resolve("a.sql");
        Files.writeString(sql, "INSERT INTO t VALUES('" + BODY + "');\n", StandardCharsets.UTF_8);
        // Office
        Path xlsx = dir.resolve("a.xlsx");
        try (Workbook wb = new XSSFWorkbook(); OutputStream os = Files.newOutputStream(xlsx)) {
            Sheet sh = wb.createSheet("s");
            Row h = sh.createRow(0);
            h.createCell(0).setCellValue("内容");
            sh.createRow(1).createCell(0).setCellValue(BODY);
            wb.write(os);
        }
        Path xls = dir.resolve("a.xls");
        try (Workbook wb = new HSSFWorkbook(); OutputStream os = Files.newOutputStream(xls)) {
            Sheet sh = wb.createSheet("s");
            Row h = sh.createRow(0);
            h.createCell(0).setCellValue("内容");
            sh.createRow(1).createCell(0).setCellValue(BODY);
            wb.write(os);
        }
        Path docx = dir.resolve("a.docx");
        try (XWPFDocument d = new XWPFDocument(); OutputStream os = Files.newOutputStream(docx)) {
            XWPFParagraph p = d.createParagraph();
            p.createRun().setText(BODY);
            d.write(os);
        }
        Path doc = dir.resolve("a.doc");
        Files.copy(Path.of("src/test/resources/sample.doc"), doc);
        Path pptx = dir.resolve("a.pptx");
        try (XMLSlideShow s = new XMLSlideShow(); OutputStream os = Files.newOutputStream(pptx)) {
            XSLFSlide sl = s.createSlide();
            XSLFTextBox box = sl.createTextBox();
            box.setAnchor(new java.awt.geom.Rectangle2D.Double(50, 50, 500, 60));
            box.addNewTextParagraph().addNewTextRun().setText(BODY);
            s.write(os);
        }
        Path ppt = dir.resolve("a.ppt");
        try (HSLFSlideShow s = new HSLFSlideShow(); OutputStream os = Files.newOutputStream(ppt)) {
            HSLFSlide sl = s.createSlide();
            HSLFTextBox box = sl.createTextBox();
            box.setAnchor(new java.awt.geom.Rectangle2D.Double(50, 50, 500, 60));
            box.setText(BODY);
            s.write(os);
        }
        // H2
        Path db = dir.resolve("a.mv.db");
        String u = "jdbc:h2:" + db.toAbsolutePath().toString().replaceFirst("(?i)\\.mv\\.db$", "");
        try (var c = DriverManager.getConnection(u, "sa", ""); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE t(plate VARCHAR(30), note VARCHAR(300))");
            st.execute("INSERT INTO t VALUES('苏E·D1234','" + BODY + "')");
        }

        List<Path> inputs = List.of(txt, csv, sql, xlsx, xls, docx, doc, pptx, ppt, db);
        for (Path in : inputs) {
            Path out = dir.resolve("M_" + in.getFileName());
            mask(in, out);
            assertNoLeak(out, in.getFileName().toString().endsWith(".csv")
                    ? List.of("昆山精密五金厂") : List.of());
        }
    }
}
