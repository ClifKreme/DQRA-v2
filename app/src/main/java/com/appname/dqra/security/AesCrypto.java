package com.appname.dqra.security;

import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class AesCrypto {
    private static final String PREFIX = "enc:v1:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_SIZE_BYTES = 12;
    private static final int TAG_SIZE_BITS = 128;
    private static final String PASSPHRASE = "DQRA_AES_FIELD_KEY_V1_2026";

    private AesCrypto() {
    }

    public static String encrypt(String value) {
        if (value == null || value.isEmpty() || isEncrypted(value)) {
            return value;
        }

        try {
            byte[] iv = new byte[IV_SIZE_BYTES];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey(), new GCMParameterSpec(TAG_SIZE_BITS, iv));
            byte[] cipherText = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));

            byte[] payload = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(cipherText, 0, payload, iv.length, cipherText.length);

            return PREFIX + Base64.encodeToString(payload, Base64.NO_WRAP);
        } catch (Exception exception) {
            return value;
        }
    }

    public static String decrypt(String value) {
        if (value == null || value.isEmpty() || !isEncrypted(value)) {
            return value;
        }

        try {
            byte[] payload = Base64.decode(value.substring(PREFIX.length()), Base64.NO_WRAP);
            if (payload.length <= IV_SIZE_BYTES) {
                return "";
            }

            byte[] iv = new byte[IV_SIZE_BYTES];
            byte[] cipherText = new byte[payload.length - IV_SIZE_BYTES];
            System.arraycopy(payload, 0, iv, 0, IV_SIZE_BYTES);
            System.arraycopy(payload, IV_SIZE_BYTES, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), new GCMParameterSpec(TAG_SIZE_BITS, iv));
            byte[] plainText = cipher.doFinal(cipherText);
            return new String(plainText, StandardCharsets.UTF_8);
        } catch (Exception exception) {
            return value;
        }
    }

    public static String sha256Hex(String value) {
        if (value == null) {
            return "";
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception exception) {
            return "";
        }
    }

    private static boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    private static SecretKeySpec secretKey() throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] key = digest.digest(PASSPHRASE.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(key, "AES");
    }
}
