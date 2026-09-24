package com.mcc.mask.rw;

import com.mcc.mask.MaskException;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** .xlsx/.xlsm 读写器（POI；公式/样式/合并单元格原样保留）。 */
public final class XlsxRw implements Rw {

    @Override
    public String name() {
        return "xlsx";
    }

    @Override
    public RwResult process(Path in, Path out, Ctx ctx) {
        try (InputStream inIs = Files.newInputStream(in); XSSFWorkbook wb = new XSSFWorkbook(inIs)) {
            RwResult r = SheetRw.process(wb, ctx);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            com.mcc.mask.util.AtomicWrite.write(out, bos.toByteArray());
            return r;
        } catch (IOException e) {
            throw new MaskException("xlsx 处理失败: " + in + " — " + e.getMessage(), e);
        }
    }
}
