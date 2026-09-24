package com.mcc.mask.web;

import com.mcc.mask.MaskException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 极简 multipart/form-data 解析（只为上传服务，不引第三方库）。
 * 用 ISO-8859-1 做字节↔字符无损中转，切完再按原字节还原，保证 xlsx 等二进制不被改坏。
 */
public final class Multipart {

    public record Part(String name, String fileName, byte[] data) {
    }

    public static List<Part> parse(byte[] body, String boundary) {
        if (boundary == null || boundary.isBlank()) {
            throw new MaskException("multipart 请求缺少 boundary");
        }
        String text = new String(body, StandardCharsets.ISO_8859_1);
        String delim = "--" + boundary;
        List<Part> parts = new ArrayList<>();
        int pos = text.indexOf(delim);
        while (pos >= 0) {
            int from = pos + delim.length();
            if (text.startsWith("--", from)) {
                break;
            }
            int next = text.indexOf(delim, from);
            if (next < 0) {
                break;
            }
            addPart(text.substring(from, next), parts);
            pos = next;
        }
        if (parts.isEmpty()) {
            throw new MaskException("上传内容为空");
        }
        return parts;
    }

    private static void addPart(String chunk, List<Part> parts) {
        int headerEnd = chunk.indexOf("\r\n\r\n");
        if (headerEnd < 0) {
            return;
        }
        String headers = chunk.substring(0, headerEnd);
        byte[] data = chunk.substring(headerEnd + 4).getBytes(StandardCharsets.ISO_8859_1);
        if (data.length >= 2 && data[data.length - 2] == '\r' && data[data.length - 1] == '\n') {
            byte[] trimmed = new byte[data.length - 2];
            System.arraycopy(data, 0, trimmed, 0, trimmed.length);
            data = trimmed;
        }
        String name = null;
        String fileName = null;
        for (String line : headers.split("\r\n")) {
            String l = line.trim();
            if (!l.toLowerCase(java.util.Locale.ROOT).startsWith("content-disposition")) {
                continue;
            }
            for (String seg : l.split(";")) {
                String s = seg.trim();
                if (s.startsWith("name=")) {
                    name = unquote(s.substring(5));
                } else if (s.startsWith("filename=")) {
                    fileName = utf8(unquote(s.substring(9)));
                }
            }
        }
        parts.add(new Part(name, fileName, data));
    }

    private static String unquote(String v) {
        String s = v.trim();
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length() - 1);
        }
        return s;
    }

    /** 头域按 ISO-8859-1 中转，中文文件名要还原成 UTF-8。 */
    private static String utf8(String latin1) {
        return new String(latin1.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
    }

    private Multipart() {
    }
}
