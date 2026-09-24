package com.mcc.mask.cli;

import com.mcc.mask.util.AtomicWrite;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CliE2ETest {

    @Test
    void maskUnmaskLookupPurgeLoop(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("t.csv");
        Files.writeString(src, "车牌,备注\n苏E·D1234,到厂 13812345678\n", StandardCharsets.UTF_8);
        Path pf = dir.resolve("pw.txt");
        Files.writeString(pf, "测试口令Abc123\n", StandardCharsets.UTF_8);
        String bookDir = dir.resolve("mb").toString();
        Path masked = dir.resolve("t.masked.csv");
        Path back = dir.resolve("t.restored.csv");
        Path bookFile = dir.resolve("mb/book.mccmask");

        assertEquals(0, MaskCli.run(new String[] {"mask", src.toString(), "-o", masked.toString(),
                "--book", bookDir, "--pass-file", pf.toString()}));
        assertTrue(Files.exists(bookFile), "首用应自动建码本");
        assertTrue(Files.exists(dir.resolve("mb/.gitignore")), "码本目录应有 .gitignore 兜底");
        String mt = Files.readString(masked, StandardCharsets.UTF_8);
        assertFalse(mt.contains("苏E·D1234"));
        assertFalse(mt.contains("13812345678"));

        assertEquals(0, MaskCli.run(new String[] {"unmask", masked.toString(), "-o", back.toString(),
                "--book", bookDir, "--pass-file", pf.toString()}));
        assertArrayEquals(Files.readAllBytes(src), Files.readAllBytes(back));

        String fakePlate = mt.lines().filter(l -> l.contains(",") && !l.startsWith("车牌"))
                .findFirst().orElseThrow().split(",")[0];
        assertEquals(0, MaskCli.run(new String[] {"lookup", fakePlate, "--book", bookDir,
                "--pass-file", pf.toString()}));

        Path badPw = dir.resolve("bad.txt");
        Files.writeString(badPw, "错口令Xyz789\n", StandardCharsets.UTF_8);
        assertEquals(2, MaskCli.run(new String[] {"unmask", masked.toString(), "-o", dir.resolve("x.csv").toString(),
                "--book", bookDir, "--pass-file", badPw.toString()}), "错口令应退出码 2");

        assertEquals(0, MaskCli.run(new String[] {"purge", "--book", bookDir}));
        assertFalse(Files.exists(bookFile), "purge 后码本必须消失");
    }

    @Test
    void jitterFlagPerturbsNumbers(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("n.xlsx");
        try (XSSFWorkbook wb = new XSSFWorkbook(); OutputStream os = Files.newOutputStream(src)) {
            Sheet sh = wb.createSheet("s");
            Row h = sh.createRow(0);
            h.createCell(0).setCellValue("重量kg");
            Row r1 = sh.createRow(1);
            r1.createCell(0).setCellValue(42.317);
            wb.write(os);
        }
        Path pf = dir.resolve("pw.txt");
        Files.writeString(pf, "口令K1l2m3\n", StandardCharsets.UTF_8);
        String bookDir = dir.resolve("mb2").toString();
        Path masked = dir.resolve("n.masked.xlsx");
        assertEquals(0, MaskCli.run(new String[] {"mask", src.toString(), "-o", masked.toString(),
                "--book", bookDir, "--pass-file", pf.toString(), "--jitter"}));
        try (var wb = new XSSFWorkbook(Files.newInputStream(masked))) {
            double v = wb.getSheetAt(0).getRow(1).getCell(0).getNumericCellValue();
            assertNotEquals(42.317, v, 1e-9);
            assertTrue(v >= 42.317 * 0.97 && v <= 42.317 * 1.03, "±3% 内: " + v);
        }
    }
}
