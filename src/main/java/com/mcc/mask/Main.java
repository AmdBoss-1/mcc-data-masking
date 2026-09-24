package com.mcc.mask;

/** MCC脱敏工具 CLI 入口（picocli 子命令在 cli 包）。 */
public final class Main {

    /** 工具版本（与 pom 同步；控制台 /api/state 报这个）。 */
    public static final String VERSION = "1.1.0";

    public static String toolName() {
        return "mask-tool";
    }

    public static void main(String[] args) {
        System.exit(com.mcc.mask.cli.MaskCli.run(args));
    }

    private Main() {
    }
}
