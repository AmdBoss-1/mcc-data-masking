package com.mcc.mask.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcc.mask.MaskException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** 字段规则表：结构化正则 + 列名命中 + 凭据关键词；支持 JSON 覆盖合并。 */
public final class RuleTable {

    public record StructRule(String id, Pattern pattern, String transformer) {
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final List<StructRule> struct;
    private final Map<String, String> columns;
    private final List<String> credentials;

    private RuleTable(List<StructRule> struct, Map<String, String> columns, List<String> credentials) {
        this.struct = struct;
        this.columns = columns;
        this.credentials = credentials;
    }

    public static RuleTable defaultTable() {
        try (InputStream in = RuleTable.class.getResourceAsStream("/default-rules.json")) {
            if (in == null) {
                throw new MaskException("缺少内置规则资源 default-rules.json");
            }
            return fromJson(MAPPER.readTree(in));
        } catch (IOException e) {
            throw new MaskException("读取默认规则失败: " + e.getMessage(), e);
        }
    }

    /** 覆盖合并：同 id 的 struct 替换、columns/credentials 追加或覆盖。 */
    public static RuleTable load(Path override) throws IOException {
        RuleTable base = defaultTable();
        JsonNode o = MAPPER.readTree(override.toFile());
        List<StructRule> struct = new ArrayList<>(base.struct);
        Map<String, String> columns = new LinkedHashMap<>(base.columns);
        List<String> creds = new ArrayList<>(base.credentials);
        for (JsonNode s : o.path("struct")) {
            String id = s.path("id").asText();
            struct.removeIf(x -> x.id().equals(id));
            struct.add(new StructRule(id, Pattern.compile(s.path("regex").asText()), s.path("transformer").asText()));
        }
        Iterator<Map.Entry<String, JsonNode>> it = o.path("columns").fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            columns.put(e.getKey(), e.getValue().asText());
        }
        for (JsonNode c : o.path("credentials")) {
            String v = c.asText();
            if (!creds.contains(v)) {
                creds.add(v);
            }
        }
        return new RuleTable(struct, columns, creds);
    }

    private static RuleTable fromJson(JsonNode root) {
        List<StructRule> struct = new ArrayList<>();
        for (JsonNode s : root.path("struct")) {
            struct.add(new StructRule(s.path("id").asText(),
                    Pattern.compile(s.path("regex").asText()),
                    s.path("transformer").asText()));
        }
        Map<String, String> columns = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> it = root.path("columns").fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            columns.put(e.getKey(), e.getValue().asText());
        }
        List<String> creds = new ArrayList<>();
        for (JsonNode c : root.path("credentials")) {
            creds.add(c.asText());
        }
        return new RuleTable(struct, columns, creds);
    }

    public List<StructRule> structRules() {
        return struct;
    }

    public Map<String, String> columnRules() {
        return columns;
    }

    public List<String> credentialPatterns() {
        return credentials;
    }

    public Transformer transformerFor(String id) {
        return switch (id) {
            case "phone" -> Transformers.phone();
            case "phonemask" -> Transformers.maskPhone();
            case "idcard" -> Transformers.idCard();
            case "plate" -> Transformers.plate();
            case "email" -> Transformers.email();
            case "taxno" -> Transformers.taxNo();
            case "factory" -> Transformers.factoryPool();
            case "name" -> Transformers.namePool();
            case "address" -> Transformers.address();
            default -> throw new MaskException("未知变换器: " + id);
        };
    }
}
