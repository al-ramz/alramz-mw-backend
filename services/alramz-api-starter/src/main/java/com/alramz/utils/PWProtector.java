package com.alramz.utils;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.security.GeneralSecurityException;

public class PWProtector {

    private static final Logger logger = LoggerFactory.getLogger(PWProtector.class);
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final SecretKey secretKey;

    public PWProtector(String masterKey) {
        if (masterKey == null || masterKey.isBlank()) {
            throw new IllegalArgumentException("cipher.password must not be blank");
        }
        byte[] keyBytes = masterKey.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
            throw new IllegalArgumentException("cipher.password must be 16, 24 or 32 bytes for AES");
        }
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    public String[] encrypt(String plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            SECURE_RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] cipherText = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            String cipherTextBase64 = Base64.getEncoder().encodeToString(cipherText);
            String ivBase64 = Base64.getEncoder().encodeToString(iv);

            return new String[]{cipherTextBase64, ivBase64};
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to encrypt", e);
        }
    }

    public String decrypt(String cipherText, String ivString) {
        try {
            byte[] iv = Base64.getDecoder().decode(ivString);
            byte[] cipherBytes = Base64.getDecoder().decode(cipherText);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] plainBytes = cipher.doFinal(cipherBytes);

            return new String(plainBytes, java.nio.charset.StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to decrypt", e);
        }
    }

    public String decrypt(String secretStr) {
        try {
            int colon = secretStr.indexOf(':');
            if (colon <= 0) {
                throw new IllegalArgumentException("Legacy secret format must be 'cipherBase64:ivBase64'");
            }
            String cipherText = secretStr.substring(0, colon);
            String iv = secretStr.substring(colon + 1);
            return decrypt(cipherText, iv);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt legacy secret", e);
        }
    }
}
