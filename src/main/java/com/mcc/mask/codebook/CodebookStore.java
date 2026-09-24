package com.mcc.mask.codebook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mcc.mask.MaskException;

import javax.crypto.AEADBadTagException;
import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/** .mccmask 码本文件读写：JSON 头 + AES-256-GCM 密文体。 */
public final class CodebookStore {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Base64.Encoder B64 = Base64.getEncoder();
    private static final Base64.Decoder B64D = Base64.getDecoder();

    public static void save(Path file, char[] pass, CodebookJson json) throws IOException {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        SecretKey key = Crypto.deriveKey(pass, salt);
        byte[] blob = Crypto.encrypt(key, MAPPER.writeValueAsBytes(json));
        ObjectNode root = MAPPER.createObjectNode();
        root.put("version", 1);
        ObjectNode kdf = root.putObject("kdf");
        kdf.put("algo", "PBKDF2WithHmacSHA256");
        kdf.put("salt", B64.encodeToString(salt));
        kdf.put("iters", Crypto.PBKDF2_ITERS);
        root.put("cipher", "AES-256-GCM");
        root.put("data", B64.encodeToString(blob));
        Files.write(file, MAPPER.writeValueAsBytes(root));
    }

    public static CodebookJson load(Path file, char[] pass) throws IOException {
        JsonNode root = MAPPER.readTree(Files.readAllBytes(file));
        byte[] salt = B64D.decode(root.path("kdf").path("salt").asText());
        int iters = root.path("kdf").path("iters").asInt(Crypto.PBKDF2_ITERS);
        byte[] blob = B64D.decode(root.path("data").asText());
        SecretKey key = Crypto.deriveKey(pass, salt, iters);
        try {
            byte[] plain = Crypto.decrypt(key, blob);
            return MAPPER.readValue(plain, CodebookJson.class);
        } catch (AEADBadTagException e) {
            throw new MaskException("口令错误：码本认证失败（GCM 标签不匹配）", e);
        } catch (GeneralSecurityException e) {
            throw new MaskException("码本解密失败: " + e.getMessage(), e);
        }
    }

    private CodebookStore() {
    }
}
