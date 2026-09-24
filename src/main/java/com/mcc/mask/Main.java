package com.mcc.mask;

/** MCC脱敏工具 CLI 入口（picocli 子命令在 Task13 挂载）。 */
public final class Main {

    public static String toolName() {
        return "mask-tool";
    }

    public static void main(String[] args) {
        System.out.println(toolName() + " v0.1.0 —— 子命令 mask / unmask / lookup / purge（Task13 接入）");
    }

    private Main() {
    }
}
