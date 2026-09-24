package com.mcc.mask.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 传输层：JDK 自带 HttpServer，只绑 127.0.0.1（本机唯一入口，局域网不可达）。
 * 静态页是 jar 内的一个文件；其余全交给 Api。
 */
public final class WebServer implements AutoCloseable {

    private static final int MAX_BODY = (int) (Workspace.MAX_UPLOAD_BYTES + 8L * 1024 * 1024);

    private final HttpServer server;
    private final Api api;
    private byte[] page;

    public WebServer(int port, Api api) throws IOException {
        this.api = api;
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 10);
        this.server.createContext("/", this::dispatch);
        this.server.setExecutor(Executors.newFixedThreadPool(3, r -> {
            Thread t = new Thread(r, "mcc-mask-web");
            t.setDaemon(true);
            return t;
        }));
    }

    public void start() {
        server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public String url() {
        return "http://127.0.0.1:" + port() + "/";
    }

    public void stop() {
        server.stop(0);
    }

    @Override
    public void close() {
        stop();
    }

    public void awaitForever() {
        try {
            TimeUnit.DAYS.sleep(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void dispatch(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            if (!localHost(ex)) {
                reply(ex, new Api.Resp(403, "{\"ok\":false,\"error\":\"只允许本机访问\"}"
                        .getBytes(StandardCharsets.UTF_8), "application/json;charset=UTF-8", null));
                return;
            }
            if (path.equals("/") || path.equals("/index.html")) {
                reply(ex, new Api.Resp(200, index(), "text/html;charset=UTF-8", null));
                return;
            }
            if (!path.startsWith("/api/") && !path.equals("/file")) {
                reply(ex, new Api.Resp(404, "{\"ok\":false,\"error\":\"not found\"}"
                        .getBytes(StandardCharsets.UTF_8), "application/json;charset=UTF-8", null));
                return;
            }
            byte[] body = "GET".equals(ex.getRequestMethod()) ? new byte[0] : readBody(ex);
            Api.Resp r = api.handle(ex.getRequestMethod(), path, query(ex), body,
                    ex.getRequestHeaders().getFirst("Content-Type"));
            reply(ex, r);
        } catch (RuntimeException e) {
            reply(ex, new Api.Resp(500, ("{\"ok\":false,\"error\":\"" + safe(e.getMessage()) + "\"}")
                    .getBytes(StandardCharsets.UTF_8), "application/json;charset=UTF-8", null));
        } finally {
            ex.close();
        }
    }

    /** Host 头必须是本机，挡掉 DNS 重绑定把页面骗到别的域上。 */
    private static boolean localHost(HttpExchange ex) {
        String host = ex.getRequestHeaders().getFirst("Host");
        if (host == null) {
            return true;
        }
        String h = host.toLowerCase(java.util.Locale.ROOT);
        int colon = h.lastIndexOf(':');
        if (colon > 0) {
            h = h.substring(0, colon);
        }
        return h.equals("127.0.0.1") || h.equals("localhost") || h.equals("[::1]") || h.equals("::1");
    }

    /** 头是 ISO-8859-1 的：中文名走 RFC 5987，另给 ASCII 兜底名。 */
    static String disposition(String name) {
        String ascii = name.replaceAll("[^\\x20-\\x7E]", "_").replace("\"", "'");
        String enc = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename=\"" + ascii + "\"; filename*=UTF-8''" + enc;
    }

    private static String safe(String s) {
        return s == null ? "error" : s.replace("\\", "\\\\").replace("\"", "'").replace("\n", " ");
    }

    private byte[] index() {
        if (page == null) {
            try (InputStream in = WebServer.class.getResourceAsStream("/web/index.html")) {
                if (in == null) {
                    throw new IllegalStateException("jar 内缺少 /web/index.html");
                }
                page = in.readAllBytes();
            } catch (IOException e) {
                throw new IllegalStateException("读取页面失败: " + e.getMessage(), e);
            }
        }
        return page;
    }

    private static byte[] readBody(HttpExchange ex) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (InputStream in = ex.getRequestBody()) {
            byte[] chunk = new byte[64 * 1024];
            int n;
            long total = 0;
            while ((n = in.read(chunk)) > 0) {
                total += n;
                if (total > MAX_BODY) {
                    throw new com.mcc.mask.MaskException("请求体超过上限（单文件 512MB）");
                }
                buf.write(chunk, 0, n);
            }
        }
        return buf.toByteArray();
    }

    private static Map<String, String> query(HttpExchange ex) {
        Map<String, String> m = new LinkedHashMap<>();
        String raw = ex.getRequestURI().getRawQuery();
        if (raw == null || raw.isEmpty()) {
            return m;
        }
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            m.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
        }
        return m;
    }

    private static void reply(HttpExchange ex, Api.Resp r) throws IOException {
        byte[] body = r.body() == null ? new byte[0] : r.body();
        ex.getResponseHeaders().set("Content-Type", r.contentType());
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        if (r.fileName() != null) {
            ex.getResponseHeaders().set("Content-Disposition", disposition(r.fileName()));
        }
        if ("GET".equals(ex.getRequestMethod())) {
            ex.sendResponseHeaders(r.status(), body.length == 0 ? -1 : body.length);
        } else {
            ex.sendResponseHeaders(r.status(), body.length == 0 ? -1 : body.length);
        }
        if (body.length > 0) {
            ex.getResponseBody().write(body);
        }
    }

    static Map<String, String> emptyQuery() {
        return new HashMap<>();
    }
}
