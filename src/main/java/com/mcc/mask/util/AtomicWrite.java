package com.mcc.mask.util;

import com.mcc.mask.MaskException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** 原子写：先写同目录 .tmp 再 ATOMIC move，失败不留半截文件。 */
public final class AtomicWrite {

    public static void write(Path out, byte[] data) {
        Path tmp = out.resolveSibling(out.getFileName() + ".tmp");
        try {
            Path parent = out.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(tmp, data);
            try {
                Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignore) {
                // 临时文件清理失败不影响报错
            }
            throw new MaskException("写入失败(原文件未改动): " + out + " — " + e.getMessage(), e);
        }
    }

    private AtomicWrite() {
    }
}
