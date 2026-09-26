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
    /** 拖文件夹进来时允许的层级深度（含文件名那一段）。 */
    public static final int MAX_UPLOAD_DEPTH = 12;
    /** 递归列目录时最多列这么多文件，免得一次拖几万个把页面卡死。 */
    public static final int MAX_LIST_ENTRIES = 3000;

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
        int slash = rawName == null ? -1
                : rawName.replace('\\', '/').lastIndexOf('/');
        return landIn(slash >= 0 ? rawName.substring(slash + 1) : rawName, data);
    }

    /**
     * 上传落地（保留相对子路径）：拖整个文件夹进来时用它，把 <code>AI产物/三月/台账.csv</code>
     * 落成 <code>.mcc-upload/AI产物/三月/台账.csv</code>，不拍扁、不重名覆盖。
     * 路径只能向上停在上传目录内：每一段都过滤非法字符，出现 <code>..</code> 直接拒绝。
     */
    public Path landIn(String relPath, byte[] data) {
        if (data.length > MAX_UPLOAD_BYTES) {
            throw new MaskException("文件超过 512MB 上限: " + relPath);
        }
        List<String> segs = segments(relPath);
        Path dir = uploadDir();
        for (int i = 0; i < segs.size() - 1; i++) {
            dir = dir.resolve(segs.get(i));
        }
        String name = segs.get(segs.size() - 1);
        Path target = unique(dir, name);
        try {
            Files.createDirectories(dir);
            Files.write(target, data);
        } catch (IOException e) {
            throw new MaskException("上传落盘失败: " + e.getMessage(), e);
        }
        return target;
    }

    /** 拆出安全的路径段：丢掉空段和点段，逐段清洗，越界即拒。 */
    private static List<String> segments(String raw) {
        String s = raw == null ? "" : raw.replace('\\', '/');
        List<String> out = new ArrayList<>();
        for (String part : s.split("/")) {
            String seg = part.replaceAll("[\\x00-\\x1F\"<>|:*?]", "_").trim();
            if (seg.isEmpty() || ".".equals(seg)) {
                continue;
            }
            if ("..".equals(seg)) {
                throw new MaskException("上传路径越出上传目录，已拒绝: " + raw);
            }
            if (seg.length() > 120) {
                seg = seg.substring(0, 120);
            }
            out.add(seg);
        }
        if (out.isEmpty()) {
            throw new MaskException("非法文件名");
        }
        if (out.size() > MAX_UPLOAD_DEPTH) {
            throw new MaskException("目录层级太深（超过 " + MAX_UPLOAD_DEPTH + " 层）: " + raw);
        }
        return out;
    }

    /** 同目录重名自动加序号，不覆盖已有文件。 */
    private static Path unique(Path dir, String name) {
        Path target = dir.resolve(name);
        int n = 1;
        while (Files.exists(target)) {
            int dot = name.lastIndexOf('.');
            target = dot > 0
                    ? dir.resolve(name.substring(0, dot) + "-" + n + name.substring(dot))
                    : dir.resolve(name + "." + n);
            n++;
        }
        return target;
    }

    public List<Entry> list(String relDir) {
        return list(relDir, false);
    }

    /**
     * 列目录。{@code deep} 为真时递归列出所有子目录里的文件（拖完一整个文件夹后要一次全选），
     * 此时名字带子路径，如 <code>三月/交付.md</code>。
     */
    public List<Entry> list(String relDir, boolean deep) {
        Path dir = resolve(relDir == null ? "" : relDir);
        List<Entry> out = new ArrayList<>();
        try {
            if (deep) {
                walk(dir, dir, out);
            } else {
                for (Path p : Files.list(dir).toList()) {
                    String name = p.getFileName().toString();
                    if (skip(p, name)) {
                        continue;
                    }
                    out.add(entry(dir, p, name));
                }
            }
        } catch (IOException e) {
            throw new MaskException("列目录失败: " + e.getMessage(), e);
        }
        out.sort(Comparator.comparing((Entry e) -> !e.dir()).thenComparing(e -> e.name().toLowerCase(Locale.ROOT)));
        return out;
    }

    private void walk(Path base, Path dir, List<Entry> out) throws IOException {
        if (out.size() >= MAX_LIST_ENTRIES) {
            return;
        }
        for (Path p : Files.list(dir).toList()) {
            String name = p.getFileName().toString();
            if (Files.isDirectory(p)) {
                if (!skip(p, name)) {
                    walk(base, p, out);
                }
            } else if (!skip(p, name) && out.size() < MAX_LIST_ENTRIES) {
                out.add(entry(base, p, base.relativize(p).toString().replace('\\', '/')));
            }
        }
    }

    private boolean skip(Path p, String name) {
        return name.startsWith(".") || name.endsWith(".tmp")
                || hidden.contains(p.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT));
    }

    private Entry entry(Path base, Path p, String name) throws IOException {
        boolean isDir = Files.isDirectory(p);
        long size = isDir ? 0 : Files.size(p);
        String mtime = MT.format(Instant.ofEpochMilli(Files.getLastModifiedTime(p).toMillis()));
        return new Entry(name, relativize(p), isDir, size, mtime, !isDir && supported(name));
    }

    private static boolean supported(String name) {
        return com.mcc.mask.rw.RwFactory.isSupported(name);
    }
}
