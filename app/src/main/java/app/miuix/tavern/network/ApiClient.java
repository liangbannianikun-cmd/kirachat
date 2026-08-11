package app.miuix.tavern.network;

import android.content.Context;

import app.miuix.tavern.data.SecureStore;
import app.miuix.tavern.model.AppConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class ApiClient {
    public interface StreamCallback {
        void onDelta(String delta);

        void onComplete();

        void onError(String message);
    }

    public interface ResultCallback {
        void onSuccess(String message);

        void onError(String message);
    }

    public interface ModelsCallback {
        void onSuccess(List<String> models);

        void onError(String message);
    }

    public static final class Call {
        private volatile boolean cancelled;
        private volatile HttpURLConnection connection;
        private volatile okhttp3.Call okHttpCall;
        private volatile java.lang.Process process;

        public void cancel() {
            cancelled = true;
            HttpURLConnection active = connection;
            if (active != null) active.disconnect();
            okhttp3.Call activeOkHttp = okHttpCall;
            if (activeOkHttp != null) activeOkHttp.cancel();
            java.lang.Process activeProcess = process;
            if (activeProcess != null) activeProcess.destroy();
        }

        public boolean isCancelled() {
            return cancelled;
        }

        void attachProcess(java.lang.Process process) {
            this.process = process;
            if (cancelled && process != null) process.destroy();
        }

        void attachOkHttpCall(okhttp3.Call call) {
            this.okHttpCall = call;
            if (cancelled && call != null) call.cancel();
        }
    }

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private static final MediaType JSON =
            MediaType.parse("application/json; charset=utf-8");
    private static final OkHttpClient CODEX_HTTP =
            new OkHttpClient.Builder()
                    .dns(ReliableDns.INSTANCE)
                    .connectTimeout(25, TimeUnit.SECONDS)
                    .readTimeout(120, TimeUnit.SECONDS)
                    .writeTimeout(45, TimeUnit.SECONDS)
                    .build();
    private static final String CODEX_USER_AGENT =
            "codex_cli_rs/0.130.0 (MiuTavern/0.9; Android)";
    private static final String BROWSER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15; Mobile) "
                    + "AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/131.0.0.0 Mobile Safari/537.36";
    private static final String CODEX_RESPONSES_URL =
            "https://chatgpt.com/backend-api/codex/responses";
    private static final String CODEX_MODELS_URL =
            "https://chatgpt.com/backend-api/codex/models?client_version=1.0.0";

    private ApiClient() {
    }

    public static Call generate(Context context, AppConfig config,
                                SecureStore secureStore, String apiKey,
                                JSONArray messages, StreamCallback callback) {
        final Call call = new Call();
        EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    generateWithSearchFallback(
                            context, call, config, secureStore,
                            apiKey, messages, callback);
                    if (!call.isCancelled()) callback.onComplete();
                } catch (Exception error) {
                    if (!call.isCancelled()) callback.onError(readableError(error));
                }
            }
        });
        return call;
    }

    private static void generateWithSearchFallback(
            Context context,
            Call call,
            AppConfig config,
            SecureStore secureStore,
            String apiKey,
            JSONArray messages,
            StreamCallback callback) throws Exception {
        if (!config.webSearch) {
            generateByMode(
                    context, call, config, secureStore,
                    apiKey, messages, callback);
            return;
        }
        if (AppConfig.GENERATION_LOCAL.equals(config.generation)) {
            JSONArray augmented = WebSearchClient.augment(call, messages);
            if (!call.isCancelled()) {
                generateByMode(
                        context, call, config, secureStore,
                        apiKey, augmented, callback);
            }
            return;
        }

        TrackingCallback nativeCallback = new TrackingCallback(callback);
        try {
            generateByMode(
                    context, call, config, secureStore,
                    apiKey, messages, nativeCallback);
        } catch (Exception nativeError) {
            if (call.isCancelled()
                    || nativeCallback.hasOutput()
                    || !isNativeSearchUnavailable(nativeError)) {
                throw nativeError;
            }
            AppConfig alternate = nativeSearchAlternative(config);
            if (alternate != null) {
                TrackingCallback alternateCallback =
                        new TrackingCallback(callback);
                try {
                    generateByMode(
                            context, call, alternate, secureStore,
                            apiKey, messages, alternateCallback);
                    return;
                } catch (Exception alternateError) {
                    if (call.isCancelled() || alternateCallback.hasOutput()) {
                        throw alternateError;
                    }
                }
            }
            JSONArray augmented = WebSearchClient.augment(call, messages);
            if (call.isCancelled()) return;
            AppConfig fallback = AppConfig.fromJson(config.toJson());
            fallback.webSearch = false;
            generateByMode(
                    context, call, fallback, secureStore,
                    apiKey, augmented, callback);
        }
    }

    private static AppConfig nativeSearchAlternative(AppConfig source)
            throws JSONException {
        if (!AppConfig.GENERATION_DIRECT_API.equals(source.generation)) {
            return null;
        }
        String format = source.directApiFormat;
        if (AppConfig.DIRECT_FORMAT_CLAUDE.equals(format)
                || AppConfig.DIRECT_FORMAT_GEMINI.equals(format)
                || AppConfig.DIRECT_FORMAT_OLLAMA.equals(format)
                || AppConfig.DIRECT_FORMAT_AZURE.equals(format)) {
            return null;
        }
        AppConfig alternate = AppConfig.fromJson(source.toJson());
        alternate.directApiFormat = AppConfig.DIRECT_FORMAT_RESPONSES.equals(format)
                ? AppConfig.DIRECT_FORMAT_CHAT
                : AppConfig.DIRECT_FORMAT_RESPONSES;
        return alternate;
    }

    private static void generateByMode(
            Context context,
            Call call,
            AppConfig config,
            SecureStore secureStore,
            String apiKey,
            JSONArray messages,
            StreamCallback callback) throws Exception {
        if (AppConfig.GENERATION_LOCAL.equals(config.generation)) {
            LocalModelEngine.generate(
                    context.getApplicationContext(), call,
                    config, messages, callback);
        } else if (AppConfig.GENERATION_ACCOUNT.equals(config.generation)) {
            if (AppConfig.ACCOUNT_COPILOT.equals(config.accountProvider)) {
                generateThroughCopilotAccount(
                        call, config, secureStore, messages, callback);
            } else {
                generateThroughGptAccount(
                        call, config, secureStore, messages, callback);
            }
        } else {
            generateThroughDirectApi(
                    call, config, apiKey, messages, callback);
        }
    }

    private static boolean isNativeSearchUnavailable(Exception error) {
        int status = error instanceof EndpointException
                ? ((EndpointException) error).status : 0;
        String message = error.getMessage() == null
                ? "" : error.getMessage().toLowerCase(Locale.ROOT);
        boolean toolMentioned = message.contains("web_search")
                || message.contains("web search")
                || message.contains("web_search_options")
                || message.contains("google_search")
                || message.contains("google search")
                || message.contains("enable_search")
                || message.contains("unsupported tool")
                || message.contains("invalid tool")
                || message.contains("does not support tools")
                || (message.contains("tool")
                && (message.contains("unknown")
                || message.contains("unsupported")
                || message.contains("not support")));
        boolean validationFailure = status == 400 || status == 404
                || status == 422 || status == 501
                || message.contains("http 400")
                || message.contains("http 404")
                || message.contains("http 422")
                || message.contains("unknown field")
                || message.contains("unknown parameter")
                || message.contains("unrecognized")
                || message.contains("extra inputs");
        if (status == 400 || status == 422 || status == 501) return true;
        return toolMentioned && validationFailure;
    }

    public static void testDirectApi(
            AppConfig config, String apiKey, ResultCallback callback) {
        EXECUTOR.execute(() -> {
            try {
                List<String> models =
                        fetchDirectModelsSync(config, apiKey);
                callback.onSuccess(models.isEmpty()
                        ? "直连 API 可访问"
                        : "直连 API 可访问 · " + models.size() + " 个模型");
            } catch (Exception error) {
                callback.onError(readableError(error));
            }
        });
    }

    public static void fetchDirectModels(
            AppConfig config, String apiKey, ModelsCallback callback) {
        EXECUTOR.execute(() -> {
            try {
                List<String> models =
                        fetchDirectModelsSync(config, apiKey);
                if (models.isEmpty()) {
                    throw new IOException("接口没有返回可用模型");
                }
                callback.onSuccess(models);
            } catch (Exception error) {
                callback.onError(readableError(error));
            }
        });
    }

    public static void fetchGptModels(
            SecureStore secureStore, ModelsCallback callback) {
        EXECUTOR.execute(() -> {
            try {
                if (!secureStore.hasGptAccount()) {
                    throw new IOException("请先登录 GPT 账户");
                }
                String token = CodexAuthClient.getAccessToken(secureStore, false);
                Response response = executeCodexGet(token, false);
                int status = response.code();
                if (status == 401) {
                    response.close();
                    token = CodexAuthClient.getAccessToken(secureStore, true);
                    response = executeCodexGet(token, false);
                    status = response.code();
                }
                if (status == 403) {
                    response.close();
                    response = executeCodexGet(token, true);
                    status = response.code();
                }
                if (status < 200 || status >= 300) {
                    String detail = responseBody(response);
                    response.close();
                    throw new IOException("获取 GPT 模型失败（HTTP " + status + "）："
                            + explainCodexFailure(status, detail));
                }
                String body = responseBody(response);
                response.close();
                List<String> models = parseCodexModels(body);
                if (models.isEmpty()) {
                    throw new IOException("此 GPT 账户没有返回可见模型");
                }
                callback.onSuccess(models);
            } catch (Exception error) {
                callback.onError(readableError(error));
            }
        });
    }

    public static void fetchAccountModels(
            AppConfig config, SecureStore secureStore, ModelsCallback callback) {
        if (!AppConfig.ACCOUNT_COPILOT.equals(config.accountProvider)) {
            fetchGptModels(secureStore, callback);
            return;
        }
        EXECUTOR.execute(() -> {
            try {
                if (!secureStore.hasCopilotAccount()) {
                    throw new IOException("请先登录 GitHub Copilot 账户");
                }
                AppConfig copilot = copilotAccountConfig(config);
                List<String> models = fetchDirectModelsSync(
                        copilot, secureStore.getCopilotAccessToken());
                if (models.isEmpty()) {
                    throw new IOException("GitHub Copilot 没有返回可用模型");
                }
                callback.onSuccess(models);
            } catch (Exception error) {
                callback.onError(readableError(error));
            }
        });
    }

    public static List<String> codexFallbackModels() {
        return new ArrayList<>(Arrays.asList(
                "gpt-5.6-sol",
                "gpt-5.6-sol-pro",
                "gpt-5.6-terra",
                "gpt-5.6-terra-pro",
                "gpt-5.6-luna",
                "gpt-5.6-luna-pro",
                "gpt-5.5",
                "gpt-5.4-mini",
                "gpt-5.4",
                "gpt-5.3-codex",
                "gpt-5.3-codex-spark"
        ));
    }

    private static void generateThroughDirectApi(
            Call call,
            AppConfig config,
            String apiKey,
            JSONArray messages,
            StreamCallback callback) throws Exception {
        if (config.baseUrl == null || config.baseUrl.trim().isEmpty()) {
            throw new IOException("请先填写直连 API 地址");
        }
        if (config.model == null || config.model.trim().isEmpty()) {
            throw new IOException("请选择或填写直连 API 模型");
        }
        List<String> formats = directFormatCandidates(config);
        EndpointException lastEndpointError = null;
        for (String format : formats) {
            List<String> endpoints =
                    directGenerationUrlCandidates(
                            config.baseUrl, format, config.model);
            for (String endpoint : endpoints) {
                if (call.isCancelled()) return;
                HttpURLConnection connection = openDirectConnection(
                        endpoint, "POST", apiKey, format);
                call.connection = connection;
                connection.setReadTimeout(120_000);
                connection.setRequestProperty(
                        "Accept", "text/event-stream, application/json");
                JSONObject body = directGenerationBody(
                        format, config, messages);
                try {
                    writeJson(connection, body);
                    streamDirectByFormat(
                            format, call, connection, callback,
                            config.showReasoning);
                    return;
                } catch (EndpointException error) {
                    lastEndpointError = error;
                    connection.disconnect();
                    if (hasImageParts(messages)
                            && isImagePayloadRejected(error)) {
                        if (call.isCancelled()) return;
                        HttpURLConnection textOnlyConnection =
                                openDirectConnection(
                                        endpoint, "POST", apiKey, format);
                        call.connection = textOnlyConnection;
                        textOnlyConnection.setReadTimeout(120_000);
                        textOnlyConnection.setRequestProperty(
                                "Accept",
                                "text/event-stream, application/json");
                        writeJson(
                                textOnlyConnection,
                                directGenerationBody(
                                        format,
                                        config,
                                        messagesWithoutImages(messages)));
                        streamDirectByFormat(
                                format,
                                call,
                                textOnlyConnection,
                                callback,
                                config.showReasoning);
                        return;
                    }
                    if (error.status != 404 && error.status != 405) throw error;
                }
            }
        }
        if (lastEndpointError != null) throw lastEndpointError;
        throw new IOException("没有可用的直连 API 请求地址");
    }

    private static JSONObject directGenerationBody(
            String format, AppConfig config, JSONArray messages)
            throws JSONException {
        if (AppConfig.DIRECT_FORMAT_RESPONSES.equals(format)) {
            return directResponsesBody(config, messages);
        }
        if (AppConfig.DIRECT_FORMAT_CLAUDE.equals(format)) {
            return claudeGenerationBody(config, messages);
        }
        if (AppConfig.DIRECT_FORMAT_GEMINI.equals(format)) {
            return geminiGenerationBody(config, messages);
        }
        JSONObject body = new JSONObject()
                .put("messages", messages)
                .put("model", config.model.trim())
                .put("stream", true);
        if (config.webSearch
                && !AppConfig.DIRECT_FORMAT_OLLAMA.equals(format)) {
            addNativeChatSearch(body, config.baseUrl);
        }
        return body;
    }

    private static void addNativeChatSearch(
            JSONObject body, String baseUrl) throws JSONException {
        String endpoint = baseUrl == null
                ? "" : baseUrl.toLowerCase(Locale.ROOT);
        if (endpoint.contains("dashscope")
                || endpoint.contains("aliyuncs.com")
                || endpoint.contains("maas.aliyun")) {
            body.put("enable_search", true);
            return;
        }
        if (endpoint.contains("openrouter.ai")) {
            body.put("tools", new JSONArray().put(
                    new JSONObject().put("type", "openrouter:web_search")));
            return;
        }
        body.put("web_search_options", new JSONObject());
    }

    private static void streamDirectByFormat(
            String format,
            Call call,
            HttpURLConnection connection,
            StreamCallback callback,
            boolean showReasoning) throws Exception {
        if (AppConfig.DIRECT_FORMAT_RESPONSES.equals(format)) {
            streamDirectResponses(
                    call, connection, callback, showReasoning);
        } else if (AppConfig.DIRECT_FORMAT_CLAUDE.equals(format)) {
            streamClaudeResponse(
                    call, connection, callback, showReasoning);
        } else if (AppConfig.DIRECT_FORMAT_GEMINI.equals(format)) {
            streamGeminiResponse(
                    call, connection, callback, showReasoning);
        } else {
            streamChatResponse(
                    call, connection, callback, showReasoning);
        }
    }

    private static boolean isImagePayloadRejected(
            EndpointException error) {
        if (error.status != 400 && error.status != 422) return false;
        String message = error.getMessage() == null
                ? "" : error.getMessage().toLowerCase(Locale.ROOT);
        return message.contains("image_url")
                && (message.contains("unknown variant")
                || message.contains("expected `text`")
                || message.contains("expected text")
                || message.contains("unsupported")
                || message.contains("not support"));
    }

    private static boolean hasImageParts(JSONArray messages) {
        for (int i = 0; i < messages.length(); i++) {
            JSONObject message = messages.optJSONObject(i);
            JSONArray content = message == null
                    ? null : message.optJSONArray("content");
            if (content == null) continue;
            for (int j = 0; j < content.length(); j++) {
                JSONObject part = content.optJSONObject(j);
                if (part != null
                        && "image_url".equals(part.optString("type"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static JSONArray messagesWithoutImages(
            JSONArray messages) throws JSONException {
        JSONArray result = new JSONArray();
        for (int i = 0; i < messages.length(); i++) {
            JSONObject original = messages.optJSONObject(i);
            if (original == null) continue;
            JSONObject message = new JSONObject(original.toString());
            JSONArray content = original.optJSONArray("content");
            if (content == null) {
                result.put(message);
                continue;
            }
            JSONArray textOnly = new JSONArray();
            boolean removedImage = false;
            for (int j = 0; j < content.length(); j++) {
                JSONObject part = content.optJSONObject(j);
                if (part == null) continue;
                if ("image_url".equals(part.optString("type"))) {
                    removedImage = true;
                } else {
                    textOnly.put(new JSONObject(part.toString()));
                }
            }
            if (removedImage && textOnly.length() == 0) {
                textOnly.put(new JSONObject()
                        .put("type", "text")
                        .put("text", "[图片]"));
            }
            message.put("content", textOnly);
            result.put(message);
        }
        return result;
    }

    private static HttpURLConnection openDirectConnection(
            String url, String method, String apiKey, String format) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(30_000);
        connection.setDoOutput("POST".equals(method) || "PUT".equals(method));
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Accept", "application/json, text/event-stream");
        connection.setRequestProperty("User-Agent", "MiuTavern/0.9 Android");
        boolean claude = AppConfig.DIRECT_FORMAT_CLAUDE.equals(format);
        boolean gemini = AppConfig.DIRECT_FORMAT_GEMINI.equals(format);
        if (claude) {
            connection.setRequestProperty("anthropic-version", "2023-06-01");
        }
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            String key = apiKey.trim();
            String host = new URL(url).getHost().toLowerCase(Locale.ROOT);
            if (claude) {
                if (key.regionMatches(true, 0, "Bearer ", 0, 7)) {
                    connection.setRequestProperty("Authorization", key);
                } else if (key.startsWith("sk-ant-oat")
                        || key.startsWith("eyJ")) {
                    connection.setRequestProperty("Authorization", "Bearer " + key);
                } else {
                    connection.setRequestProperty("x-api-key", key);
                }
            } else if (gemini) {
                connection.setRequestProperty("x-goog-api-key", key);
            } else if (AppConfig.DIRECT_FORMAT_AZURE.equals(format)
                    || host.endsWith(".openai.azure.com")
                    || host.endsWith(".services.ai.azure.com")) {
                connection.setRequestProperty("api-key", key);
            } else {
                connection.setRequestProperty("Authorization", "Bearer " + key);
            }
        }
        return connection;
    }

    private static List<String> fetchDirectModelsSync(
            AppConfig config, String apiKey) throws Exception {
        List<String> candidates = directModelUrlCandidates(
                config.baseUrl, config.directApiFormat);
        EndpointException lastEndpointError = null;
        for (String endpoint : candidates) {
            HttpURLConnection connection =
                    openDirectConnection(
                            endpoint, "GET", apiKey, config.directApiFormat);
            int status = connection.getResponseCode();
            if (status >= 200 && status < 300) {
                List<String> models = parseGeneralModels(
                        readStream(connection.getInputStream()));
                if (AppConfig.DIRECT_FORMAT_GEMINI.equals(
                        config.directApiFormat)) {
                    for (int i = 0; i < models.size(); i++) {
                        String model = models.get(i);
                        if (model.startsWith("models/")) {
                            models.set(i, model.substring("models/".length()));
                        }
                    }
                }
                return models;
            }
            String body = readStream(connection.getErrorStream());
            EndpointException error =
                    new EndpointException(status, endpoint, body);
            connection.disconnect();
            if (status != 404 && status != 405) throw error;
            lastEndpointError = error;
        }
        if (lastEndpointError != null) throw lastEndpointError;
        throw new IOException("无法从 API 地址推导模型列表端点");
    }

    private static List<String> directFormatCandidates(AppConfig config) {
        if (AppConfig.DIRECT_FORMAT_CHAT.equals(config.directApiFormat)
                || AppConfig.DIRECT_FORMAT_AZURE.equals(config.directApiFormat)) {
            return Collections.singletonList(config.directApiFormat);
        }
        if (AppConfig.DIRECT_FORMAT_OLLAMA.equals(config.directApiFormat)) {
            return Collections.singletonList(AppConfig.DIRECT_FORMAT_OLLAMA);
        }
        if (AppConfig.DIRECT_FORMAT_RESPONSES.equals(config.directApiFormat)) {
            return Collections.singletonList(AppConfig.DIRECT_FORMAT_RESPONSES);
        }
        if (AppConfig.DIRECT_FORMAT_CLAUDE.equals(config.directApiFormat)) {
            return Collections.singletonList(AppConfig.DIRECT_FORMAT_CLAUDE);
        }
        if (AppConfig.DIRECT_FORMAT_GEMINI.equals(config.directApiFormat)) {
            return Collections.singletonList(AppConfig.DIRECT_FORMAT_GEMINI);
        }
        String path = urlPath(config.baseUrl).toLowerCase(Locale.ROOT);
        if (path.endsWith("/responses")) {
            return Arrays.asList(
                    AppConfig.DIRECT_FORMAT_RESPONSES,
                    AppConfig.DIRECT_FORMAT_CHAT);
        }
        return Arrays.asList(
                AppConfig.DIRECT_FORMAT_CHAT,
                AppConfig.DIRECT_FORMAT_RESPONSES);
    }

    private static List<String> directGenerationUrlCandidates(
            String baseUrl, String format, String configModelForUrl)
            throws IOException {
        String raw = baseUrl == null ? "" : baseUrl.trim();
        if (raw.isEmpty()) throw new IOException("请先填写直连 API 地址");
        String path = urlPath(raw).toLowerCase(Locale.ROOT);
        if (AppConfig.DIRECT_FORMAT_OLLAMA.equals(format)) {
            if (path.endsWith("/api/chat")) return Collections.singletonList(raw);
            String root = trimSlash(raw);
            return Collections.singletonList(path.endsWith("/api")
                    ? root + "/chat" : root + "/api/chat");
        }
        if (AppConfig.DIRECT_FORMAT_CLAUDE.equals(format)) {
            if (path.endsWith("/v1/messages") || path.endsWith("/messages")) {
                return Collections.singletonList(raw);
            }
            String root = trimSlash(raw);
            return Collections.singletonList(path.endsWith("/v1")
                    ? root + "/messages" : root + "/v1/messages");
        }
        if (AppConfig.DIRECT_FORMAT_GEMINI.equals(format)) {
            String model = normalizedGeminiModel(configModelForUrl);
            if (path.contains(":streamgeneratecontent")) {
                return Collections.singletonList(ensureSseQuery(raw));
            }
            if (path.contains(":generatecontent")) {
                return Collections.singletonList(ensureSseQuery(
                        replaceEndpointPath(
                                raw,
                                ":generatecontent",
                                ":streamGenerateContent")));
            }
            String root = trimSlash(raw);
            if (path.endsWith("/models")) {
                return Collections.singletonList(ensureSseQuery(
                        root + "/" + model + ":streamGenerateContent"));
            }
            return Collections.singletonList(ensureSseQuery(
                    (path.endsWith("/v1beta") || path.endsWith("/v1"))
                            ? root + "/models/" + model + ":streamGenerateContent"
                            : root + "/v1beta/models/" + model
                            + ":streamGenerateContent"));
        }
        if (path.endsWith("/chat/completions") || path.endsWith("/responses")) {
            return Collections.singletonList(raw);
        }
        String root = trimSlash(raw);
        String endpoint = AppConfig.DIRECT_FORMAT_RESPONSES.equals(format)
                ? "/responses" : "/chat/completions";
        List<String> candidates = new ArrayList<>();
        if (endsWithVersionSegment(root)) {
            addUnique(candidates, root + endpoint);
        } else {
            addUnique(candidates, root + "/v1" + endpoint);
            addUnique(candidates, root + endpoint);
        }
        return candidates;
    }

    private static List<String> directModelUrlCandidates(
            String baseUrl, String format)
            throws IOException {
        String raw = baseUrl == null ? "" : baseUrl.trim();
        if (raw.isEmpty()) throw new IOException("请先填写直连 API 地址");
        String root = trimSlash(raw);
        String path = urlPath(root);
        String lowerPath = path.toLowerCase(Locale.ROOT);
        List<String> candidates = new ArrayList<>();
        if (AppConfig.DIRECT_FORMAT_CLAUDE.equals(format)) {
            if (lowerPath.endsWith("/v1/models")
                    || lowerPath.endsWith("/models")) {
                addUnique(candidates, root);
                return candidates;
            }
            addUnique(candidates, lowerPath.endsWith("/v1")
                    ? root + "/models" : root + "/v1/models");
            return candidates;
        }
        if (AppConfig.DIRECT_FORMAT_GEMINI.equals(format)) {
            int modelPath = lowerPath.indexOf("/models/");
            if (modelPath >= 0) {
                String modelSuffix = path.substring(modelPath);
                addUnique(candidates, replaceEndpointPath(
                        root,
                        modelSuffix.toLowerCase(Locale.ROOT),
                        "/models"));
                return candidates;
            }
            if (lowerPath.endsWith("/models")) {
                addUnique(candidates, root);
                return candidates;
            }
            addUnique(candidates,
                    (lowerPath.endsWith("/v1beta") || lowerPath.endsWith("/v1"))
                            ? root + "/models" : root + "/v1beta/models");
            return candidates;
        }
        if (AppConfig.DIRECT_FORMAT_OLLAMA.equals(format)) {
            if (lowerPath.endsWith("/api/tags")) {
                addUnique(candidates, root);
            } else if (lowerPath.endsWith("/api")) {
                addUnique(candidates, root + "/tags");
            } else {
                addUnique(candidates, root + "/api/tags");
            }
        }

        if (lowerPath.endsWith("/models")) {
            addUnique(candidates, root);
            return candidates;
        }
        if (lowerPath.endsWith("/chat/completions")) {
            addUnique(candidates, replaceEndpointPath(
                    root, "/chat/completions", "/models"));
            return candidates;
        }
        if (lowerPath.endsWith("/responses")) {
            addUnique(candidates, replaceEndpointPath(
                    root, "/responses", "/models"));
            return candidates;
        }

        if (endsWithVersionSegment(root)) {
            addUnique(candidates, root + "/models");
            if (!lowerPath.endsWith("/v1")) {
                addUnique(candidates, root + "/v1/models");
            }
        } else {
            addUnique(candidates, root + "/v1/models");
            addUnique(candidates, root + "/models");
        }

        String stripped = stripKnownCompatibilitySuffix(root);
        if (!stripped.equals(root)) {
            addUnique(candidates, stripped + "/v1/models");
            addUnique(candidates, stripped + "/models");
        }
        return candidates;
    }

    private static String replaceEndpointPath(
            String url, String endpoint, String replacement) {
        int query = url.indexOf('?');
        String withoutQuery = query >= 0 ? url.substring(0, query) : url;
        String lower = withoutQuery.toLowerCase(Locale.ROOT);
        int index = lower.lastIndexOf(endpoint);
        return index < 0
                ? withoutQuery
                : withoutQuery.substring(0, index) + replacement;
    }

    private static boolean endsWithVersionSegment(String value) {
        return urlPath(value).matches(".*/v\\d+$");
    }

    private static String urlPath(String value) {
        if (value == null || value.trim().isEmpty()) return "";
        try {
            return new URL(value.trim()).getPath().replaceAll("/+$", "");
        } catch (Exception ignored) {
            String result = value.trim();
            int query = result.indexOf('?');
            if (query >= 0) result = result.substring(0, query);
            return result.replaceAll("/+$", "");
        }
    }

    private static String stripKnownCompatibilitySuffix(String value) {
        String[] suffixes = {
                "/api/claudecode",
                "/api/anthropic",
                "/apps/anthropic",
                "/api/coding",
                "/claudecode",
                "/anthropic",
                "/step_plan",
                "/coding",
                "/claude"
        };
        String lower = value.toLowerCase(Locale.ROOT);
        for (String suffix : suffixes) {
            if (lower.endsWith(suffix)) {
                return trimSlash(value.substring(
                        0, value.length() - suffix.length()));
            }
        }
        return value;
    }

    private static void addUnique(List<String> values, String value) {
        if (value != null && !value.trim().isEmpty()
                && !values.contains(value)) {
            values.add(value);
        }
    }

    private static String normalizedGeminiModel(String value) {
        String model = value == null ? "" : value.trim();
        if (model.startsWith("models/")) {
            model = model.substring("models/".length());
        }
        return model.isEmpty() ? "gemini-2.5-flash" : model;
    }

    private static String ensureSseQuery(String value) {
        if (value.contains("alt=sse")) return value;
        return value + (value.contains("?") ? "&" : "?") + "alt=sse";
    }

    private static AppConfig copilotAccountConfig(AppConfig source)
            throws IOException {
        String endpoint = source.copilotEndpoint == null
                ? "" : source.copilotEndpoint.trim();
        if (endpoint.isEmpty()) {
            throw new IOException("请先填写 Copilot SDK 网关地址");
        }
        AppConfig config = new AppConfig();
        config.baseUrl = endpoint;
        config.directApiFormat = AppConfig.DIRECT_FORMAT_CHAT;
        config.model = source.copilotModel;
        config.showReasoning = source.showReasoning;
        config.webSearch = source.webSearch;
        config.characterAutonomousMessages = source.characterAutonomousMessages;
        config.groupAutonomousMessages = source.groupAutonomousMessages;
        return config;
    }

    private static void generateThroughCopilotAccount(
            Call call,
            AppConfig config,
            SecureStore secureStore,
            JSONArray messages,
            StreamCallback callback) throws Exception {
        if (!secureStore.hasCopilotAccount()) {
            throw new IOException("请先在“连接与账户”中登录 GitHub Copilot");
        }
        AppConfig copilot = copilotAccountConfig(config);
        if (copilot.model == null || copilot.model.trim().isEmpty()) {
            throw new IOException("请先选择 GitHub Copilot 模型");
        }
        generateThroughDirectApi(
                call,
                copilot,
                secureStore.getCopilotAccessToken(),
                messages,
                callback);
    }

    private static void generateThroughGptAccount(
            Call call,
            AppConfig config,
            SecureStore secureStore,
            JSONArray messages,
            StreamCallback callback) throws Exception {
        if (!secureStore.hasGptAccount()) {
            throw new IOException("请先在“连接与账户”中登录 GPT 账户");
        }
        if (config.gptModel == null || config.gptModel.trim().isEmpty()) {
            throw new IOException("请先填写 GPT 模型名称");
        }

        JSONObject requestBody = codexGenerationBody(config, messages);
        String token = CodexAuthClient.getAccessToken(secureStore, false);
        Response response = executeCodexPost(
                call, token, requestBody, false);
        int status = response.code();
        if (status == 401) {
            response.close();
            if (call.isCancelled()) return;
            token = CodexAuthClient.getAccessToken(secureStore, true);
            response = executeCodexPost(
                    call, token, requestBody, false);
            status = response.code();
        }
        if (status == 403) {
            response.close();
            if (call.isCancelled()) return;
            response = executeCodexPost(
                    call, token, requestBody, true);
            status = response.code();
        }
        if (status < 200 || status >= 300) {
            String detail = responseBody(response);
            response.close();
            throw new IOException("GPT 账户请求失败（HTTP " + status + "）："
                    + explainCodexFailure(status, detail));
        }
        ResponseBody body = response.body();
        if (body == null) {
            response.close();
            throw new IOException("GPT 账户没有返回响应内容");
        }
        try {
            streamResponsesInput(
                    call,
                    body.byteStream(),
                    callback,
                    config.showReasoning,
                    "GPT 账户");
        } finally {
            response.close();
        }
    }

    private static Response executeCodexPost(
            Call call,
            String token,
            JSONObject body,
            boolean browserUserAgent) throws IOException {
        Request request = codexRequestBuilder(
                CODEX_RESPONSES_URL, token, browserUserAgent)
                .post(RequestBody.create(JSON, body.toString()))
                .build();
        okhttp3.Call httpCall = CODEX_HTTP.newCall(request);
        call.okHttpCall = httpCall;
        return httpCall.execute();
    }

    private static Response executeCodexGet(
            String token, boolean browserUserAgent) throws IOException {
        Request request = codexRequestBuilder(
                CODEX_MODELS_URL, token, browserUserAgent)
                .get()
                .build();
        return CODEX_HTTP.newCall(request).execute();
    }

    private static Request.Builder codexRequestBuilder(
            String url, String token, boolean browserUserAgent) {
        Request.Builder builder = new Request.Builder()
                .url(url)
                .header("Accept", "text/event-stream, application/json")
                .header("Authorization", "Bearer " + token)
                .header("originator", "codex_cli_rs")
                .header("User-Agent",
                        browserUserAgent
                                ? BROWSER_USER_AGENT : CODEX_USER_AGENT);
        String accountId = CodexAuthClient.accountIdFromToken(token);
        if (!accountId.isEmpty()) {
            builder.header("ChatGPT-Account-ID", accountId);
        }
        String requestId = UUID.randomUUID().toString();
        builder.header("session_id", requestId);
        builder.header("x-client-request-id", requestId);
        return builder;
    }

    private static String responseBody(Response response) throws IOException {
        ResponseBody body = response.body();
        return body == null ? "" : body.string();
    }

    private static String explainCodexFailure(int status, String detail) {
        String normalized = detail == null ? "" : detail.trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (status == 403 && (lower.contains("cloudflare")
                || lower.contains("enable javascript")
                || lower.contains("cf-chl")
                || lower.contains("challenge"))) {
            return "当前网络触发了 ChatGPT 的 Cloudflare 验证。"
                    + "应用已切换安全 DNS 并重试；仍失败时请更换网络或代理节点。";
        }
        if (status == 403) {
            return "当前账户或工作区没有 Codex 访问权限，请确认订阅和工作区策略后重新登录。"
                    + (normalized.isEmpty() ? "" : " " + normalized);
        }
        return normalized;
    }

    private static HttpURLConnection openCodexConnection(Call call, String token)
            throws IOException {
        HttpURLConnection connection =
                (HttpURLConnection) new URL(CODEX_RESPONSES_URL).openConnection();
        call.connection = connection;
        configureStreamingPost(connection);
        connection.setRequestProperty("Authorization", "Bearer " + token);
        connection.setRequestProperty("originator", "codex_cli_rs");
        connection.setRequestProperty("User-Agent", "codex_cli_rs/0.0.0 (MiuTavern)");
        String requestId = UUID.randomUUID().toString();
        connection.setRequestProperty("session_id", requestId);
        connection.setRequestProperty("x-client-request-id", requestId);
        String accountId = CodexAuthClient.accountIdFromToken(token);
        if (!accountId.isEmpty()) {
            connection.setRequestProperty("ChatGPT-Account-ID", accountId);
        }
        return connection;
    }

    private static HttpURLConnection openCodexModelsConnection(String token)
            throws IOException {
        HttpURLConnection connection =
                (HttpURLConnection) new URL(CODEX_MODELS_URL).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(20_000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Authorization", "Bearer " + token);
        connection.setRequestProperty("originator", "codex_cli_rs");
        connection.setRequestProperty("User-Agent", "codex_cli_rs/0.0.0 (MiuTavern)");
        String accountId = CodexAuthClient.accountIdFromToken(token);
        if (!accountId.isEmpty()) {
            connection.setRequestProperty("ChatGPT-Account-ID", accountId);
        }
        return connection;
    }

    private static JSONObject codexGenerationBody(AppConfig config, JSONArray messages)
            throws JSONException {
        JSONObject body = responsesGenerationBody(
                config.gptModel.trim(), messages);
        body.put("store", false);
        body.put("reasoning", new JSONObject()
                .put("effort", "medium")
                .put("summary", "auto"));
        if (config.webSearch) {
            body.put("tools", new JSONArray().put(
                    new JSONObject().put("type", "web_search")));
        }
        return body;
    }

    private static JSONObject directResponsesBody(
            AppConfig config, JSONArray messages) throws JSONException {
        JSONObject body = responsesGenerationBody(config.model.trim(), messages);
        if (config.webSearch) {
            body.put("tools", new JSONArray().put(
                    new JSONObject().put(
                            "type",
                            config.baseUrl != null
                                    && config.baseUrl.toLowerCase(Locale.ROOT)
                                    .contains("openrouter.ai")
                                    ? "openrouter:web_search"
                                    : "web_search")));
        }
        return body;
    }

    private static JSONObject claudeGenerationBody(
            AppConfig config, JSONArray messages) throws JSONException {
        StringBuilder system = new StringBuilder();
        JSONArray conversation = new JSONArray();
        for (int i = 0; i < messages.length(); i++) {
            JSONObject item = messages.optJSONObject(i);
            if (item == null) continue;
            String role = item.optString("role", "");
            Object content = item.opt("content");
            if ("system".equals(role)) {
                if (system.length() > 0) system.append("\n\n");
                system.append(content instanceof String ? content : "");
            } else if ("user".equals(role) || "assistant".equals(role)) {
                conversation.put(new JSONObject()
                        .put("role", role)
                        .put("content", claudeContent(content)));
            }
        }
        JSONObject body = new JSONObject()
                .put("model", config.model.trim())
                .put("max_tokens", 4096)
                .put("messages", conversation)
                .put("stream", true);
        if (system.length() > 0) body.put("system", system.toString());
        if (config.webSearch) {
            body.put("tools", new JSONArray().put(new JSONObject()
                    .put("type", "web_search_20250305")
                    .put("name", "web_search")
                    .put("max_uses", 5)));
        }
        return body;
    }

    private static Object claudeContent(Object content) throws JSONException {
        if (!(content instanceof JSONArray)) {
            return content == null ? "" : content;
        }
        JSONArray source = (JSONArray) content;
        JSONArray converted = new JSONArray();
        for (int i = 0; i < source.length(); i++) {
            JSONObject part = source.optJSONObject(i);
            if (part == null) continue;
            String type = part.optString("type", "");
            if ("text".equals(type)) {
                converted.put(new JSONObject()
                        .put("type", "text")
                        .put("text", part.optString("text", "")));
            } else if ("image_url".equals(type)) {
                JSONObject image = part.optJSONObject("image_url");
                String url = image == null ? "" : image.optString("url", "");
                JSONObject sourceObject = imageSource(url, true);
                if (sourceObject != null) {
                    converted.put(new JSONObject()
                            .put("type", "image")
                            .put("source", sourceObject));
                }
            }
        }
        return converted;
    }

    private static JSONObject geminiGenerationBody(
            AppConfig config, JSONArray messages) throws JSONException {
        StringBuilder system = new StringBuilder();
        JSONArray contents = new JSONArray();
        for (int i = 0; i < messages.length(); i++) {
            JSONObject item = messages.optJSONObject(i);
            if (item == null) continue;
            String role = item.optString("role", "");
            Object content = item.opt("content");
            if ("system".equals(role)) {
                if (system.length() > 0) system.append("\n\n");
                system.append(content instanceof String ? content : "");
            } else if ("user".equals(role) || "assistant".equals(role)) {
                contents.put(new JSONObject()
                        .put("role", "assistant".equals(role) ? "model" : "user")
                        .put("parts", geminiParts(content)));
            }
        }
        JSONObject body = new JSONObject()
                .put("contents", contents)
                .put("generationConfig", new JSONObject()
                        .put("maxOutputTokens", 4096));
        if (system.length() > 0) {
            body.put("systemInstruction", new JSONObject()
                    .put("parts", new JSONArray()
                            .put(new JSONObject().put("text", system.toString()))));
        }
        if (config.webSearch) {
            body.put("tools", new JSONArray().put(
                    new JSONObject().put("google_search", new JSONObject())));
        }
        return body;
    }

    private static JSONArray geminiParts(Object content) throws JSONException {
        JSONArray converted = new JSONArray();
        if (!(content instanceof JSONArray)) {
            converted.put(new JSONObject()
                    .put("text", content == null ? "" : content));
            return converted;
        }
        JSONArray source = (JSONArray) content;
        for (int i = 0; i < source.length(); i++) {
            JSONObject part = source.optJSONObject(i);
            if (part == null) continue;
            String type = part.optString("type", "");
            if ("text".equals(type)) {
                converted.put(new JSONObject()
                        .put("text", part.optString("text", "")));
            } else if ("image_url".equals(type)) {
                JSONObject image = part.optJSONObject("image_url");
                String url = image == null ? "" : image.optString("url", "");
                JSONObject inline = imageSource(url, false);
                if (inline != null) converted.put(inline);
            }
        }
        return converted;
    }

    private static JSONObject imageSource(String url, boolean claude)
            throws JSONException {
        if (url == null || url.isEmpty()) return null;
        if (!url.startsWith("data:")) {
            if (claude) {
                return new JSONObject()
                        .put("type", "url")
                        .put("url", url);
            }
            return new JSONObject().put(
                    "text", "[图片链接：" + url + "]");
        }
        int separator = url.indexOf(',');
        int typeEnd = url.indexOf(';');
        if (separator < 0 || typeEnd < 5 || typeEnd > separator) return null;
        String mime = url.substring(5, typeEnd);
        String data = url.substring(separator + 1);
        if (claude) {
            return new JSONObject()
                    .put("type", "base64")
                    .put("media_type", mime)
                    .put("data", data);
        }
        return new JSONObject().put("inline_data", new JSONObject()
                .put("mime_type", mime)
                .put("data", data));
    }

    private static JSONObject responsesGenerationBody(
            String model, JSONArray messages) throws JSONException {
        StringBuilder instructions = new StringBuilder();
        JSONArray input = new JSONArray();
        for (int i = 0; i < messages.length(); i++) {
            JSONObject item = messages.optJSONObject(i);
            if (item == null) continue;
            String role = item.optString("role", "");
            Object content = item.opt("content");
            if ("system".equals(role)) {
                if (instructions.length() > 0) instructions.append("\n\n");
                instructions.append(content instanceof String ? content : "");
            } else if ("user".equals(role) || "assistant".equals(role)) {
                input.put(new JSONObject()
                        .put("role", role)
                        .put("content", codexContent(content, role)));
            }
        }
        JSONObject body = new JSONObject()
                .put("model", model)
                .put("input", input)
                .put("stream", true);
        if (instructions.length() > 0) {
            body.put("instructions", instructions.toString());
        }
        return body;
    }

    private static Object codexContent(Object content, String role)
            throws JSONException {
        if (!(content instanceof JSONArray)) return content == null ? "" : content;
        JSONArray source = (JSONArray) content;
        JSONArray converted = new JSONArray();
        for (int i = 0; i < source.length(); i++) {
            JSONObject part = source.optJSONObject(i);
            if (part == null) continue;
            String type = part.optString("type", "");
            if ("text".equals(type)) {
                converted.put(new JSONObject()
                        .put("type", "assistant".equals(role)
                                ? "output_text" : "input_text")
                        .put("text", part.optString("text", "")));
            } else if ("image_url".equals(type)) {
                JSONObject image = part.optJSONObject("image_url");
                String url = image == null ? "" : image.optString("url", "");
                if (!url.isEmpty()) {
                    converted.put(new JSONObject()
                            .put("type", "input_image")
                            .put("image_url", url));
                }
            }
        }
        return converted;
    }

    private static JSONObject commonGenerationBody(AppConfig config, JSONArray messages) throws JSONException {
        return new JSONObject()
                .put("messages", messages)
                .put("model", config.model)
                .put("temperature", 0.85)
                .put("top_p", 0.95)
                .put("frequency_penalty", 0)
                .put("presence_penalty", 0)
                .put("max_tokens", 1200)
                .put("stream", true);
    }

    private static void streamChatResponse(
            Call call,
            HttpURLConnection connection,
            StreamCallback callback,
            boolean showReasoning) throws Exception {
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new EndpointException(
                    code,
                    connection.getURL().toString(),
                    readStream(connection.getErrorStream()));
        }
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder plain = new StringBuilder();
        StreamDisplayState state = new StreamDisplayState();
        String line;
        while (!call.isCancelled() && (line = reader.readLine()) != null) {
            if (line.startsWith("data:")) {
                String data = line.substring(5).trim();
                if ("[DONE]".equals(data)) break;
                String streamError = extractGeneralError(data);
                if (!streamError.isEmpty()) throw new IOException(streamError);
                state.emit(
                        parseChatDelta(data), showReasoning, callback);
            } else if (!line.trim().isEmpty()
                    && !line.startsWith("event:")
                    && !line.startsWith(":")) {
                String trimmed = line.trim();
                if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                    String streamError = extractGeneralError(trimmed);
                    if (!streamError.isEmpty()) throw new IOException(streamError);
                    ParsedDelta delta = parseChatDelta(trimmed);
                    if (!delta.isEmpty()) {
                        state.emit(delta, showReasoning, callback);
                        continue;
                    }
                }
                plain.append(line).append('\n');
            }
        }
        if (!call.isCancelled() && !state.emitted && plain.length() > 0) {
            String body = plain.toString().trim();
            String responseError = extractGeneralError(body);
            if (!responseError.isEmpty()) throw new IOException(responseError);
            state.emit(parseChatDelta(body), showReasoning, callback);
        }
        if (!call.isCancelled() && !state.emitted) {
            throw new IOException(showReasoning
                    ? "服务没有返回可显示的文字内容"
                    : "服务只返回了思考过程，没有返回最终回答");
        }
    }

    private static void streamCodexResponse(
            Call call,
            HttpURLConnection connection,
            StreamCallback callback,
            boolean showReasoning) throws Exception {
        streamResponsesBody(
                call, connection, callback, showReasoning, "GPT 账户");
    }

    private static void streamDirectResponses(
            Call call,
            HttpURLConnection connection,
            StreamCallback callback,
            boolean showReasoning) throws Exception {
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new EndpointException(
                    code,
                    connection.getURL().toString(),
                    readStream(connection.getErrorStream()));
        }
        streamResponsesBody(
                call, connection, callback, showReasoning, "Responses API");
    }

    private static void streamClaudeResponse(
            Call call,
            HttpURLConnection connection,
            StreamCallback callback,
            boolean showReasoning) throws Exception {
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new EndpointException(
                    code,
                    connection.getURL().toString(),
                    readStream(connection.getErrorStream()));
        }
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        connection.getInputStream(), StandardCharsets.UTF_8));
        StreamDisplayState state = new StreamDisplayState();
        LinkedHashSet<String> sources = new LinkedHashSet<>();
        StringBuilder plain = new StringBuilder();
        String line;
        while (!call.isCancelled() && (line = reader.readLine()) != null) {
            if (line.startsWith("data:")) {
                String data = line.substring(5).trim();
                String streamError = extractGeneralError(data);
                if (!streamError.isEmpty()) throw new IOException(streamError);
                collectClaudeSources(data, sources);
                state.emit(
                        parseClaudeDelta(data), showReasoning, callback);
            } else if (!line.trim().isEmpty()
                    && !line.startsWith("event:")
                    && !line.startsWith(":")) {
                plain.append(line).append('\n');
            }
        }
        if (!call.isCancelled() && !state.emitted && plain.length() > 0) {
            collectClaudeSources(plain.toString().trim(), sources);
            state.emit(
                    parseClaudeDelta(plain.toString().trim()),
                    showReasoning,
                    callback);
        }
        if (!call.isCancelled() && !state.emitted) {
            throw new IOException(showReasoning
                    ? "Claude 没有返回文字内容"
                    : "Claude 只返回了思考过程，没有返回最终回答");
        }
        if (!call.isCancelled() && !sources.isEmpty()) {
            callback.onDelta(formatSources(sources));
        }
    }

    private static ParsedDelta parseClaudeDelta(String json) {
        try {
            JSONObject root = new JSONObject(json);
            String type = root.optString("type", "");
            if ("content_block_delta".equals(type)) {
                JSONObject delta = root.optJSONObject("delta");
                if (delta == null) return ParsedDelta.EMPTY;
                String deltaType = delta.optString("type", "");
                if ("text_delta".equals(deltaType)) {
                    return new ParsedDelta(delta.optString("text", ""), "");
                }
                if ("thinking_delta".equals(deltaType)) {
                    return new ParsedDelta(
                            "", delta.optString("thinking", ""));
                }
            }
            JSONArray content = root.optJSONArray("content");
            if (content == null) {
                JSONObject message = root.optJSONObject("message");
                if (message != null) content = message.optJSONArray("content");
            }
            if (content == null) return ParsedDelta.EMPTY;
            StringBuilder text = new StringBuilder();
            StringBuilder reasoning = new StringBuilder();
            for (int i = 0; i < content.length(); i++) {
                JSONObject block = content.optJSONObject(i);
                if (block == null) continue;
                if ("text".equals(block.optString("type"))) {
                    text.append(block.optString("text", ""));
                } else if ("thinking".equals(block.optString("type"))) {
                    reasoning.append(block.optString("thinking", ""));
                }
            }
            return new ParsedDelta(text.toString(), reasoning.toString());
        } catch (JSONException ignored) {
            return ParsedDelta.EMPTY;
        }
    }

    private static void streamGeminiResponse(
            Call call,
            HttpURLConnection connection,
            StreamCallback callback,
            boolean showReasoning) throws Exception {
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new EndpointException(
                    code,
                    connection.getURL().toString(),
                    readStream(connection.getErrorStream()));
        }
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        connection.getInputStream(), StandardCharsets.UTF_8));
        StreamDisplayState state = new StreamDisplayState();
        LinkedHashSet<String> sources = new LinkedHashSet<>();
        StringBuilder plain = new StringBuilder();
        String line;
        while (!call.isCancelled() && (line = reader.readLine()) != null) {
            if (line.startsWith("data:")) {
                String data = line.substring(5).trim();
                String streamError = extractGeneralError(data);
                if (!streamError.isEmpty()) throw new IOException(streamError);
                collectGeminiSources(data, sources);
                state.emit(
                        parseGeminiDelta(data), showReasoning, callback);
            } else if (!line.trim().isEmpty()
                    && !line.startsWith("event:")
                    && !line.startsWith(":")) {
                plain.append(line).append('\n');
            }
        }
        if (!call.isCancelled() && !state.emitted && plain.length() > 0) {
            collectGeminiSources(plain.toString().trim(), sources);
            state.emit(
                    parseGeminiDelta(plain.toString().trim()),
                    showReasoning,
                    callback);
        }
        if (!call.isCancelled() && !state.emitted) {
            throw new IOException(showReasoning
                    ? "Gemini 没有返回文字内容"
                    : "Gemini 只返回了思考过程，没有返回最终回答");
        }
        if (!call.isCancelled() && !sources.isEmpty()) {
            callback.onDelta(formatSources(sources));
        }
    }

    private static ParsedDelta parseGeminiDelta(String json) {
        try {
            JSONObject root = new JSONObject(json);
            JSONArray candidates = root.optJSONArray("candidates");
            if (candidates == null || candidates.length() == 0) {
                return ParsedDelta.EMPTY;
            }
            JSONObject candidate = candidates.optJSONObject(0);
            JSONObject content =
                    candidate == null ? null : candidate.optJSONObject("content");
            JSONArray parts = content == null
                    ? null : content.optJSONArray("parts");
            StringBuilder text = new StringBuilder();
            StringBuilder reasoning = new StringBuilder();
            if (parts != null) {
                for (int i = 0; i < parts.length(); i++) {
                    JSONObject part = parts.optJSONObject(i);
                    if (part == null) continue;
                    String value = part.optString("text", "");
                    if (part.optBoolean("thought", false)) {
                        reasoning.append(value);
                    } else {
                        text.append(value);
                    }
                }
            }
            return new ParsedDelta(text.toString(), reasoning.toString());
        } catch (JSONException ignored) {
            return ParsedDelta.EMPTY;
        }
    }

    private static void collectClaudeSources(
            String json, LinkedHashSet<String> sources) {
        try {
            collectClaudeSourcesValue(
                    new org.json.JSONTokener(json).nextValue(), sources);
        } catch (Exception ignored) {
        }
    }

    private static void collectClaudeSourcesValue(
            Object value, LinkedHashSet<String> sources) {
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) {
                collectClaudeSourcesValue(array.opt(i), sources);
            }
            return;
        }
        if (!(value instanceof JSONObject)) return;
        JSONObject object = (JSONObject) value;
        String type = object.optString("type", "");
        if ("web_search_result".equals(type)
                || "web_search_result_location".equals(type)) {
            addSource(
                    sources,
                    object.optString("title", ""),
                    firstNonEmpty(
                            object.optString("url", ""),
                            object.optString("uri", "")));
        }
        collectClaudeSourcesValue(object.opt("content"), sources);
        collectClaudeSourcesValue(object.opt("content_block"), sources);
        collectClaudeSourcesValue(object.opt("message"), sources);
        collectClaudeSourcesValue(object.opt("citation"), sources);
        collectClaudeSourcesValue(object.opt("citations"), sources);
        collectClaudeSourcesValue(object.opt("delta"), sources);
    }

    private static void collectGeminiSources(
            String json, LinkedHashSet<String> sources) {
        try {
            Object root = new org.json.JSONTokener(json).nextValue();
            if (root instanceof JSONArray) {
                JSONArray array = (JSONArray) root;
                for (int i = 0; i < array.length(); i++) {
                    collectGeminiCandidateSources(
                            array.optJSONObject(i), sources);
                }
            } else if (root instanceof JSONObject) {
                collectGeminiCandidateSources((JSONObject) root, sources);
            }
        } catch (Exception ignored) {
        }
    }

    private static void collectGeminiCandidateSources(
            JSONObject root, LinkedHashSet<String> sources) {
        JSONArray candidates =
                root == null ? null : root.optJSONArray("candidates");
        if (candidates == null) return;
        for (int candidateIndex = 0;
             candidateIndex < candidates.length();
             candidateIndex++) {
            JSONObject candidate = candidates.optJSONObject(candidateIndex);
            JSONObject metadata = candidate == null
                    ? null : candidate.optJSONObject("groundingMetadata");
            JSONArray chunks = metadata == null
                    ? null : metadata.optJSONArray("groundingChunks");
            if (chunks == null) continue;
            for (int i = 0; i < chunks.length(); i++) {
                JSONObject chunk = chunks.optJSONObject(i);
                JSONObject web =
                        chunk == null ? null : chunk.optJSONObject("web");
                if (web == null) continue;
                addSource(
                        sources,
                        web.optString("title", ""),
                        firstNonEmpty(
                                web.optString("uri", ""),
                                web.optString("url", "")));
            }
        }
    }

    private static void addSource(
            LinkedHashSet<String> sources, String title, String url) {
        String safeUrl = url == null ? "" : url.trim();
        if (safeUrl.isEmpty()) return;
        String safeTitle = title == null ? "" : title.trim();
        sources.add(safeTitle.isEmpty()
                ? safeUrl : safeTitle + "：" + safeUrl);
    }

    private static String formatSources(Set<String> sources) {
        if (sources == null || sources.isEmpty()) return "";
        StringBuilder result = new StringBuilder("\n\n来源：");
        for (String citation : sources) {
            result.append("\n- ").append(citation);
        }
        return result.toString();
    }

    private static void streamResponsesBody(
            Call call,
            HttpURLConnection connection,
            StreamCallback callback,
            boolean showReasoning,
            String sourceName) throws Exception {
        streamResponsesInput(
                call,
                connection.getInputStream(),
                callback,
                showReasoning,
                sourceName);
    }

    private static void streamResponsesInput(
            Call call,
            InputStream input,
            StreamCallback callback,
            boolean showReasoning,
            String sourceName) throws Exception {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8));
        StringBuilder plain = new StringBuilder();
        StreamDisplayState state = new StreamDisplayState();
        String line;
        while (!call.isCancelled() && (line = reader.readLine()) != null) {
            if (line.startsWith("data:")) {
                String data = line.substring(5).trim();
                if ("[DONE]".equals(data)) break;
                state.emit(
                        parseResponsesDelta(data), showReasoning, callback);
                String streamError = extractCodexError(data);
                if (!streamError.isEmpty()) throw new IOException(streamError);
            } else if (!line.trim().isEmpty() && !line.startsWith("event:")) {
                plain.append(line).append('\n');
            }
        }
        if (!call.isCancelled() && !state.emitted && plain.length() > 0) {
            String json = plain.toString().trim();
            state.emit(new ParsedDelta(
                    extractCodexOutput(json),
                    extractResponsesReasoning(json)), showReasoning, callback);
        }
        if (!call.isCancelled() && !state.emitted) {
            throw new IOException(sourceName + (showReasoning
                    ? "没有返回文字内容"
                    : "只返回了思考过程，没有返回最终回答"));
        }
    }

    private static ParsedDelta parseResponsesDelta(String json) {
        try {
            JSONObject root = new JSONObject(json);
            String type = root.optString("type", "");
            if ("response.output_text.delta".equals(type)) {
                return new ParsedDelta(root.optString("delta", ""), "");
            }
            if ("response.reasoning_summary_text.delta".equals(type)
                    || "response.reasoning_text.delta".equals(type)
                    || "response.reasoning.delta".equals(type)) {
                return new ParsedDelta("", root.optString("delta", ""));
            }
            if ("response.completed".equals(type)) {
                JSONObject response = root.optJSONObject("response");
                return new ParsedDelta(
                        response == null ? "" : extractResponseCitations(response), "");
            }
            return ParsedDelta.EMPTY;
        } catch (JSONException ignored) {
            return ParsedDelta.EMPTY;
        }
    }

    private static String extractCodexError(String json) {
        try {
            JSONObject root = new JSONObject(json);
            String type = root.optString("type", "");
            if (!"error".equals(type) && !"response.failed".equals(type)) return "";
            JSONObject error = root.optJSONObject("error");
            if (error == null) {
                JSONObject response = root.optJSONObject("response");
                if (response != null) error = response.optJSONObject("error");
            }
            String message = error == null ? "" : error.optString("message", "");
            return message.isEmpty() ? "GPT 账户流式请求失败" : message;
        } catch (JSONException ignored) {
            return "";
        }
    }

    private static String extractCodexOutput(String json) {
        try {
            JSONObject root = new JSONObject(json);
            String direct = root.optString("output_text", "");
            if (!direct.isEmpty()) return direct;
            JSONArray output = root.optJSONArray("output");
            if (output == null) return "";
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < output.length(); i++) {
                JSONObject item = output.optJSONObject(i);
                if (item == null) continue;
                JSONArray content = item.optJSONArray("content");
                if (content == null) continue;
                for (int c = 0; c < content.length(); c++) {
                    JSONObject part = content.optJSONObject(c);
                    if (part != null && "output_text".equals(part.optString("type"))) {
                        text.append(part.optString("text", ""));
                    }
                }
            }
            text.append(extractResponseCitations(root));
            return text.toString();
        } catch (JSONException ignored) {
            return "";
        }
    }

    private static String extractResponseCitations(JSONObject response) {
        JSONArray output = response.optJSONArray("output");
        if (output == null) return "";
        LinkedHashSet<String> citations = new LinkedHashSet<>();
        for (int i = 0; i < output.length(); i++) {
            JSONObject item = output.optJSONObject(i);
            if (item == null) continue;
            JSONArray content = item.optJSONArray("content");
            if (content == null) continue;
            for (int c = 0; c < content.length(); c++) {
                JSONObject part = content.optJSONObject(c);
                if (part == null) continue;
                JSONArray annotations = part.optJSONArray("annotations");
                if (annotations == null) continue;
                for (int a = 0; a < annotations.length(); a++) {
                    JSONObject annotation = annotations.optJSONObject(a);
                    if (annotation == null
                            || !"url_citation".equals(annotation.optString("type"))) {
                        continue;
                    }
                    String url = annotation.optString("url", "").trim();
                    if (url.isEmpty()) continue;
                    String title = annotation.optString("title", "").trim();
                    citations.add(title.isEmpty() ? url : title + "：" + url);
                }
            }
        }
        if (citations.isEmpty()) return "";
        StringBuilder result = new StringBuilder("\n\n来源：");
        for (String citation : citations) {
            result.append("\n- ").append(citation);
        }
        return result.toString();
    }

    private static List<String> parseGeneralModels(String json) throws JSONException {
        Object root = new org.json.JSONTokener(json).nextValue();
        List<String> models = new ArrayList<>();
        collectModelIds(root, models);
        Set<String> unique = new LinkedHashSet<>(models);
        models = new ArrayList<>(unique);
        Collections.sort(models, String.CASE_INSENSITIVE_ORDER);
        return models;
    }

    private static void collectModelIds(Object value, List<String> output) {
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) {
                Object item = array.opt(i);
                if (item instanceof String) {
                    String id = ((String) item).trim();
                    if (!id.isEmpty()) output.add(id);
                } else if (item instanceof JSONObject) {
                    JSONObject object = (JSONObject) item;
                    String id = firstNonEmpty(
                            object.optString("id", ""),
                            object.optString("slug", ""),
                            object.optString("name", ""));
                    if (!id.isEmpty()) output.add(id);
                }
            }
            return;
        }
        if (!(value instanceof JSONObject)) return;
        JSONObject object = (JSONObject) value;
        Object data = object.opt("data");
        if (data instanceof JSONArray || data instanceof JSONObject) {
            collectModelIds(data, output);
        }
        Object models = object.opt("models");
        if (models instanceof JSONArray || models instanceof JSONObject) {
            collectModelIds(models, output);
        }
    }

    private static List<String> parseCodexModels(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        JSONArray array = root.optJSONArray("models");
        List<ModelRank> ranked = new ArrayList<>();
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                String visibility = item.optString("visibility", "");
                if ("hide".equalsIgnoreCase(visibility)
                        || "hidden".equalsIgnoreCase(visibility)) {
                    continue;
                }
                String slug = item.optString("slug", "").trim();
                if (slug.isEmpty()) continue;
                ranked.add(new ModelRank(slug, item.optInt("priority", 10_000)));
            }
        }
        Collections.sort(ranked, new Comparator<ModelRank>() {
            @Override
            public int compare(ModelRank left, ModelRank right) {
                int priority = Integer.compare(left.priority, right.priority);
                return priority != 0
                        ? priority
                        : left.id.compareToIgnoreCase(right.id);
            }
        });
        List<String> models = new ArrayList<>();
        for (ModelRank item : ranked) {
            if (!models.contains(item.id)) models.add(item.id);
        }
        return models;
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return "";
    }

    private static ParsedDelta parseChatDelta(String json) {
        try {
            JSONObject root = new JSONObject(json);
            JSONArray choices = root.optJSONArray("choices");
            if (choices == null || choices.length() == 0) {
                JSONObject message = root.optJSONObject("message");
                if (message != null) {
                    return parsedMessage(message);
                }
                return new ParsedDelta(
                        firstNonEmpty(
                                root.optString("output_text", ""),
                                root.optString("text", ""),
                                root.optString("response", "")),
                        reasoningFrom(root));
            }
            JSONObject first = choices.optJSONObject(0);
            if (first == null) return ParsedDelta.EMPTY;
            JSONObject delta = first.optJSONObject("delta");
            if (delta != null) {
                ParsedDelta parsed = parsedMessage(delta);
                if (!parsed.isEmpty()) return parsed;
            }
            JSONObject message = first.optJSONObject("message");
            if (message != null) {
                ParsedDelta parsed = parsedMessage(message);
                if (!parsed.isEmpty()) return parsed;
            }
            return new ParsedDelta(
                    first.optString("text", ""),
                    reasoningFrom(first));
        } catch (JSONException ignored) {
            return ParsedDelta.EMPTY;
        }
    }

    private static ParsedDelta parsedMessage(JSONObject object) {
        String content = contentValue(object.opt("content"));
        if (content.isEmpty()) {
            content = firstNonEmpty(
                    object.optString("refusal", ""),
                    object.optString("text", ""));
        }
        return new ParsedDelta(content, reasoningFrom(object));
    }

    private static String reasoningFrom(JSONObject object) {
        StringBuilder reasoning = new StringBuilder();
        appendReasoning(reasoning, object.opt("reasoning_content"));
        appendReasoning(reasoning, object.opt("reasoning"));
        appendReasoning(reasoning, object.opt("reasoning_details"));
        appendReasoning(reasoning, object.opt("thinking"));
        return reasoning.toString();
    }

    private static void appendReasoning(StringBuilder target, Object value) {
        if (value instanceof String) {
            target.append((String) value);
            return;
        }
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) {
                appendReasoning(target, array.opt(i));
            }
            return;
        }
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            String text = firstNonEmpty(
                    object.optString("text", ""),
                    object.optString("content", ""),
                    object.optString("data", ""),
                    object.optString("summary", ""));
            if (!text.isEmpty()) target.append(text);
        }
    }

    private static String extractGeneralError(String json) {
        try {
            JSONObject root = new JSONObject(json);
            Object errorValue = root.opt("error");
            if (errorValue instanceof JSONObject) {
                JSONObject error = (JSONObject) errorValue;
                return firstNonEmpty(
                        error.optString("message", ""),
                        error.optString("msg", ""),
                        error.optString("detail", ""));
            }
            if (errorValue instanceof String) return (String) errorValue;
            String type = root.optString("type", "");
            if ("error".equalsIgnoreCase(type)) {
                return firstNonEmpty(
                        root.optString("message", ""),
                        root.optString("detail", ""));
            }
        } catch (JSONException ignored) {
        }
        return "";
    }

    private static String contentValue(Object value) {
        if (value instanceof String) return (String) value;
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < array.length(); i++) {
                JSONObject part = array.optJSONObject(i);
                if (part != null) text.append(part.optString("text", ""));
            }
            return text.toString();
        }
        return "";
    }

    private static String extractResponsesReasoning(String json) {
        try {
            JSONObject root = new JSONObject(json);
            StringBuilder reasoning = new StringBuilder();
            appendReasoning(reasoning, root.opt("reasoning"));
            JSONArray output = root.optJSONArray("output");
            if (output == null) return reasoning.toString();
            for (int i = 0; i < output.length(); i++) {
                JSONObject item = output.optJSONObject(i);
                if (item == null) continue;
                String type = item.optString("type", "");
                if ("reasoning".equals(type)) {
                    appendReasoning(reasoning, item.opt("summary"));
                    appendReasoning(reasoning, item.opt("content"));
                }
            }
            return reasoning.toString();
        } catch (JSONException ignored) {
            return "";
        }
    }

    private static void configureStreamingPost(HttpURLConnection connection) throws IOException {
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(120_000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Accept", "text/event-stream, application/json");
    }

    private static void writeJson(HttpURLConnection connection, JSONObject body) throws IOException {
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(bytes.length);
        OutputStream output = connection.getOutputStream();
        output.write(bytes);
        output.flush();
        output.close();
    }

    private static String readSuccess(HttpURLConnection connection) throws IOException {
        ensureSuccess(connection);
        return readStream(connection.getInputStream());
    }

    private static void ensureSuccess(HttpURLConnection connection) throws IOException {
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new IOException("HTTP " + code + " · " + readStream(connection.getErrorStream()));
        }
    }

    private static String readStream(InputStream input) throws IOException {
        if (input == null) return "";
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        return new String(output.toByteArray(), StandardCharsets.UTF_8).trim();
    }

    private static String trimSlash(String value) {
        String result = value == null ? "" : value.trim();
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        return result;
    }

    private static String readableError(Exception error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) message = error.getClass().getSimpleName();
        if (message.length() > 240) message = message.substring(0, 240) + "…";
        return message;
    }

    private static final class TrackingCallback implements StreamCallback {
        private final StreamCallback delegate;
        private boolean output;

        TrackingCallback(StreamCallback delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onDelta(String delta) {
            if (delta != null && !delta.isEmpty()) output = true;
            delegate.onDelta(delta);
        }

        @Override
        public void onComplete() {
            delegate.onComplete();
        }

        @Override
        public void onError(String message) {
            delegate.onError(message);
        }

        boolean hasOutput() {
            return output;
        }
    }

    private static final class ParsedDelta {
        static final ParsedDelta EMPTY = new ParsedDelta("", "");

        final String content;
        final String reasoning;

        ParsedDelta(String content, String reasoning) {
            this.content = content == null ? "" : content;
            this.reasoning = reasoning == null ? "" : reasoning;
        }

        boolean isEmpty() {
            return content.isEmpty() && reasoning.isEmpty();
        }
    }

    private static final class StreamDisplayState {
        boolean emitted;
        boolean reasoningStarted;
        boolean answerStarted;

        void emit(ParsedDelta delta, boolean showReasoning,
                  StreamCallback callback) {
            if (delta == null) return;
            if (showReasoning && !delta.reasoning.isEmpty()) {
                if (!reasoningStarted) {
                    callback.onDelta(answerStarted
                            ? "\n\n【思考过程】\n"
                            : "【思考过程】\n");
                    reasoningStarted = true;
                }
                callback.onDelta(delta.reasoning);
                emitted = true;
            }
            if (!delta.content.isEmpty()) {
                if (showReasoning && reasoningStarted && !answerStarted) {
                    callback.onDelta("\n\n【回答】\n");
                }
                callback.onDelta(delta.content);
                answerStarted = true;
                emitted = true;
            }
        }
    }

    private static final class EndpointException extends IOException {
        final int status;

        EndpointException(int status, String endpoint, String body) {
            super("HTTP " + status + " · "
                    + (body == null || body.trim().isEmpty()
                    ? endpoint : body.trim()));
            this.status = status;
        }
    }

    private static final class ModelRank {
        final String id;
        final int priority;

        ModelRank(String id, int priority) {
            this.id = id;
            this.priority = priority;
        }
    }
}
