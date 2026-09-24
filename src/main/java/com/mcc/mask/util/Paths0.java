package com.mcc.mask.util;

import java.nio.file.Path;

/** 产物命名：在同目录给文件名插后缀（CLI 与网页控制台共用，保证两边产物名一致）。 */
public final class Paths0 {

    public static Path sib(Path in, String suffix) {
        String n = in.getFileName().toString();
        int dot = n.lastIndexOf('.');
        return dot < 0 ? in.resolveSibling(n + suffix)
                : in.resolveSibling(n.substring(0, dot) + suffix + n.substring(dot));
    }

    private Paths0() {
    }
}
