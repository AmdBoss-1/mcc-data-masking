package com.mcc.mask.rw;

import com.mcc.mask.scan.TextScanner;
import com.mcc.mask.util.AtomicWrite;
import com.mcc.mask.util.TextCodec;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** 纯文本（txt/log/md）：整文双向替换。 */
public final class TextRw implements Rw {

    @Override
    public String name() {
        return "text";
    }

    @Override
    public RwResult process(Path in, Path out, Ctx ctx) {
        try {
            TextCodec.Decoded d = TextCodec.read(in);
            TextScanner s = new TextScanner(ctx.rules(), ctx.book());
            TextScanner.ScanResult r = ctx.reverse() ? s.unmaskText(d.text()) : s.maskText(d.text());
            AtomicWrite.write(out, d.encode(r.text()));
            return new RwResult(r.hits(), r.warnings());
        } catch (IOException e) {
            throw new com.mcc.mask.MaskException("读取失败: " + in + " — " + e.getMessage(), e);
        }
    }

    private static final List<String> EXT = List.of(".txt", ".log", ".md");

    public static List<String> exts() {
        return EXT;
    }
}
