package app.miuix.tavern.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class SecureStore {
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String ALIAS = "miu_tavern_secret_key_v1";
    private static final String PREFS = "miu_tavern_secrets_v1";
    private static final String DIRECT_API_KEY = "direct_api_key";
    private static final String GPT_ACCESS_TOKEN = "gpt_access_token";
    private static final String GPT_REFRESH_TOKEN = "gpt_refresh_token";
    private static final String GPT_EXPIRES_AT = "gpt_expires_at";
    private static final String CLAUDE_ACCESS_TOKEN = "claude_access_token";
    private static final String REALTIME_TOKEN_AUTH = "realtime_token_auth";

    private final SharedPreferences prefs;

    public SecureStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void setDirectApiKey(String value) {
        put(DIRECT_API_KEY, value);
    }

    public String getDirectApiKey() {
        return get(DIRECT_API_KEY);
    }

    public void saveGptTokens(String accessToken, String refreshToken, long expiresAtMillis) {
        put(GPT_ACCESS_TOKEN, accessToken);
        if (refreshToken != null && !refreshToken.isEmpty()) {
            put(GPT_REFRESH_TOKEN, refreshToken);
        }
        put(GPT_EXPIRES_AT, String.valueOf(expiresAtMillis));
    }

    public String getGptAccessToken() {
        return get(GPT_ACCESS_TOKEN);
    }

    public String getGptRefreshToken() {
        return get(GPT_REFRESH_TOKEN);
    }

    public long getGptExpiresAtMillis() {
        try {
            return Long.parseLong(get(GPT_EXPIRES_AT));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    public boolean hasGptAccount() {
        return !getGptAccessToken().isEmpty() || !getGptRefreshToken().isEmpty();
    }

    public void clearGptAccount() {
        prefs.edit()
                .remove(GPT_ACCESS_TOKEN)
                .remove(GPT_REFRESH_TOKEN)
                .remove(GPT_EXPIRES_AT)
                .apply();
    }

    public void setClaudeAccessToken(String value) {
        put(CLAUDE_ACCESS_TOKEN, value == null ? "" : value.trim());
    }

    public String getClaudeAccessToken() {
        return get(CLAUDE_ACCESS_TOKEN);
    }

    public boolean hasClaudeAccount() {
        return !getClaudeAccessToken().isEmpty();
    }

    public void clearClaudeAccount() {
        prefs.edit().remove(CLAUDE_ACCESS_TOKEN).apply();
    }

    public void setRealtimeTokenAuth(String value) {
        put(REALTIME_TOKEN_AUTH, value);
    }

    public String getRealtimeTokenAuth() {
        return get(REALTIME_TOKEN_AUTH);
    }

    private void put(String key, String value) {
        if (value == null || value.isEmpty()) {
            prefs.edit().remove(key).apply();
            return;
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            String payload = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP)
                    + "."
                    + Base64.encodeToString(encrypted, Base64.NO_WRAP);
            prefs.edit().putString(key, payload).apply();
        } catch (Exception ignored) {
            // Do not fall back to plaintext storage.
        }
    }

    private String get(String key) {
        String payload = prefs.getString(key, "");
        if (payload == null || payload.isEmpty()) return "";
        try {
            String[] parts = payload.split("\\.", 2);
            if (parts.length != 2) return "";
            byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
            byte[] encrypted = Base64.decode(parts[1], Base64.NO_WRAP);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return "";
        }
    }

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
        keyStore.load(null);
        if (keyStore.containsAlias(ALIAS)) {
            KeyStore.SecretKeyEntry entry = (KeyStore.SecretKeyEntry) keyStore.getEntry(ALIAS, null);
            return entry.getSecretKey();
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
        generator.init(new KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        return generator.generateKey();
    }
}
