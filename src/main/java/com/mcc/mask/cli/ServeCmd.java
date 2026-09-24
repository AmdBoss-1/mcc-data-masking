package com.mcc.mask.cli;

import com.mcc.mask.MaskException;
import com.mcc.mask.rules.RuleTable;
import com.mcc.mask.web.Api;
import com.mcc.mask.web.Session;
import com.mcc.mask.web.WebServer;
import com.mcc.mask.web.Workspace;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/** serve：本机网页控制台（只绑 127.0.0.1，不联网、不打包）。 */
@Command(name = "serve", description = "本机网页控制台：批量脱敏 → 发 AI → 还原 AI 产物 → 对照核查")
public class ServeCmd implements Callable<Integer> {

    @Option(names = "--port", defaultValue = "8098", description = "端口（默认 8098；填 0 让系统随机给）")
    int port;

    @Option(names = "--root", defaultValue = ".", description = "工作目录（默认当前目录）")
    Path root;

    @Option(names = "--book", description = "码本目录（默认 root/maskbook）")
    String bookDir;

    @Option(names = "--rules", description = "自定义规则 JSON")
    Path rulesFile;

    @Option(names = "--idle-min", defaultValue = "30", description = "多少分钟无操作自动锁（默认 30）")
    long idleMin;

    @Option(names = "--open", description = "启动后自动打开默认浏览器")
    boolean openBrowser;

    @Override
    public Integer call() throws Exception {
        if (!Files.isDirectory(root)) {
            throw new MaskException("工作目录不存在: " + root.toAbsolutePath());
        }
        String book = bookDir == null ? root.toAbsolutePath().resolve("maskbook").toString() : bookDir;
        RuleTable rules = rulesFile == null ? RuleTable.defaultTable() : RuleTable.load(rulesFile);
        Workspace ws = new Workspace(root);
        Session session = new Session(idleMin * 60_000L);
        Api api = new Api(ws, session, book, rules);
        try (WebServer server = new WebServer(port, api)) {
            server.start();
            System.out.println("MCC 脱敏控制台已启动: " + server.url());
            System.out.println("  工作目录: " + ws.root());
            System.out.println("  码本目录: " + book);
            System.out.println("  只监听本机；Ctrl+C 停止。口令不写日志、锁定即清。");
            if (openBrowser) {
                browse(server.url());
            }
            Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "mcc-mask-web-stop"));
            server.awaitForever();
        }
        return 0;
    }

    private static void browse(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                return;
            }
            new ProcessBuilder("cmd", "/c", "start", "", url).start();
        } catch (IOException | RuntimeException e) {
            System.out.println("  （自动开浏览器失败，请手动打开 " + url + "）");
        }
    }
}
