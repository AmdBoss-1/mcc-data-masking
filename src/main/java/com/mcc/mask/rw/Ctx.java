package com.mcc.mask.rw;

import com.mcc.mask.codebook.Codebook;
import com.mcc.mask.rules.RuleTable;
import com.mcc.mask.util.Summary;

/** 一次 mask/unmask 运行的上下文。jitter=true 时对数值做 ±3% 微扰（默认关）。 */
public record Ctx(Codebook book, RuleTable rules, boolean reverse, Summary sum, boolean jitter) {

    public static Ctx mask(Codebook book, RuleTable rules, Summary sum) {
        return new Ctx(book, rules, false, sum, false);
    }

    public static Ctx mask(Codebook book, RuleTable rules, Summary sum, boolean jitter) {
        return new Ctx(book, rules, false, sum, jitter);
    }

    public static Ctx unmask(Codebook book, RuleTable rules, Summary sum) {
        return new Ctx(book, rules, true, sum, false);
    }
}
