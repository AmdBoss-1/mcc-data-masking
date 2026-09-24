package com.mcc.mask.rw;

import com.mcc.mask.MaskException;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** .xls（HSSF 老 Excel）读写器：成熟读写，同 SheetRw 逻辑。 */
public final class XlsRw implements Rw {

    @Override
    public String name() {
        return "xls";
    }

    @Override
    public RwResult process(Path in, Path out, Ctx ctx) {
        try (InputStream inIs = Files.newInputStream(in); HSSFWorkbook wb = new HSSFWorkbook(inIs)) {
            RwResult r = SheetRw.process(wb, ctx);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            com.mcc.mask.util.AtomicWrite.write(out, bos.toByteArray());
            return r;
        } catch (IOException e) {
            throw new MaskException("xls 处理失败: " + in + " — " + e.getMessage(), e);
        }
    }
}
