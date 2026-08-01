package app.miuix.tavern.network;

import app.miuix.tavern.model.RealtimeProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** Loads the current realtime-capable model IDs from providers or adapters. */
public final class RealtimeModelClient {
    public interface Callback {
        void onSuccess(List<String> models);

        void onError(String message);
    }

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .dns(ReliableDns.INSTANCE)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    private RealtimeModelClient() {
    }

    public static void fetch(
            RealtimeProvider.Spec provider,
            String configuredEndpoint,
            String credential,
            Callback callback) {
        EXECUTOR.execute(() -> {
            try {
                callback.onSuccess(fetchSync(
                        provider,
                        configuredEndpoint == null ? "" : configuredEndpoint.trim(),
                        credential == null ? "" : credential.trim()));
            } catch (Exception error) {
                String message = error.getMessage();
                if (message == null || message.trim().isEmpty()) {
                    message = error.getClass().getSimpleName();
                }
                if (message.length() > 180) {
                    message = message.substring(0, 180) + "…";
                }
                callback.onError(message);
            }
        });
    }

    private static List<String> fetchSync(
            RealtimeProvider.Spec provider,
            String configuredEndpoint,
            String credential) throws Exception {
        credential = cleanCredential(credential);
        if (provider.credentialRequired && credential.isEmpty()) {
            throw new IOException("请先填写该厂商的 API Key");
        }
        if (RealtimeProvider.GEMINI.equals(provider.id)) {
            return fetchGeminiModels(credential);
        }
        List<String> endpoints = modelEndpoints(provider, configuredEndpoint);
        if (endpoints.isEmpty()) {
            throw new IOException("该厂商没有公开的模型目录，当前使用内置列表");
        }
        IOException lastError = null;
        for (String endpoint : endpoints) {
            Request.Builder request = new Request.Builder()
                    .url(endpoint)
                    .get()
                    .header("Accept", "application/json")
                    .header("User-Agent", "MiuTavern/0.9 Android");
            if (!credential.isEmpty()
                    && !RealtimeProvider.GEMINI.equals(provider.id)) {
                request.header("Authorization", "Bearer " + credential);
            }
            try (Response response = HTTP.newCall(request.build()).execute()) {
                ResponseBody body = response.body();
                String json = body == null ? "" : body.string();
                if (!response.isSuccessful()) {
                    String detail = json.trim();
                    if (detail.length() > 140) detail = detail.substring(0, 140) + "…";
                    IOException error = new IOException(
                            "HTTP " + response.code()
                                    + (detail.isEmpty() ? "" : " · " + detail));
                    if (response.code() == 404 || response.code() == 405) {
                        lastError = error;
                        continue;
                    }
                    throw error;
                }
                List<String> models = parseModels(provider.id, json);
                if (!models.isEmpty()) return models;
                lastError = new IOException("厂商目录没有返回实时语音模型");
            }
        }
        if (lastError != null) throw lastError;
        throw new IOException("无法读取厂商模型目录");
    }

    private static List<String> modelEndpoints(
            RealtimeProvider.Spec provider,
            String configuredEndpoint) throws Exception {
        List<String> result = new ArrayList<>();
        switch (provider.id) {
            case RealtimeProvider.QWEN:
                result.add("https://dashscope.aliyuncs.com/compatible-mode/v1/models");
                break;
            case RealtimeProvider.ZHIPU:
                result.add("https://open.bigmodel.cn/api/paas/v4/models");
                break;
            case RealtimeProvider.OPENAI:
                result.add("https://api.openai.com/v1/models");
                break;
            case RealtimeProvider.XAI:
                result.add("https://api.x.ai/v1/models");
                break;
            case RealtimeProvider.MISTRAL:
                result.add("https://api.mistral.ai/v1/models");
                break;
            default:
                if (provider.adapterRequired && !configuredEndpoint.isEmpty()) {
                    result.addAll(adapterModelEndpoints(
                            configuredEndpoint, provider.id));
                }
                break;
        }
        return result;
    }

    private static List<String> fetchGeminiModels(String credential) throws Exception {
        Set<String> models = new LinkedHashSet<>();
        String pageToken = "";
        for (int page = 0; page < 20; page++) {
            StringBuilder endpoint = new StringBuilder(
                    "https://generativelanguage.googleapis.com/v1beta/models?pageSize=1000");
            if (!pageToken.isEmpty()) {
                endpoint.append("&pageToken=")
                        .append(URLEncoder.encode(pageToken, "UTF-8"));
            }
            Request request = new Request.Builder()
                    .url(endpoint.toString())
                    .get()
                    .header("Accept", "application/json")
                    .header("User-Agent", "MiuTavern/0.9 Android")
                    .header("x-goog-api-key", credential)
                    .build();
            try (Response response = HTTP.newCall(request).execute()) {
                ResponseBody body = response.body();
                String json = body == null ? "" : body.string();
                if (!response.isSuccessful()) {
                    String detail = json.trim();
                    if (detail.length() > 140) detail = detail.substring(0, 140) + "…";
                    throw new IOException("HTTP " + response.code()
                            + (detail.isEmpty() ? "" : " · " + detail));
                }
                JSONObject root = new JSONObject(json);
                models.addAll(parseModels(RealtimeProvider.GEMINI, json));
                pageToken = root.optString("nextPageToken", "").trim();
                if (pageToken.isEmpty()) break;
            }
        }
        if (models.isEmpty()) {
            throw new IOException("Gemini 厂商目录没有返回可用的实时语音模型");
        }
        List<String> result = new ArrayList<>(models);
        Collections.sort(result, String.CASE_INSENSITIVE_ORDER);
        return result;
    }

