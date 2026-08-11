package app.miuix.tavern.model;

import org.json.JSONException;
import org.json.JSONObject;

public final class AppConfig {
    public static final String MODE_DIRECT_API = "direct_api";
    public static final String GENERATION_DIRECT_API = "direct_api";
    public static final String GENERATION_ACCOUNT = "account";
    public static final String GENERATION_LOCAL = "local";
    public static final String GENERATION_GPT_ACCOUNT = GENERATION_ACCOUNT;
    public static final String ACCOUNT_GPT = "gpt";
    public static final String ACCOUNT_COPILOT = "copilot";
    public static final String DIRECT_FORMAT_AUTO = "auto";
    public static final String DIRECT_FORMAT_CHAT = "chat_completions";
    public static final String DIRECT_FORMAT_RESPONSES = "responses";
    public static final String DIRECT_FORMAT_AZURE = "azure_openai";
    public static final String DIRECT_FORMAT_OLLAMA = "ollama";
    public static final String DIRECT_FORMAT_CLAUDE = "claude_messages";
    public static final String DIRECT_FORMAT_GEMINI = "gemini_generate_content";
    public static final String LOCAL_MODEL_QWEN_08 = "qwen3.5-0.8b-q4_0";
    public static final String LOCAL_MODEL_QWEN_2B = "qwen3.5-2b-q4_k_m";

    public String mode = MODE_DIRECT_API;
    public String generation = GENERATION_DIRECT_API;
    public String baseUrl = "https://api.openai.com/v1";
    public String directApiFormat = DIRECT_FORMAT_CHAT;
    public String model = "";
    public String gptModel = "gpt-5.4";
    public String copilotModel = "gpt-5.4";
    public String copilotEndpoint = "";
    public String githubOAuthClientId = "";
    public String localModel = LOCAL_MODEL_QWEN_08;
    public String accountProvider = ACCOUNT_GPT;
    public String persona = "你";
    public String personaAvatarPath = "";
    public String realtimeProvider = RealtimeProvider.QWEN;
    public String realtimeModel = "qwen3.5-omni-flash-realtime";
    public String realtimeEndpoint = "";
    public String realtimeVoice = "";
    public String realtimeExtra = "";
    public boolean reduceTransparency;
    public boolean showReasoning;
    public boolean webSearch = true;
    public boolean characterAutonomousMessages = true;
    public boolean groupAutonomousMessages = true;

    public JSONObject toJson() throws JSONException {
        return new JSONObject()
                .put("mode", mode)
                .put("generation", generation)
                .put("baseUrl", baseUrl)
                .put("directApiFormat", directApiFormat)
                .put("model", model)
                .put("gptModel", gptModel)
                .put("copilotModel", copilotModel)
                .put("copilotEndpoint", copilotEndpoint)
                .put("githubOAuthClientId", githubOAuthClientId)
                .put("localModel", localModel)
                .put("accountProvider", accountProvider)
                .put("apiBaseUrl", baseUrl)
                .put("apiModel", model)
                .put("persona", persona)
                .put("personaAvatarPath", personaAvatarPath)
                .put("realtimeProvider", realtimeProvider)
                .put("realtimeModel", realtimeModel)
                .put("realtimeEndpoint", realtimeEndpoint)
                .put("realtimeVoice", realtimeVoice)
                .put("realtimeExtra", realtimeExtra)
                .put("reduceTransparency", reduceTransparency)
                .put("showReasoning", showReasoning)
                .put("webSearch", webSearch)
                .put("characterAutonomousMessages", characterAutonomousMessages)
                .put("groupAutonomousMessages", groupAutonomousMessages);
    }

