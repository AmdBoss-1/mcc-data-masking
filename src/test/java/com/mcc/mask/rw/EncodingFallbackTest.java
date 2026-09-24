package com.mcc.mask.rw;

import com.mcc.mask.codebook.Codebook;
import com.mcc.mask.codebook.CodebookJson;
import com.mcc.mask.rules.RuleTable;
import com.mcc.mask.util.Summary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实世界的编码：Windows 记事本/多数 ERP 导出的 txt·csv·sql 是 GBK。
 * 之前一律按 UTF-8 读 → 整文件报 "Input length = 1" 直接失败。
 * 要求：按原编码读、按原编码写回，还原后逐字节相同。
 */
class EncodingFallbackTest {

    @TempDir
    Path dir;

    private static final Charset GBK = Charset.forName("GBK");
    private final RuleTable rules = RuleTable.defaultTable();

    private Codebook book() {
        return new Codebook(new CodebookJson());
    }

    @Test
    void gbk的csv能脱敏且能逐字节还原() throws Exception {
        String src = "车牌,手机,厂名\n苏C6677A,15912345678,苏州立达机械厂\n";
        Path in = dir.resolve("台账.csv");
        Files.write(in, src.getBytes(GBK));

        Codebook book = book();
        Summary sum = new Summary();
        Path masked = dir.resolve("台账.masked.csv");
        Rw.RwResult r = RwFactory.byPath(in).process(in, masked, Ctx.mask(book, rules, sum));
        assertTrue(r.hits() >= 3, "3 格结构化命中: " + r.hits());

        String m = new String(Files.readAllBytes(masked), GBK);
        assertFalse(m.contains("苏C6677A"), m);
        assertFalse(m.contains("苏州立达机械厂"), m);
        assertTrue(m.contains("车牌"), "表头与编码都要在: " + m);

        Path back = dir.resolve("台账.restored.csv");
        RwFactory.byPath(masked).process(masked, back, Ctx.unmask(book, rules, sum));
        assertArrayEquals(Files.readAllBytes(in), Files.readAllBytes(back), "还原应逐字节相同");
    }

    @Test
    void gbk的日志文本能脱敏且能逐字节还原() throws Exception {
        String src = "09:12 苏B12345 进厂，司机 13700005555 登记\n10:05 身份证 320502198803034321 放行\n";
        Path in = dir.resolve("车间日志.txt");
        Files.write(in, src.getBytes(GBK));

        Codebook book = book();
        Summary sum = new Summary();
        Path masked = dir.resolve("车间日志.masked.txt");
        Rw.RwResult r = RwFactory.byPath(in).process(in, masked, Ctx.mask(book, rules, sum));
        assertTrue(r.hits() >= 2, "命中数: " + r.hits());

        Path back = dir.resolve("车间日志.restored.txt");
        RwFactory.byPath(masked).process(masked, back, Ctx.unmask(book, rules, sum));
        assertArrayEquals(Files.readAllBytes(in), Files.readAllBytes(back), "还原应逐字节相同");
    }

    @Test
    void utf8带BOM的文件保持BOM() throws Exception {
        String src = "手机\n13900002222\n";
        Path in = dir.resolve("bom.csv");
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] body = src.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] all = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, all, 0, 3);
        System.arraycopy(body, 0, all, 3, body.length);
        Files.write(in, all);

        Codebook book = book();
        Summary sum = new Summary();
        Path masked = dir.resolve("bom.masked.csv");
        RwFactory.byPath(in).process(in, masked, Ctx.mask(book, rules, sum));
        byte[] out = Files.readAllBytes(masked);
        assertTrue(out.length > 3 && out[0] == bom[0] && out[1] == bom[1] && out[2] == bom[2],
                "BOM 不能丢，否则 Excel 打开中文会乱码");
    }
}
