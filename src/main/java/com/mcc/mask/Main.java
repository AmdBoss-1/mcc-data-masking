package com.mcc.mask;

/** MCC脱敏工具 CLI 入口（picocli 子命令在 cli 包）。 */
public final class Main {

    public static String toolName() {
        return "mask-tool";
    }

    public static void main(String[] args) {
        System.exit(com.mcc.mask.cli.MaskCli.run(args));
    }

    private Main() {
    }
}