    public static AppConfig fromJson(JSONObject object) {
        AppConfig config = new AppConfig();
        config.mode = MODE_DIRECT_API;
        String storedGeneration =
                object.optString("generation", GENERATION_DIRECT_API);
        config.generation = "gpt_account".equals(storedGeneration)
                ? GENERATION_ACCOUNT : storedGeneration;
        if (!GENERATION_ACCOUNT.equals(config.generation)
                && !GENERATION_LOCAL.equals(config.generation)) {
            config.generation = GENERATION_DIRECT_API;
        }
        config.baseUrl = object.optString(
                "apiBaseUrl", "https://api.openai.com/v1").trim();
        if (config.baseUrl.isEmpty()) config.baseUrl = "https://api.openai.com/v1";
        config.directApiFormat = object.optString(
                "directApiFormat", DIRECT_FORMAT_CHAT);
        if (!DIRECT_FORMAT_CHAT.equals(config.directApiFormat)
                && !DIRECT_FORMAT_RESPONSES.equals(config.directApiFormat)
                && !DIRECT_FORMAT_AZURE.equals(config.directApiFormat)
                && !DIRECT_FORMAT_OLLAMA.equals(config.directApiFormat)
                && !DIRECT_FORMAT_CLAUDE.equals(config.directApiFormat)
                && !DIRECT_FORMAT_GEMINI.equals(config.directApiFormat)
                && !DIRECT_FORMAT_AUTO.equals(config.directApiFormat)) {
            config.directApiFormat = DIRECT_FORMAT_CHAT;
        }
        config.model = object.optString("apiModel", object.optString("model", ""));
        config.gptModel = object.optString("gptModel", "gpt-5.4");
        config.copilotModel = object.optString("copilotModel", "gpt-5.4").trim();
        if (config.copilotModel.isEmpty()
                || config.copilotModel.startsWith("claude-")) {
            config.copilotModel = "gpt-5.4";
        }
        config.copilotEndpoint = object.optString("copilotEndpoint", "").trim();
        config.githubOAuthClientId = object.optString(
                "githubOAuthClientId", "").trim();
        config.localModel = object.optString(
                "localModel", LOCAL_MODEL_QWEN_08);
        if (!LOCAL_MODEL_QWEN_2B.equals(config.localModel)) {
            config.localModel = LOCAL_MODEL_QWEN_08;
        }
        config.accountProvider = object.optString(
                "accountProvider", ACCOUNT_GPT);
        if ("claude".equals(config.accountProvider)) {
            config.accountProvider = ACCOUNT_COPILOT;
        } else if (!ACCOUNT_COPILOT.equals(config.accountProvider)) {
            config.accountProvider = ACCOUNT_GPT;
        }
        config.persona = object.optString("persona", "你");
        config.personaAvatarPath = object.optString("personaAvatarPath", "");
        config.realtimeProvider = RealtimeProvider.find(
                object.optString("realtimeProvider", RealtimeProvider.QWEN)).id;
        config.realtimeModel = RealtimeProvider.normalizeModel(
                config.realtimeProvider,
                object.optString("realtimeModel", ""));
        config.realtimeEndpoint = object.optString("realtimeEndpoint", "").trim();
        config.realtimeVoice = object.optString("realtimeVoice", "").trim();
        config.realtimeExtra = object.optString("realtimeExtra", "").trim();
        config.reduceTransparency = object.optBoolean("reduceTransparency", false);
        config.showReasoning = object.optBoolean("showReasoning", false);
        config.webSearch = object.optBoolean("webSearch", true);
        config.characterAutonomousMessages =
                object.optBoolean("characterAutonomousMessages", true);
        config.groupAutonomousMessages =
                object.optBoolean("groupAutonomousMessages", true);
        return config;
    }

    public String readableMode() {
        if (GENERATION_LOCAL.equals(generation)) {
            return LOCAL_MODEL_QWEN_2B.equals(localModel)
                    ? "本地 · Qwen3.5-2B" : "本地 · Qwen3.5-0.8B";
        }
        if (GENERATION_ACCOUNT.equals(generation)) {
            return ACCOUNT_COPILOT.equals(accountProvider)
                    ? "账户 · GitHub Copilot" : "账户 · GPT";
        }
        return "直连 API";
    }
}
