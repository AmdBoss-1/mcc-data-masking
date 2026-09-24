package com.mcc.mask.rw;

import com.mcc.mask.scan.TextScanner;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

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
                    if (cell.getCellType() != CellType.STRING) {
                        continue; // 数值/公式/日期不动（--jitter 在 CLI 层另接）
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
                        cell.setCellValue(sr.text());
                    }
                }
            }
        }
        return new Rw.RwResult(hits, warnings);
    }

    private SheetRw() {
    }
}
