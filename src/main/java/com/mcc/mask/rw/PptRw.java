package com.mcc.mask.rw;

import com.mcc.mask.MaskException;
import com.mcc.mask.scan.TextScanner;
import com.mcc.mask.util.AtomicWrite;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hslf.usermodel.HSLFSlide;
import org.apache.poi.hslf.usermodel.HSLFTextParagraph;
import org.apache.poi.hslf.usermodel.HSLFTextRun;
import org.apache.poi.hslf.usermodel.HSLFTextShape;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** .ppt（HSLF 老演示）读写器：幻灯片文本框 run 级替换 + 写回自验。 */
public final class PptRw implements Rw {

    @Override
    public String name() {
        return "ppt";
    }

    @Override
    public RwResult process(Path in, Path out, Ctx ctx) {
        List<String[]> pairsAll = new ArrayList<>();
        try (InputStream is = Files.newInputStream(in); HSLFSlideShow ppt = new HSLFSlideShow(is)) {
            TextScanner s = new TextScanner(ctx.rules(), ctx.book());
            int hits = 0;
            List<String> warnings = new ArrayList<>();
            for (HSLFSlide slide : ppt.getSlides()) {
                for (Object sh : slide.getShapes()) {
                    if (!(sh instanceof HSLFTextShape ts)) {
                        continue;
                    }
                    for (HSLFTextParagraph p : ts.getTextParagraphs()) {
                        String text = paraText(p);
                        if (text == null || text.isEmpty()) {
                            continue;
                        }
                        for (String kw : ctx.rules().credentialPatterns()) {
                            if (text.contains(kw)) {
                                warnings.add("疑似凭据: " + kw);
                            }
                        }
                        List<String[]> pairs = ctx.reverse() ? s.unmaskPairs(text) : s.maskPairs(text);
                        List<RunReplace.RunLike> runs = new ArrayList<>();
                        for (HSLFTextRun r : p.getTextRuns()) {
                            runs.add(new RunLike(r));
                        }
                        for (String[] pair : pairs) {
                            int n = RunReplace.replace(runs, pair[0], pair[1]);
                            hits += n;
                            for (int k = 0; k < n; k++) {
                                pairsAll.add(pair);
                            }
                        }
                    }
                }
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ppt.write(bos);
            AtomicWrite.write(out, bos.toByteArray());
            SelfVerify.run(out, pairsAll, hits, PptRw::extractText, "ppt");
            return new RwResult(hits, warnings);
        } catch (IOException e) {
            throw new MaskException("ppt 处理失败: " + in + " — " + e.getMessage(), e);
        }
    }

    /** 重新打开产物提取全文（自验用）。 */
    public static String extractText(Path pptx) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (InputStream is = Files.newInputStream(pptx); HSLFSlideShow ppt = new HSLFSlideShow(is)) {
            for (HSLFSlide slide : ppt.getSlides()) {
                for (Object sh : slide.getShapes()) {
                    if (sh instanceof HSLFTextShape ts) {
                        for (HSLFTextParagraph p : ts.getTextParagraphs()) {
                            sb.append(paraText(p)).append('\n');
                        }
                    }
                }
            }
        }
        return sb.toString();
    }

    private static String paraText(HSLFTextParagraph p) {
        StringBuilder sb = new StringBuilder();
        for (HSLFTextRun r : p.getTextRuns()) {
            String t = r.getRawText();
            if (t != null) {
                sb.append(t);
            }
        }
        return sb.toString();
    }

    private static final class RunLike implements RunReplace.RunLike {
        private final HSLFTextRun run;

        RunLike(HSLFTextRun run) {
            this.run = run;
        }

        @Override
        public String text() {
            String t = run.getRawText();
            return t == null ? "" : t;
        }

        @Override
        public void setText(String t) {
            run.setText(t);
        }
    }
}
