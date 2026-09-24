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
}
