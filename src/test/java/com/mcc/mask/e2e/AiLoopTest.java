package com.mcc.mask.e2e;

import com.mcc.mask.cli.MaskCli;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** 主闭环：mask 台账 → 假 AI 产出带假值的报告 → unmask 报告 → 真值回来。 */
class AiLoopTest {

    static final String PLATE1 = "苏E·D1234";
    static final String PLATE2 = "苏A·B8888";
    static final String CUST1 = "昆山精密五金厂";
    static final String CUST2 = "吴江恒达金属制品厂";
    static final String PHONE1 = "13812345678";

    private Path buildLedger(Path dir) throws Exception {
        Path p = dir.resolve("台账.xlsx");
        try (XSSFWorkbook wb = new XSSFWorkbook(); OutputStream os = Files.newOutputStream(p)) {
            Sheet sh = wb.createSheet("重量台账");
            Row h = sh.createRow(0);
            h.createCell(0).setCellValue("车牌");
            h.createCell(1).setCellValue("重量kg");
            h.createCell(2).setCellValue("客户");
            h.createCell(3).setCellValue("备注");
            Row r1 = sh.createRow(1);
            r1.createCell(0).setCellValue(PLATE1);
            r1.createCell(1).setCellValue(42.317);
            r1.createCell(2).setCellValue(CUST1);
            r1.createCell(3).setCellValue("到厂 " + PHONE1);
            Row r2 = sh.createRow(2);
            r2.createCell(0).setCellValue(PLATE2);
            r2.createCell(1).setCellValue(10.5);
            r2.createCell(2).setCellValue(CUST2);
            r2.createCell(3).setCellValue("正常");
            wb.write(os);
        }
        return p;
    }

    @Test
    void maskSendToAiThenRestoreAiOutput(@TempDir Path dir) throws Exception {
        Path ledger = buildLedger(dir);
        Path pw = dir.resolve("pw.txt");
        Files.writeString(pw, "闭环口令Aa123456\n", StandardCharsets.UTF_8);
        String book = dir.resolve("mb").toString();
        Path masked = dir.resolve("台账.masked.xlsx");

        assertEquals(0, MaskCli.run(new String[] {"mask", ledger.toString(), "-o", masked.toString(),
                "--book", book, "--pass-file", pw.toString()}));

        // 读脱敏件（模拟"AI 只看到这份"）
        String fakePlate;
        String fakeCust;
        String fakeNote;
        try (var is = Files.newInputStream(masked); XSSFWorkbook wb = new XSSFWorkbook(is)) {
            Sheet sh = wb.getSheetAt(0);
            fakePlate = sh.getRow(1).getCell(0).getStringCellValue();
            fakeCust = sh.getRow(1).getCell(2).getStringCellValue();
            fakeNote = sh.getRow(1).getCell(3).getStringCellValue();
        }
        assertNotEquals(PLATE1, fakePlate);
        assertNotEquals(CUST1, fakeCust);
        assertFalse(fakeNote.contains(PHONE1));

        // "假 AI"：产出一份 md 报告 + 一份 xlsx 汇总，内容只含假值
        Path aiMd = dir.resolve("ai-report.md");
        Files.writeString(aiMd, "# 过磅分析\n- 车辆 " + fakePlate + " 今日 42.317 kg，客户 " + fakeCust
                + "，备注：" + fakeNote + "\n- 另一车 " + sh2(masked) + " 合计 52.817 kg\n", StandardCharsets.UTF_8);
        Path aiXlsx = dir.resolve("ai-summary.xlsx");
        try (XSSFWorkbook wb = new XSSFWorkbook(); OutputStream os = Files.newOutputStream(aiXlsx)) {
            Sheet sh = wb.createSheet("汇总");
            Row r = sh.createRow(0);
            r.createCell(0).setCellValue("客户");
            r.createCell(1).setCellValue("合计kg");
            Row r1 = sh.createRow(1);
            r1.createCell(0).setCellValue(fakeCust);
            r1.createCell(1).setCellValue(42.317);
            wb.write(os);
        }

        Path restoredMd = dir.resolve("ai-report.restored.md");
        Path restoredXlsx = dir.resolve("ai-summary.restored.xlsx");
        assertEquals(0, MaskCli.run(new String[] {"unmask", aiMd.toString(), "-o", restoredMd.toString(),
                "--book", book, "--pass-file", pw.toString()}));
        assertEquals(0, MaskCli.run(new String[] {"unmask", aiXlsx.toString(), "-o", restoredXlsx.toString(),
                "--book", book, "--pass-file", pw.toString()}));

        String md = Files.readString(restoredMd, StandardCharsets.UTF_8);
        assertTrue(md.contains(PLATE1), md);
        assertTrue(md.contains(CUST1), md);
        assertTrue(md.contains(PHONE1), md);
        assertTrue(md.contains("42.317"), "数值原样保留才能对账: " + md);

        try (var is = Files.newInputStream(restoredXlsx); XSSFWorkbook wb = new XSSFWorkbook(is)) {
            assertEquals(CUST1, wb.getSheetAt(0).getRow(1).getCell(0).getStringCellValue());
            assertEquals(42.317, wb.getSheetAt(0).getRow(1).getCell(1).getNumericCellValue(), 1e-9);
        }
    }

    private static String sh2(Path masked) throws Exception {
        try (var is = Files.newInputStream(masked); XSSFWorkbook wb = new XSSFWorkbook(is)) {
            return wb.getSheetAt(0).getRow(2).getCell(0).getStringCellValue();
        }
    }
}
