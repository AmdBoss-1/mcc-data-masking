package com.mcc.mask.rw;

import com.mcc.mask.scan.TextScanner;
import com.mcc.mask.util.AtomicWrite;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
            String text = Files.readString(in, StandardCharsets.UTF_8);
            TextScanner s = new TextScanner(ctx.rules(), ctx.book());
            TextScanner.ScanResult r = ctx.reverse() ? s.unmaskText(text) : s.maskText(text);
            AtomicWrite.write(out, r.text().getBytes(StandardCharsets.UTF_8));
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
