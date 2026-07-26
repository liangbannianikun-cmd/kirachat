package app.miuix.tavern.network;

import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import app.miuix.tavern.data.SecureStore;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ChatGPT/Codex device authorization compatible with Hermes Agent.
 *
 * The app never asks for an OpenAI password. Authorization happens on
 * auth.openai.com and the resulting tokens are persisted by SecureStore.
 */
public final class CodexAuthClient {
    public interface LoginCallback {
        void onCode(String userCode, String verificationUrl);

        void onSuccess(String accountSummary);

        void onError(String message);
    }

    public static final class LoginCall {
        private volatile boolean cancelled;
        private volatile HttpURLConnection connection;

        public void cancel() {
            cancelled = true;
            HttpURLConnection active = connection;
            if (active != null) active.disconnect();
        }

        public boolean isCancelled() {
            return cancelled;
        }
    }

    private static final String ISSUER = "https://auth.openai.com";
    private static final String DEVICE_CODE_URL =
            ISSUER + "/api/accounts/deviceauth/usercode";
    private static final String DEVICE_TOKEN_URL =
            ISSUER + "/api/accounts/deviceauth/token";
    private static final String VERIFICATION_URL = ISSUER + "/codex/device";
    private static final String TOKEN_URL = ISSUER + "/oauth/token";
    private static final String CLIENT_ID = "app_EMoamEEZ73f0CkXaXp7hrann";
    private static final String REDIRECT_URI = ISSUER + "/deviceauth/callback";
    private static final String BROWSER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15; Mobile) "
                    + "AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/131.0.0.0 Mobile Safari/537.36";
    private static final long LOGIN_TIMEOUT_MILLIS = 15L * 60L * 1000L;
    private static final Object TOKEN_LOCK = new Object();
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private CodexAuthClient() {
    }

    public static LoginCall startDeviceLogin(SecureStore secureStore, LoginCallback callback) {
        LoginCall call = new LoginCall();
        EXECUTOR.execute(() -> runDeviceLogin(call, secureStore, callback));
        return call;
    }

    public static String accountSummary(SecureStore secureStore) {
        if (!secureStore.hasGptAccount()) return "未登录";
        JSONObject claims = decodeJwtClaims(secureStore.getGptAccessToken());
        JSONObject auth = claims == null
                ? null
                : claims.optJSONObject("https://api.openai.com/auth");
        String plan = auth == null ? "" : auth.optString("chatgpt_plan_type", "");
        if (plan.isEmpty()) return "已登录 · ChatGPT";
        return "已登录 · ChatGPT " + planLabel(plan);
    }

    static String getAccessToken(SecureStore secureStore, boolean forceRefresh)
            throws IOException {
        synchronized (TOKEN_LOCK) {
            String accessToken = secureStore.getGptAccessToken();
            String refreshToken = secureStore.getGptRefreshToken();
            long expiresAt = secureStore.getGptExpiresAtMillis();
            if (expiresAt <= 0L) expiresAt = jwtExpiryMillis(accessToken);

            boolean usable = !accessToken.isEmpty()
                    && (expiresAt <= 0L || System.currentTimeMillis() + 120_000L < expiresAt);
            if (!forceRefresh && usable) return accessToken;
            if (refreshToken.isEmpty()) {
                if (!forceRefresh && !accessToken.isEmpty()) return accessToken;
                throw new IOException("GPT 登录已失效，请重新登录");
            }

            TokenSet refreshed = refresh(refreshToken);
            secureStore.saveGptTokens(
                    refreshed.accessToken,
                    refreshed.refreshToken.isEmpty() ? refreshToken : refreshed.refreshToken,
                    refreshed.expiresAtMillis);
            if (secureStore.getGptAccessToken().isEmpty()) {
                throw new IOException("无法使用 Android Keystore 保存 GPT 登录");
            }
            return refreshed.accessToken;
        }
    }

    static String accountIdFromToken(String accessToken) {
        JSONObject claims = decodeJwtClaims(accessToken);
        JSONObject auth = claims == null
                ? null
                : claims.optJSONObject("https://api.openai.com/auth");
        return auth == null ? "" : auth.optString("chatgpt_account_id", "");
    }

    private static void runDeviceLogin(
            LoginCall call, SecureStore secureStore, LoginCallback callback) {
        try {
            Response deviceResponse = null;
            for (int attempt = 1; attempt <= 4 && !call.isCancelled(); attempt++) {
                deviceResponse = postJson(call, DEVICE_CODE_URL,
                        new JSONObject().put("client_id", CLIENT_ID));
                if (deviceResponse.status != 429) break;
                if (attempt < 4) {
                    long delaySeconds = deviceResponse.retryAfterSeconds > 0
                            ? deviceResponse.retryAfterSeconds
                            : (1L << attempt);
                    sleepCancelable(call, Math.min(60L, Math.max(1L, delaySeconds)) * 1000L);
                }
            }
            if (call.isCancelled()) return;
            if (deviceResponse == null || deviceResponse.status != 200) {
                if (deviceResponse != null && deviceResponse.status == 429) {
                    throw new IOException("OpenAI 暂时限制登录请求，请稍后再试");
                }
                throw statusError("获取登录验证码失败", deviceResponse);
            }

            JSONObject device = new JSONObject(deviceResponse.body);
            String userCode = device.optString("user_code", "");
            String deviceAuthId = device.optString("device_auth_id", "");
            int interval = Math.max(3, parseInt(device.opt("interval"), 5));
            if (userCode.isEmpty() || deviceAuthId.isEmpty()) {
                throw new IOException("OpenAI 没有返回完整的设备验证码");
            }
            MAIN.post(() -> {
                if (!call.isCancelled()) callback.onCode(userCode, VERIFICATION_URL);
            });

            JSONObject authorization = null;
            long startedAt = System.currentTimeMillis();
            while (!call.isCancelled()
                    && System.currentTimeMillis() - startedAt < LOGIN_TIMEOUT_MILLIS) {
                sleepCancelable(call, interval * 1000L);
                if (call.isCancelled()) return;
                Response poll = postJson(call, DEVICE_TOKEN_URL, new JSONObject()
                        .put("device_auth_id", deviceAuthId)
                        .put("user_code", userCode));
                if (poll.status == 200) {
                    authorization = new JSONObject(poll.body);
                    break;
                }
                if (poll.status == 403 && poll.cloudflareChallenge) {
                    throw statusError("OpenAI 登录被安全验证拦截", poll);
                }
                if (poll.status == 403 || poll.status == 404 || poll.status == 429) {
                    continue;
                }
                throw statusError("等待 OpenAI 授权失败", poll);
            }
            if (call.isCancelled()) return;
            if (authorization == null) throw new IOException("登录等待已超时，请重试");

            String authorizationCode = authorization.optString("authorization_code", "");
            String verifier = authorization.optString("code_verifier", "");
            if (authorizationCode.isEmpty() || verifier.isEmpty()) {
                throw new IOException("OpenAI 授权响应不完整");
            }

            String form = form(
                    "grant_type", "authorization_code",
                    "code", authorizationCode,
                    "redirect_uri", REDIRECT_URI,
                    "client_id", CLIENT_ID,
                    "code_verifier", verifier);
            Response tokenResponse = postForm(call, TOKEN_URL, form);
            if (call.isCancelled()) return;
            if (tokenResponse.status != 200) {
                throw statusError("交换登录令牌失败", tokenResponse);
            }
            TokenSet tokens = parseTokens(tokenResponse.body, "");
            secureStore.saveGptTokens(
                    tokens.accessToken, tokens.refreshToken, tokens.expiresAtMillis);
            if (secureStore.getGptAccessToken().isEmpty()) {
                throw new IOException("无法使用 Android Keystore 保存 GPT 登录");
            }
            String summary = accountSummary(secureStore);
            MAIN.post(() -> {
                if (!call.isCancelled()) callback.onSuccess(summary);
            });
        } catch (Exception error) {
            if (call.isCancelled()) return;
            String message = error.getMessage();
            if (message == null || message.trim().isEmpty()) {
                message = error.getClass().getSimpleName();
            }
            String finalMessage = trimError(message);
            MAIN.post(() -> {
                if (!call.isCancelled()) callback.onError(finalMessage);
            });
        }
    }

    private static TokenSet refresh(String refreshToken) throws IOException {
        Response response = postForm(null, TOKEN_URL, form(
                "grant_type", "refresh_token",
                "refresh_token", refreshToken,
                "client_id", CLIENT_ID));
        if (response.status != 200) {
            if (response.status == 400 || response.status == 401 || response.status == 403) {
                throw new IOException("GPT 登录已过期，请重新登录");
            }
            throw statusError("刷新 GPT 登录失败", response);
        }
        return parseTokens(response.body, refreshToken);
    }

    private static TokenSet parseTokens(String body, String previousRefreshToken)
            throws IOException {
        try {
            JSONObject json = new JSONObject(body);
            String accessToken = json.optString("access_token", "");
            String refreshToken = json.optString("refresh_token", previousRefreshToken);
            if (accessToken.isEmpty()) throw new IOException("OpenAI 没有返回访问令牌");
            long expiresInSeconds = Math.max(0L, json.optLong("expires_in", 0L));
            long expiresAt = expiresInSeconds > 0L
                    ? System.currentTimeMillis() + expiresInSeconds * 1000L
                    : jwtExpiryMillis(accessToken);
            return new TokenSet(accessToken, refreshToken, expiresAt);
        } catch (JSONException error) {
            throw new IOException("无法读取 OpenAI 登录响应");
        }
    }

    private static Response postJson(LoginCall call, String url, JSONObject body)
            throws IOException {
        return post(call, url, "application/json; charset=utf-8",
                body.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static Response postForm(LoginCall call, String url, String form)
            throws IOException {
        return post(call, url, "application/x-www-form-urlencoded",
                form.getBytes(StandardCharsets.UTF_8));
    }

    private static Response post(
            LoginCall call, String url, String contentType, byte[] bytes) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        if (call != null) call.connection = connection;
        try {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(20_000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", contentType);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", BROWSER_USER_AGENT);
            connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
            connection.setFixedLengthStreamingMode(bytes.length);
            OutputStream output = connection.getOutputStream();
            output.write(bytes);
            output.flush();
            output.close();
            int status = connection.getResponseCode();
            InputStream input = status >= 200 && status < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            String body = read(input);
            long retryAfter = parseLong(connection.getHeaderField("Retry-After"), 0L);
            String lowerBody = body == null
                    ? "" : body.toLowerCase(java.util.Locale.ROOT);
            boolean cloudflareChallenge = "challenge".equalsIgnoreCase(
                    connection.getHeaderField("cf-mitigated"))
                    || lowerBody.contains("cloudflare")
                    || lowerBody.contains("cf-chl")
                    || lowerBody.contains("enable javascript");
            return new Response(
                    status, body, retryAfter, cloudflareChallenge);
        } finally {
            if (call != null) call.connection = null;
            connection.disconnect();
        }
    }

    private static IOException statusError(String prefix, Response response) {
        if (response == null) return new IOException(prefix);
        if (response.status == 403 && response.cloudflareChallenge) {
            return new IOException(prefix
                    + "（HTTP 403）：当前网络触发了 Cloudflare 验证，请切换网络或代理后重试");
        }
        String detail = response.body;
        try {
            JSONObject root = new JSONObject(detail);
            Object errorValue = root.opt("error");
            if (errorValue instanceof JSONObject) {
                JSONObject error = (JSONObject) errorValue;
                detail = error.optString("message", error.optString("code", ""));
            } else if (errorValue instanceof String) {
                detail = (String) errorValue;
            } else {
                detail = root.optString("error_description", "");
            }
        } catch (JSONException ignored) {
        }
        if (detail == null || detail.trim().isEmpty()) {
            return new IOException(prefix + "（HTTP " + response.status + "）");
        }
        return new IOException(prefix + "（HTTP " + response.status + "）："
                + trimError(detail));
    }

    private static String form(String... pairs) throws IOException {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            if (result.length() > 0) result.append('&');
            result.append(URLEncoder.encode(pairs[i], "UTF-8"));
            result.append('=');
            result.append(URLEncoder.encode(pairs[i + 1], "UTF-8"));
        }
        return result.toString();
    }

    private static JSONObject decodeJwtClaims(String token) {
        if (token == null || token.isEmpty()) return null;
        String[] parts = token.split("\\.");
        if (parts.length < 2) return null;
        try {
            byte[] decoded = Base64.decode(
                    parts[1], Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
            return new JSONObject(new String(decoded, StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static long jwtExpiryMillis(String token) {
        JSONObject claims = decodeJwtClaims(token);
        long seconds = claims == null ? 0L : claims.optLong("exp", 0L);
        return seconds <= 0L ? 0L : seconds * 1000L;
    }

    private static String planLabel(String plan) {
        if ("plus".equalsIgnoreCase(plan)) return "Plus";
        if ("pro".equalsIgnoreCase(plan)) return "Pro";
        if ("team".equalsIgnoreCase(plan)) return "Team";
        if ("enterprise".equalsIgnoreCase(plan)) return "Enterprise";
        if ("free".equalsIgnoreCase(plan)) return "Free";
        return plan;
    }

    private static void sleepCancelable(LoginCall call, long millis) throws IOException {
        long remaining = millis;
        while (remaining > 0L && !call.isCancelled()) {
            long slice = Math.min(250L, remaining);
            try {
                Thread.sleep(slice);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IOException("登录已中断");
            }
            remaining -= slice;
        }
    }

    private static String read(InputStream input) throws IOException {
        if (input == null) return "";
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        return new String(output.toByteArray(), StandardCharsets.UTF_8).trim();
    }

    private static int parseInt(Object value, int fallback) {
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(value == null ? "" : value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String trimError(String value) {
        String normalized = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        return normalized.length() > 240 ? normalized.substring(0, 240) + "…" : normalized;
    }

    private static final class Response {
        final int status;
        final String body;
        final long retryAfterSeconds;
        final boolean cloudflareChallenge;

        Response(int status, String body, long retryAfterSeconds,
                 boolean cloudflareChallenge) {
            this.status = status;
            this.body = body;
            this.retryAfterSeconds = retryAfterSeconds;
            this.cloudflareChallenge = cloudflareChallenge;
        }
    }

    private static final class TokenSet {
        final String accessToken;
        final String refreshToken;
        final long expiresAtMillis;

        TokenSet(String accessToken, String refreshToken, long expiresAtMillis) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.expiresAtMillis = expiresAtMillis;
        }
    }
}
