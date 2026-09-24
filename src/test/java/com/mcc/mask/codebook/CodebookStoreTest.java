package com.mcc.mask.codebook;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import com.mcc.mask.MaskException;

import static org.junit.jupiter.api.Assertions.*;

class CodebookStoreTest {

    private CodebookJson sample() {
        CodebookJson j = new CodebookJson();
        j.created = 1758000000000L;
        j.rulesVersion = "v1";
        j.files.add("台账.xlsx");
        j.maps.put("plate", Map.of("苏E·D1234", "苏E·K7Q92"));
        return j;
    }

    @Test
    void roundTrip(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("book.mccmask");
        CodebookStore.save(f, "口令abc".toCharArray(), sample());
        CodebookJson back = CodebookStore.load(f, "口令abc".toCharArray());
        assertEquals("苏E·K7Q92", back.maps.get("plate").get("苏E·D1234"));
        assertEquals(1758000000000L, back.created);
        assertEquals("台账.xlsx", back.files.get(0));
    }

    @Test
    void wrongPasswordRejected(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("book.mccmask");
        CodebookStore.save(f, "对的口令".toCharArray(), sample());
        MaskException ex = assertThrows(MaskException.class,
                () -> CodebookStore.load(f, "错的口令".toCharArray()));
        assertTrue(ex.getMessage().contains("口令"), "错误消息应提示口令: " + ex.getMessage());
    }

    @Test
    void ciphertextLeaksNoPlaintext(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("book.mccmask");
        CodebookStore.save(f, "口令abc".toCharArray(), sample());
        String raw = Files.readString(f, StandardCharsets.UTF_8);
        assertFalse(raw.contains("苏E·D1234"), "码本文件明文不得含原值");
        assertFalse(raw.contains("苏E·K7Q92"), "码本文件明文不得含假值");
    }
}
