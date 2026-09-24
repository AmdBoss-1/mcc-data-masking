package com.mcc.mask.rw;

import com.mcc.mask.codebook.Codebook;
import com.mcc.mask.codebook.CodebookJson;
import com.mcc.mask.rules.RuleTable;
import com.mcc.mask.util.Summary;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PdfScanRwTest {

    private final RuleTable rules = RuleTable.defaultTable();

    @Test
    void detectsPerPageAndWritesNothing(@TempDir Path dir) throws Exception {
        Path pdf = dir.resolve("t.pdf");
        try (PDDocument doc = new PDDocument()) {
            for (String[] line : new String[][] {
                    {"page1 order 100 pcs"},
                    {"page2 call 13812345678 now"},
            }) {
                PDPage page = new PDPage();
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    cs.newLineAtOffset(50, 700);
                    cs.showText(line[0]);
                    cs.endText();
                }
            }
            doc.save(pdf.toFile());
        }
        Path out = dir.resolve("should-not-exist.pdf");
        Codebook book = new Codebook(new CodebookJson());
        Summary sum = new Summary();
        Rw.RwResult r = RwFactory.byPath(pdf).process(pdf, out, Ctx.mask(book, rules, sum));
        assertEquals(1, r.hits());
        assertEquals(1, r.warnings().size());
        assertTrue(r.warnings().get(0).contains("第 2 页"), r.warnings().toString());
        assertTrue(r.warnings().get(0).contains("仅检测不替换"), r.warnings().toString());
        assertFalse(Files.exists(out), "PDF 只检测，不得产出文件");
    }
}
