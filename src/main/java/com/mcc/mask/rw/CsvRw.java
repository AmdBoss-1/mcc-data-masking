package com.mcc.mask.rw;

import com.mcc.mask.scan.TextScanner;
import com.mcc.mask.util.AtomicWrite;
import com.mcc.mask.util.TextCodec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** CSV：首行表头→列名命中整格变换；未命中列走结构化文本替换。 */
public final class CsvRw implements Rw {

    @Override
    public String name() {
        return "csv";
    }

    @Override
    public RwResult process(Path in, Path out, Ctx ctx) {
        try {
            TextCodec.Decoded d = TextCodec.read(in);
            byte[] raw = Files.readAllBytes(in);
            List<String> lines = splitLines(d.text());
            TextScanner s = new TextScanner(ctx.rules(), ctx.book());
            int hits = 0;
            java.util.List<String> warnings = new java.util.ArrayList<>();
            StringBuilder sb = new StringBuilder();
            String[] header = null;
            for (int li = 0; li < lines.size(); li++) {
                String line = lines.get(li);
                if (line.isEmpty()) {
                    sb.append('\n');
                    continue;
                }
                List<String> cells = CsvUtil.parseLine(line);
                if (header == null) {
                    header = cells.toArray(new String[0]);
                    for (int i = 0; i < header.length; i++) {
                        header[i] = header[i].trim();
                    }
                    sb.append(line).append('\n');
                    continue;
                }
                for (int i = 0; i < cells.size(); i++) {
                    String col = header != null && i < header.length ? header[i] : "";
                    TextScanner.ScanResult r = ctx.reverse()
                            ? s.unmaskText(cells.get(i))
                            : s.maskCell(col, cells.get(i));
                    hits += r.hits();
                    warnings.addAll(r.warnings());
                    cells.set(i, r.text());
                }
                sb.append(CsvUtil.join(cells)).append('\n');
            }
            byte[] bytes = d.encode(sb.toString());
            // 与原文件行尾一致性：原文件末行无换行则去掉多出的一个
            if (raw.length > 0 && raw[raw.length - 1] != '\n' && bytes.length > 0 && bytes[bytes.length - 1] == '\n') {
                bytes = java.util.Arrays.copyOf(bytes, bytes.length - 1);
            }
            AtomicWrite.write(out, bytes);
            return new RwResult(hits, warnings);
        } catch (IOException e) {
            throw new com.mcc.mask.MaskException("CSV 处理失败: " + in + " — " + e.getMessage(), e);
        }
    }

    /** 拆行（不丢空行、不残留 \r）：写回时按 \n 统一重建，与原行为一致。 */
    private static List<String> splitLines(String text) {
        String t = text;
        if (t.endsWith("\r\n")) {
            t = t.substring(0, t.length() - 2);
        } else if (t.endsWith("\n")) {
            t = t.substring(0, t.length() - 1);
        }
        List<String> out = new java.util.ArrayList<>();
        for (String l : t.split("\n", -1)) {
            out.add(l.endsWith("\r") ? l.substring(0, l.length() - 1) : l);
        }
        if (out.size() == 1 && out.get(0).isEmpty()) {
            out.clear();
        }
        return out;
    }
}
