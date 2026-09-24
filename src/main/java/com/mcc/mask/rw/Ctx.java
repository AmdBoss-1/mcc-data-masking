package com.mcc.mask.rw;

import com.mcc.mask.codebook.Codebook;
import com.mcc.mask.rules.RuleTable;
import com.mcc.mask.util.Summary;

/** 一次 mask/unmask 运行的上下文。 */
public record Ctx(Codebook book, RuleTable rules, boolean reverse, Summary sum) {

    public static Ctx mask(Codebook book, RuleTable rules, Summary sum) {
        return new Ctx(book, rules, false, sum);
    }

    public static Ctx unmask(Codebook book, RuleTable rules, Summary sum) {
        return new Ctx(book, rules, true, sum);
    }
}
