package com.mcc.mask.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcc.mask.MaskException;
import com.mcc.mask.cli.BookIo;
import com.mcc.mask.codebook.Codebook;
import com.mcc.mask.rw.Ctx;
import com.mcc.mask.rw.Rw;
import com.mcc.mask.rw.RwFactory;
import com.mcc.mask.rules.RuleTable;
import com.mcc.mask.scan.TextScanner;
import com.mcc.mask.util.Paths0;
import com.mcc.mask.util.Summary;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 控制台接口层：与 HTTP 传输解耦（handle() 收解码后的请求，回字节），
 * 因此单测可以不起服务直接调，也可以被 WebServer 真实挂载。
 */
public final class Api {

    public static final int PAGE = 200;

    /** status + 字节响应；fileName 非空表示作为附件下载。 */
    public record Resp(int status, byte[] body, String contentType, String fileName) {

        static Resp json(int status, Object o, ObjectMapper om) {
            try {
                return new Resp(status, om.writeValueAsBytes(o), "application/json;charset=UTF-8", null);
            } catch (IOException e) {
                throw new MaskException("序列化失败: " + e.getMessage(), e);
            }
        }
    }

    private static final String MASK_OUT_SUFFIX = ".masked";
    private static final String UNMASK_OUT_SUFFIX = ".restored";

    private final Workspace ws;
    private final Session session;
    private final ObjectMapper om = new ObjectMapper();
    private final String bookDir;
    private final RuleTable rules;

    public Api(Workspace ws, Session session, String bookDir, RuleTable rules) {
        this.ws = ws;
        this.session = session;
        this.bookDir = bookDir;
        this.rules = rules;
        Path bookParent = BookIo.resolve(bookDir, false).getParent();
        if (bookParent != null) {
            ws.hide(bookParent);   // 码本目录不出现在数据列表里
        }
    }

    public Resp handle(String method, String path, Map<String, String> query, byte[] body, String contentType) {
        try {
            return route(method, path, query, body, contentType);
        } catch (LockedEx e) {
            return Resp.json(401, fail(e.getMessage()), om);
        } catch (MaskException e) {
            return Resp.json(400, fail(e.getMessage()), om);
        } catch (RuntimeException e) {
            return Resp.json(500, fail(e.getClass().getSimpleName() + ": " + e.getMessage()), om);
        }
    }

    private Resp route(String method, String path, Map<String, String> query, byte[] body, String contentType) {
        return switch (path) {
            case "/api/state" -> state();
            case "/api/book/new" -> bookNew(body);
            case "/api/book/unlock" -> bookUnlock(body);
            case "/api/book/purge" -> bookPurge(body);
            case "/api/lock" -> {
                requireUnlock();
                session.lock();
                yield Resp.json(200, ok(Map.of()), om);
            }
            case "/api/ls" -> ls(query.getOrDefault("dir", ""));
            case "/api/run" -> run(body);
            case "/api/pairs" -> pairs(query);
            case "/api/export/pairs" -> exportPairs();
            case "/api/lookup" -> lookup(body);
            case "/api/upload" -> upload(body, contentType);
            case "/file" -> file(query.get("path"));
            default -> Resp.json(404, fail("没有这个接口: " + path), om);
        };
    }

    // ---- 端点实现 ----

    private Resp state() {
        Codebook book = session.unlocked() ? session.book() : null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("unlocked", book != null);
        m.put("root", ws.root().toString());
        m.put("book", session.unlocked() ? session.bookFile().toString() : bookHint());
        m.put("mapped", book == null ? 0 : book.maps().values().stream().mapToInt(Map::size).sum());
        m.put("files", book == null ? List.of() : book.json().files);
        m.put("version", com.mcc.mask.Main.VERSION);
        return Resp.json(200, ok(m), om);
    }

    private String bookHint() {
        Path f = BookIo.resolve(bookDir, false);
        return Files.exists(f) ? f + "（未解锁）" : "尚未创建";
    }

    private Resp bookNew(byte[] body) {
        char[] pass = passOf(body);
        Path file = BookIo.resolve(bookDir, true);
        if (Files.exists(file)) {
            throw new MaskException("码本已存在: " + file + "（要换一本请先销毁，或改用别的 --book 名）");
        }
        Codebook book = BookIo.open(file, pass, true);
        BookIo.save(file, pass, book);
        session.open(file, pass, book);
        wipe(pass);
        return Resp.json(200, ok(Map.of("book", file.toString())), om);
    }

