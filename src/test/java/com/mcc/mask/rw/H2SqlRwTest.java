package com.mcc.mask.rw;

import com.mcc.mask.MaskException;
import com.mcc.mask.codebook.Codebook;
import com.mcc.mask.codebook.CodebookJson;
import com.mcc.mask.rules.RuleTable;
import com.mcc.mask.util.AtomicWrite;
import com.mcc.mask.util.Summary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class H2SqlRwTest {

    private final RuleTable rules = RuleTable.defaultTable();

    private String urlOf(Path db) {
        String u = db.toAbsolutePath().toString();
        return "jdbc:h2:" + (u.toLowerCase().endsWith(".mv.db") ? u.substring(0, u.length() - ".mv.db".length()) : u);
    }

    private Path makeDb(Path dir, String name) throws Exception {
        Path db = dir.resolve(name);
        try (Connection c = DriverManager.getConnection(urlOf(db), "sa", ""); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE weight_item(plate VARCHAR(30), kg NUMERIC(12,3), customer VARCHAR(60), note VARCHAR(200))");
        }
        try (Connection c = DriverManager.getConnection(urlOf(db), "sa", "")) {
            try (PreparedStatement p = c.prepareStatement(
                    "INSERT INTO weight_item VALUES(?,?,?,?)")) {
                String[][] rows = {
                        {"苏E·D1234", "42.317", "昆山精密五金厂", "到厂 13812345678"},
                        {"苏A·B8888", "10.500", "吴江恒达金属制品厂", "正常"},
                        {"苏B·C1111", "7.200", "昆山精密五金厂", "电话13900001111"},
                };
                for (String[] r : rows) {
                    p.setString(1, r[0]);
                    p.setBigDecimal(2, new java.math.BigDecimal(r[1]));
                    p.setString(3, r[2]);
                    p.setString(4, r[3]);
                    p.addBatch();
                }
                p.executeBatch();
            }
        }
        return db;
    }

    private List<String> dump(Path db) throws Exception {
        List<String> out = new ArrayList<>();
        try (Connection c = DriverManager.getConnection(urlOf(db), "sa", "");
             Statement st = c.createStatement();
             ResultSet r = st.executeQuery("SELECT plate, kg, customer, note FROM weight_item ORDER BY plate")) {
            while (r.next()) {
                out.add(r.getString(1) + "|" + r.getBigDecimal(2).stripTrailingZeros().toPlainString()
                        + "|" + r.getString(3) + "|" + r.getString(4));
            }
        }
        return out;
    }

    @Test
    void h2RoundTrip(@TempDir Path dir) throws Exception {
        Path src = makeDb(dir, "weight.mv.db");
        List<String> before = dump(src);
        Path masked = dir.resolve("m.mv.db");
        Path back = dir.resolve("r.mv.db");
        Codebook book = new Codebook(new CodebookJson());
        Summary sum = new Summary();

        Rw.RwResult r = RwFactory.byPath(src).process(src, masked, Ctx.mask(book, rules, sum));
        assertTrue(r.hits() >= 6, "3车牌+2客户+2手机 去重后应≥6: " + r.hits());
        List<String> mt = dump(masked);
        String joined = String.join("\n", mt);
        assertFalse(joined.contains("苏E·D1234"));
        assertFalse(joined.contains("昆山精密五金厂"));
        assertFalse(joined.contains("13812345678"));
        assertTrue(joined.contains("42.317"), "数值列不动");
        for (String line : mt) {
            assertTrue(line.split("\\|")[0].matches("^[一-龥][A-Z]·?[A-Z0-9]{5,6}$"), line);
        }
        assertArrayEquals(before.toArray(new String[0]), dump(src).toArray(new String[0]), "原库不得被改动");

        RwFactory.byPath(masked).process(masked, back, Ctx.unmask(book, rules, sum));
        assertArrayEquals(before.toArray(new String[0]), dump(back).toArray(new String[0]));
    }

    @Test
    void mccbakRejectedWithGuidance(@TempDir Path dir) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(bos)) {
            z.putNextEntry(new ZipEntry("manifest.json"));
            z.write("{\"format\":\"mcc-suite-bak\"}".getBytes(StandardCharsets.UTF_8));
            z.closeEntry();
        }
        Path pkg = dir.resolve("suite-20260924.mccbak");
        AtomicWrite.write(pkg, bos.toByteArray());
        MaskException ex = assertThrows(MaskException.class,
                () -> RwFactory.byPath(pkg).process(pkg, dir.resolve("o.mccbak"),
                        Ctx.mask(new Codebook(new CodebookJson()), rules, new Summary())));
        assertTrue(ex.getMessage().contains("不支持直读"), ex.getMessage());
    }

    @Test
    void sqlDumpRoundTrip(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("dump.sql");
        String sql = "INSERT INTO weight_item(plate,kg,note) VALUES('苏E·D1234',42.317,'到厂 13812345678');\n";
        Files.writeString(src, sql, StandardCharsets.UTF_8);
        Path masked = dir.resolve("m.sql");
        Path back = dir.resolve("r.sql");
        Codebook book = new Codebook(new CodebookJson());
        Summary sum = new Summary();
        Rw.RwResult r = RwFactory.byPath(src).process(src, masked, Ctx.mask(book, rules, sum));
        assertEquals(2, r.hits());
        String mt = Files.readString(masked, StandardCharsets.UTF_8);
        assertTrue(mt.contains("42.317"));
        RwFactory.byPath(masked).process(masked, back, Ctx.unmask(book, rules, sum));
        assertArrayEquals(Files.readAllBytes(src), Files.readAllBytes(back));
    }
}
