package com.mcc.mask.web;

import com.mcc.mask.MaskException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 工作目录护栏：越界一律拒绝，上传件只留文件名，目录列举隐藏内部件。 */
class WorkspaceTest {

    @TempDir
    Path root;

    @Test
    void 正常相对路径放行() {
        Workspace ws = new Workspace(root);
        Path p = ws.resolve("台账/2026.csv");
        assertTrue(p.startsWith(root.toAbsolutePath().normalize()));
        assertEquals("台账/2026.csv", ws.relativize(p));
    }

    @Test
    void 父目录逃逸与绝对路径全拒() {
        Workspace ws = new Workspace(root);
        for (String bad : List.of("../evil.csv", "a/../../evil.csv", "/etc/passwd",
                "C:\\Windows\\win.ini", "\\\\server\\share\\x.csv")) {
            MaskException e = assertThrows(MaskException.class, () -> ws.resolve(bad), bad);
            assertTrue(e.getMessage().contains("目录") || e.getMessage().contains("相对路径"), e.getMessage());
        }
    }

    @Test
    void 上传只取文件名且重名自动避让() throws Exception {
        Workspace ws = new Workspace(root);
        byte[] data = "a,b\n1,2\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Path first = ws.land("../../恶意/报告.csv", data);
        Path second = ws.land("报告.csv", data);
        assertEquals(ws.uploadDir(), first.getParent());
        assertFalse(first.equals(second));
        assertTrue(Files.exists(second));
        assertEquals(".mcc-upload", ws.relativize(first).split("/")[0]);
    }

    @Test
    void 拖文件夹进来保留子目录_但越界与超深直接拒() throws Exception {
        Workspace ws = new Workspace(root);
        byte[] data = "车牌\n苏A12345\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Path in = ws.landIn("AI产物/三月/台账.csv", data);
        assertEquals(".mcc-upload/AI产物/三月/台账.csv", ws.relativize(in));
        Path same = ws.landIn("AI产物/三月/台账.csv", data);
        assertFalse(in.equals(same), "同目录重名要避让，不能覆盖");
        assertTrue(Files.exists(same));
        assertThrows(MaskException.class, () -> ws.landIn("AI产物/../../evil.csv", data));
        assertThrows(MaskException.class, () -> ws.landIn("../outside.csv", data));
        StringBuilder deep = new StringBuilder();
        for (int i = 0; i <= Workspace.MAX_UPLOAD_DEPTH; i++) {
            deep.append("d").append(i).append('/');
        }
        assertThrows(MaskException.class, () -> ws.landIn(deep + "x.csv", data));
        assertFalse(Files.exists(root.resolveSibling("evil.csv")));
    }

    @Test
    void 超大上传直接拒绝() {
        Workspace ws = new Workspace(root);
        byte[] big = new byte[(int) (Workspace.MAX_UPLOAD_BYTES + 1)];
        assertThrows(MaskException.class, () -> ws.land("x.csv", big));
    }

    @Test
    void 列目录隐藏内部件并标注支持格式() throws Exception {
        Files.writeString(root.resolve("台账.csv"), "车牌\n苏A12345\n");
        Files.writeString(root.resolve("照片.png"), "x");
        Files.createDirectory(root.resolve("子目录"));
        ws_upload_probe();
        List<Workspace.Entry> entries = new Workspace(root).list("");
        List<String> names = entries.stream().map(e -> e.name()).toList();
        assertTrue(names.contains("台账.csv"), names.toString());
        assertTrue(names.contains("子目录"), names.toString());
        assertFalse(names.contains(".mcc-upload"), "上传区不出现在主列表");
        assertTrue(names.contains("照片.png"), "不支持的文件照样列出，交给前端置灰");
        Workspace.Entry csv = entries.stream().filter(e -> e.name().equals("台账.csv")).findFirst().orElseThrow();
        assertTrue(csv.supported());
        assertFalse(entries.stream().filter(e -> e.name().equals("照片.png")).findFirst().orElseThrow().supported());
        assertFalse(entries.stream().filter(e -> e.name().equals("子目录")).findFirst().orElseThrow().supported());
    }

    /** 先碰一次 uploadDir()，确认它会被建出来并被 list 隐藏。 */
    private void ws_upload_probe() {
        new Workspace(root).uploadDir();
    }
}
