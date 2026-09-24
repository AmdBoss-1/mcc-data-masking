package com.mcc.mask.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * 文本编码识别：真实世界里的 txt / csv / sql 很多是 GBK（记事本、多数 ERP 导出），
 * 一律按 UTF-8 读会整文件报 MalformedInput；这里按「BOM → 严格 UTF-8 → 严格 GBK → 宽松 UTF-8」
 * 识别，写回时用**同一个编码**，保证还原后逐字节相同。
 */
public final class TextCodec {

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final Charset GBK = Charset.isSupported("GBK") ? Charset.forName("GBK") : null;

    public record Decoded(String text, Charset charset, boolean bom) {

        public byte[] encode(String s) {
            byte[] body = s.getBytes(charset);
            if (!bom) {
                return body;
            }
            byte[] out = new byte[body.length + 3];
            System.arraycopy(BOM, 0, out, 0, 3);
            System.arraycopy(body, 0, out, 3, body.length);
            return out;
        }
    }

    public static Decoded read(Path p) throws IOException {
        return decode(Files.readAllBytes(p));
    }

    public static Decoded decode(byte[] raw) {
        boolean bom = raw.length >= 3 && raw[0] == BOM[0] && raw[1] == BOM[1] && raw[2] == BOM[2];
        byte[] body = bom ? Arrays.copyOfRange(raw, 3, raw.length) : raw;
        if (strict(body, StandardCharsets.UTF_8)) {
            return new Decoded(new String(body, StandardCharsets.UTF_8), StandardCharsets.UTF_8, bom);
        }
        if (GBK != null && strict(body, GBK)) {
            return new Decoded(new String(body, GBK), GBK, bom);
        }
        // 两种都不严格合法（极少见）：宽松 UTF-8 兜底，至少不炸
        return new Decoded(new String(body, StandardCharsets.UTF_8), StandardCharsets.UTF_8, bom);
    }

    private static boolean strict(byte[] body, Charset cs) {
        try {
            cs.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(body));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }

    private TextCodec() {
    }
}
