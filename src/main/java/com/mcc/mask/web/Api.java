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

    private Workspace ws;
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
            case "/api/book/prune" -> bookPrune();
            case "/api/lock" -> {
                requireUnlock();
                session.lock();
                yield Resp.json(200, ok(Map.of()), om);
            }
            case "/api/ls" -> ls(query);
            case "/api/root" -> setRoot(body);
            case "/api/dirs" -> dirs(query.get("path"));
            case "/api/run" -> run(body);
            case "/api/pairs" -> pairs(query);
            case "/api/export/pairs" -> exportPairs(query);
            case "/api/download-zip" -> zip(body);
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
        if (!parse(body).path("confirm").asBoolean(false)) {
            throw new MaskException("销毁码本需要前端二次确认（confirm=true）");
        }
        Path file = session.bookFile();
        if (!BookIo.purge(file)) {
            throw new MaskException("码本文件已不在: " + file);
        }
        session.lock();
        return Resp.json(200, ok(Map.of("purged", file.toString())), om);
    }

    /** 清理「产物已不在工作目录」的映射：码本瘦身，但仍保留在档产物能还原的部分。 */
    private Resp bookPrune() {
        requireUnlock();
        Codebook book = session.book();
        Map<String, java.util.List<String>> byFile = book.json().byFile;
        if (byFile.isEmpty()) {
            throw new MaskException("这本码本没有产物归属信息（老版本创建的），只能整本销毁重建");
        }
        java.util.Set<String> live = new java.util.HashSet<>();
        for (Map.Entry<String, java.util.List<String>> e : byFile.entrySet()) {
            if (productExists(e.getKey())) {
                live.addAll(e.getValue());
            }
        }
        int removed = 0;
        java.util.Iterator<Map.Entry<String, Map<String, String>>> it = book.json().maps.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Map<String, String>> byRule = it.next();
            java.util.Iterator<Map.Entry<String, String>> vals = byRule.getValue().entrySet().iterator();
            while (vals.hasNext()) {
                Map.Entry<String, String> e = vals.next();
                if (!live.contains(byRule.getKey() + "" + e.getKey())) {
                    vals.remove();
                    removed++;
                }
            }
            if (byRule.getValue().isEmpty()) {
                it.remove();
            }
            byFile.entrySet().removeIf(f -> !productExists(f.getKey()));
        }
        BookIo.save(session.bookFile(), session.pass(), book);
        return Resp.json(200, ok(Map.of("removed", removed)), om);
    }

    /** 页内切换工作目录（只本机可达，选择后文件区即以它为根）。 */
    private Resp setRoot(byte[] body) {
        requireUnlock();
        String p = parse(body).path("path").asText("").trim();
        if (p.isEmpty()) {
            throw new MaskException("目录不能为空");
        }
        Path dir = Path.of(p);
        if (!Files.isDirectory(dir)) {
            throw new MaskException("目录不存在: " + dir);
        }
        ws = new Workspace(dir);
        Path bookParent = BookIo.resolve(bookDir, false).getParent();
        if (bookParent != null) {
            ws.hide(bookParent);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("root", ws.root().toString());
        return Resp.json(200, ok(m), om);
    }

    /** 目录浏览（供页内选择器）：给空则列盘符，否则列该目录下的子目录。 */
    private Resp dirs(String path) {
        requireUnlock();
        List<Map<String, Object>> out = new ArrayList<>();
        String cur = path == null ? "" : path.trim();
        try {
            if (cur.isEmpty()) {
                for (char c = 'A'; c <= 'Z'; c++) {
                    Path d = Path.of(c + ":\\");
                    if (Files.isDirectory(d)) {
                        out.add(Map.of("name", c + ":", "path", d.toString()));
                    }
                }
                if (out.isEmpty()) {
                    out.add(Map.of("name", "/", "path", "/"));
                }
            } else {
                Path dir = Path.of(cur);
                if (!Files.isDirectory(dir)) {
                    throw new MaskException("目录不存在: " + dir);
                }
                try (var stream = Files.list(dir)) {
                    for (Path p : stream.sorted().toList()) {
                        String n = p.getFileName().toString();
                        if (Files.isDirectory(p) && !n.startsWith("$") && !n.equals("System Volume Information")) {
                            out.add(Map.of("name", n, "path", p.toString()));
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new MaskException("列目录失败: " + e.getMessage(), e);
        }
        Path parent = cur.isEmpty() ? null : Path.of(cur).getParent();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("path", cur);
        m.put("parent", parent == null ? "" : parent.toString());
        m.put("dirs", out);
        return Resp.json(200, ok(m), om);
    }

    private Resp ls(Map<String, String> q) {
        requireUnlock();
        String dir = q.getOrDefault("dir", "");
        boolean deep = "1".equalsIgnoreCase(q.get("deep")) || "true".equalsIgnoreCase(q.get("deep"));
        if (Workspace.UPLOAD_DIR_NAME.equals(dir)) {
            ws.uploadDir();   // 还没传过东西时也要能进去看（空目录）
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Workspace.Entry e : ws.list(dir, deep)) {
            rows.add(Map.of("name", e.name(), "rel", e.rel(), "dir", e.dir(),
                    "size", e.size(), "mtime", e.mtime(), "supported", e.supported()));
        }
        return Resp.json(200, ok(Map.of("dir", dir, "deep", deep, "entries", rows)), om);
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
                if (mode.equals("mask") && in.getFileName().toString().matches(".*[.](masked|restored)[.].*")) {
                    throw new MaskException("这是脱敏/还原产物，已跳过：再脱一遍会把假值当原值，码本里会多出一串链式映射。要处理请先改回原始文件。");
                }
                Path out = Paths0.sib(in, mode.equals("mask") ? MASK_OUT_SUFFIX : UNMASK_OUT_SUFFIX);
                Summary sum = new Summary();
                Ctx ctx = new Ctx(book, rules, mode.equals("unmask"), sum, jitter);
                book.beginFile(mode.equals("mask") ? ws.relativize(out) : null);
                Rw.RwResult res = RwFactory.byPath(in).process(in, out, ctx);
                book.beginFile(null);
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
        return Resp.json(200, ok(pairsJson(query.get("q"), parseInt(query.get("page"), 1),
                "all".equals(query.get("scope")))), om);
    }

    /**
     * 对照表跟随产物：码本里记了 byFile（哪个产物用了哪些映射）时，只显示产物仍在工作目录的映射；
     * 老码本没有归属信息（byFile 空）则全部显示，免得历史数据凭空消失。
     */
    private Map<String, Object> pairsJson(String q, int page, boolean showAll) {
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        Map<String, java.util.List<String>> byFile = session.book().json().byFile;
        java.util.Set<String> visible = null;
        if (!showAll && !byFile.isEmpty()) {
            visible = new java.util.HashSet<>();
            for (Map.Entry<String, java.util.List<String>> e : byFile.entrySet()) {
                if (productExists(e.getKey())) {
                    visible.addAll(e.getValue());
                }
            }
        }
        // 链式判定：本条的假值又被当成"原值"脱过第二次（脱敏产物再脱一遍留下的脏数据）
        java.util.Set<String> allOrigs = new java.util.HashSet<>();
        for (Map<String, String> m : session.book().maps().values()) {
            allOrigs.addAll(m.keySet());
        }
        List<Map<String, Object>> all = new ArrayList<>();
        int hidden = 0;
        int chained = 0;
        for (Map.Entry<String, Map<String, String>> byRule : session.book().maps().entrySet()) {
            for (Map.Entry<String, String> e : byRule.getValue().entrySet()) {
                boolean isChained = allOrigs.contains(e.getValue());
                if (visible != null && !visible.contains(byRule.getKey() + "\u0001" + e.getKey())) {
                    hidden++;
                    continue;
                }
                if (isChained) {
                    chained++;
                }
                if (!needle.isEmpty()
                        && !e.getKey().toLowerCase(Locale.ROOT).contains(needle)
                        && !e.getValue().toLowerCase(Locale.ROOT).contains(needle)) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("rule", byRule.getKey());
                row.put("orig", e.getKey());
                row.put("fake", e.getValue());
                row.put("chained", isChained);
                all.add(row);
            }
        }
        int from = Math.min((page - 1) * PAGE, all.size());
        int to = Math.min(from + PAGE, all.size());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", all.size());
        m.put("hidden", hidden);
        m.put("chained", chained);
        m.put("scoped", visible != null);
        m.put("page", page);
        m.put("pageSize", PAGE);
        m.put("rows", all.subList(from, to));
        return m;
    }

    /** byFile 里存的可能是工作目录内相对路径（网页脱敏），也可能是绝对路径（CLI 脱敏）。 */
    private boolean productExists(String stored) {
        if (stored == null || stored.isBlank()) {
            return false;
        }
        try {
            Path p = stored.startsWith("/") || stored.matches("^[A-Za-z]:[\\\\/].*")
                    ? Path.of(stored) : ws.resolve(stored);
            return Files.isRegularFile(p);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private Resp exportPairs(Map<String, String> query) {
        requireUnlock();
        boolean showAll = "all".equals(query.get("scope"));
        StringBuilder sb = new StringBuilder("﻿类别,原值,假值\n");
        Map<String, java.util.List<String>> byFile = session.book().json().byFile;
        java.util.Set<String> visible = null;
        if (!showAll && !byFile.isEmpty()) {
            visible = new java.util.HashSet<>();
            for (Map.Entry<String, java.util.List<String>> e : byFile.entrySet()) {
                if (productExists(e.getKey())) {
                    visible.addAll(e.getValue());
                }
            }
        }
        for (Map.Entry<String, Map<String, String>> byRule : session.book().maps().entrySet()) {
            for (Map.Entry<String, String> e : byRule.getValue().entrySet()) {
                if (visible != null && !visible.contains(byRule.getKey() + "\u0001" + e.getKey())) {
                    continue;
                }
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

    /**
     * 上传：一次可以收多个文件（拖文件夹时前端分批打包过来）。
     * 每个文件可选带一个同名的 {@code path} 字段保留相对子路径，没带就只取文件名。
     */
    private synchronized Resp upload(byte[] body, String contentType) {
        requireUnlock();
        String boundary = boundaryOf(contentType);
        List<Multipart.Part> parts = Multipart.parse(body, boundary);
        List<Multipart.Part> files = parts.stream()
                .filter(p -> p.fileName() != null && !p.fileName().isEmpty()).toList();
        if (files.isEmpty()) {
            throw new MaskException("没收到文件");
        }
        List<Map<String, Object>> landedFiles = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int i = 0;
        for (Multipart.Part f : files) {
            String rel = pathOf(parts, f, i++);
            try {
                Path p = ws.landIn(rel, f.data());
                landedFiles.add(Map.of("rel", ws.relativize(p),
                        "name", p.getFileName().toString(), "size", p.toFile().length()));
            } catch (MaskException e) {
                errors.add(rel + " — " + e.getMessage());
            }
        }
        if (landedFiles.isEmpty()) {
            throw new MaskException("文件都没落地：" + String.join("；", errors));
        }
        Map<String, Object> first = landedFiles.get(0);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rel", first.get("rel"));
        out.put("name", first.get("name"));
        out.put("size", first.get("size"));
        out.put("count", landedFiles.size());
        out.put("files", landedFiles);
        out.put("errors", errors);
        return Resp.json(200, ok(out), om);
    }

    /** 取这个文件对应的相对路径： multipart 里同名字段按出现顺序排，file 前面第 k 个 path 就是它的。 */
    private static String pathOf(List<Multipart.Part> parts, Multipart.Part file, int index) {
        List<String> paths = parts.stream()
                .filter(p -> "path".equals(p.name()) && p.fileName() == null)
                .map(p -> new String(p.data(), StandardCharsets.UTF_8).trim())
                .toList();
        if (index < paths.size() && !paths.get(index).isEmpty()) {
            return paths.get(index);
        }
        return file.fileName();
    }

    /** 打包下载：把选中的产物打成 zip（浏览器一次只弹一个保存框，选中多个也不烦）。 */
    private Resp zip(byte[] body) {
        requireUnlock();
        JsonNode j = parse(body);
        JsonNode paths = j.path("paths");
        if (!paths.isArray() || paths.isEmpty()) {
            throw new MaskException("没选中要下载的产物");
        }
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        int packed = 0;
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(bos, StandardCharsets.UTF_8)) {
            java.util.Set<String> used = new java.util.HashSet<>();
            for (JsonNode n : paths) {
                Path p = ws.resolve(n.asText());
                if (!Files.isRegularFile(p)) {
                    continue;
                }
                String name = p.getFileName().toString();
                String unique = name;
                int k = 1;
                while (!used.add(unique)) {
                    int dot = name.lastIndexOf('.');
                    unique = dot > 0 ? name.substring(0, dot) + "-" + k + name.substring(dot) : name + "-" + k;
                    k++;
                }
                zos.putNextEntry(new java.util.zip.ZipEntry(unique));
                zos.write(Files.readAllBytes(p));
                zos.closeEntry();
                packed++;
            }
        } catch (IOException e) {
            throw new MaskException("打包失败: " + e.getMessage(), e);
        }
        if (packed == 0) {
            throw new MaskException("选中的产物都不在工作目录里了");
        }
        return new Resp(200, bos.toByteArray(), "application/zip", "脱敏产物.zip");
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
