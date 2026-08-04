package app.miuix.tavern.network;

import android.content.Context;

import app.miuix.tavern.data.LocalStore;
import app.miuix.tavern.data.SyncPayloadCodec;
import app.miuix.tavern.data.SyncSettings;

import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public final class RemoteSyncClient {
    public enum Mode { AUTOMATIC, FORCE_UPLOAD, FORCE_DOWNLOAD }

    public static final class Result {
        public final String message;
        public final boolean appliedRemote;

        Result(String message, boolean appliedRemote) {
            this.message = message;
            this.appliedRemote = appliedRemote;
        }
    }

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final int MAX_RESPONSE_BYTES = 256 * 1024 * 1024;

    private final LocalStore store;
    private final SyncSettings settings;
    private final OkHttpClient http;

    public RemoteSyncClient(Context context) {
        Context app = context.getApplicationContext();
        store = new LocalStore(app);
        settings = new SyncSettings(app);
        http = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .build();
    }

    public String testConnection() throws Exception {
        requireConfigured();
        Request health = new Request.Builder().url(endpoint("/v1/health")).get().build();
        try (Response response = http.newCall(health).execute()) {
            JSONObject value = readJson(response, false);
            if (!"kirachat-sync".equals(value.optString("service"))) {
                throw new IOException("该地址不是澄语同步服务器");
            }
        }
        Meta meta = fetchMeta();
        return meta == null ? "连接成功 · 服务器还没有同步内容"
                : "连接成功 · 服务器修订版 " + meta.revision;
    }

    public Result synchronize(Mode mode) throws Exception {
        requireConfigured();
        if (mode == Mode.FORCE_UPLOAD) return upload(fetchMeta());
        if (mode == Mode.FORCE_DOWNLOAD) return download();

        Meta remote = fetchMeta();
        byte[] localPayload = SyncPayloadCodec.encode(store);
        String localDigest = SyncCrypto.digest(localPayload);
        if (remote == null) return upload(null, localPayload, localDigest);

        long savedRevision = settings.revision();
        String savedDigest = settings.digest();
        if (savedRevision == 0) {
            throw new IOException("首次同步请明确选择“上传本机”或“下载服务器”");
        }
        if (remote.revision == savedRevision) {
            if (localDigest.equals(savedDigest)) {
                settings.record(remote.revision, localDigest);
                return new Result("已是最新版本", false);
            }
            return upload(remote, localPayload, localDigest);
        }
        if (remote.revision > savedRevision && localDigest.equals(savedDigest)) {
            return download();
        }
        throw new IOException("检测到同步冲突，请选择上传本机或下载服务器");
    }

    private Result upload(Meta remote) throws Exception {
        byte[] payload = SyncPayloadCodec.encode(store);
        return upload(remote, payload, SyncCrypto.digest(payload));
    }

    private Result upload(Meta remote, byte[] payload, String digest) throws Exception {
        long baseRevision = remote == null ? 0 : remote.revision;
        JSONObject requestJson = new JSONObject()
                .put("baseRevision", baseRevision)
                .put("deviceId", settings.deviceId())
                .put("platform", "android")
                .put("blob", SyncCrypto.encrypt(payload, settings.encryptionPassword()));
        Request request = authorized(new Request.Builder()
                .url(endpoint("/v1/sync/snapshot")))
                .put(RequestBody.create(JSON, requestJson.toString()))
                .build();
        try (Response response = http.newCall(request).execute()) {
            JSONObject value = readJson(response, false);
            long revision = value.optLong("revision", 0);
            if (revision <= 0) throw new IOException("同步服务器没有返回修订号");
            settings.record(revision, digest);
            return new Result("本机内容已上传 · 修订版 " + revision, false);
        }
    }

    private Result download() throws Exception {
        Request request = authorized(new Request.Builder()
                .url(endpoint("/v1/sync/snapshot"))).get().build();
        try (Response response = http.newCall(request).execute()) {
            JSONObject value = readJson(response, false);
            long revision = value.optLong("revision", 0);
            byte[] payload = SyncCrypto.decrypt(
                    value.optJSONObject("blob"), settings.encryptionPassword());
            SyncPayloadCodec.restore(store, payload);
            byte[] restoredPayload = SyncPayloadCodec.encode(store);
            settings.record(revision, SyncCrypto.digest(restoredPayload));
            return new Result("已下载服务器内容 · 修订版 " + revision, true);
        }
    }

    private Meta fetchMeta() throws Exception {
        Request request = authorized(new Request.Builder()
                .url(endpoint("/v1/sync/meta"))).get().build();
        try (Response response = http.newCall(request).execute()) {
            if (response.code() == 404) return null;
            JSONObject value = readJson(response, false);
            return new Meta(value.optLong("revision", 0));
        }
    }

    private Request.Builder authorized(Request.Builder request) {
        return request.header("Authorization", "Bearer " + settings.token())
                .header("Accept", "application/json");
    }

    private String endpoint(String path) throws IOException {
        String base = settings.serverUrl().trim();
        if (!base.startsWith("https://") && !base.startsWith("http://")) {
            throw new IOException("服务器地址必须以 https:// 或 http:// 开头");
        }
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + path;
    }

    private JSONObject readJson(Response response, boolean allowNotFound) throws Exception {
        if (allowNotFound && response.code() == 404) return null;
        long length = response.body() == null ? 0 : response.body().contentLength();
        if (length > MAX_RESPONSE_BYTES) throw new IOException("服务器响应过大");
        String text = response.body() == null ? "" : response.body().string();
        if (!response.isSuccessful()) {
            if (response.code() == 401) throw new IOException("同步令牌无效");
            if (response.code() == 409) throw new IOException("服务器内容已更新，请重新同步");
            String detail = text.length() > 300 ? text.substring(0, 300) : text;
            throw new IOException("同步服务器 HTTP " + response.code()
                    + (detail.isEmpty() ? "" : " · " + detail));
        }
        if (text.getBytes(StandardCharsets.UTF_8).length > MAX_RESPONSE_BYTES) {
            throw new IOException("服务器响应过大");
        }
        try {
            return new JSONObject(text);
        } catch (Exception error) {
            throw new IOException("同步服务器返回了无效 JSON");
        }
    }

    private void requireConfigured() throws IOException {
        if (!settings.configured()) {
            throw new IOException("请先填写服务器地址、同步令牌和加密密码");
        }
    }

    private static final class Meta {
        final long revision;

        Meta(long revision) {
            this.revision = revision;
        }
    }
}
