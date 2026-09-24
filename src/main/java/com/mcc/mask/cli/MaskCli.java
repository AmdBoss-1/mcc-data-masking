package com.mcc.mask.cli;

import com.mcc.mask.MaskException;
import com.mcc.mask.codebook.Codebook;
import com.mcc.mask.rw.Ctx;
import com.mcc.mask.rw.Rw;
import com.mcc.mask.rw.RwFactory;
import com.mcc.mask.rules.RuleTable;
import com.mcc.mask.scan.TextScanner;
import com.mcc.mask.util.Summary;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IExecutionExceptionHandler;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/** CLI：mask / unmask / lookup / purge。 */
public final class MaskCli {

    private MaskCli() {
    }

    public static int run(String[] args) {
        CommandLine cl = new CommandLine(new Root());
        cl.setCaseInsensitiveEnumValuesAllowed(true);
        cl.setExecutionExceptionHandler((ex, cmd, parseResult) -> {
            if (ex instanceof MaskException) {
                System.err.println("[错误] " + ex.getMessage());
                return 2;
            }
            ex.printStackTrace();
            return 3;
        });
        return cl.execute(args);
    }

    @Command(name = "mask-tool", subcommands = {MaskCmd.class, UnmaskCmd.class, LookupCmd.class, PurgeCmd.class,
            ServeCmd.class},
            mixinStandardHelpOptions = true, description = "MCC 数据脱敏工具（离线；码本绝不出门）")
    static class Root implements Callable<Integer> {
        @Override
        public Integer call() {
            System.out.println("用法: mask | unmask | lookup | purge | serve（见 --help）");
            return 0;
        }
    }

    abstract static class Base implements Callable<Integer> {
        @Parameters(index = "0", description = "输入文件")
        Path in;

        @Option(names = {"-o", "--out"}, description = "输出文件（默认同名 .masked/.orig 后缀）")
        Path out;

        @Option(names = "--book", description = "码本目录（默认 ./maskbook）")
        String bookDir;

        @Option(names = "--pass-file", description = "口令文件（批处理用；默认交互输入，不落盘）")
        Path passFile;

        @Option(names = "--rules", description = "自定义规则 JSON（覆盖默认规则表）")
        Path rulesFile;

        protected RuleTable rules() throws IOException {
            return rulesFile == null ? RuleTable.defaultTable() : RuleTable.load(rulesFile);
        }

        /** 未指定 -o 时的默认产物名（子类给后缀）。 */
        protected abstract Path defaultOut(Path in);

        protected char[] password(String prompt) {
            return passFile != null ? Console0.fromFile(passFile) : Console0.read(prompt);
        }

        @Override
        public Integer call() throws Exception {
            Path target = out != null ? out : defaultOut(in);
            Path bookFile = BookIo.resolve(bookDir, true);
            boolean exists = Files.exists(bookFile);
            Codebook book;
            char[] pass;
            if (exists) {
                pass = password("码本口令: ");
                book = BookIo.open(bookFile, pass, false);
            } else {
                if (!allowCreateNew()) {
                    throw new MaskException("码本不存在: " + bookFile);
                }
                pass = password("新建码本口令: ");
                confirmIfInteractive(pass);
                book = BookIo.open(bookFile, pass, true);
            }
            Summary sum = new Summary();
            Ctx ctx = new Ctx(book, rules(), reverse(), sum, jitter());
            Rw rw = RwFactory.byPath(in);
            Rw.RwResult r = rw.process(in, target, ctx);
            sum.fileDone(in.getFileName().toString(), r.hits(), r.warnings());
            book.touchFile(in.getFileName().toString());
            if (!exists) {
                BookIo.save(bookFile, pass, book);
            } else if (r.hits() > 0) {
                BookIo.save(bookFile, pass, book);
            }
            printSummary(sum, book, bookFile, target);
            return 0;
        }

        protected boolean reverse() {
            return false;
        }

