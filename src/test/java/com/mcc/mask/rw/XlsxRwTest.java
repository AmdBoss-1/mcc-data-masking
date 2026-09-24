package com.mcc.mask.rw;

import com.mcc.mask.codebook.Codebook;
import com.mcc.mask.codebook.CodebookJson;
import com.mcc.mask.rules.RuleTable;
import com.mcc.mask.util.Summary;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.*;

class XlsxRwTest {

    private final RuleTable rules = RuleTable.defaultTable();

    private Path build(Path dir, String name, boolean xlsx) throws Exception {
        Workbook wb = xlsx ? new XSSFWorkbook() : new HSSFWorkbook();
        Sheet sh = wb.createSheet("台账");
        Row h = sh.createRow(0);
        h.createCell(0).setCellValue("车牌");
        h.createCell(1).setCellValue("重量kg");
        h.createCell(2).setCellValue("备注");
        h.createCell(3).setCellValue("计算");
        Row r1 = sh.createRow(1);
        r1.createCell(0).setCellValue("苏E·D1234");
        r1.createCell(1).setCellValue(42.317);
        r1.createCell(2).setCellValue("到厂 13812345678");
        r1.createCell(3).setCellFormula("B2*2");
        Row r2 = sh.createRow(2);
        r2.createCell(0).setCellValue("苏A·B8888");
        r2.createCell(1).setCellValue(10.5);
        r2.createCell(2).setCellValue("正常");
        sh.addMergedRegion(new CellRangeAddress(3, 3, 0, 1));
        Row r3 = sh.createRow(3);
        r3.createCell(0).setCellValue("昆山精密五金厂");
        Path p = dir.resolve(name);
        try (OutputStream os = Files.newOutputStream(p)) {
            wb.write(os);
        }
        wb.close();
        return p;
    }

    private void roundTrip(boolean xlsx, Path dir) throws Exception {
        Path src = build(dir, xlsx ? "a.xlsx" : "a.xls", xlsx);
        Path masked = dir.resolve(xlsx ? "m.xlsx" : "m.xls");
        Path back = dir.resolve(xlsx ? "r.xlsx" : "r.xls");
        Codebook book = new Codebook(new CodebookJson());
        Summary sum = new Summary();

        Rw.RwResult r = RwFactory.byPath(src).process(src, masked, Ctx.mask(book, rules, sum));
        assertTrue(r.hits() >= 3, "两块车牌+一个手机: " + r.hits());
        try (Workbook wb = (xlsx ? new XSSFWorkbook(Files.newInputStream(masked))
                : new HSSFWorkbook(Files.newInputStream(masked)))) {
            Sheet sh = wb.getSheetAt(0);
            assertNotEquals("苏E·D1234", sh.getRow(1).getCell(0).getStringCellValue());
            assertEquals(42.317, sh.getRow(1).getCell(1).getNumericCellValue(), 1e-9);
            assertEquals(CellType.FORMULA, sh.getRow(1).getCell(3).getCellType());
            assertEquals("B2*2", sh.getRow(1).getCell(3).getCellFormula());
            assertEquals(1, sh.getNumMergedRegions());
            assertEquals("昆山精密五金厂", sh.getRow(3).getCell(0).getStringCellValue(),
                    "车牌列里的非车牌形值不被硬套");
        }

        RwFactory.byPath(masked).process(masked, back, Ctx.unmask(book, rules, sum));
        try (Workbook a = (xlsx ? new XSSFWorkbook(Files.newInputStream(src)) : new HSSFWorkbook(Files.newInputStream(src)));
             Workbook b = (xlsx ? new XSSFWorkbook(Files.newInputStream(back)) : new HSSFWorkbook(Files.newInputStream(back)))) {
            Sheet sa = a.getSheetAt(0);
            Sheet sb = b.getSheetAt(0);
            for (int row = 0; row <= 3; row++) {
                Row ra = sa.getRow(row);
                Row rb = sb.getRow(row);
                for (int col = 0; col < 4; col++) {
                    Cell ca = ra == null ? null : ra.getCell(col);
                    Cell cb = rb == null ? null : rb.getCell(col);
                    if (ca == null || cb == null) {
                        assertEquals(ca == null, cb == null, "row" + row + " col" + col);
                        continue;
                    }
                    assertEquals(ca.getCellType(), cb.getCellType(), "row" + row + " col" + col);
                    if (ca.getCellType() == CellType.STRING) {
                        assertEquals(ca.getStringCellValue(), cb.getStringCellValue());
                    } else if (ca.getCellType() == CellType.NUMERIC) {
                        assertEquals(ca.getNumericCellValue(), cb.getNumericCellValue(), 1e-9);
                    }
                }
            }
        }
    }

    @Test
    void xlsxRoundTrip(@TempDir Path dir) throws Exception {
        roundTrip(true, dir);
    }

    @Test
    void xlsRoundTrip(@TempDir Path dir) throws Exception {
        roundTrip(false, dir);
    }

    /**
     * 回归：openpyxl / 部分导出工具写的是 inline string（<c t="inlineStr"><is><t>值</t></is></c>）。
     * POI 的 setCellValue 只新增 <v>、不删 <is>，而 Excel 读的是 <is> → 产物看着没脱敏（真值仍在），
     * 且工具还会报"替换 N 处"。这类文件必须真换掉，还原方向也要能换回来。
     */
    @Test
    void 内联字符串单元格也要真换掉(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("in.xlsx");
        try (InputStream is = getClass().getResourceAsStream("/inline-strings.xlsx")) {
            assertNotNull(is, "缺少夹具 inline-strings.xlsx");
            Files.copy(is, src, StandardCopyOption.REPLACE_EXISTING);
        }
        Codebook book = new Codebook(new CodebookJson());
        Summary sum = new Summary();
        Path masked = dir.resolve("m.xlsx");

        Rw.RwResult r = RwFactory.byPath(src).process(src, masked, Ctx.mask(book, rules, sum));
        assertTrue(r.hits() >= 6, "2 行×3 文本列: " + r.hits());
        try (Workbook wb = new XSSFWorkbook(Files.newInputStream(masked))) {
            Sheet sh = wb.getSheetAt(0);
            assertEquals(book.maps().get("plate").get("苏A12345"), sh.getRow(1).getCell(0).getStringCellValue(),
                    "Excel 打开必须看到假值");
            assertEquals(book.maps().get("phone").get("13800001111"), sh.getRow(1).getCell(1).getStringCellValue());
            assertEquals(book.maps().get("factory").get("昆山精密五金厂"), sh.getRow(1).getCell(2).getStringCellValue());
            assertNotEquals("苏A12345", sh.getRow(2).getCell(0).getStringCellValue());
            assertEquals(42.317, sh.getRow(1).getCell(3).getNumericCellValue(), 1e-9, "数值默认不动");
        }

        Path back = dir.resolve("r.xlsx");
        RwFactory.byPath(masked).process(masked, back, Ctx.unmask(book, rules, sum));
        try (Workbook wb = new XSSFWorkbook(Files.newInputStream(back))) {
            Sheet sh = wb.getSheetAt(0);
            assertEquals("苏A12345", sh.getRow(1).getCell(0).getStringCellValue());
            assertEquals("昆山精密五金厂", sh.getRow(1).getCell(2).getStringCellValue());
        }
    }
}
