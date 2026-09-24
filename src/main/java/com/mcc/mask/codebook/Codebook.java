package com.mcc.mask.codebook;

import java.util.LinkedHashMap;
import java.util.Map;

/** 码本内存模型：同码本内同原值恒同假值；支持反查与跨文件累积。 */
public class Codebook {

    /** 假值生成器（Task4 的 Transformer 以 lambda 适配本接口）。 */
    public interface FakeGen {
        String fake(String orig);
    }

    private final CodebookJson json;
    private int replaced;

    public Codebook(CodebookJson json) {
        this.json = json;
    }

    public String getOrFake(String ruleId, String orig, FakeGen gen) {
        Map<String, String> m = json.maps.computeIfAbsent(ruleId, k -> new LinkedHashMap<>());
        String fake = m.get(orig);
        if (fake == null) {
            fake = gen.fake(orig);
            m.put(orig, fake);
        }
        replaced++;
        return fake;
    }

    /** 假值→原值；未命中返回 null。 */
    public String reverse(String fake) {
        for (Map<String, String> m : json.maps.values()) {
            for (Map.Entry<String, String> e : m.entrySet()) {
                if (e.getValue().equals(fake)) {
                    return e.getKey();
                }
            }
        }
        return null;
    }

    public Map<String, Map<String, String>> maps() {
        return json.maps;
    }

    public CodebookJson json() {
        return json;
    }

    public void touchFile(String name) {
        if (!json.files.contains(name)) {
            json.files.add(name);
        }
    }

    public int replacedCount() {
        return replaced;
    }
}
