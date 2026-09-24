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
        List<Hit> hits = collectHits(text);
        hits.sort(Comparator.comparingInt(Hit::start));
        StringBuilder sb = new StringBuilder();
        int pos = 0;
        for (Hit h : hits) {
            sb.append(text, pos, h.start());
            sb.append(fakeFor(h));
            pos = h.end();
        }
        sb.append(text.substring(pos));
        return new ScanResult(sb.toString(), hits.size(), warnings);
    }

    /** 与 maskText 相同的命中逻辑，但返回 {原值,假值} 对（供 run 级替换使用）。 */
    public List<String[]> maskPairs(String text) {
        List<String[]> pairs = new ArrayList<>();
        for (Hit h : collectHits(text)) {
            pairs.add(new String[] {h.orig(), fakeFor(h)});
        }
        return pairs;
    }

    /** unmask 用：码本中出现在 text 里的假值 → {假值,原值}，最长优先。 */
    public List<String[]> unmaskPairs(String text) {
        List<String[]> pairs = new ArrayList<>();
        for (Map<String, String> m : book.maps().values()) {
            for (Map.Entry<String, String> e : m.entrySet()) {
                if (text.contains(e.getValue())) {
                    pairs.add(new String[] {e.getValue(), e.getKey()});
                }
            }
        }
        pairs.sort(Comparator.comparingInt((String[] p) -> p[0].length()).reversed());
        return pairs;
    }

    private List<Hit> collectHits(String text) {
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
        return hits;
    }

    private String fakeFor(Hit h) {
        Transformer t = rules.transformerFor(h.ruleId());
        String rid = h.ruleId();
        return book.getOrFake(rid, h.orig(), orig -> t.fake(orig, seed(rid, orig)));
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

    /** 表格单元格：列名命中→整格变换（查表大小写不敏感）；结构化规则要求值形匹配，不匹配退回文本扫描。 */
    public ScanResult maskCell(String column, String value) {
        String key = column == null ? "" : column.trim().toLowerCase(java.util.Locale.ROOT);
        String ruleId = rules.columnRules().get(key);
        if (ruleId == null || value == null || value.isEmpty()) {
            return maskText(value == null ? "" : value);
        }
        for (RuleTable.StructRule sr : rules.structRules()) {
            if (sr.id().equals(ruleId) && !sr.pattern().matcher(value).matches()) {
                return maskText(value); // 列名说该是车牌但值不是车牌形：不硬套
            }
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