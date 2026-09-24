package com.mcc.mask.web;

import com.mcc.mask.rules.RuleTable;
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

/** serve 冒烟：页面能开、是 UTF-8 中文、且一个外链都没有（断网可用）。 */
class ServeSmokeTest {

    @TempDir
    Path root;

    @Test
    void 首页200且零外链() throws Exception {
        Api api = new Api(new Workspace(root), new Session(60_000), root.resolve("mb").toString(),
                RuleTable.defaultTable());
        try (WebServer server = new WebServer(0, api)) {
            server.start();
            assertTrue(server.port() > 0);
            HttpResponse<String> r = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + "/")).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            assertEquals(200, r.statusCode());
            assertTrue(r.headers().firstValue("Content-Type").orElse("").contains("text/html"));
            assertTrue(r.body().contains("MCC 脱敏控制台"), "标题应渲染出来");
            assertTrue(r.body().contains("码本"), "门禁文案在");
            assertFalse(r.body().matches("(?s).*<script[^>]+src=.*"), "不得引外部脚本");
            assertFalse(r.body().matches("(?s).*<link[^>]+href=\"https?:.*"), "不得引外部样式");
            assertEquals(404, get(server, "/nope").statusCode());
        }
    }

    @Test
    void 假Host头被拒_DNS重绑定无效() throws Exception {
        Api api = new Api(new Workspace(root), new Session(60_000), root.resolve("mb").toString(),
                RuleTable.defaultTable());
        try (WebServer server = new WebServer(0, api)) {
            server.start();
            String raw = "GET /api/state HTTP/1.1\r\nHost: evil.example.com\r\nConnection: close\r\n\r\n";
            try (java.net.Socket so = new java.net.Socket("127.0.0.1", server.port())) {
                so.getOutputStream().write(raw.getBytes(StandardCharsets.US_ASCII));
                so.getOutputStream().flush();
                String resp = new String(so.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(resp.startsWith("HTTP/1.1 403"), resp.substring(0, Math.min(80, resp.length())));
            }
        }
    }

    private static HttpResponse<String> get(WebServer s, String path) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + s.port() + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}
