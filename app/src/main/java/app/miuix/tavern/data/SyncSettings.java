package app.miuix.tavern.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.UUID;

public final class SyncSettings {
    private static final String PREFS = "kirachat_sync_settings_v1";
    private static final String URL = "server_url";
    private static final String AUTO = "auto_sync";
    private static final String DEVICE = "device_id";
    private static final String REVISION = "revision";
    private static final String DIGEST = "local_digest";
    private static final String LAST_SYNC = "last_sync_at";
    private static final String STATUS = "last_status";

    private final SharedPreferences prefs;
    private final SecureStore secureStore;

    public SyncSettings(Context context) {
        Context app = context.getApplicationContext();
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        secureStore = new SecureStore(app);
    }

    public String serverUrl() {
        return prefs.getString(URL, "");
    }

    public boolean autoSync() {
        return prefs.getBoolean(AUTO, false);
    }

    public String token() {
        return secureStore.getSyncToken();
    }

    public String encryptionPassword() {
        return secureStore.getSyncEncryptionPassword();
    }

    public boolean configured() {
        return !serverUrl().trim().isEmpty() && token().length() >= 24
                && encryptionPassword().length() >= 8;
    }

    public void save(String serverUrl, String token, String password, boolean autoSync) {
        String cleanUrl = serverUrl == null ? "" : serverUrl.trim();
        String cleanToken = token == null ? "" : token.trim();
        String oldUrl = serverUrl();
        String oldToken = token();
        String oldPassword = encryptionPassword();
        secureStore.setSyncToken(cleanToken);
        secureStore.setSyncEncryptionPassword(password == null ? "" : password);
        SharedPreferences.Editor editor = prefs.edit()
                .putString(URL, cleanUrl)
                .putBoolean(AUTO, autoSync);
        if (!oldUrl.equals(cleanUrl) || !oldToken.equals(cleanToken)
                || !oldPassword.equals(password == null ? "" : password)) {
            editor.remove(REVISION).remove(DIGEST).remove(LAST_SYNC);
        }
        editor.apply();
    }

    public String deviceId() {
        String id = prefs.getString(DEVICE, "");
        if (id != null && !id.isEmpty()) return id;
        id = UUID.randomUUID().toString();
        prefs.edit().putString(DEVICE, id).apply();
        return id;
    }

    public long revision() {
        return prefs.getLong(REVISION, 0L);
    }

    public String digest() {
        return prefs.getString(DIGEST, "");
    }

    public long lastSyncAt() {
        return prefs.getLong(LAST_SYNC, 0L);
    }

    public String lastStatus() {
        return prefs.getString(STATUS, "尚未同步");
    }

    public void setLastStatus(String value) {
        prefs.edit().putString(STATUS, value == null ? "" : value).apply();
    }

    public void record(long revision, String digest) {
        prefs.edit()
                .putLong(REVISION, revision)
                .putString(DIGEST, digest == null ? "" : digest)
                .putLong(LAST_SYNC, System.currentTimeMillis())
                .apply();
    }
}