    private Resp bookUnlock(byte[] body) {
        char[] pass = passOf(body);
        Path file = BookIo.resolve(bookDir, true);
        try {
            if (!Files.exists(file)) {
                throw new MaskException("码本不存在: " + file + "（请先新建）");
            }
            Codebook book = BookIo.open(file, pass, false);
            session.open(file, pass, book);
            return Resp.json(200, ok(Map.of("book", file.toString())), om);
        } catch (MaskException e) {
            String msg = e.getMessage();
            throw msg != null && msg.contains("口令") ? new LockedEx("码本口令错误") : e;
        } finally {
            wipe(pass);
        }
    }

    private Resp bookPurge(byte[] body) {
        requireUnlock();
        char[] pass = passOf(body);
        Path file = session.bookFile();
        if (!BookIo.purge(file)) {
            throw new MaskException("码本文件已不在: " + file);
        }
        session.lock();
        wipe(pass);
        return Resp.json(200, ok(Map.of("purged", file.toString())), om);
    }

    private Resp ls(String relDir) {
        requireUnlock();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Workspace.Entry e : ws.list(relDir)) {
            rows.add(Map.of("name", e.name(), "rel", e.rel(), "dir", e.dir(),
                    "size", e.size(), "mtime", e.mtime(), "supported", e.supported()));
        }
        return Resp.json(200, ok(Map.of("dir", relDir, "entries", rows)), om);
    }

    private synchronized Resp run(byte[] body) {
        requireUnlock();
        JsonNode j = parse(body);
        String mode = j.path("mode").asText();
        if (!mode.equals("mask") && !mode.equals("unmask")) {
            throw new MaskException("mode 只能是 mask 或 unmask，收到: " + mode);
        }
        boolean jitter = j.path("jitter").asBoolean(false);
        JsonNode files = j.path("files");
        if (!files.isArray() || files.isEmpty()) {
            throw new MaskException("没选中文件");
        }
        Codebook book = session.book();
        Path file = session.bookFile();
        char[] pass = session.pass();
        List<Map<String, Object>> results = new ArrayList<>();
        int done = 0;
        for (JsonNode f : files) {
            String rel = f.asText();
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("file", rel);
            try {
                Path in = ws.resolve(rel);
                Path out = Paths0.sib(in, mode.equals("mask") ? MASK_OUT_SUFFIX : UNMASK_OUT_SUFFIX);
                Summary sum = new Summary();
                Ctx ctx = new Ctx(book, rules, mode.equals("unmask"), sum, jitter);
                Rw.RwResult res = RwFactory.byPath(in).process(in, out, ctx);
                sum.fileDone(in.getFileName().toString(), res.hits(), res.warnings());
                book.touchFile(in.getFileName().toString());
                if (res.hits() > 0) {
                    BookIo.save(file, pass, book);
                }
                r.put("ok", true);
                r.put("hits", res.hits());
                r.put("out", ws.relativize(out));
                r.put("warnings", sum.credentialWarnings());
                r.put("notes", sum.lines());
                done++;
            } catch (MaskException e) {
                r.put("ok", false);
                r.put("error", e.getMessage());
            }
            results.add(r);
        }
        return Resp.json(200, ok(Map.of("mode", mode, "results", results,
                "succeeded", done, "failed", results.size() - done,
                "mapped", book.maps().values().stream().mapToInt(Map::size).sum())), om);
    }

    private Resp pairs(Map<String, String> query) {
        requireUnlock();
        return Resp.json(200, ok(pairsJson(query.get("q"), parseInt(query.get("page"), 1))), om);
    }

    private Map<String, Object> pairsJson(String q, int page) {
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        List<Map<String, Object>> all = new ArrayList<>();
        for (Map.Entry<String, Map<String, String>> byRule : session.book().maps().entrySet()) {
            for (Map.Entry<String, String> e : byRule.getValue().entrySet()) {
                if (!needle.isEmpty()
                        && !e.getKey().toLowerCase(Locale.ROOT).contains(needle)
                        && !e.getValue().toLowerCase(Locale.ROOT).contains(needle)) {
                    continue;
                }
                all.add(Map.of("rule", byRule.getKey(), "orig", e.getKey(), "fake", e.getValue()));
            }
        }
        int from = Math.min((page - 1) * PAGE, all.size());
        int to = Math.min(from + PAGE, all.size());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", all.size());
        m.put("page", page);
        m.put("pageSize", PAGE);
        m.put("rows", all.subList(from, to));
        return m;
    }

    private Resp exportPairs() {
        requireUnlock();
        StringBuilder sb = new StringBuilder("﻿类别,原值,假值\n");
        for (Map.Entry<String, Map<String, String>> byRule : session.book().maps().entrySet()) {
            for (Map.Entry<String, String> e : byRule.getValue().entrySet()) {
                sb.append(csv(byRule.getKey())).append(',').append(csv(e.getKey()))
                        .append(',').append(csv(e.getValue())).append('\n');
            }
        }
        return new Resp(200, sb.toString().getBytes(StandardCharsets.UTF_8), "text/csv;charset=UTF-8", "对照表.csv");
    }

    private Resp lookup(byte[] body) {
        requireUnlock();
        JsonNode j = parse(body);
        String value = j.path("value").asText();
        boolean rev = j.path("rev").asBoolean(false);
        Map<String, Object> m = new LinkedHashMap<>();
        if (value.isEmpty()) {
            throw new MaskException("要查的值不能为空");
        }
        if (rev) {
            List<String[]> hits = new TextScanner(rules, session.book()).maskPairs(value);
            m.put("hits", hits.stream().map(p -> Map.of("orig", p[0], "fake", p[1])).toList());
        } else {
            String orig = session.book().reverse(value);
            m.put("hit", orig != null);
            m.put("orig", orig);
        }
        return Resp.json(200, ok(m), om);
    }

    private Resp upload(byte[] body, String contentType) {
        requireUnlock();
        String boundary = boundaryOf(contentType);
        List<Multipart.Part> parts = Multipart.parse(body, boundary);
        Multipart.Part file = parts.stream().filter(p -> p.fileName() != null && !p.fileName().isEmpty())
                .findFirst().orElseThrow(() -> new MaskException("没收到文件"));
        Path landed = ws.land(file.fileName(), file.data());
        String rel = ws.relativize(landed);
        return Resp.json(200, ok(Map.of("rel", rel, "name", landed.getFileName().toString(),
                "size", landed.toFile().length())), om);
    }

    private Resp file(String rel) {
        requireUnlock();
        Path p = ws.resolve(rel);
        if (!Files.isRegularFile(p)) {
            throw new MaskException("文件不存在: " + rel);
        }
        try {
            return new Resp(200, Files.readAllBytes(p), mime(p.getFileName().toString()),
                    p.getFileName().toString());
        } catch (IOException e) {
            throw new MaskException("读取失败: " + e.getMessage(), e);
        }
    }

    // ---- 小工具 ----

    private static final class LockedEx extends MaskException {
        private LockedEx(String msg) {
            super(msg);
        }
    }

    private void requireUnlock() {
        if (!session.unlocked()) {
            throw new LockedEx("码本未解锁");
        }
        session.touch();
    }

    private char[] passOf(byte[] body) {
        String p = parse(body).path("pass").asText("");
        if (p.isEmpty()) {
            throw new MaskException("口令不能为空");
        }
        return p.toCharArray();
    }

    private static void wipe(char[] pass) {
        if (pass != null) {
            java.util.Arrays.fill(pass, '\0');
        }
    }

    private JsonNode parse(byte[] body) {
        try {
            return body == null || body.length == 0 ? om.createObjectNode() : om.readTree(body);
        } catch (IOException e) {
            throw new MaskException("请求体不是合法 JSON: " + e.getMessage(), e);
        }
    }

    private static int parseInt(String s, int def) {
        try {
            return s == null ? def : Math.max(1, Integer.parseInt(s));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static String csv(String v) {
        return v.contains(",") || v.contains("\"") || v.contains("\n")
                ? '"' + v.replace("\"", "\"\"") + '"' : v;
    }

    private static String mime(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.endsWith(".csv") || n.endsWith(".txt") || n.endsWith(".md") || n.endsWith(".log") || n.endsWith(".sql")) {
            return "text/plain;charset=UTF-8";
        }
        return "application/octet-stream";
    }

    private static String boundaryOf(String contentType) {
        if (contentType == null) {
            throw new MaskException("缺少 Content-Type");
        }
        for (String seg : contentType.split(";")) {
            String s = seg.trim();
            if (s.regionMatches(true, 0, "boundary=", 0, 9)) {
                return s.substring(9).trim();
            }
        }
        throw new MaskException("multipart 请求缺少 boundary");
    }

    private static Map<String, Object> ok(Map<String, ?> data) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.putAll(data);
        return m;
    }

    private static Map<String, Object> fail(String error) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", false);
        m.put("error", error);
        return m;
    }
}