    private static String cleanCredential(String value) {
        String credential = value == null ? "" : value.trim();
        if (credential.regionMatches(true, 0, "Bearer ", 0, 7)) {
            credential = credential.substring(7).trim();
        }
        return credential;
    }

    private static List<String> adapterModelEndpoints(
            String endpoint, String provider) throws Exception {
        URI source = new URI(endpoint);
        String scheme = source.getScheme();
        if ("wss".equalsIgnoreCase(scheme)) scheme = "https";
        else if ("ws".equalsIgnoreCase(scheme)) scheme = "http";
        if (!"http".equalsIgnoreCase(scheme)
                && !"https".equalsIgnoreCase(scheme)) {
            throw new IOException("适配器地址必须使用 WSS 或 HTTPS");
        }
        String origin = new URI(
                scheme, source.getUserInfo(), source.getHost(), source.getPort(),
                "", null, null).toString();
        String query = "provider=" + URLEncoder.encode(provider, "UTF-8");
        List<String> result = new ArrayList<>();
        result.add(origin + "/models?" + query);
        String path = source.getPath() == null ? "" : source.getPath();
        int slash = path.lastIndexOf('/');
        if (slash > 0) {
            String parent = path.substring(0, slash);
            String nested = new URI(
                    scheme, source.getUserInfo(), source.getHost(), source.getPort(),
                    parent + "/models", query, null).toString();
            if (!result.contains(nested)) result.add(nested);
        }
        return result;
    }

    private static List<String> parseModels(String provider, String json)
            throws Exception {
        JSONObject root = new JSONObject(json);
        JSONArray array = firstArray(
                root.optJSONArray("data"),
                root.optJSONArray("models"),
                root.optJSONArray("result"),
                root.optJSONArray("list"));
        if (array == null) return Collections.emptyList();
        Set<String> models = new LinkedHashSet<>();
        for (int i = 0; i < array.length(); i++) {
            Object value = array.opt(i);
            String id = "";
            JSONObject card = value instanceof JSONObject
                    ? (JSONObject) value : null;
            if (value instanceof String) {
                id = ((String) value).trim();
            } else if (card != null) {
                id = firstNonEmpty(
                        card.optString("id", ""),
                        card.optString("name", ""),
                        card.optString("model", ""),
                        card.optString("model_id", ""),
                        card.optString("baseModelId", ""));
            }
            if (id.startsWith("models/")) id = id.substring("models/".length());
            if (!id.isEmpty() && isRealtimeModel(provider, id, card)) {
                models.add(id);
            }
        }
        List<String> result = new ArrayList<>(models);
        Collections.sort(result, String.CASE_INSENSITIVE_ORDER);
        return result;
    }

    private static boolean isRealtimeModel(
            String provider, String id, JSONObject card) {
        String normalized = id.toLowerCase(Locale.ROOT);
        if (RealtimeProvider.GEMINI.equals(provider)) {
            JSONArray methods = card == null
                    ? null : card.optJSONArray("supportedGenerationMethods");
            if (methods != null) {
                for (int i = 0; i < methods.length(); i++) {
                    String method = methods.optString(i, "")
                            .toLowerCase(Locale.ROOT);
                    if (method.contains("bidigeneratecontent")) return true;
                }
            }
            return normalized.contains("live")
                    || normalized.contains("realtime")
                    || normalized.contains("native-audio");
        }
        if (RealtimeProvider.XAI.equals(provider)) {
            return normalized.contains("voice") || normalized.contains("realtime");
        }
        if (RealtimeProvider.MISTRAL.equals(provider)) {
            return normalized.contains("voxtral")
                    && (normalized.contains("realtime")
                    || normalized.contains("tts")
                    || normalized.contains("audio"));
        }
        if (RealtimeProvider.OPENAI.equals(provider)
                || RealtimeProvider.QWEN.equals(provider)
                || RealtimeProvider.ZHIPU.equals(provider)) {
            return normalized.contains("realtime");
        }
        return true;
    }

    private static JSONArray firstArray(JSONArray... arrays) {
        for (JSONArray array : arrays) {
            if (array != null) return array;
        }
        return null;
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return "";
    }
}
