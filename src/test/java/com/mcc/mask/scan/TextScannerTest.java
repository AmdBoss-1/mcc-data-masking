package com.mcc.mask.scan;

import com.mcc.mask.codebook.Codebook;
import com.mcc.mask.rules.RuleTable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TextScannerTest {

    private final RuleTable rules = RuleTable.defaultTable();
    private final Codebook book = new Codebook(new com.mcc.mask.codebook.CodebookJson());

    @Test
    void maskThenUnmaskRoundTrip() {
        TextScanner s = new TextScanner(rules, book);
        String orig = "苏E·D1234 今天到厂，联系 13812345678";
        TextScanner.ScanResult masked = s.maskText(orig);
        assertEquals(2, masked.hits());
        assertFalse(masked.text().contains("苏E·D1234"));
        assertFalse(masked.text().contains("13812345678"));
        assertEquals(orig, s.unmaskText(masked.text()).text());
    }

    @Test
    void idCardFakeKeepsValidCheckDigit() {
        TextScanner s = new TextScanner(rules, book);
        TextScanner.ScanResult r = s.maskText("身份证 320583199003074516 备案");
        String fake = book.reverse(r.text().replaceAll(".*身份证 (\\S{18}) 备案.*", "$1"));
        assertEquals("320583199003074516", fake);
    }

    @Test
    void unmaskLongestFirstNoCrosstalk() {
        Codebook b = new Codebook(new com.mcc.mask.codebook.CodebookJson());
        b.maps().put("name", Map.of("欧阳建华", "华阳厂", "建华", "阳厂"));
        TextScanner s = new TextScanner(rules, b);
        assertEquals("欧阳建华", s.unmaskText("华阳厂").text());
    }

    @Test
    void credentialWarnsNotReplaces() {
        TextScanner s = new TextScanner(rules, book);
        TextScanner.ScanResult r = s.maskText("下载码: ABC123 请收好");
        assertEquals("下载码: ABC123 请收好", r.text());
        assertEquals(List.of("疑似凭据: 下载码"), r.warnings());
    }

    @Test
    void maskCellUsesColumnRule() {
        TextScanner s = new TextScanner(rules, book);
        TextScanner.ScanResult r = s.maskCell("厂名", "昆山精密五金厂");
        assertEquals(1, r.hits());
        assertTrue(r.text().endsWith("厂"), r.text());
        assertEquals("昆山精密五金厂", book.reverse(r.text()));
        TextScanner.ScanResult plain = s.maskCell("备注", "普通备注无敏感");
        assertEquals(0, plain.hits());
    }
}
