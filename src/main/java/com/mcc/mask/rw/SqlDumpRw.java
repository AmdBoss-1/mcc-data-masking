package com.mcc.mask.rw;

import com.mcc.mask.MaskException;
import com.mcc.mask.scan.TextScanner;
import com.mcc.mask.util.AtomicWrite;
import com.mcc.mask.util.TextCodec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** SQL dump（H2 SCRIPT 导出/套件备份明文 SQL）：按文本规则双向替换。 */
public final class SqlDumpRw implements Rw {

    @Override
    public String name() {
        return "sql";
    }

    @Override
    public RwResult process(Path in, Path out, Ctx ctx) {
        try {
            TextCodec.Decoded d = TextCodec.read(in);
            String text = d.text();
            TextScanner s = new TextScanner(ctx.rules(), ctx.book());
            TextScanner.ScanResult r = ctx.reverse() ? s.unmaskText(text) : s.maskText(text);
            AtomicWrite.write(out, d.encode(r.text()));
            return new RwResult(r.hits(), r.warnings());
        } catch (IOException e) {
            throw new MaskException("SQL 处理失败: " + in + " — " + e.getMessage(), e);
        }
    }

    public static List<String> exts() {
        return List.of(".sql");
    }
}
