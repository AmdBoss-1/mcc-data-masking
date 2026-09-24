package com.mcc.mask.rules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TransformersTest {

    @Test
    void phoneFakeKeepsFormat() {
        String fake = Transformers.phone().fake("13812345678", 7L);
        assertTrue(fake.matches("^1[3-9]\\d{9}$"), fake);
        assertNotEquals("13812345678", fake);
    }

    @Test
    void idCardFakePassesCheckDigit() {
        String orig = "320583199003074516";
        String fake = Transformers.idCard().fake(orig, 11L);
        assertEquals(18, fake.length());
        assertNotEquals(orig, fake);
        assertTrue(Transformers.idCardCheckValid(fake), "假身份证校验位应自验通过: " + fake);
    }

    @Test
    void plateFakeKeepsProvinceAndLength() {
        String orig = "苏E·D1234";
        String fake = Transformers.plate().fake(orig, 3L);
        assertEquals(orig.length(), fake.length());
        assertEquals(orig.charAt(0), fake.charAt(0));
        assertTrue(fake.matches("^.[A-Z]·?[A-Z0-9]{5,6}$"), fake);
        assertNotEquals(orig, fake);
    }

    @Test
    void taxNoFakePassesCheckDigit() {
        String orig = "91320583MA1WXL1234";
        String fake = Transformers.taxNo().fake(orig, 5L);
        assertEquals(18, fake.length());
        assertTrue(Transformers.usccCheckValid(fake), "假税号校验位应自验通过: " + fake);
    }

    @Test
    void jitterStaysWithinPctAndScale() {
        String fake = Transformers.jitter(3.0).fake("42.317", 9L);
        double v = Double.parseDouble(fake);
        assertTrue(v >= 42.317 * 0.97 && v <= 42.317 * 1.03, fake);
        assertEquals(3, fake.split("\\.")[1].length(), "小数位应保持: " + fake);
    }

    @Test
    void poolsDeterministicBySeed() {
        String a = Transformers.factoryPool().fake("昆山精密五金厂", 42L);
        String b = Transformers.factoryPool().fake("昆山精密五金厂", 42L);
        String c = Transformers.factoryPool().fake("昆山精密五金厂", 43L);
        assertEquals(a, b);
        assertNotEquals(a, c);
        assertTrue(a.endsWith("厂"), a);
        assertTrue(Transformers.factoryPool().fake("昆山精密五金有限公司", 42L).endsWith("有限公司"));
    }

    @Test
    void namePoolLooksLikeName() {
        String fake = Transformers.namePool().fake("张伟", 21L);
        assertTrue(fake.length() >= 2 && fake.length() <= 3, fake);
        assertNotEquals("张伟", fake);
    }

    @Test
    void maskPhoneShape() {
        String fake = Transformers.maskPhone().fake("13812345678", 1L);
        assertTrue(fake.matches("^1[3-9]\\d\\*\\*\\*\\*\\d{4}$"), fake);
    }
}
