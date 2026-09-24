package com.mcc.mask.codebook;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

import com.mcc.mask.MaskException;

/** PBKDF2 派生 + AES-256-GCM 加解密（blob = IV(12) || ciphertext || tag）。 */
public final class Crypto {

    public static final int PBKDF2_ITERS = 600_000;
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;

    public static SecretKey deriveKey(char[] pass, byte[] salt) {
        return deriveKey(pass, salt, PBKDF2_ITERS);
    }

    public static SecretKey deriveKey(char[] pass, byte[] salt, int iters) {
        try {
            PBEKeySpec spec = new PBEKeySpec(pass, salt, iters, 256);
            SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return new SecretKeySpec(f.generateSecret(spec).getEncoded(), "AES");
        } catch (GeneralSecurityException e) {
            throw new MaskException("密钥派生失败: " + e.getMessage(), e);
        }
    }

    public static byte[] encrypt(SecretKey key, byte[] plain) {
        try {
            byte[] iv = new byte[IV_LEN];
            new SecureRandom().nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = c.doFinal(plain);
            byte[] out = new byte[IV_LEN + ct.length];
            System.arraycopy(iv, 0, out, 0, IV_LEN);
            System.arraycopy(ct, 0, out, IV_LEN, ct.length);
            return out;
        } catch (GeneralSecurityException e) {
            throw new MaskException("加密失败: " + e.getMessage(), e);
        }
    }

    public static byte[] decrypt(SecretKey key, byte[] blob) throws GeneralSecurityException {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, blob, 0, IV_LEN));
        return c.doFinal(blob, IV_LEN, blob.length - IV_LEN);
    }

    private Crypto() {
    }
}
