package com.mcc.mask.rules;

/** 变换器：由原值+种子确定性地产出假值（同种子同输出，便于测试与复跑）。 */
public interface Transformer {
    String fake(String orig, long seed);
}
