package com.mcc.mask.rules;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class RuleTableTest {

    @Test
    void defaultsLoaded() {
        RuleTable t = RuleTable.defaultTable();
        assertEquals(5, t.structRules().size());
        assertEquals("plate", t.columnRules().get("车牌"));
        assertTrue(t.credentialPatterns().contains("下载码"));
        String fake = t.transformerFor("plate").fake("苏E·D1234", 1L);
        assertTrue(fake.matches("^.[A-Z]·?[A-Z0-9]{5,6}$"), fake);
    }

    @Test
    void addressKeepsCityPrefix() {
        String orig = "江苏省苏州市昆山市玉山镇XX路1号";
        String fake = RuleTable.defaultTable().transformerFor("address").fake(orig, 4L);
        assertTrue(fake.startsWith("江苏省苏州市"), fake);
        assertTrue(fake.endsWith("号"), fake);
        assertNotEquals(orig, fake);
    }

    @Test
    void overrideMergesColumns(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("rules.json");
        Files.writeString(f, "{\"columns\":{\"买方\":\"factory\"},\"credentials\":[\"apikey\"]}");
        RuleTable t = RuleTable.load(f);
        assertEquals("factory", t.columnRules().get("买方"));
        assertEquals("plate", t.columnRules().get("车牌"), "默认列名应保留");
        assertTrue(t.credentialPatterns().contains("apikey"));
        assertEquals(5, t.structRules().size());
    }
}
