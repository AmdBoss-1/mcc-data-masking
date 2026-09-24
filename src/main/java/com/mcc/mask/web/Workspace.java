package com.mcc.mask.web;

import com.mcc.mask.MaskException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 工作目录：把 URL 里的相对路径翻译成 root 内的真实路径，任何越界一律拒绝。
 * 上传件落 root/.mcc-upload/，同样受越界与大小限制约束。
 */
public final class Workspace {

    public static final String UPLOAD_DIR_NAME = ".mcc-upload";
    public static final long MAX_UPLOAD_BYTES = 512L * 1024 * 1024;

    private static final DateTimeFormatter MT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    public record Entry(String name, String rel, boolean dir, long size, String mtime, boolean supported) {
    }

    private final Path root;
    private final java.util.Set<String> hidden = new java.util.HashSet<>();

    public Workspace(Path root) {
        this.root = root.toAbsolutePath().normalize();
        if (!Files.isDirectory(this.root)) {
            throw new MaskException("工作目录不存在: " + this.root);
        }
    }

    /** 工具自己的目录（码本）不该出现在数据列表里。 */
    public void hide(Path p) {
        hidden.add(p.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT));
    }

    public Path root() {
        return root;
    }

    /** rel → root 内绝对路径；越界/绝对路径/空字节一律拒绝。 */
    public Path resolve(String rel) {
        if (rel == null) {
            throw new MaskException("路径为空");
        }
        String r = rel.replace('\\', '/').trim();
        if (r.indexOf('\0') >= 0) {
            throw new MaskException("路径含非法字符");
        }
        if (r.startsWith("/") || r.matches("^[A-Za-z]:.*")) {
            throw new MaskException("只接受工作目录内的相对路径，收到: " + rel);
        }
        Path p = root.resolve(r).normalize();
        if (!p.startsWith(root)) {
            throw new MaskException("路径越出工作目录，已拒绝: " + rel);
        }
        return p;
    }

    /** 真实路径 → 相对路径（供下载链接用）。 */
    public String relativize(Path p) {
        return root.relativize(p.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    public Path uploadDir() {
        Path d = root.resolve(UPLOAD_DIR_NAME);
        try {
            Files.createDirectories(d);
        } catch (IOException e) {
            throw new MaskException("创建上传目录失败: " + e.getMessage(), e);
        }
        return d;
    }

    /** 上传落地：只取文件名（丢掉任何路径成分），重名自动加序号。 */
    public Path land(String rawName, byte[] data) {
        if (data.length > MAX_UPLOAD_BYTES) {
            throw new MaskException("文件超过 512MB 上限: " + rawName);
        }
        String clean = rawName == null ? "" : rawName.replace('\\', '/');
        int slash = clean.lastIndexOf('/');
        if (slash >= 0) {
            clean = clean.substring(slash + 1);
        }
        clean = clean.replaceAll("[\\x00-\\x1F\"<>|:*?]", "_").trim();
        if (clean.isEmpty() || ".".equals(clean) || "..".equals(clean)) {
            throw new MaskException("非法文件名");
        }
        Path dir = uploadDir();
        Path target = dir.resolve(clean);
        int n = 1;
        while (Files.exists(target)) {
            int dot = clean.lastIndexOf('.');
            target = dot > 0
                    ? dir.resolve(clean.substring(0, dot) + "-" + n + clean.substring(dot))
                    : dir.resolve(clean + "." + n);
            n++;
        }
        try {
            Files.createFile(target);
            Files.write(target, data);
        } catch (IOException e) {
            throw new MaskException("上传落盘失败: " + e.getMessage(), e);
        }
        return target;
    }

    public List<Entry> list(String relDir) {
        Path dir = resolve(relDir == null ? "" : relDir);
        List<Entry> out = new ArrayList<>();
        try (var stream = Files.list(dir)) {
            for (Path p : stream.toList()) {
                String name = p.getFileName().toString();
                if (name.equals(UPLOAD_DIR_NAME) || name.equals(".gitignore") || name.endsWith(".tmp")
                        || hidden.contains(p.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT))) {
                    continue;
                }
                boolean isDir = Files.isDirectory(p);
                long size = isDir ? 0 : Files.size(p);
                String mtime = MT.format(Instant.ofEpochMilli(Files.getLastModifiedTime(p).toMillis()));
                out.add(new Entry(name, relativize(p), isDir, size, mtime, !isDir && supported(name)));
            }
        } catch (IOException e) {
            throw new MaskException("列目录失败: " + e.getMessage(), e);
        }
        out.sort(Comparator.comparing((Entry e) -> !e.dir()).thenComparing(e -> e.name().toLowerCase(Locale.ROOT)));
        return out;
    }

    private static boolean supported(String name) {
        return com.mcc.mask.rw.RwFactory.isSupported(name);
    }
}
