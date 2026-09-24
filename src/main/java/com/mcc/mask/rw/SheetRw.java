package com.mcc.mask.rw;

import com.mcc.mask.scan.TextScanner;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFCell;

import java.util.ArrayList;
import java.util.List;

/** 电子表格通用处理（XSSF/HSSF 共用）：首行=表头→列名命中；仅动字符串单元格。 */
final class SheetRw {

    static Rw.RwResult process(Workbook wb, Ctx ctx) {
        TextScanner s = new TextScanner(ctx.rules(), ctx.book());
        int hits = 0;
        List<String> warnings = new ArrayList<>();
        for (int i = 0; i < wb.getNumberOfSheets(); i++) {
            Sheet sh = wb.getSheetAt(i);
            Row header = sh.getRow(sh.getFirstRowNum());
            String[] cols = new String[0];
            int firstDataRow = sh.getFirstRowNum();
            if (header != null) {
                cols = new String[header.getLastCellNum()];
                for (int c = 0; c < header.getLastCellNum(); c++) {
                    Cell hc = header.getCell(c);
                    cols[c] = hc == null || hc.getCellType() != CellType.STRING ? "" : hc.getStringCellValue().trim();
                }
                firstDataRow = sh.getFirstRowNum() + 1;
            }
            for (int r = firstDataRow; r <= sh.getLastRowNum(); r++) {
                Row row = sh.getRow(r);
                if (row == null) {
                    continue;
                }
                for (Cell cell : row) {
                    if (cell.getCellType() == CellType.NUMERIC && ctx.jitter() && !ctx.reverse()) {
                        double d = cell.getNumericCellValue();
                        String s0 = new java.math.BigDecimal(Double.toString(d)).toPlainString();
                        String j = com.mcc.mask.rules.Transformers.jitter(3.0)
                                .fake(s0, (sh.getSheetName() + r + cell.getColumnIndex()).hashCode());
                        cell.setCellValue(Double.parseDouble(j));
                        hits++;
                        continue;
                    }
                    if (cell.getCellType() != CellType.STRING) {
                        continue; // 数值(未开 jitter)/公式/日期不动
                    }
                    String v = cell.getStringCellValue();
                    if (v.isEmpty()) {
                        continue;
                    }
                    String col = cell.getColumnIndex() < cols.length ? cols[cell.getColumnIndex()] : "";
                    TextScanner.ScanResult sr = ctx.reverse() ? s.unmaskText(v) : s.maskCell(col, v);
                    hits += sr.hits();
                    warnings.addAll(sr.warnings());
                    if (sr.hits() > 0) {
                        writeText(cell, sr.text());
                    }
                }
            }
        }
        return new Rw.RwResult(hits, warnings);
    }

    /**
     * 写回文本。坑：openpyxl 等工具写的 xlsx 用 inline string（{@code <c t="inlineStr"><is><t>值</t></is></c>}），
     * POI 的 setCellValue 只新增 {@code <v>} 不清 {@code <is>}，而 Excel/POI 读的都是 {@code <is>}
     * → 产物看着完全没脱敏（真值还在），日志却报"替换 N 处"。先摘掉 inline 串再写。
     */
    static void writeText(Cell cell, String text) {
        if (cell instanceof XSSFCell xssf && xssf.getCTCell().isSetIs()) {
            xssf.getCTCell().unsetIs();
            xssf.getCTCell().unsetT();
        }
        cell.setCellValue(text);
    }

    private SheetRw() {
    }
}
