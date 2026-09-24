package com.mcc.mask.cli;

import com.mcc.mask.MaskException;

import java.io.Console;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** 口令输入：优先交互（不落盘）；批处理可 --pass-file（单行文件），使用时红字警告。 */
public final class Console0 {

    public static char[] read(String prompt) {
        Console c = System.console();
        if (c != null) {
            char[] p = c.readPassword("%s", prompt);
            if (p == null || p.length == 0) {
                throw new MaskException("口令不能为空");
            }
            return p;
        }
        System.out.print(prompt + "（非交互终端，明文回显，建议改用 --pass-file）: ");
        try {
            String line = new String(System.in.readAllBytes(), StandardCharsets.UTF_8).trim();
            if (line.isEmpty()) {
                throw new MaskException("口令不能为空");
            }
            return line.toCharArray();
        } catch (IOException e) {
            throw new MaskException("读取口令失败: " + e.getMessage(), e);
        }
    }

    public static char[] fromFile(Path passFile) {
        try {
            List<String> lines = Files.readAllLines(passFile, StandardCharsets.UTF_8);
            String s = lines.isEmpty() ? "" : lines.get(0).trim();
            if (s.isEmpty()) {
                throw new MaskException("口令文件为空: " + passFile);
            }
            System.err.println("⚠ 已使用口令文件，请确保其权限受控（不进 git、不上云）");
            return s.toCharArray();
        } catch (IOException e) {
            throw new MaskException("读取口令文件失败: " + e.getMessage(), e);
        }
    }

    private Console0() {
    }
}
