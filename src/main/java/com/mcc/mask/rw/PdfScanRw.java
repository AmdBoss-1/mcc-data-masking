package com.mcc.mask.rw;

import com.mcc.mask.MaskException;
import com.mcc.mask.rules.RuleTable;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

/** PDF：v1 仅检测+红字报告（逐页命中哪类敏感串），不产出替换文件（替换/打码归 D 线）。 */
public final class PdfScanRw implements Rw {

    @Override
    public String name() {
        return "pdf-scan";
    }

    @Override
    public RwResult process(Path in, Path out, Ctx ctx) {
        List<String> warnings = new ArrayList<>();
        int hits = 0;
        try (PDDocument doc = Loader.loadPDF(in.toFile())) {
            int pages = doc.getNumberOfPages();
            for (int p = 1; p <= pages; p++) {
                PDFTextStripper st = new PDFTextStripper();
                st.setStartPage(p);
                st.setEndPage(p);
                String text = st.getText(doc);
                for (RuleTable.StructRule r : ctx.rules().structRules()) {
                    Matcher m = r.pattern().matcher(text);
                    int n = 0;
                    while (m.find()) {
                        n++;
                    }
                    if (n > 0) {
                        hits += n;
                        warnings.add("PDF 第 " + p + " 页命中 " + r.id() + "×" + n + "（仅检测不替换）");
                    }
                }
            }
        } catch (IOException e) {
            throw new MaskException("PDF 读取失败: " + in + " — " + e.getMessage(), e);
        }
        return new RwResult(hits, warnings);
    }
}
