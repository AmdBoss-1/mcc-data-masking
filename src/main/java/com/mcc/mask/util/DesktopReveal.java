package com.mcc.mask.util;

import com.mcc.mask.MaskException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * 在本机文件管理器里定位一个路径（网页控制台点「打开位置」用）。
 * 服务只绑 127.0.0.1，页面和文件在同一台机器上，所以直接起系统命令是合理的；
 * 路径由调用方（Api.reveal）先过 Workspace 的越界护栏，这里只管开。
 */
public final class DesktopReveal {

    /** 文件 → 打开所在目录并选中它；目录 → 直接打开该目录。起完不等退出（explorer 成功也返回非 0）。 */
    public static void open(Path p) {
        boolean isDir = Files.isDirectory(p);
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String[] cmd;
        if (os.contains("win")) {
            cmd = isDir ? new String[]{"explorer.exe", p.toString()}
                    : new String[]{"explorer.exe", "/select," + p};
        } else if (os.contains("mac")) {
            cmd = isDir ? new String[]{"open", p.toString()}
                    : new String[]{"open", "-R", p.toString()};
        } else {
            cmd = new String[]{"xdg-open", (isDir ? p : p.getParent()).toString()};
        }
        try {
            new ProcessBuilder(cmd).redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        } catch (IOException e) {
            throw new MaskException("打不开文件位置：" + e.getMessage(), e);
        }
    }

    private DesktopReveal() {
    }
}
