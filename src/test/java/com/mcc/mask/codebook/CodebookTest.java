package com.mcc.mask.codebook;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CodebookTest {

    private static final Codebook.FakeGen SEQ = orig -> "假-" + orig;

    @Test
    void sameOrigAlwaysSameFake() {
        Codebook b = new Codebook(new CodebookJson());
        String f1 = b.getOrFake("plate", "苏E·D1234", SEQ);
        String f2 = b.getOrFake("plate", "苏E·D1234", SEQ);
        assertEquals(f1, f2);
        assertEquals(2, b.replacedCount());
    }

    @Test
    void reverseLookup() {
        Codebook b = new Codebook(new CodebookJson());
        String fake = b.getOrFake("factory", "昆山精密五金厂", SEQ);
        assertEquals("昆山精密五金厂", b.reverse(fake));
        assertNull(b.reverse("不存在的假值"));
    }

    @Test
    void sharedJsonKeepsMappingConsistent() {
        CodebookJson json = new CodebookJson();
        Codebook a = new Codebook(json);
        String fake = a.getOrFake("name", "张伟", SEQ);
        Codebook b = new Codebook(json);
        assertEquals(fake, b.getOrFake("name", "张伟", SEQ));
        assertEquals(Map.of("张伟", fake), b.maps().get("name"));
    }

    @Test
    void touchFileDedups() {
        Codebook b = new Codebook(new CodebookJson());
        b.touchFile("台账.xlsx");
        b.touchFile("台账.xlsx");
        assertEquals(1, b.json().files.size());
    }
}
