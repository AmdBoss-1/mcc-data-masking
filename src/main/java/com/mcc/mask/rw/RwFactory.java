package com.mcc.mask.rw;

import com.mcc.mask.MaskException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** 格式工厂：扩展名→读写器；未知扩展名做魔数嗅探给出明确报错。 */
public final class RwFactory {

    private static final Map<String, Rw> REG = new LinkedHashMap<>();

    static {
        TextRw text = new TextRw();
        REG.put(".txt", text);
        REG.put(".log", text);
        REG.put(".md", text);
        CsvRw csv = new CsvRw();
        REG.put(".csv", csv);
        XlsxRw xlsx = new XlsxRw();
        REG.put(".xlsx", xlsx);
        REG.put(".xlsm", xlsx);
        REG.put(".xls", new XlsRw());
        REG.put(".docx", new DocxRw());
        REG.put(".pptx", new PptxRw());
        REG.put(".doc", new DocRw());
        REG.put(".ppt", new PptRw());
        H2Rw h2 = new H2Rw();
        REG.put(".mv.db", h2);
        REG.put(".mccbak", h2);
        REG.put(".sql", new SqlDumpRw());
        REG.put(".pdf", new PdfScanRw());
    }

    /** 后续任务注册新读写器（T8-T12）。 */
    public static void register(String ext, Rw rw) {
        REG.put(ext.toLowerCase(Locale.ROOT), rw);
    }

    public static Rw byPath(Path p) {
        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
        if (n.endsWith(".mv.db")) {
            Rw h2 = REG.get(".mv.db");
            if (h2 != null) {
                return h2;
            }
        }
        int dot = n.lastIndexOf('.');
        String ext = dot < 0 ? "" : n.substring(dot);
        Rw rw = REG.get(ext);
        if (rw != null) {
            return rw;
        }
        throw new MaskException("不支持的格式 " + ext + "（" + sniffHint(p) + "）。支持："
                + String.join(" ", REG.keySet()) + "；Office 老格式请另存为 xlsx/docx/pptx");
    }

    private static String sniffHint(Path p) {
        try (InputStream in = Files.newInputStream(p)) {
            byte[] head = in.readNBytes(4);
            if (head.length >= 4 && head[0] == 'P' && head[1] == 'K') {
                return "zip/OOXML 系";
            }
            if (head.length >= 4 && (head[0] & 0xFF) == 0xD0 && (head[1] & 0xFF) == 0xCF) {
                return "OLE 老二进制系";
            }
            if (head.length >= 4 && head[0] == '%' && head[1] == 'P') {
                return "PDF";
            }
        } catch (IOException ignore) {
            // 嗅探失败仅影响提示文案
        }
        return "未知类型";
    }

    private RwFactory() {
    }
}
