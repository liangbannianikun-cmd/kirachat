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

import app.miuix.tavern.model.AppConfig;
import app.miuix.tavern.model.CharacterCard;
import app.miuix.tavern.model.ChatMessage;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public final class RealtimeVoiceClient {
    public static final String MODEL = "gpt-realtime-2.1";
    private static final String REALTIME_URL =
            "wss://api.openai.com/v1/realtime?model=" + MODEL;
    private static final int SAMPLE_RATE = 24_000;
    private static final int AUDIO_CHUNK_BYTES = 4_800;

    public interface Listener {
        void onState(String state);

        void onTranscript(String transcript);

        void onError(String error);

        void onEnded();
    }

    private final Context context;
    private final AppConfig config;
    private final String tokenAuthorization;
    private final CharacterCard character;
    private final List<ChatMessage> recentMessages;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build();
    private final LinkedBlockingQueue<byte[]> remoteAudio = new LinkedBlockingQueue<>(160);
    private final AtomicBoolean ended = new AtomicBoolean();
    private final StringBuilder remoteTranscript = new StringBuilder();

    private volatile boolean running;
    private volatile boolean muted;
    private volatile boolean speaker;
    private Call tokenCall;
    private WebSocket socket;
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
            String tokenAuthorization,
            CharacterCard character,
            List<ChatMessage> recentMessages,
            Listener listener) {
        this.context = context.getApplicationContext();
        this.config = config;
        this.tokenAuthorization = tokenAuthorization == null ? "" : tokenAuthorization.trim();
        this.character = character;
        this.recentMessages = recentMessages;
        this.listener = listener;
    }

    public void start() {
        if (running) return;
        running = true;
        ended.set(false);
        emitState("正在获取短期令牌…");
        Request.Builder request = new Request.Builder()
                .url(config.realtimeTokenUrl.trim())
                .get()
                .header("Accept", "application/json");
        if (!tokenAuthorization.isEmpty()) {
            request.header("Authorization", "Bearer " + tokenAuthorization);
        }
        tokenCall = http.newCall(request.build());
        tokenCall.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException error) {
                if (!running) return;
                fail("令牌服务连接失败：" + readableError(error));
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (Response closeable = response) {
                    String body = closeable.body() == null ? "" : closeable.body().string();
                    if (!closeable.isSuccessful()) {
                        throw new IOException(
                                "HTTP " + closeable.code() + " · " + compact(body));
                    }
                    String token = parseClientSecret(body);
                    if (token.isEmpty()) {
                        throw new IOException("令牌服务没有返回 value");
                    }
                    connect(token);
                } catch (Exception error) {
                    if (running) fail("短期令牌无效：" + readableError(error));
                }
            }
        });
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
        running = false;
        Call activeTokenCall = tokenCall;
        if (activeTokenCall != null) activeTokenCall.cancel();
        WebSocket activeSocket = socket;
        socket = null;
        if (activeSocket != null) activeSocket.close(1000, "user_hangup");
        stopAudio();
        if (wasRunning) emitState("通话已结束");
        emitEnded();
        http.dispatcher().executorService().shutdown();
    }

    private void connect(String token) {
        if (!running) return;
        emitState("正在连接 " + MODEL + "…");
        Request request = new Request.Builder()
                .url(REALTIME_URL)
                .header("Authorization", "Bearer " + token)
                .build();
        socket = http.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                if (!running) {
                    webSocket.close(1000, "cancelled");
                    return;
                }
                try {
                    webSocket.send(buildSessionUpdate().toString());
                    startAudio();
                    emitState("通话中 · 正在聆听");
                } catch (Exception error) {
                    fail("无法启动音频：" + readableError(error));
                }
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                handleEvent(text);
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                webSocket.close(code, reason);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                if (!running) return;
                running = false;
                stopAudio();
                emitState("通话已结束");
                emitEnded();
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable error, Response response) {
                if (!running) return;
                String responseDetail = "";
                if (response != null) responseDetail = " · HTTP " + response.code();
                fail("实时连接中断：" + readableError(error) + responseDetail);
            }
        });
    }

    private JSONObject buildSessionUpdate() throws JSONException {
        JSONObject input = new JSONObject()
                .put("format", new JSONObject()
                        .put("type", "audio/pcm")
                        .put("rate", SAMPLE_RATE))
                .put("turn_detection", new JSONObject()
                        .put("type", "semantic_vad"));
        JSONObject output = new JSONObject()
                .put("format", new JSONObject().put("type", "audio/pcm"))
                .put("voice", "marin");
        JSONObject session = new JSONObject()
                .put("type", "realtime")
                .put("model", MODEL)
                .put("output_modalities", new JSONArray().put("audio"))
                .put("instructions", buildInstructions())
                .put("audio", new JSONObject()
                        .put("input", input)
                        .put("output", output));
        return new JSONObject()
                .put("type", "session.update")
                .put("session", session);
    }

    private String buildInstructions() {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你正在与用户进行一对一实时语音通话。")
                .append("始终扮演角色“").append(character.name).append("”，")
                .append("自然、简洁地口语回应，不要朗读舞台说明或 Markdown。")
                .append("用户的称呼是“").append(config.persona).append("”。");
        appendPrompt(prompt, "角色描述", character.description);
        appendPrompt(prompt, "性格", character.personality);
        appendPrompt(prompt, "场景", character.scenario);
        if (recentMessages != null && !recentMessages.isEmpty()) {
            prompt.append("\n\n最近文字聊天：");
            int start = Math.max(0, recentMessages.size() - 8);
            for (int i = start; i < recentMessages.size(); i++) {
                ChatMessage message = recentMessages.get(i);
                String role = ChatMessage.USER.equals(message.role)
                        ? config.persona
                        : character.name;
                String content = compact(message.content);
                if (content.length() > 380) content = content.substring(0, 380) + "…";
                prompt.append("\n").append(role).append("：").append(content);
            }
        }
        if (prompt.length() > 6_000) return prompt.substring(0, 6_000);
        return prompt.toString();
    }

    private static void appendPrompt(StringBuilder prompt, String label, String value) {
        if (TextUtils.isEmpty(value)) return;
        String compact = compact(value);
        if (compact.length() > 1_200) compact = compact.substring(0, 1_200) + "…";
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
                    focusListener, AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        }

        int inputMinimum = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        recorder = new AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build())
                .setBufferSizeInBytes(Math.max(inputMinimum, AUDIO_CHUNK_BYTES * 4))
                .build();
        if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
            throw new IllegalStateException("麦克风初始化失败");
        }

        int outputMinimum = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        player = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(Math.max(outputMinimum, AUDIO_CHUNK_BYTES * 8))
                .build();
        if (player.getState() != AudioTrack.STATE_INITIALIZED) {
            throw new IllegalStateException("听筒初始化失败");
        }

        recorder.startRecording();
        player.play();
        captureThread = new Thread(this::captureLoop, "realtime-microphone");
        playbackThread = new Thread(this::playbackLoop, "realtime-speaker");
        captureThread.start();
        playbackThread.start();
    }

    private void captureLoop() {
        byte[] buffer = new byte[AUDIO_CHUNK_BYTES];
        while (running) {
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
                activeSocket.send(new JSONObject()
                        .put("type", "input_audio_buffer.append")
                        .put("audio", audio)
                        .toString());
            } catch (JSONException ignored) {
            }
        }
    }

    private void playbackLoop() {
        while (running) {
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
            String type = event.optString("type", "");
            if ("session.created".equals(type) || "session.updated".equals(type)) {
                emitState("通话中 · 正在聆听");
            } else if ("input_audio_buffer.speech_started".equals(type)) {
                clearRemoteAudio();
                remoteTranscript.setLength(0);
                emitTranscript("");
                emitState("通话中 · 你正在说话");
            } else if ("input_audio_buffer.speech_stopped".equals(type)) {
                emitState("通话中 · 正在思考");
            } else if ("response.output_audio.delta".equals(type)
                    || "response.audio.delta".equals(type)) {
                String delta = event.optString("delta", "");
                if (!delta.isEmpty()) {
                    byte[] bytes = Base64.decode(delta, Base64.DEFAULT);
                    if (!remoteAudio.offer(bytes)) {
                        remoteAudio.poll();
                        remoteAudio.offer(bytes);
                    }
                    emitState("通话中 · " + character.name + " 正在说话");
                }
            } else if ("response.output_audio_transcript.delta".equals(type)
                    || "response.audio_transcript.delta".equals(type)) {
                remoteTranscript.append(event.optString("delta", ""));
                emitTranscript(remoteTranscript.toString());
            } else if ("response.output_audio_transcript.done".equals(type)
                    || "response.audio_transcript.done".equals(type)) {
                String transcript = event.optString("transcript", "");
                if (!transcript.isEmpty()) {
                    remoteTranscript.setLength(0);
                    remoteTranscript.append(transcript);
                    emitTranscript(transcript);
                }
            } else if ("response.output_audio.done".equals(type)
                    || "response.done".equals(type)) {
                emitState("通话中 · 正在聆听");
            } else if ("error".equals(type)) {
                JSONObject error = event.optJSONObject("error");
                String message = error == null
                        ? "Realtime API 返回错误"
                        : error.optString("message", "Realtime API 返回错误");
                fail(message);
            }
        } catch (Exception error) {
            if (running) emitError("无法解析实时事件：" + readableError(error));
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
        WebSocket activeSocket = socket;
        socket = null;
        if (activeSocket != null) activeSocket.close(1011, "client_error");
        stopAudio();
        emitError(message);
        emitEnded();
    }

    private static String parseClientSecret(String body) throws JSONException {
        String trimmed = body == null ? "" : body.trim();
        if (trimmed.isEmpty()) return "";
        if (!trimmed.startsWith("{")) return trimmed;
        JSONObject root = new JSONObject(trimmed);
        String value = root.optString("value", "");
        if (!value.isEmpty()) return value;
        JSONObject secret = root.optJSONObject("client_secret");
        if (secret != null) {
            value = secret.optString("value", "");
            if (!value.isEmpty()) return value;
        }
        Object token = root.opt("token");
        if (token instanceof String) return ((String) token).trim();
        if (token instanceof JSONObject) {
            return ((JSONObject) token).optString("value", "");
        }
        return "";
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
