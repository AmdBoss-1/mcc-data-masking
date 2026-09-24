package com.mcc.mask.rw;

import com.mcc.mask.MaskException;
import com.mcc.mask.scan.TextScanner;
import com.mcc.mask.util.AtomicWrite;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFHeaderFooter;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** .docx 读写器：正文+表格+页眉页脚，run 级保格式替换。 */
public final class DocxRw implements Rw {

    @Override
    public String name() {
        return "docx";
    }

    @Override
    public RwResult process(Path in, Path out, Ctx ctx) {
        try (InputStream is = Files.newInputStream(in); XWPFDocument doc = new XWPFDocument(is)) {
            List<XWPFParagraph> paras = new ArrayList<>(doc.getParagraphs());
            collectTables(doc.getTables(), paras);
            for (XWPFHeaderFooter hf : doc.getHeaderList()) {
                paras.addAll(hf.getParagraphs());
                collectTables(hf.getTables(), paras);
            }
            for (XWPFHeaderFooter hf : doc.getFooterList()) {
                paras.addAll(hf.getParagraphs());
                collectTables(hf.getTables(), paras);
            }
            TextScanner s = new TextScanner(ctx.rules(), ctx.book());
            int hits = 0;
            List<String> warnings = new ArrayList<>();
            for (XWPFParagraph p : paras) {
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
                for (XWPFRun r : p.getRuns()) {
                    runs.add(new RunLike(r));
                }
                for (String[] pair : pairs) {
                    hits += RunReplace.replace(runs, pair[0], pair[1]);
                }
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            doc.write(bos);
            AtomicWrite.write(out, bos.toByteArray());
            return new RwResult(hits, warnings);
        } catch (IOException e) {
            throw new MaskException("docx 处理失败: " + in + " — " + e.getMessage(), e);
        }
    }

    private static void collectTables(List<XWPFTable> tables, List<XWPFParagraph> out) {
        for (XWPFTable t : tables) {
            t.getRows().forEach(row -> row.getTableCells().forEach(cell -> out.addAll(cell.getParagraphs())));
        }
    }

    /** XWPFRun 适配器（首 t 元素文本）。 */
    private static final class RunLike implements RunReplace.RunLike {
        private final XWPFRun run;

        RunLike(XWPFRun run) {
            this.run = run;
        }

        @Override
        public String text() {
            try {
                String t = run.getText(0);
                return t == null ? "" : t;
            } catch (IndexOutOfBoundsException e) {
                return "";
            }
        }

        @Override
        public void setText(String t) {
            run.setText(t, 0);
        }
    }
}