        protected boolean jitter() {
            return false;
        }

        protected boolean allowCreateNew() {
            return true;
        }

        private void confirmIfInteractive(char[] pass) {
            if (passFile != null || System.console() == null) {
                return;
            }
            char[] again = Console0.read("再次输入确认: ");
            if (!java.util.Arrays.equals(pass, again)) {
                throw new MaskException("两次口令不一致");
            }
        }
    }

    @Command(name = "mask", description = "脱敏：产出副本，映射写入加密码本")
    static class MaskCmd extends Base {
        @Option(names = "--jitter", description = "对数值单元格做 ±3% 微扰（默认关，保持 AI 汇总可对账）")
        boolean jitterOn;

        @Override
        protected boolean jitter() {
            return jitterOn;
        }

        @Override
        protected Path defaultOut(Path in) {
            return sib(in, ".masked");
        }
    }

    @Command(name = "unmask", description = "还原：对任何含假值的文件按码本反向替换（AI 产物/回传件均可）")
    static class UnmaskCmd extends Base {
        @Override
        protected boolean reverse() {
            return true;
        }

        @Override
        protected boolean allowCreateNew() {
            return false;
        }

        @Override
        protected Path defaultOut(Path in) {
            return sib(in, ".restored");
        }
    }

    @Command(name = "lookup", description = "单点互查：假值→原值（默认）；--rev 原值→假值")
    static class LookupCmd implements Callable<Integer> {
        @Parameters(index = "0", description = "要查的值")
        String value;

        @Option(names = "--book")
        String bookDir;

        @Option(names = "--pass-file")
        Path passFile;

        @Option(names = "--rev", description =  "反向：原值→假值（按规则形匹配）")
        boolean rev;

        @Override
        public Integer call() {
            Path bookFile = BookIo.resolve(bookDir, false);
            if (!Files.exists(bookFile)) {
                throw new MaskException("码本不存在: " + bookFile);
            }
            char[] pass = passFile != null ? Console0.fromFile(passFile) : Console0.read("码本口令: ");
            Codebook book = BookIo.open(bookFile, pass, false);
            if (!rev) {
                String orig = book.reverse(value);
                System.out.println(orig == null ? "未命中：" + value : value + " → " + orig);
                return orig == null ? 1 : 0;
            }
            RuleTable rules = RuleTable.defaultTable();
            TextScanner s = new TextScanner(rules, book);
            for (String[] pair : s.maskPairs(value)) {
                System.out.println(pair[0] + " → " + pair[1]);
            }
            return 0;
        }
    }

    @Command(name = "purge", description = "销毁码本（覆写后删除；确认已无需还原再用）")
    static class PurgeCmd implements Callable<Integer> {
        @Option(names = "--book")
        String bookDir;

        @Override
        public Integer call() {
            Path bookFile = BookIo.resolve(bookDir, false);
            if (!BookIo.purge(bookFile)) {
                System.out.println("码本不存在，无需销毁: " + bookFile);
                return 0;
            }
            System.out.println("码本已覆写删除: " + bookFile);
            return 0;
        }
    }

    private static Path sib(Path in, String suffix) {
        return com.mcc.mask.util.Paths0.sib(in, suffix);
    }

    static void printSummary(Summary sum, Codebook book, Path bookFile, Path target) {
        for (String line : sum.lines()) {
            System.out.println("· " + line);
        }
        int uniq = book.maps().values().stream().mapToInt(java.util.Map::size).sum();
        System.out.println("· 唯一值 " + uniq + " 个；产物: " + target);
        System.out.println("· 码本: " + bookFile + "（口令不落盘；用完可 purge）");
        if (!sum.credentialWarnings().isEmpty()) {
            System.err.println("⚠ 检测到疑似凭据（未替换，请确认是否该外发）：");
            sum.credentialWarnings().forEach(w -> System.err.println("   ! " + w));
        }
    }
}
