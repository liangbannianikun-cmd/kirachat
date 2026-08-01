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
    private static final String COPILOT_ACCESS_TOKEN = "copilot_access_token";
    private static final String COPILOT_LOGIN = "copilot_login";
    private static final String REALTIME_CREDENTIAL_PREFIX = "realtime_credential_";
    private static final String POINTS_ACCESS_TOKEN = "points_access_token";

    private final SharedPreferences prefs;

    public SecureStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        // Remove the retired short-lived-token-service credential.
        prefs.edit()
                .remove("realtime_token_auth")
                .remove("claude_access_token")
                .apply();
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

    public void saveCopilotAccount(String accessToken, String login) {
        put(COPILOT_ACCESS_TOKEN,
                accessToken == null ? "" : accessToken.trim());
        put(COPILOT_LOGIN, login == null ? "" : login.trim());
    }

    public String getCopilotAccessToken() {
        return get(COPILOT_ACCESS_TOKEN);
    }

    public String getCopilotLogin() {
        return get(COPILOT_LOGIN);
    }

    public boolean hasCopilotAccount() {
        return !getCopilotAccessToken().isEmpty();
    }

    public void clearCopilotAccount() {
        prefs.edit()
                .remove(COPILOT_ACCESS_TOKEN)
                .remove(COPILOT_LOGIN)
                .apply();
    }

    public void setRealtimeCredential(String provider, String value) {
        put(realtimeKey(provider), value == null ? "" : value.trim());
    }

    public String getRealtimeCredential(String provider) {
        return get(realtimeKey(provider));
    }

    public void setPointsAccessToken(String value) {
        put(POINTS_ACCESS_TOKEN, value);
    }

    public String getPointsAccessToken() {
        return get(POINTS_ACCESS_TOKEN);
    }

    private static String realtimeKey(String provider) {
        String safe = provider == null ? "default"
                : provider.replaceAll("[^a-zA-Z0-9_-]", "_");
        return REALTIME_CREDENTIAL_PREFIX + safe;
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
