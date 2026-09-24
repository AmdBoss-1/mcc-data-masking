package com.mcc.mask.rw;

import com.mcc.mask.MaskException;
import com.mcc.mask.scan.TextScanner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * H2 .mv.db 读写器：复制副本后原地改（原文件不动）。
 * 列名命中→整值变换；其余 VARCHAR 值走结构化文本替换。
 * 注意：套件的 .mccbak 整机包内模块数据为 MccBakCrypto 加密（明文=备份 SQL/JSON），
 * 本工具 v1 不直读，遇到给明确指引。
 */
public final class H2Rw implements Rw {

    @Override
    public String name() {
        return "h2";
    }

    @Override
    public RwResult process(Path in, Path out, Ctx ctx) {
        String n = in.getFileName().toString().toLowerCase();
        if (n.endsWith(".mccbak") || isZip(in)) {
            throw new MaskException("不支持直读 .mccbak（套件内模块数据为加密包）。请改用：①复制 .mv.db 文件脱敏；"
                    + "②或在套件里导出备份 SQL 后脱敏（脱敏后如需回灌，用套件导入）");
        }
        try {
            Files.createDirectories(out.toAbsolutePath().getParent());
            Files.copy(in, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            String url = "jdbc:h2:" + out.toAbsolutePath();
            if (url.toLowerCase().endsWith(".mv.db")) {
                url = url.substring(0, url.length() - ".mv.db".length());
            }
            int hits;
            try (Connection c = DriverManager.getConnection(url, "sa", "")) {
                hits = processConnection(c, ctx);
            }
            return new RwResult(hits, List.of());
        } catch (IOException | SQLException e) {
            throw new MaskException("H2 处理失败: " + in + " — " + e.getMessage(), e);
        }
    }

    /** 对已打开的 H2 连接做双向替换（PUBLIC 模式全部 VARCHAR 列）。 */
    static int processConnection(Connection c, Ctx ctx) throws SQLException {
        TextScanner s = new TextScanner(ctx.rules(), ctx.book());
        List<String[]> targets = new ArrayList<>(); // table, column, ruleId|""
        ResultSet rs = c.getMetaData().getColumns(null, "PUBLIC", "%", "%");
        while (rs.next()) {
            String type = rs.getString("TYPE_NAME");
            if (type == null || !type.toUpperCase().startsWith("CHARACTER VARYING")
                    && !type.toUpperCase().startsWith("VARCHAR")) {
                continue;
            }
            String col = rs.getString("COLUMN_NAME");
            String ruleId = ctx.rules().columnRules().getOrDefault(col.toLowerCase(), "");
            targets.add(new String[] {rs.getString("TABLE_NAME"), col, ruleId});
        }
        rs.close();
        int hits = 0;
        for (String[] t : targets) {
            String q = "\"" + t[0] + "\"";
            String qc = "\"" + t[1] + "\"";
            List<String> values = new ArrayList<>();
            try (Statement st = c.createStatement();
                    ResultSet r = st.executeQuery("SELECT DISTINCT " + qc + " FROM " + q
                            + " WHERE " + qc + " IS NOT NULL")) {
                while (r.next()) {
                    values.add(r.getString(1));
                }
            }
            for (String v : values) {
                TextScanner.ScanResult sr = ctx.reverse()
                        ? s.unmaskText(v)
                        : (t[2].isEmpty() ? s.maskText(v) : s.maskCell(t[1], v));
                if (sr.hits() == 0 || sr.text().equals(v)) {
                    continue;
                }
                try (java.sql.PreparedStatement p = c.prepareStatement("UPDATE " + q + " SET " + qc + "=? WHERE "
                        + qc + "=?")) {
                    p.setString(1, sr.text());
                    p.setString(2, v);
                    p.executeUpdate();
                }
                hits += sr.hits();
            }
        }
        return hits;
    }

    private static boolean isZip(Path p) {
        try (var is = Files.newInputStream(p)) {
            byte[] h = is.readNBytes(2);
            return h.length >= 2 && h[0] == 'P' && h[1] == 'K';
        } catch (IOException e) {
            return false;
        }
    }
}
