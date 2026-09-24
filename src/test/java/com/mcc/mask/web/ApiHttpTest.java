package com.mcc.mask.web;

import com.mcc.mask.rules.RuleTable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 真 HTTP 走一遍（原名 ApiIT；改名让 mvn test 默认收进来看得到）：门禁 → 建码本 → 批量脱敏 → 对照表 → 上传 AI 产物还原 → 口令/越界负例。 */
class ApiHttpTest {

    @TempDir
    Path root;

    private WebServer server;
    private String base;
    private final HttpClient http = HttpClient.newHttpClient();

    private record Http(int status, String body, String disposition) {
        boolean has(String s) {
            return body.contains(s);
        }
    }

    @BeforeEach
    void up() throws IOException {
        Files.writeString(root.resolve("台账.csv"),
                "车牌,手机,厂名\n苏A12345,13800001111,昆山精密五金厂\n浙B88888,13900002222,宁波模具有限公司\n");
        Files.writeString(root.resolve("报告.md"), "客户 13800001111 于 2026-09-20 反馈交期\n");
        Files.writeString(root.resolve("名单.txt"), "司机 320501199001011234 已登记\n");
        Api api = new Api(new Workspace(root), new Session(30 * 60_000),
                root.resolve("mb").toString(), RuleTable.defaultTable());
        server = new WebServer(0, api);
        server.start();
        base = "http://127.0.0.1:" + server.port();
    }

    @AfterEach
    void down() {
        server.stop();
    }

    private Http get(String path) throws Exception {
        return send(HttpRequest.newBuilder(URI.create(base + path)).GET());
    }

    private Http post(String path, String json) throws Exception {
        return send(HttpRequest.newBuilder(URI.create(base + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)));
    }

    private Http postMultipart(String path, String fileName, byte[] data) throws Exception {
        String b = "----mccit";
        byte[] head = ("--" + b + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\""
                + fileName + "\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] tail = ("\r\n--" + b + "--\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[head.length + data.length + tail.length];
        System.arraycopy(head, 0, body, 0, head.length);
        System.arraycopy(data, 0, body, head.length, data.length);
        System.arraycopy(tail, 0, body, head.length + data.length, tail.length);
        return send(HttpRequest.newBuilder(URI.create(base + path))
                .header("Content-Type", "multipart/form-data; boundary=" + b)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)));
    }

    private static String enc(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private Http send(HttpRequest.Builder rb) throws Exception {
        HttpResponse<byte[]> r = http.send(rb.build(), HttpResponse.BodyHandlers.ofByteArray());
        String cd = r.headers().firstValue("Content-Disposition").orElse("");
        return new Http(r.statusCode(), new String(r.body(), StandardCharsets.UTF_8), cd);
    }

    @Test
    void 未解锁时数据接口全401() throws Exception {
        assertEquals(200, get("/api/state").status);
        assertTrue(get("/api/state").has("\"unlocked\":false"));
        assertEquals(401, get("/api/ls").status);
        assertEquals(401, get("/api/pairs").status);
        assertEquals(401, get("/file?path=台账.csv").status);
        assertEquals(401, post("/api/run", "{\"mode\":\"mask\",\"files\":[\"台账.csv\"]}").status);
    }

    @Test
    void 建码本_批量脱敏_上传AI产物_还原闭环() throws Exception {
        assertEquals(200, post("/api/book/new", "{\"pass\":\"口令A\"}").status);
        assertTrue(get("/api/state").has("\"unlocked\":true"));
        assertFalse(get("/api/ls").body().contains("\"mb\""), "码本目录不该出现在数据列表里");

        Http run = post("/api/run", "{\"mode\":\"mask\",\"files\":[\"台账.csv\",\"报告.md\",\"名单.txt\"]}");
        assertEquals(200, run.status, run.body);
        assertTrue(run.has("\"succeeded\":3"), run.body);
        assertTrue(run.has("\"hits\":6"), "台账.csv 2 行×3 列结构化命中: " + run.body);
        assertTrue(run.has("\"mapped\":7"), "手机跨文件复用同一条映射: " + run.body);

        assertTrue(Files.readString(root.resolve("台账.csv")).contains("苏A12345"), "原文件不许动");
        String masked = Files.readString(root.resolve("台账.masked.csv"));
        assertFalse(masked.contains("苏A12345"), masked);
        assertFalse(masked.contains("13800001111"), masked);
        assertFalse(masked.contains("昆山精密五金厂"), masked);
        String maskedId = Files.readString(root.resolve("名单.masked.txt"));
        assertFalse(maskedId.contains("320501199001011234"), maskedId);

        Http pairs = get("/api/pairs?q=苏");
        assertTrue(pairs.has("\"total\":1"), pairs.body);

        // 假"AI 产物"：只含假值的 md（模拟 AI 汇总后回传）
        String fake = Files.readString(root.resolve("报告.masked.md"));
        Http up = postMultipart("/api/upload", "AI汇总.md", ("# 汇总\n" + fake + "\n额外结论\n").getBytes(StandardCharsets.UTF_8));
        assertEquals(200, up.status, up.body);
        assertTrue(up.has(".mcc-upload/AI汇总.md"), up.body);

        Http back = post("/api/run", "{\"mode\":\"unmask\",\"files\":[\".mcc-upload/AI汇总.md\"]}");
        assertEquals(200, back.status, back.body);
        assertTrue(back.has("\"hits\":1"), back.body);
        String restored = Files.readString(root.resolve(".mcc-upload/AI汇总.restored.md"));
        assertTrue(restored.contains("13800001111"), restored);
        assertTrue(restored.contains("额外结论"), "AI 自己写的内容要原样保留");

        Http csv = get("/api/export/pairs");
        assertEquals(200, csv.status);
        assertTrue(csv.disposition.contains("filename*=UTF-8''%E5%AF%B9%E7%85%A7%E8%A1%A8.csv"), csv.disposition);
        assertTrue(csv.has("昆山精密五金厂"), csv.body);
    }

    @Test
    void 错口令与路径越界与锁定() throws Exception {
        post("/api/book/new", "{\"pass\":\"口令A\"}");
        assertEquals(200, post("/api/lock", "{}").status);
        assertTrue(get("/api/state").has("\"unlocked\":false"));

        Http wrong = post("/api/book/unlock", "{\"pass\":\"猜的\"}");
        assertEquals(401, wrong.status, wrong.body);
        assertTrue(wrong.has("码本口令错误"), wrong.body);

        assertTrue(post("/api/book/unlock", "{\"pass\":\"口令A\"}").has("\"ok\":true"));
        assertEquals(400, get("/file?path=" + enc("../外部.csv")).status);
        assertEquals(400, get("/file?path=" + enc("C:\\Windows\\win.ini")).status);
        assertEquals(200, get("/file?path=" + enc("台账.csv")).status);

        Http dup = post("/api/book/new", "{\"pass\":\"口令B\"}");
        assertEquals(400, dup.status);
        assertTrue(dup.has("码本已存在"), dup.body);

        Http notSup = post("/api/run", "{\"mode\":\"mask\",\"files\":[\"不存在.xyz\"]}");
        assertTrue(notSup.has("\"ok\":false"), notSup.body);
    }
}
