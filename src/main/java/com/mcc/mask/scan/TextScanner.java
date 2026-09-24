package com.mcc.mask.scan;

import com.mcc.mask.codebook.Codebook;
import com.mcc.mask.rules.RuleTable;
import com.mcc.mask.rules.Transformer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

/** 双向文本替换：mask=结构化正则整串命中→码本；unmask=码本反向往返（最长优先）。 */
public final class TextScanner {

    public record ScanResult(String text, int hits, List<String> warnings) {
    }

    private record Hit(int start, int end, String ruleId, String orig) {
    }

    private final RuleTable rules;
    private final Codebook book;

    public TextScanner(RuleTable rules, Codebook book) {
        this.rules = rules;
        this.book = book;
    }

    public ScanResult maskText(String text) {
        List<String> warnings = new ArrayList<>();
        for (String kw : rules.credentialPatterns()) {
            if (text.contains(kw)) {
                warnings.add("疑似凭据: " + kw);
            }
        }
        List<Hit> hits = new ArrayList<>();
        for (RuleTable.StructRule rule : rules.structRules()) {
            Matcher m = rule.pattern().matcher(text);
            while (m.find()) {
                String orig = m.group();
                int st = m.start();
                boolean overlap = hits.stream().anyMatch(h -> st < h.end() && h.start() < st + orig.length());
                if (!overlap) {
                    hits.add(new Hit(st, st + orig.length(), rule.id(), orig));
                }
            }
        }
        hits.sort(Comparator.comparingInt(Hit::start));
        StringBuilder sb = new StringBuilder();
        int pos = 0;
        for (Hit h : hits) {
            sb.append(text, pos, h.start());
            Transformer t = rules.transformerFor(h.ruleId());
            String ruleId = h.ruleId();
            sb.append(book.getOrFake(ruleId, h.orig(), orig -> t.fake(orig, seed(ruleId, orig))));
            pos = h.end();
        }
        sb.append(text.substring(pos));
        return new ScanResult(sb.toString(), hits.size(), warnings);
    }

    public ScanResult unmaskText(String text) {
        List<Map.Entry<String, String>> pairs = new ArrayList<>();
        for (Map<String, String> m : book.maps().values()) {
            pairs.addAll(m.entrySet());
        }
        pairs.sort(Comparator.comparingInt((Map.Entry<String, String> e) -> e.getValue().length()).reversed());
        String out = text;
        int hits = 0;
        for (Map.Entry<String, String> e : pairs) {
            String fake = e.getValue();
            int idx = out.indexOf(fake);
            while (idx >= 0) {
                hits++;
                idx = out.indexOf(fake, idx + e.getKey().length());
            }
            out = out.replace(fake, e.getKey());
        }
        return new ScanResult(out, hits, List.of());
    }

    /** 表格单元格：列名命中→整格变换；否则走 maskText。 */
    public ScanResult maskCell(String column, String value) {
        String ruleId = rules.columnRules().get(column == null ? "" : column.trim());
        if (ruleId == null || value == null || value.isEmpty()) {
            return maskText(value == null ? "" : value);
        }
        Transformer t = rules.transformerFor(ruleId);
        String rid = ruleId;
        String fake = book.getOrFake(rid, value, orig -> t.fake(orig, seed(rid, orig)));
        return new ScanResult(fake, 1, List.of());
    }

    private static long seed(String ruleId, String orig) {
        return (ruleId + "|" + orig).hashCode();
    }
}