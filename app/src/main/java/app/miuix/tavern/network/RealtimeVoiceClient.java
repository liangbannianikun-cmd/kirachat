package app.miuix.tavern.network;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import app.miuix.tavern.model.AppConfig;
import app.miuix.tavern.model.CharacterCard;
import app.miuix.tavern.model.ChatMessage;
import app.miuix.tavern.model.RealtimeProvider;
import app.miuix.tavern.util.LocaleUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public final class RealtimeVoiceClient {
    private static final String TAG = "ChengyuRealtime";
    private static final int MAX_RECONNECT_ATTEMPTS = 2;
    private static final long SESSION_READY_TIMEOUT_MS = 12_000L;
    public interface Listener {
        void onState(String state);

        void onTranscript(String transcript);

        void onError(String error);

        void onEnded();
    }

    private final Context context;
    private final AppConfig config;
    private final String credential;
    private final CharacterCard character;
    private final List<ChatMessage> recentMessages;
    private final Listener listener;
    private final RealtimeProvider.Spec provider;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build();
    private final LinkedBlockingQueue<byte[]> remoteAudio =
            new LinkedBlockingQueue<>(180);
    private final AtomicBoolean ended = new AtomicBoolean();
    private final StringBuilder remoteTranscript = new StringBuilder();

    private volatile boolean running;
    private volatile boolean userStopped;
    private volatile boolean sessionReady;
    private volatile boolean audioStarted;
    private volatile boolean muted;
    private volatile boolean speaker;
    private WebSocket socket;
    private long connectionGeneration;
    private int reconnectAttempts;
    private String geminiResumeHandle = "";
    private Runnable sessionReadyTimeout;
    private AudioRecord recorder;
    private AudioTrack player;
    private Thread captureThread;
    private Thread playbackThread;
    private AudioManager audioManager;
    private int previousAudioMode = AudioManager.MODE_NORMAL;
    private boolean previousSpeaker;
    private final AudioManager.OnAudioFocusChangeListener focusListener = focus -> {
    };

    public RealtimeVoiceClient(
            Context context,
            AppConfig config,
            String credential,
            CharacterCard character,
            List<ChatMessage> recentMessages,
            Listener listener) {
        this.context = context.getApplicationContext();
        this.config = config;
        this.credential = credential == null ? "" : credential.trim();
        this.character = character;
        this.recentMessages = recentMessages;
        this.listener = listener;
        this.provider = RealtimeProvider.find(config.realtimeProvider);
    }

    public static String displayLabel(AppConfig config) {
        RealtimeProvider.Spec spec = RealtimeProvider.find(
                config.realtimeProvider);
        return spec.displayName + " · "
                + RealtimeProvider.normalizeModel(spec.id, config.realtimeModel);
    }

    public static String configurationError(AppConfig config, String credential) {
        RealtimeProvider.Spec spec = RealtimeProvider.find(
                config.realtimeProvider);
        String endpoint = config.realtimeEndpoint == null
                ? "" : config.realtimeEndpoint.trim();
        String secret = credential == null ? "" : credential.trim();
        if (spec.adapterRequired && endpoint.isEmpty()) {
            return spec.displayName + " 需要填写实时适配器 WSS 地址";
        }
        if (spec.defaultEndpoint.isEmpty() && endpoint.isEmpty()) {
            return spec.displayName + " 需要填写厂商 WSS 地址";
        }
        if (spec.credentialRequired && secret.isEmpty()) {
            return spec.displayName + " 需要 API Key";
        }
        if (RealtimeProvider.ELEVENLABS.equals(spec.id)
                && !secret.startsWith("wss://")
                && !endpoint.contains("agent_id=")
                && TextUtils.isEmpty(config.realtimeVoice)) {
            return "ElevenAgents 需要 Agent ID 或已签名 WSS 地址";
        }
        return "";
    }

    public void start() {
        if (running) return;
        String error = configurationError(config, credential);
        if (!error.isEmpty()) {
            running = true;
            fail(error);
            return;
        }
        running = true;
        userStopped = false;
        sessionReady = false;
        audioStarted = false;
        reconnectAttempts = 0;
        ended.set(false);
        connect();
    }

    public void setMuted(boolean muted) {
        this.muted = muted;
    }

    public boolean isMuted() {
        return muted;
    }

    public void setSpeaker(boolean speaker) {
        this.speaker = speaker;
        AudioManager manager = audioManager;
        if (manager != null) manager.setSpeakerphoneOn(speaker);
    }

    public boolean isSpeaker() {
        return speaker;
    }

    public void stop() {
        boolean wasRunning = running;
        userStopped = true;
        running = false;
        cancelSessionReadyTimeout();
        WebSocket activeSocket;
        synchronized (this) {
            connectionGeneration++;
            activeSocket = socket;
            socket = null;
        }
        if (activeSocket != null) activeSocket.close(1000, "user_hangup");
        stopAudio();
        if (wasRunning) emitState("通话已结束");
        emitEnded();
        http.dispatcher().executorService().shutdown();
    }

    private void connect() {
        try {
            String model = RealtimeProvider.normalizeModel(
                    provider.id, config.realtimeModel);
            final long generation;
            synchronized (this) {
                if (!running || userStopped) return;
                generation = ++connectionGeneration;
                sessionReady = false;
                audioStarted = false;
            }
            emitState(reconnectAttempts == 0
                    ? "正在连接 " + provider.displayName + "…"
                    : "正在重新连接 " + provider.displayName + "…");
            Request.Builder request = new Request.Builder()
                    .url(resolveEndpoint(model));
            applyAuthorization(request);
            WebSocket newSocket = http.newWebSocket(
                    request.build(), new WebSocketListener() {
                @Override
                public void onOpen(WebSocket webSocket, Response response) {
                    if (!isCurrentConnection(generation)) {
                        webSocket.close(1000, "cancelled");
                        return;
                    }
                    try {
                        String sessionStart = buildSessionStart(model).toString();
                        Log.i(TAG, "WebSocket connected: " + provider.id);
                        boolean queued = webSocket.send(sessionStart);
                        if (!queued) throw new IllegalStateException("会话配置发送失败");
                        emitState("正在初始化实时会话…");
                        scheduleSessionReadyTimeout(generation);
                    } catch (Exception error) {
                        fail("无法启动音频：" + readableError(error));
                    }
                }

                @Override
                public void onMessage(WebSocket webSocket, String text) {
                    if (isCurrentConnection(generation)) handleEvent(text);
                }

                @Override
                public void onMessage(WebSocket webSocket, ByteString bytes) {
                    if (!isCurrentConnection(generation)) return;
                    Log.d(TAG, "Binary realtime event: provider=" + provider.id
                            + ", bytes=" + bytes.size());
                    if (provider.protocol == RealtimeProvider.GEMINI_LIVE) {
                        String json = bytes.utf8();
                        if (json.startsWith("{")) handleEvent(json);
                    } else if (sessionReady && provider.adapterRequired) {
                        enqueueAudio(bytes.toByteArray());
                    }
                }

                @Override
                public void onClosing(WebSocket webSocket, int code, String reason) {
                    webSocket.close(code, reason);
                }

                @Override
                public void onClosed(WebSocket webSocket, int code, String reason) {
                    if (!isCurrentConnection(generation)) return;
                    Log.w(TAG, "WebSocket closed: provider=" + provider.id
                            + ", code=" + code + ", reason=" + compact(reason));
                    String detail = closeDetail(code, reason);
                    if (code == 1007 || code == 1008) {
                        fail(provider.displayName + " 拒绝了实时会话" + detail);
                    } else {
                        reconnectAfterDisconnect(webSocket, generation, detail);
                    }
                }

                @Override
                public void onFailure(
                        WebSocket webSocket, Throwable error, Response response) {
                    if (!isCurrentConnection(generation)) return;
                    Log.w(TAG, "WebSocket failure: provider=" + provider.id
                            + ", error=" + readableError(error));
                    String detail = response == null
                            ? "" : " · HTTP " + response.code();
                    if (response != null && response.code() >= 400
                            && response.code() < 500) {
                        fail("实时连接被拒绝：" + readableError(error) + detail);
                    } else {
                        reconnectAfterDisconnect(webSocket, generation,
                                "：" + readableError(error) + detail);
                    }
                }
            });
            synchronized (this) {
                if (generation == connectionGeneration && running && !userStopped) {
                    socket = newSocket;
                } else {
                    newSocket.close(1000, "cancelled");
                }
            }
        } catch (Exception error) {
            fail("实时连接配置错误：" + readableError(error));
        }
    }

    private synchronized boolean isCurrentConnection(long generation) {
        return running && !userStopped && generation == connectionGeneration;
    }

    private void scheduleSessionReadyTimeout(long generation) {
        cancelSessionReadyTimeout();
        Runnable timeout = () -> {
            if (!isCurrentConnection(generation) || sessionReady) return;
            WebSocket activeSocket = socket;
            reconnectAfterDisconnect(activeSocket, generation,
                    "：服务未确认会话初始化");
        };
        sessionReadyTimeout = timeout;
        main.postDelayed(timeout, SESSION_READY_TIMEOUT_MS);
    }

    private void cancelSessionReadyTimeout() {
        Runnable timeout = sessionReadyTimeout;
        sessionReadyTimeout = null;
        if (timeout != null) main.removeCallbacks(timeout);
    }

    private void markSessionReady() {
        synchronized (this) {
            if (!running || userStopped || sessionReady || audioStarted) return;
            sessionReady = true;
            audioStarted = true;
            reconnectAttempts = 0;
        }
        cancelSessionReadyTimeout();
        try {
            startAudio();
            emitState("通话中 · 正在聆听");
        } catch (Exception error) {
            synchronized (this) {
                audioStarted = false;
                sessionReady = false;
            }
            fail("无法启动音频：" + readableError(error));
        }
    }

    private void reconnectAfterDisconnect(
            WebSocket disconnected, long generation, String detail) {
        final int attempt;
        final long reconnectGeneration;
        synchronized (this) {
            if (!running || userStopped || generation != connectionGeneration) return;
            connectionGeneration++;
            reconnectGeneration = connectionGeneration;
            if (socket == disconnected) socket = null;
            sessionReady = false;
            audioStarted = false;
            attempt = ++reconnectAttempts;
        }
        cancelSessionReadyTimeout();
        if (disconnected != null) disconnected.cancel();
        stopAudio();
        if (attempt > MAX_RECONNECT_ATTEMPTS) {
            fail("实时连接反复中断" + detail);
            return;
        }
        emitState("连接中断，正在重连（" + attempt + "/"
                + MAX_RECONNECT_ATTEMPTS + "）…");
        main.postDelayed(() -> {
            synchronized (RealtimeVoiceClient.this) {
                if (!running || userStopped
                        || reconnectGeneration != connectionGeneration) return;
            }
            connect();
        }, attempt == 1 ? 500L : 1_200L);
    }

    private static String closeDetail(int code, String reason) {
        String cleanReason = compact(reason);
        return "（" + code + (cleanReason.isEmpty() ? "" : " · " + cleanReason) + "）";
    }

    private String resolveEndpoint(String model) throws Exception {
        if (RealtimeProvider.ELEVENLABS.equals(provider.id)
                && credential.startsWith("wss://")) {
            return credential;
        }
        String endpoint = TextUtils.isEmpty(config.realtimeEndpoint)
                ? provider.defaultEndpoint : config.realtimeEndpoint.trim();
        if (RealtimeProvider.QWEN.equals(provider.id)
                || RealtimeProvider.BAIDU.equals(provider.id)
                || RealtimeProvider.OPENAI.equals(provider.id)
                || RealtimeProvider.XAI.equals(provider.id)) {
            endpoint = appendQuery(endpoint, "model", model);
        } else if (RealtimeProvider.GEMINI.equals(provider.id)) {
            endpoint = appendQuery(endpoint, "key", credential);
        } else if (RealtimeProvider.ELEVENLABS.equals(provider.id)
                && !endpoint.contains("agent_id=")) {
            endpoint = appendQuery(endpoint, "agent_id", config.realtimeVoice.trim());
        }
        return endpoint;
    }

    private static String appendQuery(String url, String name, String value)
            throws Exception {
        if (url.contains(name + "=")) return url;
        return url + (url.contains("?") ? "&" : "?")
                + name + "=" + URLEncoder.encode(value, "UTF-8");
    }

    private void applyAuthorization(Request.Builder request) {
        if (credential.isEmpty()
                || RealtimeProvider.GEMINI.equals(provider.id)
                || RealtimeProvider.ELEVENLABS.equals(provider.id)) {
            return;
        }
        if (RealtimeProvider.ZHIPU.equals(provider.id)) {
            request.header("Authorization", credential);
        } else {
            request.header("Authorization", credential.startsWith("Bearer ")
                    ? credential : "Bearer " + credential);
        }
        if (RealtimeProvider.OPENAI.equals(provider.id)) {
            request.header("OpenAI-Beta", "realtime=v1");
        }
    }

    private JSONObject buildSessionStart(String model) throws JSONException {
        if (provider.protocol == RealtimeProvider.GEMINI_LIVE) {
            return buildGeminiSetup(model);
        }
        if (provider.protocol == RealtimeProvider.ELEVEN_AGENTS) {
            return buildElevenStart();
        }
        if (provider.protocol == RealtimeProvider.REALTIME_ADAPTER) {
            return buildAdapterStart(model);
        }
        if (provider.protocol == RealtimeProvider.OPENAI_LEGACY) {
            return buildLegacySession(model);
        }
        return buildModernSession(model);
    }

    private JSONObject buildModernSession(String model) throws JSONException {
        JSONObject input = new JSONObject()
                .put("format", new JSONObject()
                        .put("type", "audio/pcm")
                        .put("rate", provider.inputSampleRate))
                .put("turn_detection", new JSONObject()
                        .put("type", "server_vad"));
        JSONObject output = new JSONObject()
                .put("format", new JSONObject().put("type", "audio/pcm"))
                .put("voice", effectiveVoice());
        JSONObject session = new JSONObject()
                .put("instructions", buildInstructions())
                .put("audio", new JSONObject()
                        .put("input", input)
                        .put("output", output));
        if (RealtimeProvider.XAI.equals(provider.id)) {
            session.put("voice", effectiveVoice())
                    .put("turn_detection", new JSONObject()
                            .put("type", "server_vad"));
            if (config.webSearch) {
                session.put("tools", new JSONArray().put(
                        new JSONObject().put("type", "web_search")));
            }
        } else {
            session.put("type", "realtime")
                    .put("model", model)
                    .put("output_modalities", new JSONArray().put("audio"));
        }
        return new JSONObject()
                .put("type", "session.update")
                .put("session", session);
    }

    private JSONObject buildLegacySession(String model) throws JSONException {
        boolean qwen = RealtimeProvider.QWEN.equals(provider.id);
        JSONObject session = new JSONObject()
                .put("model", model)
                .put("modalities", new JSONArray().put("text").put("audio"))
                .put("instructions", buildInstructions())
                .put("voice", effectiveVoice())
                .put("input_audio_format", qwen ? "pcm" : "pcm16")
                .put("output_audio_format",
                        RealtimeProvider.ZHIPU.equals(provider.id)
                                ? "pcm" : (qwen ? "pcm" : "pcm16"))
                .put("turn_detection", new JSONObject()
                        .put("type", "server_vad"));
        if (qwen) {
            session.put("input_audio_transcription", new JSONObject()
                    .put("model", "qwen3-asr-flash-realtime"));
            session.put("enable_search", config.webSearch);
            if (config.webSearch) {
                session.put("search_options", new JSONObject()
                        .put("enable_source", true));
            }
        }
        if (RealtimeProvider.ZHIPU.equals(provider.id)) {
            session.put("beta_fields", new JSONObject()
                    .put("chat_mode", "audio")
                    .put("tts_source", "e2e")
                    .put("auto_search", config.webSearch));
        }
        return new JSONObject()
                .put("type", "session.update")
                .put("session", session);
    }

    private JSONObject buildGeminiSetup(String model) throws JSONException {
        JSONObject generationConfig = new JSONObject()
                .put("responseModalities", new JSONArray().put("AUDIO"));
        if (!TextUtils.isEmpty(config.realtimeVoice)) {
            generationConfig.put("speechConfig", new JSONObject()
                    .put("voiceConfig", new JSONObject()
                            .put("prebuiltVoiceConfig", new JSONObject()
                                    .put("voiceName", config.realtimeVoice.trim()))));
        }
        JSONObject setup = new JSONObject()
                .put("model", "models/" + model)
                .put("generationConfig", generationConfig)
                .put("systemInstruction", new JSONObject()
                        .put("parts", new JSONArray().put(
                                new JSONObject().put("text", buildInstructions()))))
                .put("inputAudioTranscription", new JSONObject())
                .put("outputAudioTranscription", new JSONObject());
        if (!TextUtils.isEmpty(geminiResumeHandle)) {
            setup.put("sessionResumption",
                    new JSONObject().put("handle", geminiResumeHandle));
        }
        return new JSONObject().put("setup", setup);
    }

    private JSONObject buildElevenStart() throws JSONException {
        JSONObject override = new JSONObject()
                .put("agent", new JSONObject()
                        .put("prompt", new JSONObject()
                                .put("prompt", buildInstructions())));
        JSONObject start = new JSONObject()
                .put("type", "conversation_initiation_client_data")
                .put("conversation_config_override", override)
                .put("dynamic_variables", new JSONObject()
                        .put("chengyu_character", character.name)
                        .put("chengyu_user", config.persona));
        return start;
    }

    private JSONObject buildAdapterStart(String model) throws JSONException {
        JSONObject metadata = new JSONObject();
        if (!TextUtils.isEmpty(config.realtimeExtra)) {
            try {
                metadata = new JSONObject(config.realtimeExtra.trim());
            } catch (JSONException ignored) {
                metadata.put("raw", config.realtimeExtra.trim());
            }
        }
        return new JSONObject()
                .put("type", "session.start")
                .put("provider", provider.id)
                .put("model", model)
                .put("voice", effectiveVoice())
                .put("instructions", buildInstructions())
                .put("input_audio", new JSONObject()
                        .put("format", "pcm_s16le")
                        .put("sample_rate", provider.inputSampleRate)
                        .put("channels", 1))
                .put("output_audio", new JSONObject()
                        .put("format", "pcm_s16le")
                        .put("sample_rate", provider.outputSampleRate)
                        .put("channels", 1))
                .put("metadata", metadata);
    }

    private String effectiveVoice() {
        return TextUtils.isEmpty(config.realtimeVoice)
                ? provider.defaultVoice : config.realtimeVoice.trim();
    }

    private String buildInstructions() {
        StringBuilder prompt = new StringBuilder();
        boolean traditional = LocaleUtils.isTraditionalChinese(Locale.getDefault());
        if (traditional) {
            prompt.append("你正在與使用者進行一對一即時語音通話。")
                    .append("預設使用自然的繁體中文交流，除非使用者明確要求切換語言。")
                    .append("始終扮演角色「").append(character.name).append("」，")
                    .append("以自然、簡潔的口語回應，不要朗讀舞臺說明或 Markdown。")
                    .append("使用者的稱呼是「").append(config.persona).append("」。");
        } else {
            prompt.append("你正在与用户进行一对一实时语音通话。")
                    .append("默认使用自然的简体中文交流，除非用户明确要求切换语言。")
                    .append("始终扮演角色“").append(character.name).append("”，")
                    .append("自然、简洁地口语回应，不要朗读舞台说明或 Markdown。")
                    .append("用户的称呼是“").append(config.persona).append("”。");
        }
        appendPrompt(prompt, "角色描述", character.description);
        appendPrompt(prompt, traditional ? "個性" : "性格", character.personality);
        appendPrompt(prompt, traditional ? "場景" : "场景", character.scenario);
        if (recentMessages != null && !recentMessages.isEmpty()) {
            prompt.append(traditional ? "\n\n最近的文字聊天：" : "\n\n最近文字聊天：");
            int start = Math.max(0, recentMessages.size() - 8);
            for (int i = start; i < recentMessages.size(); i++) {
                ChatMessage message = recentMessages.get(i);
                String role = ChatMessage.USER.equals(message.role)
                        ? config.persona : character.name;
                String content = compact(message.content);
                if (content.length() > 380) {
                    content = content.substring(0, 380) + "…";
                }
                prompt.append("\n").append(role).append("：").append(content);
            }
        }
        if (prompt.length() > 6_000) return prompt.substring(0, 6_000);
        return prompt.toString();
    }

    private static void appendPrompt(
            StringBuilder prompt, String label, String value) {
        if (TextUtils.isEmpty(value)) return;
        String compact = compact(value);
        if (compact.length() > 1_200) {
            compact = compact.substring(0, 1_200) + "…";
        }
        prompt.append("\n\n").append(label).append("：").append(compact);
    }

    private void startAudio() {
        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            previousAudioMode = audioManager.getMode();
            previousSpeaker = audioManager.isSpeakerphoneOn();
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            audioManager.setSpeakerphoneOn(speaker);
            audioManager.requestAudioFocus(
                    focusListener,
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        }

        int chunkBytes = Math.max(1_600, provider.inputSampleRate / 5);
        int inputMinimum = AudioRecord.getMinBufferSize(
                provider.inputSampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        recorder = new AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(provider.inputSampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build())
                .setBufferSizeInBytes(Math.max(inputMinimum, chunkBytes * 4))
                .build();
        if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
            throw new IllegalStateException("麦克风初始化失败");
        }

        int outputMinimum = AudioTrack.getMinBufferSize(
                provider.outputSampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        player = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(provider.outputSampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(Math.max(
                        outputMinimum, provider.outputSampleRate * 2))
                .build();
        if (player.getState() != AudioTrack.STATE_INITIALIZED) {
            throw new IllegalStateException("听筒初始化失败");
        }

        recorder.startRecording();
        player.play();
        final int captureBytes = chunkBytes;
        captureThread = new Thread(
                () -> captureLoop(captureBytes), "realtime-microphone");
        playbackThread = new Thread(this::playbackLoop, "realtime-speaker");
        captureThread.start();
        playbackThread.start();
    }

    private void captureLoop(int chunkBytes) {
        byte[] buffer = new byte[chunkBytes];
        while (running && sessionReady) {
            AudioRecord activeRecorder = recorder;
            if (activeRecorder == null) return;
            int count;
            try {
                count = activeRecorder.read(buffer, 0, buffer.length);
            } catch (Exception error) {
                if (running) fail("麦克风读取失败：" + readableError(error));
                return;
            }
            if (count <= 0 || muted) continue;
            WebSocket activeSocket = socket;
            if (activeSocket == null) continue;
            try {
                String audio = Base64.encodeToString(
                        buffer, 0, count, Base64.NO_WRAP);
                activeSocket.send(buildAudioEvent(audio).toString());
            } catch (JSONException ignored) {
            }
        }
    }

    private JSONObject buildAudioEvent(String audio) throws JSONException {
        if (provider.protocol == RealtimeProvider.GEMINI_LIVE) {
            return new JSONObject().put("realtimeInput", new JSONObject()
                    .put("audio", new JSONObject()
                            .put("data", audio)
                            .put("mimeType", "audio/pcm;rate="
                                    + provider.inputSampleRate)));
        }
        if (provider.protocol == RealtimeProvider.ELEVEN_AGENTS) {
            return new JSONObject().put("user_audio_chunk", audio);
        }
        if (provider.protocol == RealtimeProvider.REALTIME_ADAPTER) {
            return new JSONObject()
                    .put("type", "input_audio.append")
                    .put("audio", audio);
        }
        return new JSONObject()
                .put("type", "input_audio_buffer.append")
                .put("audio", audio);
    }

    private void playbackLoop() {
        while (running && sessionReady) {
            try {
                byte[] bytes = remoteAudio.poll(300, TimeUnit.MILLISECONDS);
                if (bytes == null) continue;
                AudioTrack activePlayer = player;
                if (activePlayer != null) {
                    activePlayer.write(bytes, 0, bytes.length);
                }
            } catch (InterruptedException ignored) {
                return;
            } catch (Exception error) {
                if (running) fail("音频播放失败：" + readableError(error));
                return;
            }
        }
    }

    private void handleEvent(String text) {
        if (!running) return;
        try {
            JSONObject event = new JSONObject(text);
            Log.d(TAG, "Realtime event: provider=" + provider.id
                    + ", fields=" + String.valueOf(event.names()));
            if (provider.protocol == RealtimeProvider.GEMINI_LIVE) {
                handleGeminiEvent(event);
            } else if (provider.protocol == RealtimeProvider.ELEVEN_AGENTS) {
                handleElevenEvent(event);
            } else if (provider.protocol == RealtimeProvider.REALTIME_ADAPTER) {
                handleAdapterEvent(event);
            } else {
                handleOpenAiEvent(event);
            }
        } catch (Exception error) {
            if (running) emitError("无法解析实时事件：" + readableError(error));
        }
    }

    private void handleOpenAiEvent(JSONObject event) {
        String type = event.optString("type", "");
        if ("session.updated".equals(type)) {
            markSessionReady();
        } else if ("input_audio_buffer.speech_started".equals(type)) {
            clearRemoteAudio();
            remoteTranscript.setLength(0);
            emitTranscript("");
            emitState("通话中 · 你正在说话");
        } else if ("input_audio_buffer.speech_stopped".equals(type)) {
            emitState("通话中 · 正在思考");
        } else if ("response.output_audio.delta".equals(type)
                || "response.audio.delta".equals(type)) {
            enqueueBase64(event.optString("delta", ""));
            emitState("通话中 · " + character.name + " 正在说话");
        } else if ("response.output_audio_transcript.delta".equals(type)
                || "response.audio_transcript.delta".equals(type)) {
            remoteTranscript.append(event.optString("delta", ""));
            emitTranscript(remoteTranscript.toString());
        } else if ("response.output_audio_transcript.done".equals(type)
                || "response.audio_transcript.done".equals(type)) {
            String transcript = event.optString("transcript", "");
            if (!transcript.isEmpty()) setTranscript(transcript);
        } else if ("response.output_audio.done".equals(type)
                || "response.audio.done".equals(type)
                || "response.done".equals(type)) {
            emitState("通话中 · 正在聆听");
        } else if ("error".equals(type)) {
            JSONObject error = event.optJSONObject("error");
            fail(error == null
                    ? "Realtime API 返回错误"
                    : error.optString("message", "Realtime API 返回错误"));
        }
    }

    private void handleGeminiEvent(JSONObject event) {
        JSONObject error = event.optJSONObject("error");
        if (error != null) {
            String message = error.optString("message", "Gemini Live 返回错误");
            String status = error.optString("status", "");
            fail(status.isEmpty() ? message : status + "：" + message);
            return;
        }
        JSONObject setup = event.optJSONObject("setupComplete");
        if (setup != null) markSessionReady();
        JSONObject resumption = event.optJSONObject("sessionResumptionUpdate");
        if (resumption != null && resumption.optBoolean("resumable", false)) {
            String handle = resumption.optString("newHandle", "");
            if (!handle.isEmpty()) geminiResumeHandle = handle;
        }
        if (event.optJSONObject("goAway") != null) {
            emitState("服务即将切换连接…");
        }
        JSONObject content = event.optJSONObject("serverContent");
        if (content == null) return;
        if (content.optBoolean("interrupted", false)) clearRemoteAudio();
        JSONObject turn = content.optJSONObject("modelTurn");
        if (turn != null) {
            JSONArray parts = turn.optJSONArray("parts");
            if (parts != null) {
                for (int i = 0; i < parts.length(); i++) {
                    JSONObject part = parts.optJSONObject(i);
                    JSONObject data = part == null
                            ? null : part.optJSONObject("inlineData");
                    if (data != null) enqueueBase64(data.optString("data", ""));
                }
                emitState("通话中 · " + character.name + " 正在说话");
            }
        }
        JSONObject transcription = content.optJSONObject("outputTranscription");
        if (transcription != null) {
            String value = transcription.optString("text", "");
            if (!value.isEmpty()) {
                remoteTranscript.append(value);
                emitTranscript(remoteTranscript.toString());
            }
        }
        if (content.optBoolean("turnComplete", false)) {
            emitState("通话中 · 正在聆听");
            remoteTranscript.setLength(0);
        }
    }

    private void handleElevenEvent(JSONObject event) throws JSONException {
        String type = event.optString("type", "");
        if ("conversation_initiation_metadata".equals(type)) {
            markSessionReady();
        } else if ("audio".equals(type)) {
            JSONObject audio = event.optJSONObject("audio_event");
            if (audio != null) {
                enqueueBase64(audio.optString("audio_base_64", ""));
                emitState("通话中 · " + character.name + " 正在说话");
            }
        } else if ("agent_response".equals(type)) {
            JSONObject response = event.optJSONObject("agent_response_event");
            if (response != null) {
                setTranscript(response.optString("agent_response", ""));
            }
        } else if ("interruption".equals(type)) {
            clearRemoteAudio();
        } else if ("ping".equals(type)) {
            JSONObject ping = event.optJSONObject("ping_event");
            if (ping != null && socket != null) {
                socket.send(new JSONObject()
                        .put("type", "pong")
                        .put("event_id", ping.optInt("event_id"))
                        .toString());
            }
        }
    }

    private void handleAdapterEvent(JSONObject event) {
        String type = event.optString("type", "");
        if ("session.ready".equals(type) || "session.updated".equals(type)) {
            markSessionReady();
        } else if ("input_audio.speech_started".equals(type)) {
            clearRemoteAudio();
            emitState("通话中 · 你正在说话");
        } else if ("input_audio.speech_stopped".equals(type)) {
            emitState("通话中 · 正在思考");
        } else if ("audio.delta".equals(type)
                || "output_audio.delta".equals(type)) {
            String audio = event.optString(
                    "audio", event.optString("delta", ""));
            enqueueBase64(audio);
            emitState("通话中 · " + character.name + " 正在说话");
        } else if ("transcript.delta".equals(type)) {
            remoteTranscript.append(event.optString(
                    "text", event.optString("delta", "")));
            emitTranscript(remoteTranscript.toString());
        } else if ("transcript.done".equals(type)) {
            setTranscript(event.optString(
                    "text", event.optString("transcript", "")));
        } else if ("response.done".equals(type)) {
            emitState("通话中 · 正在聆听");
            remoteTranscript.setLength(0);
        } else if ("state".equals(type)) {
            emitState(event.optString("message", "通话中"));
        } else if ("error".equals(type)) {
            JSONObject detail = event.optJSONObject("error");
            fail(detail == null
                    ? event.optString("message", "实时适配器返回错误")
                    : detail.optString("message", "实时适配器返回错误"));
        } else {
            handleOpenAiEvent(event);
        }
    }

    private void setTranscript(String value) {
        if (TextUtils.isEmpty(value)) return;
        remoteTranscript.setLength(0);
        remoteTranscript.append(value);
        emitTranscript(value);
    }

    private void enqueueBase64(String value) {
        if (TextUtils.isEmpty(value)) return;
        try {
            enqueueAudio(Base64.decode(value, Base64.DEFAULT));
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void enqueueAudio(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return;
        if (!remoteAudio.offer(bytes)) {
            remoteAudio.poll();
            remoteAudio.offer(bytes);
        }
    }

    private void clearRemoteAudio() {
        remoteAudio.clear();
        AudioTrack activePlayer = player;
        if (activePlayer == null) return;
        try {
            activePlayer.pause();
            activePlayer.flush();
            activePlayer.play();
        } catch (IllegalStateException ignored) {
        }
    }

    private synchronized void stopAudio() {
        audioStarted = false;
        Thread capture = captureThread;
        captureThread = null;
        if (capture != null) capture.interrupt();
        Thread playback = playbackThread;
        playbackThread = null;
        if (playback != null) playback.interrupt();

        AudioRecord activeRecorder = recorder;
        recorder = null;
        if (activeRecorder != null) {
            try {
                activeRecorder.stop();
            } catch (IllegalStateException ignored) {
            }
            activeRecorder.release();
        }
        AudioTrack activePlayer = player;
        player = null;
        if (activePlayer != null) {
            try {
                activePlayer.pause();
                activePlayer.flush();
                activePlayer.stop();
            } catch (IllegalStateException ignored) {
            }
            activePlayer.release();
        }
        remoteAudio.clear();
        if (audioManager != null) {
            audioManager.abandonAudioFocus(focusListener);
            audioManager.setSpeakerphoneOn(previousSpeaker);
            audioManager.setMode(previousAudioMode);
            audioManager = null;
        }
    }

    private void fail(String message) {
        if (!running) return;
        running = false;
        cancelSessionReadyTimeout();
        WebSocket activeSocket = socket;
        socket = null;
        if (activeSocket != null) activeSocket.close(1011, "client_error");
        stopAudio();
        emitError(message);
        emitEnded();
    }

    private void emitState(String state) {
        main.post(() -> listener.onState(state));
    }

    private void emitTranscript(String transcript) {
        main.post(() -> listener.onTranscript(transcript));
    }

    private void emitError(String error) {
        main.post(() -> listener.onError(error));
    }

    private void emitEnded() {
        if (ended.compareAndSet(false, true)) {
            main.post(listener::onEnded);
        }
    }

    private static String compact(String value) {
        if (value == null) return "";
        return value.replace('\r', ' ').replace('\n', ' ').trim();
    }

    private static String readableError(Throwable error) {
        String message = error == null ? "" : error.getMessage();
        if (TextUtils.isEmpty(message) && error != null) {
            message = error.getClass().getSimpleName();
        }
        return compact(message);
    }
}
