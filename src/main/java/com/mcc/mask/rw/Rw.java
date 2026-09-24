package com.mcc.mask.rw;

import java.nio.file.Path;
import java.util.List;

/** 格式读写器：同一实现双向（ctx.reverse 决定方向）。 */
public interface Rw {

    record RwResult(int hits, List<String> warnings) {
    }

    String name();

    RwResult process(Path in, Path out, Ctx ctx);
}
