package com.mcc.mask.rw;

import com.mcc.mask.MaskException;
import com.mcc.mask.scan.TextScanner;
import com.mcc.mask.util.AtomicWrite;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.usermodel.Range;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** .doc（HWPF 老 Word）读写器：全文替换 + 写回自验（保真差就报错出局，不产坏文件）。 */
public final class DocRw implements Rw {

    @Override
    public String name() {
        return "doc";
    }

    @Override
    public RwResult process(Path in, Path out, Ctx ctx) {
        try (InputStream is = Files.newInputStream(in); HWPFDocument doc = new HWPFDocument(is)) {
            Range range = doc.getRange();
            String text = range.text();
            TextScanner s = new TextScanner(ctx.rules(), ctx.book());
            List<String> warnings = new ArrayList<>();
            for (String kw : ctx.rules().credentialPatterns()) {
                if (text.contains(kw)) {
                    warnings.add("疑似凭据: " + kw);
                }
            }
            List<String[]> pairs = ctx.reverse() ? s.unmaskPairs(text) : s.maskPairs(text);
            for (String[] p : pairs) {
                range.replaceText(p[0], p[1]); // POI 返回 void；处数由写回自验兜底
            }
            int hits = pairs.size();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            doc.write(bos);
            AtomicWrite.write(out, bos.toByteArray());
            SelfVerify.run(out, pairs, pairs.size(), DocRw::extractText, "doc");
            return new RwResult(hits, warnings);
        } catch (IOException e) {
            throw new MaskException("doc 处理失败: " + in + " — " + e.getMessage(), e);
        }
    }

    public static String extractText(Path docFile) throws IOException {
        try (InputStream is = Files.newInputStream(docFile); HWPFDocument doc = new HWPFDocument(is)) {
            return doc.getRange().text();
        }
    }
}
