package com.mcc.mask.codebook;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 码本明文结构（Jackson 序列化后整体 AES-GCM 加密落盘）。 */
public class CodebookJson {
    public long created;
    public String rulesVersion = "v1";
    public List<String> files = new ArrayList<>();
    /** ruleId -> (原值 -> 假值) */
    public Map<String, Map<String, String>> maps = new LinkedHashMap<>();
    /**
     * 产物文件 → 该产物用到的 "ruleId\u0001原值" 列表。
     * 对照表据此跟随产物：产物不在工作目录了，对应映射默认不显示。
     * 老码本没有这个字段（Jackson 留空），读取端按"无归属信息"兜底。
     */
    public Map<String, List<String>> byFile = new LinkedHashMap<>();
}
