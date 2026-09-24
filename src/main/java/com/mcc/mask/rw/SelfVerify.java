package com.mcc.mask.rw;

import com.mcc.mask.MaskException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 写回自验（.doc/.ppt 等保真风险高的格式用）：
 * 产物必须能重新打开；假值出现次数=预期替换数；原值不得残留。任何一条不过 → 删除产物并报错，绝不产坏文件。
 */
public final class SelfVerify {

    public interface TextExtractor {
        String extract(Path p) throws IOException;
    }

    public static void run(Path produced, List<String[]> pairs, int expectedHits, TextExtractor ex, String label) {
        String t;
        try {
            t = ex.extract(produced);
        } catch (Exception e) {
            deleteQuietly(produced);
            throw new MaskException(label + " 写回自验失败：产物无法重新打开（" + e.getMessage() + "），已删除产物");
        }
        int fakes = 0;
        for (String[] p : pairs) {
            fakes += count(t, p[1]);
            if (!p[1].contains(p[0]) && count(t, p[0]) > 0) {
                deleteQuietly(produced);
                throw new MaskException(label + " 写回自验失败：原值残留（" + p[0] + "），已删除产物");
            }
        }
        if (fakes != expectedHits) {
            deleteQuietly(produced);
            throw new MaskException(label + " 写回自验失败：假值处数 " + fakes + " ≠ 预期 " + expectedHits + "，已删除产物");
        }
    }

    static int count(String hay, String needle) {
        if (needle.isEmpty()) {
            return 0;
        }
        int n = 0;
        int i = 0;
        while ((i = hay.indexOf(needle, i)) >= 0) {
            n++;
            i += needle.length();
        }
        return n;
    }

    private static void deleteQuietly(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException ignore) {
            // 尽力而为
        }
    }

    private SelfVerify() {
    }
}
