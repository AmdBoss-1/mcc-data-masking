package com.mcc.mask.rw;

import com.mcc.mask.codebook.Codebook;
import com.mcc.mask.codebook.CodebookJson;
import com.mcc.mask.rules.RuleTable;
import com.mcc.mask.util.Summary;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Rectangle;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DocxPptxRwTest {

    private final RuleTable rules = RuleTable.defaultTable();

    @Test
    void docxCrossRunRoundTrip(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("a.docx");
        try (XWPFDocument doc = new XWPFDocument(); OutputStream os = Files.newOutputStream(src)) {
            XWPFParagraph p = doc.createParagraph();
            XWPFRun r0 = p.createRun();
            r0.setBold(true);
            r0.setText("13812");
            XWPFRun r1 = p.createRun();
            r1.setText("345678 到货");
            XWPFTable t = doc.createTable(2, 1);
            XWPFTableCell cell = t.getRow(1).getCell(0);
            cell.getParagraphs().get(0).createRun().setText("苏E·D1234");
            doc.write(os);
        }
        Path masked = dir.resolve("m.docx");
        Path back = dir.resolve("r.docx");
        Codebook book = new Codebook(new CodebookJson());
        Summary sum = new Summary();

        Rw.RwResult r = RwFactory.byPath(src).process(src, masked, Ctx.mask(book, rules, sum));
        assertEquals(2, r.hits(), "跨 run 手机号 + 表格单元格车牌");
        try (InputStream is = Files.newInputStream(masked); XWPFDocument doc = new XWPFDocument(is)) {
            XWPFParagraph p = doc.getParagraphs().get(0);
            String text = p.getText();
            assertFalse(text.contains("13812345678"));
            assertTrue(p.getRuns().get(0).isBold(), "替换文本应继承首命中 run 的加粗");
            assertTrue(doc.getTables().get(0).getRow(1).getCell(0).getText()
                    .matches("(?s).*[一-龥][A-Z]·?[A-Z0-9]{5,6}.*"), "表格车牌应为假牌形");
        }
        RwFactory.byPath(masked).process(masked, back, Ctx.unmask(book, rules, sum));
        try (InputStream is = Files.newInputStream(back); XWPFDocument doc = new XWPFDocument(is)) {
            assertTrue(doc.getParagraphs().get(0).getText().startsWith("13812345678"));
            assertEquals("苏E·D1234", doc.getTables().get(0).getRow(1).getCell(0).getText());
        }
    }

    @Test
    void pptxRoundTrip(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("a.pptx");
        try (XMLSlideShow ppt = new XMLSlideShow(); OutputStream os = Files.newOutputStream(src)) {
            XSLFSlide slide = ppt.createSlide();
            XSLFTextBox box = slide.createTextBox();
            box.setAnchor(new Rectangle(50, 50, 400, 60));
            var tp = box.addNewTextParagraph();
            tp.addNewTextRun().setText("司机 139");
            tp.addNewTextRun().setText("00001111 备注");
            ppt.write(os);
        }
        Path masked = dir.resolve("m.pptx");
        Path back = dir.resolve("r.pptx");
        Codebook book = new Codebook(new CodebookJson());
        Summary sum = new Summary();
        RwFactory.byPath(src).process(src, masked, Ctx.mask(book, rules, sum));
        try (InputStream is = Files.newInputStream(masked); XMLSlideShow ppt = new XMLSlideShow(is)) {
            org.apache.poi.xslf.usermodel.XSLFTextShape ts =
                    (org.apache.poi.xslf.usermodel.XSLFTextShape) ppt.getSlides().get(0).getShapes().get(0);
            String text = ts.getText();
            assertFalse(text.contains("13900001111"), text);
            assertTrue(text.contains("司机"), text);
        }
        RwFactory.byPath(masked).process(masked, back, Ctx.unmask(book, rules, sum));
        try (InputStream is = Files.newInputStream(back); XMLSlideShow ppt = new XMLSlideShow(is)) {
            org.apache.poi.xslf.usermodel.XSLFTextShape ts =
                    (org.apache.poi.xslf.usermodel.XSLFTextShape) ppt.getSlides().get(0).getShapes().get(0);
            assertEquals("司机 13900001111 备注", ts.getText());
        }
    }
}
