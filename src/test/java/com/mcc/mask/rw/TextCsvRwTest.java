package com.mcc.mask.rw;

import com.mcc.mask.MaskException;
import com.mcc.mask.codebook.Codebook;
import com.mcc.mask.codebook.CodebookJson;
import com.mcc.mask.rules.RuleTable;
import com.mcc.mask.util.Summary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TextCsvRwTest {

    private final RuleTable rules = RuleTable.defaultTable();
    private final Codebook book = new Codebook(new CodebookJson());
    private final Summary sum = new Summary();

    @Test
    void txtRoundTrip(@TempDir Path dir) throws Exception {
        Path a = dir.resolve("a.txt");
        Path b = dir.resolve("b.txt");
        Path c = dir.resolve("c.txt");
        Files.writeString(a, "苏E·D1234 到厂 13812345678\n备注正常\n", StandardCharsets.UTF_8);
        Rw.RwResult m = RwFactory.byPath(a).process(a, b, Ctx.mask(book, rules, sum));
        assertEquals(2, m.hits());
        String masked = Files.readString(b, StandardCharsets.UTF_8);
        assertFalse(masked.contains("苏E·D1234"));
        assertFalse(masked.contains("13812345678"));
        RwFactory.byPath(b).process(b, c, Ctx.unmask(book, rules, sum));
        assertArrayEquals(Files.readAllBytes(a), Files.readAllBytes(c));
    }

    @Test
    void csvColumnHitAndRoundTrip(@TempDir Path dir) throws Exception {
        Path a = dir.resolve("t.csv");
        Path b = dir.resolve("m.csv");
        Path c = dir.resolve("r.csv");
        Files.writeString(a, "车牌,重量kg,备注\n苏E·D1234,42.317,正常\n苏A·B8888,10.5,13900001111\n",
                StandardCharsets.UTF_8);
        RwFactory.byPath(a).process(a, b, Ctx.mask(book, rules, sum));
        String masked = Files.readString(b, StandardCharsets.UTF_8);
        assertFalse(masked.contains("苏E·D1234"));
        assertFalse(masked.contains("苏A·B8888"));
        assertFalse(masked.contains("13900001111"));
        assertTrue(masked.contains("42.317"), "数值列默认不动");
        assertTrue(masked.contains("10.5"));
        RwFactory.byPath(b).process(b, c, Ctx.unmask(book, rules, sum));
        assertArrayEquals(Files.readAllBytes(a), Files.readAllBytes(c));
    }

    @Test
    void unknownExtFailsWithHint(@TempDir Path dir) throws Exception {
        Path x = dir.resolve("f.xyz");
        Files.writeString(x, "abc");
        MaskException ex = assertThrows(MaskException.class, () -> RwFactory.byPath(x));
        assertTrue(ex.getMessage().contains("不支持的格式"), ex.getMessage());
    }

    @Test
    void missingInputLeavesNoOutput(@TempDir Path dir) {
        Path in = dir.resolve("nope.txt");
        Path out = dir.resolve("out.txt");
        assertThrows(MaskException.class,
                () -> RwFactory.byPath(in).process(in, out, Ctx.mask(book, rules, sum)));
        assertFalse(Files.exists(out), "失败不得留半截产物");
    }
}
