package app.miuix.tavern.network;

import android.util.Base64;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

final class SyncCrypto {
    private static final String FORMAT = "kirachat-sync-encrypted";
    private static final String KDF = "sha256-chain-10000";
    private static final int ITERATIONS = 10000;
    private static final byte[] PREFIX = "KiraChat Sync v1\0".getBytes(StandardCharsets.UTF_8);
    private static final byte[] AAD = "kirachat-sync-v1".getBytes(StandardCharsets.UTF_8);
    private static final SecureRandom RANDOM = new SecureRandom();

    private SyncCrypto() {
    }

    static JSONObject encrypt(byte[] plaintext, String password) throws Exception {
        byte[] salt = new byte[16];
        byte[] nonce = new byte[12];
        RANDOM.nextBytes(salt);
        RANDOM.nextBytes(nonce);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,
                new SecretKeySpec(derive(password, salt), "AES"),
                new GCMParameterSpec(128, nonce));
        cipher.updateAAD(AAD);
        byte[] ciphertext = cipher.doFinal(plaintext);
        return new JSONObject()
                .put("format", FORMAT)
                .put("schemaVersion", 1)
                .put("kdf", KDF)
                .put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
                .put("nonce", Base64.encodeToString(nonce, Base64.NO_WRAP))
                .put("ciphertext", Base64.encodeToString(ciphertext, Base64.NO_WRAP));
    }

    static byte[] decrypt(JSONObject blob, String password) throws Exception {
        if (blob == null || !FORMAT.equals(blob.optString("format"))
                || blob.optInt("schemaVersion") != 1
                || !KDF.equals(blob.optString("kdf"))) {
            throw new IllegalArgumentException("服务器加密格式不受支持");
        }
        byte[] salt = Base64.decode(blob.getString("salt"), Base64.DEFAULT);
        byte[] nonce = Base64.decode(blob.getString("nonce"), Base64.DEFAULT);
        byte[] ciphertext = Base64.decode(blob.getString("ciphertext"), Base64.DEFAULT);
        if (salt.length != 16 || nonce.length != 12 || ciphertext.length < 17) {
            throw new IllegalArgumentException("服务器加密数据损坏");
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,
                new SecretKeySpec(derive(password, salt), "AES"),
                new GCMParameterSpec(128, nonce));
        cipher.updateAAD(AAD);
        try {
            return cipher.doFinal(ciphertext);
        } catch (Exception error) {
            throw new IllegalArgumentException("无法解密同步内容，请检查加密密码", error);
        }
    }

    static String digest(byte[] data) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder value = new StringBuilder(hash.length * 2);
        for (byte item : hash) value.append(String.format(java.util.Locale.US, "%02x", item));
        return value.toString();
    }

    private static byte[] derive(String password, byte[] salt) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        ByteArrayOutputStream initial = new ByteArrayOutputStream();
        initial.write(PREFIX);
        initial.write(password.getBytes(StandardCharsets.UTF_8));
        initial.write(salt);
        byte[] key = digest.digest(initial.toByteArray());
        byte[] round = new byte[key.length + salt.length];
        for (int i = 1; i < ITERATIONS; i++) {
            System.arraycopy(key, 0, round, 0, key.length);
            System.arraycopy(salt, 0, round, key.length, salt.length);
            key = digest.digest(round);
        }
        return key;
    }
}
