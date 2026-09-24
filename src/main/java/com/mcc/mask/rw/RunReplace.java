package com.mcc.mask.rw;

import java.util.ArrayList;
import java.util.List;

/** 跨 run 保格式替换：按段落拼串定位，匹配边界切 run，替换文本落在首个命中 run（继承其格式）。 */
public final class RunReplace {

    /** 适配 XWPFRun / XSLFTextRun 等。 */
    public interface RunLike {
        String text();

        void setText(String t);
    }

    public static int replace(List<RunLike> runs, String find, String repl) {
        if (runs.isEmpty() || find.isEmpty()) {
            return 0;
        }
        int[] start = new int[runs.size()];
        StringBuilder full = new StringBuilder();
        for (int i = 0; i < runs.size(); i++) {
            start[i] = full.length();
            full.append(runs.get(i).text());
        }
        String s = full.toString();
        List<int[]> matches = new ArrayList<>();
        int idx = 0;
        while ((idx = s.indexOf(find, idx)) >= 0) {
            matches.add(new int[] {idx, idx + find.length()});
            idx += find.length();
        }
        if (matches.isEmpty()) {
            return 0;
        }
        // 从后往前替换，前面的偏移保持有效
        for (int mi = matches.size() - 1; mi >= 0; mi--) {
            int st = matches.get(mi)[0];
            int en = matches.get(mi)[1];
            boolean replaced = false;
            for (int i = 0; i < runs.size(); i++) {
                int rs = start[i];
                int re = rs + runs.get(i).text().length();
                if (re <= st || rs >= en) {
                    continue; // 不相交
                }
                String pre = st <= rs ? "" : runs.get(i).text().substring(0, st - rs);
                String post = en >= re ? "" : runs.get(i).text().substring(en - rs);
                if (!replaced) {
                    runs.get(i).setText(pre + repl + post);
                    replaced = true;
                } else {
                    runs.get(i).setText(pre + post);
                }
            }
        }
        return matches.size();
    }

    private RunReplace() {
    }
}
