package com.mcc.mask.util;

import java.util.ArrayList;
import java.util.List;

/** 运行摘要收集器：每文件命中数、告警、自验结果；CLI 层统一打印。 */
public final class Summary {

    private final List<String> lines = new ArrayList<>();
    private final List<String> credentialWarnings = new ArrayList<>();
    private int totalHits;

    public void fileDone(String name, int hits, List<String> warnings) {
        totalHits += hits;
        lines.add(String.format("%s：替换 %d 处", name, hits));
        for (String w : warnings) {
            credentialWarnings.add(name + " → " + w);
        }
    }

    public void note(String line) {
        lines.add(line);
    }

    public List<String> lines() {
        return lines;
    }

    public List<String> credentialWarnings() {
        return credentialWarnings;
    }

    public int totalHits() {
        return totalHits;
    }
}
