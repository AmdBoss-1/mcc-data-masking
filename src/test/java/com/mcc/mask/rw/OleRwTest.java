package com.mcc.mask.rw;

import com.mcc.mask.MaskException;
import com.mcc.mask.codebook.Codebook;
import com.mcc.mask.codebook.CodebookJson;
import com.mcc.mask.rules.RuleTable;
import com.mcc.mask.util.Summary;
import org.apache.poi.hslf.usermodel.HSLFSlide;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hslf.usermodel.HSLFTextBox;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Rectangle;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OleRwTest {

    private final RuleTable rules = RuleTable.defaultTable();

    private static Path fixture() {
        return Path.of("src/test/resources/sample.doc");
    }

    @Test
    void pptRoundTripWithSelfVerify(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("a.ppt");
        try (HSLFSlideShow ppt = new HSLFSlideShow(); OutputStream os = Files.newOutputStream(src)) {
            HSLFSlide slide = ppt.createSlide();
            HSLFTextBox box = slide.createTextBox();
            box.setAnchor(new java.awt.geom.Rectangle2D.Double(50, 50, 400, 60));
            box.setText("电话 13800001111 到货");
            ppt.write(os);
        }
        Path masked = dir.resolve("m.ppt");
        Path back = dir.resolve("r.ppt");
        Codebook book = new Codebook(new CodebookJson());
        Summary sum = new Summary();
        Rw.RwResult r = RwFactory.byPath(src).process(src, masked, Ctx.mask(book, rules, sum));
        assertEquals(1, r.hits());
        String mt = PptRw.extractText(masked);
        assertFalse(mt.contains("13800001111"), mt);
        RwFactory.byPath(masked).process(masked, back, Ctx.unmask(book, rules, sum));
        assertEquals("电话 13800001111 到货", PptRw.extractText(back).trim());
    }

    @Test
    void docRoundTripWithSelfVerify(@TempDir Path dir) throws Exception {
        assertTrue(Files.exists(fixture()), "缺少 .doc 夹具 src/test/resources/sample.doc");
        Path src = dir.resolve("a.doc");
        Files.copy(fixture(), src);
        String origText = DocRw.extractText(src);
        Path masked = dir.resolve("m.doc");
        Path back = dir.resolve("r.doc");
        Codebook book = new Codebook(new CodebookJson());
        Summary sum = new Summary();
        Rw.RwResult r = RwFactory.byPath(src).process(src, masked, Ctx.mask(book, rules, sum));
        assertTrue(r.hits() >= 2, "车牌+手机 应命中: " + r.hits());
        String mt = DocRw.extractText(masked);
        assertFalse(mt.contains("苏E·D1234"), mt);
        assertFalse(mt.contains("13812345678"), mt);
        RwFactory.byPath(masked).process(masked, back, Ctx.unmask(book, rules, sum));
        assertEquals(origText, DocRw.extractText(back));
    }

    @Test
    void selfVerifyDeletesBadOutput(@TempDir Path dir) throws Exception {
        Path bad = dir.resolve("bad.doc");
        Files.writeString(bad, "hello", StandardCharsets.UTF_8);
        List<String[]> pairs = List.<String[]>of(new String[] {"原值", "假值"});
        MaskException ex = assertThrows(MaskException.class,
                () -> SelfVerify.run(bad, pairs, 1, p -> "hello", "doc"));
        assertTrue(ex.getMessage().contains("自验失败"), ex.getMessage());
        assertFalse(Files.exists(bad), "自验不过必须删除坏产物");
    }
}
