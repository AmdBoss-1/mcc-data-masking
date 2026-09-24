package com.mcc.mask.rw;

import com.mcc.mask.MaskException;
import com.mcc.mask.scan.TextScanner;
import com.mcc.mask.util.AtomicWrite;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFGroupShape;
import org.apache.poi.xslf.usermodel.XSLFNotes;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.apache.poi.xslf.usermodel.XSLFTextShape;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** .pptx 读写器：幻灯片+备注+组合形状内文本框，run 级替换。 */
public final class PptxRw implements Rw {

    @Override
    public String name() {
        return "pptx";
    }

    @Override
    public RwResult process(Path in, Path out, Ctx ctx) {
        try (InputStream is = Files.newInputStream(in); XMLSlideShow ppt = new XMLSlideShow(is)) {
            TextScanner s = new TextScanner(ctx.rules(), ctx.book());
            int hits = 0;
            List<String> warnings = new ArrayList<>();
            for (XSLFSlide slide : ppt.getSlides()) {
                hits += processShapes(slide.getShapes(), s, ctx, warnings);
                XSLFNotes notes = slide.getNotes();
                if (notes != null) {
                    hits += processShapes(notes.getShapes(), s, ctx, warnings);
                }
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ppt.write(bos);
            AtomicWrite.write(out, bos.toByteArray());
            return new RwResult(hits, warnings);
        } catch (IOException e) {
            throw new MaskException("pptx 处理失败: " + in + " — " + e.getMessage(), e);
        }
    }

    private int processShapes(List<XSLFShape> shapes, TextScanner s, Ctx ctx, List<String> warnings) {
        int hits = 0;
        for (XSLFShape sh : shapes) {
            if (sh instanceof XSLFGroupShape gs) {
                hits += processShapes(gs.getShapes(), s, ctx, warnings);
            } else if (sh instanceof XSLFTextShape ts) {
                for (var p : ts.getTextParagraphs()) {
                    String text = p.getText();
                    if (text == null || text.isEmpty()) {
                        continue;
                    }
                    for (String kw : ctx.rules().credentialPatterns()) {
                        if (text.contains(kw)) {
                            warnings.add("疑似凭据: " + kw);
                        }
                    }
                    List<String[]> pairs = ctx.reverse() ? s.unmaskPairs(text) : s.maskPairs(text);
                    if (pairs.isEmpty()) {
                        continue;
                    }
                    List<RunReplace.RunLike> runs = new ArrayList<>();
                    for (XSLFTextRun r : p.getTextRuns()) {
                        runs.add(new RunLike(r));
                    }
                    for (String[] pair : pairs) {
                        hits += RunReplace.replace(runs, pair[0], pair[1]);
                    }
                }
            }
        }
        return hits;
    }

    private static final class RunLike implements RunReplace.RunLike {
        private final XSLFTextRun run;

        RunLike(XSLFTextRun run) {
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
