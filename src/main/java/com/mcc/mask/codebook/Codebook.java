package com.mcc.mask.codebook;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 码本内存模型：同码本内同原值恒同假值；支持反查与跨文件累积。 */
public class Codebook {

    /** 假值生成器（Task4 的 Transformer 以 lambda 适配本接口）。 */
    public interface FakeGen {
        String fake(String orig);
    }

    private final CodebookJson json;
    private int replaced;
    private String currentFile;

    public Codebook(CodebookJson json) {
        this.json = json;
    }

    /**
     * 开始处理某个产物文件：之后的映射都记到它名下（对照表按产物归属显示）。
     * 传 null 表示不记录（还原方向不产生新对照）。
     */
    public void beginFile(String product) {
        this.currentFile = product;
    }

    public String getOrFake(String ruleId, String orig, FakeGen gen) {
        Map<String, String> m = json.maps.computeIfAbsent(ruleId, k -> new LinkedHashMap<>());
        String fake = m.get(orig);
        if (fake == null) {
            fake = gen.fake(orig);
            m.put(orig, fake);
        }
        if (currentFile != null) {
            List<String> pairs = json.byFile.computeIfAbsent(currentFile, k -> new ArrayList<>());
            String key = ruleId + "\u0001" + orig;
            if (!pairs.contains(key)) {
                pairs.add(key);
            }
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
