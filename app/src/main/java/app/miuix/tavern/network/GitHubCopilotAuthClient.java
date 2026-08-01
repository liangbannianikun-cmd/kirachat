package app.miuix.tavern.network;

import android.os.Handler;
import android.os.Looper;

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

/** GitHub OAuth device flow for GitHub Copilot SDK user tokens. */
public final class GitHubCopilotAuthClient {
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

    private static final String DEVICE_CODE_URL =
            "https://github.com/login/device/code";
    private static final String TOKEN_URL =
            "https://github.com/login/oauth/access_token";
    private static final String USER_URL = "https://api.github.com/user";
    private static final String DEFAULT_VERIFICATION_URL =
            "https://github.com/login/device";
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private GitHubCopilotAuthClient() {
    }

    public static LoginCall startDeviceLogin(
            String clientId,
            SecureStore secureStore,
            LoginCallback callback) {
        LoginCall call = new LoginCall();
        String safeClientId = clientId == null ? "" : clientId.trim();
        EXECUTOR.execute(() -> runDeviceLogin(
                call, safeClientId, secureStore, callback));
        return call;
    }

    public static String accountSummary(SecureStore secureStore) {
        if (!secureStore.hasCopilotAccount()) return "未登录";
        String login = secureStore.getCopilotLogin();
        return login.isEmpty()
                ? "已登录 · GitHub Copilot"
                : "已登录 · @" + login;
    }

    private static void runDeviceLogin(
            LoginCall call,
            String clientId,
            SecureStore secureStore,
            LoginCallback callback) {
        try {
            if (clientId.isEmpty()) {
                throw new IOException("请先填写 GitHub OAuth Client ID");
            }
            Response deviceResponse = postForm(call, DEVICE_CODE_URL, form(
                    "client_id", clientId,
                    "scope", "read:user"));
            if (call.isCancelled()) return;
            if (deviceResponse.status < 200 || deviceResponse.status >= 300) {
                throw statusError("获取 GitHub 验证码失败", deviceResponse);
            }

            JSONObject device = new JSONObject(deviceResponse.body);
            String deviceCode = device.optString("device_code", "");
            String userCode = device.optString("user_code", "");
            String verificationUrl = device.optString(
                    "verification_uri", DEFAULT_VERIFICATION_URL);
            int expiresIn = Math.max(60, device.optInt("expires_in", 900));
            int interval = Math.max(5, device.optInt("interval", 5));
            if (deviceCode.isEmpty() || userCode.isEmpty()) {
                throw new IOException("GitHub 没有返回完整的设备验证码");
            }
            String finalVerificationUrl = verificationUrl;
            MAIN.post(() -> {
                if (!call.isCancelled()) {
                    callback.onCode(userCode, finalVerificationUrl);
                }
            });

            long deadline = System.currentTimeMillis() + expiresIn * 1000L;
            String accessToken = "";
            while (!call.isCancelled() && System.currentTimeMillis() < deadline) {
                sleepCancelable(call, interval * 1000L);
                if (call.isCancelled()) return;
                Response poll = postForm(call, TOKEN_URL, form(
                        "client_id", clientId,
                        "device_code", deviceCode,
                        "grant_type", "urn:ietf:params:oauth:grant-type:device_code"));
                if (poll.status < 200 || poll.status >= 300) {
                    throw statusError("等待 GitHub 授权失败", poll);
                }
                JSONObject token = new JSONObject(poll.body);
                accessToken = token.optString("access_token", "").trim();
                if (!accessToken.isEmpty()) break;
                String error = token.optString("error", "");
                if ("authorization_pending".equals(error)) continue;
                if ("slow_down".equals(error)) {
                    interval += 5;
                    continue;
                }
                if ("access_denied".equals(error)) {
                    throw new IOException("你已拒绝 GitHub 授权");
                }
                if ("expired_token".equals(error)) {
                    throw new IOException("GitHub 验证码已过期，请重试");
                }
                if (!error.isEmpty()) {
                    throw new IOException(token.optString(
                            "error_description", error));
                }
            }
            if (call.isCancelled()) return;
            if (accessToken.isEmpty()) {
                throw new IOException("GitHub 登录等待已超时，请重试");
            }

            String login = fetchLogin(call, accessToken);
            secureStore.saveCopilotAccount(accessToken, login);
            if (!secureStore.hasCopilotAccount()) {
                throw new IOException("无法使用 Android Keystore 保存 GitHub 登录");
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

    private static String fetchLogin(LoginCall call, String accessToken)
            throws IOException, JSONException {
        HttpURLConnection connection = (HttpURLConnection) new URL(USER_URL)
                .openConnection();
        call.connection = connection;
        try {
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(20_000);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("Authorization", "Bearer " + accessToken);
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
            connection.setRequestProperty("User-Agent", "Chengyu-Android/0.9");
            int status = connection.getResponseCode();
            String body = read(status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream());
            if (status < 200 || status >= 300) {
                throw statusError("读取 GitHub 账户失败", new Response(status, body));
            }
            return new JSONObject(body).optString("login", "").trim();
        } finally {
            call.connection = null;
            connection.disconnect();
        }
    }

    private static Response postForm(
            LoginCall call, String url, String form) throws IOException {
        byte[] bytes = form.getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection = (HttpURLConnection) new URL(url)
                .openConnection();
        call.connection = connection;
        try {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(20_000);
            connection.setDoOutput(true);
            connection.setRequestProperty(
                    "Content-Type", "application/x-www-form-urlencoded");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "Chengyu-Android/0.9");
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(bytes);
                output.flush();
            }
            int status = connection.getResponseCode();
            String body = read(status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream());
            return new Response(status, body);
        } finally {
            call.connection = null;
            connection.disconnect();
        }
    }

    private static IOException statusError(String prefix, Response response) {
        String detail = response == null ? "" : response.body;
        try {
            JSONObject root = new JSONObject(detail);
            detail = root.optString("error_description",
                    root.optString("message", root.optString("error", "")));
        } catch (Exception ignored) {
        }
        int status = response == null ? 0 : response.status;
        return new IOException(prefix + (status <= 0 ? "" : "（HTTP " + status + "）")
                + (detail == null || detail.trim().isEmpty()
                ? "" : "：" + trimError(detail)));
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

    private static void sleepCancelable(LoginCall call, long millis)
            throws IOException {
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
        while ((count = input.read(buffer)) != -1) {
            output.write(buffer, 0, count);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8).trim();
    }

    private static String trimError(String value) {
        String normalized = value == null
                ? "" : value.trim().replaceAll("\\s+", " ");
        return normalized.length() > 240
                ? normalized.substring(0, 240) + "…" : normalized;
    }

    private static final class Response {
        final int status;
        final String body;

        Response(int status, String body) {
            this.status = status;
            this.body = body == null ? "" : body;
        }
    }
}
