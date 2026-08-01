package app.miuix.tavern.model;

import android.text.TextUtils;

public final class RealtimeProvider {
    public static final String QWEN = "qwen";
    public static final String VOLCENGINE = "volcengine";
    public static final String ZHIPU = "zhipu";
    public static final String BAIDU = "baidu";
    public static final String TRTC = "trtc";
    public static final String MINIMAX = "minimax";
    public static final String OPENAI = "openai";
    public static final String GEMINI = "gemini";
    public static final String XAI = "xai";
    public static final String AMAZON_NOVA = "amazon_nova";
    public static final String ELEVENLABS = "elevenlabs";
    public static final String MISTRAL = "mistral";

    public static final int OPENAI_MODERN = 1;
    public static final int OPENAI_LEGACY = 2;
    public static final int GEMINI_LIVE = 3;
    public static final int ELEVEN_AGENTS = 4;
    public static final int REALTIME_ADAPTER = 5;

    private static final Spec[] SPECS = {
            new Spec(
                    QWEN, "Qwen Realtime", OPENAI_LEGACY,
                    new String[]{
                            "qwen3.5-omni-plus-realtime",
                            "qwen3.5-omni-flash-realtime"
                    },
                    "",
                    "Tina", 16_000, 24_000, true, false,
                    "可填写百炼工作空间专属 WSS；Plus 支持原生联网搜索。"),
            new Spec(
                    VOLCENGINE, "Doubao / Volcengine S2S", REALTIME_ADAPTER,
                    new String[]{"S2S-O", "S2S-SC"}, "", "", 16_000, 24_000,
                    false, true,
                    "火山 S2S 使用二进制事件协议和服务端鉴权，请填写实时适配器 WSS。"),
            new Spec(
                    ZHIPU, "Zhipu GLM-Realtime", OPENAI_LEGACY,
                    new String[]{"glm-realtime-flash", "glm-realtime-air"},
                    "wss://open.bigmodel.cn/api/paas/v4/realtime", "tongtong",
                    16_000, 24_000, true, false,
                    "支持 GLM-Realtime Flash / Air，并默认启用服务端搜索。"),
            new Spec(
                    BAIDU, "Baidu Realtime", OPENAI_LEGACY,
                    new String[]{
                            "audio-mini-realtime-near", "audio-mini-realtime-far",
                            "audio-realtime-near", "audio-realtime-far"
                    },
                    "wss://aip.baidubce.com/ws/2.0/speech/v1/realtime", "default",
                    16_000, 24_000, true, false,
                    "near / far 分别适合近场与远场拾音。"),
            new Spec(
                    TRTC, "TRTC AI Conversation", REALTIME_ADAPTER,
                    new String[]{"trtc-ai-conversation"}, "", "", 16_000, 24_000,
                    false, true,
                    "UserSig 与 StartAIConversation 必须由后端签发；请填写 TRTC 实时适配器 WSS。"),
            new Spec(
                    MINIMAX, "MiniMax Speech + M2.7", REALTIME_ADAPTER,
                    new String[]{
                            "MiniMax-M2.7 + speech-2.8-turbo",
                            "MiniMax-M2.7 + speech-2.8-hd"
                    },
                    "", "", 16_000, 24_000, false, true,
                    "将语音识别、M2.7 对话与 Speech 2.8 合成为双向实时流。"),
            new Spec(
                    OPENAI, "OpenAI Realtime", OPENAI_MODERN,
                    new String[]{
                            "gpt-realtime-2.1", "gpt-realtime-2.1-mini",
                            "gpt-realtime-1.5"
                    },
                    "wss://api.openai.com/v1/realtime", "marin",
                    24_000, 24_000, true, false,
                    "直接使用 OpenAI API Key，不再请求短期令牌服务。"),
            new Spec(
                    GEMINI, "Google Gemini Live", GEMINI_LIVE,
                    new String[]{"gemini-3.1-flash-live-preview"},
                    "wss://generativelanguage.googleapis.com/ws/"
                            + "google.ai.generativelanguage.v1beta.GenerativeService."
                            + "BidiGenerateContent",
                    "", 16_000, 24_000, true, false,
                    "使用 Gemini Live 原生双向 WebSocket。"),
            new Spec(
                    XAI, "xAI Voice Agent", OPENAI_MODERN,
                    new String[]{
                            "grok-voice-latest", "grok-voice-think-fast-2.0",
                            "grok-voice-think-fast-1.0"
                    },
                    "wss://api.x.ai/v1/realtime", "eve",
                    24_000, 24_000, true, false,
                    "使用 xAI Voice Agent API 的 Realtime 兼容事件。"),
            new Spec(
                    AMAZON_NOVA, "Amazon Nova 2 Sonic", REALTIME_ADAPTER,
                    new String[]{"amazon.nova-2-sonic-v1:0"}, "", "", 16_000, 24_000,
                    false, true,
                    "Bedrock 双向 EventStream 需要 SigV4；请填写 Nova 实时适配器 WSS。"),
            new Spec(
                    ELEVENLABS, "ElevenLabs ElevenAgents", ELEVEN_AGENTS,
                    new String[]{"elevenagents"},
                    "wss://api.elevenlabs.io/v1/convai/conversation", "",
                    16_000, 16_000, false, false,
                    "公开 Agent 在“音色 / Agent ID”填写 ID；私有 Agent 将已签名 WSS 填入加密凭据栏。"),
            new Spec(
                    MISTRAL, "Mistral Voxtral Voice Pipeline", REALTIME_ADAPTER,
                    new String[]{
                            "voxtral-mini-transcribe-realtime-2602 + "
                                    + "mistral-small-2603 + voxtral-mini-tts-2603"
                    },
                    "", "", 16_000, 24_000, false, true,
                    "由适配器串联 Voxtral Realtime、LLM 与 Voxtral TTS。")
    };

    private RealtimeProvider() {
    }

    public static Spec find(String id) {
        for (Spec spec : SPECS) {
            if (spec.id.equals(id)) return spec;
        }
        return SPECS[0];
    }

    public static Spec[] all() {
        return SPECS.clone();
    }

    public static String normalizeModel(String provider, String model) {
        Spec spec = find(provider);
        if (!TextUtils.isEmpty(model) && !model.trim().isEmpty()) {
            return model.trim();
        }
        return spec.models[0];
    }

    public static final class Spec {
        public final String id;
        public final String displayName;
        public final int protocol;
        public final String[] models;
        public final String defaultEndpoint;
        public final String defaultVoice;
        public final int inputSampleRate;
        public final int outputSampleRate;
        public final boolean credentialRequired;
        public final boolean adapterRequired;
        public final String note;

        Spec(
                String id,
                String displayName,
                int protocol,
                String[] models,
                String defaultEndpoint,
                String defaultVoice,
                int inputSampleRate,
                int outputSampleRate,
                boolean credentialRequired,
                boolean adapterRequired,
                String note) {
            this.id = id;
            this.displayName = displayName;
            this.protocol = protocol;
            this.models = models;
            this.defaultEndpoint = defaultEndpoint;
            this.defaultVoice = defaultVoice;
            this.inputSampleRate = inputSampleRate;
            this.outputSampleRate = outputSampleRate;
            this.credentialRequired = credentialRequired;
            this.adapterRequired = adapterRequired;
            this.note = note;
        }
    }
}
