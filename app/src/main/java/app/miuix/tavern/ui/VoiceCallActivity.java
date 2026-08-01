package app.miuix.tavern.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import app.miuix.tavern.data.LocalStore;
import app.miuix.tavern.data.SecureStore;
import app.miuix.tavern.model.AppConfig;
import app.miuix.tavern.model.CharacterCard;
import app.miuix.tavern.network.RealtimeVoiceClient;

public final class VoiceCallActivity extends AppCompatActivity
        implements RealtimeVoiceClient.Listener {
    public static final String EXTRA_CHARACTER_ID = "character_id";

    private static final int REQUEST_MICROPHONE = 4310;
    private static final int BACKGROUND = Color.rgb(35, 37, 42);
    private static final int SECONDARY = Color.rgb(177, 180, 188);
    private static final int CONTROL = Color.rgb(69, 72, 79);

    private LocalStore store;
    private SecureStore secureStore;
    private CharacterCard character;
    private RealtimeVoiceClient client;
    private TextView status;
    private TextView transcript;
    private TextView actionButton;
    private LineIconView muteIcon;
    private LineIconView speakerIcon;
    private TextView muteLabel;
    private TextView speakerLabel;
    private boolean clientStarted;
    private boolean waitingPermission;
    private boolean openedSettings;
    private boolean failed;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BACKGROUND);
        getWindow().setNavigationBarColor(BACKGROUND);
        getWindow().getDecorView().setSystemUiVisibility(0);

        store = new LocalStore(this);
        secureStore = new SecureStore(this);
        String id = getIntent().getStringExtra(EXTRA_CHARACTER_ID);
        character = store.getCharacter(id == null ? "" : id);
        if (character == null) {
            LocalizedToast.makeText(this, "角色不存在", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        setContentView(buildContent());
        prepareCall();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (openedSettings) {
            openedSettings = false;
            failed = false;
            prepareCall();
        }
    }

    private View buildContent() {
        LinearLayout root = MiuixUi.vertical(this);
        root.setBackgroundColor(BACKGROUND);

        LinearLayout toolbar = MiuixUi.horizontal(this);
        toolbar.setPadding(MiuixUi.dp(this, 7), MiuixUi.dp(this, 3),
                MiuixUi.dp(this, 7), MiuixUi.dp(this, 3));
        FrameLayout back = new FrameLayout(this);
        back.setContentDescription(L10n.tr(this, "返回并挂断"));
        back.setOnClickListener(v -> finish());
        LineIconView backIcon = new LineIconView(this);
        backIcon.setType(LineIconView.BACK);
        backIcon.setTintColor(Color.WHITE);
        back.addView(backIcon, new FrameLayout.LayoutParams(
                MiuixUi.dp(this, 31), MiuixUi.dp(this, 31), Gravity.CENTER));
        MiuixUi.pressable(back, 0.9f);
        toolbar.addView(back, new LinearLayout.LayoutParams(
                MiuixUi.dp(this, 50), MiuixUi.dp(this, 54)));
        TextView title = MiuixUi.text(
                this, "语音通话", 16, Color.WHITE, true);
        toolbar.addView(title, new LinearLayout.LayoutParams(
                0, MiuixUi.dp(this, 54), 1));
        root.addView(toolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, MiuixUi.dp(this, 62)));

        LinearLayout center = MiuixUi.vertical(this);
        center.setGravity(Gravity.CENTER_HORIZONTAL);
        center.setPadding(
                MiuixUi.dp(this, 28), MiuixUi.dp(this, 38),
                MiuixUi.dp(this, 28), MiuixUi.dp(this, 18));
        root.addView(center, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        AvatarView avatar = new AvatarView(this);
        avatar.setCharacter(character);
        center.addView(avatar, new LinearLayout.LayoutParams(
                MiuixUi.dp(this, 118), MiuixUi.dp(this, 118)));

        TextView name = MiuixUi.rawText(
                this, character.name, 25, Color.WHITE, true);
        name.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nameParams.topMargin = MiuixUi.dp(this, 22);
        center.addView(name, nameParams);

        TextView model = MiuixUi.rawText(
                this,
                RealtimeVoiceClient.displayLabel(store.getConfig()),
                12.5f,
                SECONDARY,
                true);
        model.setGravity(Gravity.CENTER);
        model.setPadding(MiuixUi.dp(this, 12), 0, MiuixUi.dp(this, 12), 0);
        model.setBackground(MiuixUi.shape(Color.rgb(53, 55, 62), 13, this));
        LinearLayout.LayoutParams modelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, MiuixUi.dp(this, 28));
        modelParams.topMargin = MiuixUi.dp(this, 10);
        center.addView(model, modelParams);

        status = MiuixUi.text(
                this, "正在准备通话…", 16, Color.WHITE, false);
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = MiuixUi.dp(this, 24);
        center.addView(status, statusParams);

        transcript = MiuixUi.text(
                this, "允许麦克风后即可开始实时对话", 14, SECONDARY, false);
        transcript.setGravity(Gravity.CENTER);
        transcript.setLineSpacing(MiuixUi.dp(this, 4), 1.04f);
        transcript.setMaxLines(5);
        LinearLayout.LayoutParams transcriptParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        transcriptParams.topMargin = MiuixUi.dp(this, 13);
        center.addView(transcript, transcriptParams);

        actionButton = MiuixUi.pillButton(this, "前往设置", false);
        actionButton.setTextColor(Color.WHITE);
        actionButton.setBackground(MiuixUi.shape(CONTROL, 14, this));
        actionButton.setVisibility(View.GONE);
        actionButton.setOnClickListener(v -> retryOrConfigure());
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, MiuixUi.dp(this, 46));
        actionParams.topMargin = MiuixUi.dp(this, 22);
        center.addView(actionButton, actionParams);

        LinearLayout controls = MiuixUi.horizontal(this);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(
                MiuixUi.dp(this, 22), MiuixUi.dp(this, 12),
                MiuixUi.dp(this, 22), MiuixUi.dp(this, 32));

        Control mute = addControl(
                controls, LineIconView.MIC, "静音", CONTROL, v -> toggleMute());
        muteIcon = mute.icon;
        muteLabel = mute.label;

        Control hangup = addControl(
                controls, LineIconView.HANGUP, "挂断",
                Color.rgb(236, 74, 74), v -> finish());
        LinearLayout.LayoutParams hangupParams =
                (LinearLayout.LayoutParams) hangup.container.getLayoutParams();
        hangupParams.leftMargin = MiuixUi.dp(this, 30);
        hangupParams.rightMargin = MiuixUi.dp(this, 30);
        hangup.container.setLayoutParams(hangupParams);

        Control speaker = addControl(
                controls, LineIconView.SPEAKER, "扬声器", CONTROL, v -> toggleSpeaker());
        speakerIcon = speaker.icon;
        speakerLabel = speaker.label;
        root.addView(controls, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return root;
    }

    private Control addControl(
            LinearLayout parent,
            int iconType,
            String labelValue,
            int background,
            View.OnClickListener listener) {
        LinearLayout container = MiuixUi.vertical(this);
        container.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout button = new FrameLayout(this);
        button.setContentDescription(L10n.tr(this, labelValue));
        button.setBackground(MiuixUi.shape(background, 32, this));
        button.setOnClickListener(listener);
        MiuixUi.pressable(button, 0.9f);
        LineIconView icon = new LineIconView(this);
        icon.setType(iconType);
        icon.setTintColor(Color.WHITE);
        button.addView(icon, new FrameLayout.LayoutParams(
                MiuixUi.dp(this, 32), MiuixUi.dp(this, 32), Gravity.CENTER));
        container.addView(button, new LinearLayout.LayoutParams(
                MiuixUi.dp(this, 64), MiuixUi.dp(this, 64)));
        TextView label = MiuixUi.text(
                this, labelValue, 13.5f, Color.WHITE, false);
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                MiuixUi.dp(this, 76), MiuixUi.dp(this, 34));
        labelParams.topMargin = MiuixUi.dp(this, 7);
        container.addView(label, labelParams);
        parent.addView(container, new LinearLayout.LayoutParams(
                MiuixUi.dp(this, 80), ViewGroup.LayoutParams.WRAP_CONTENT));
        return new Control(container, icon, label);
    }

    private void prepareCall() {
        if (clientStarted || waitingPermission || failed) return;
        AppConfig config = store.getConfig();
        String credential = secureStore.getRealtimeCredential(
                config.realtimeProvider);
        String configurationError = RealtimeVoiceClient.configurationError(
                config, credential);
        if (!configurationError.isEmpty()) {
            status.setText(L10n.tr(this, "需要配置实时语音服务"));
            transcript.setText(L10n.tr(this, configurationError
                    + "。请在“连接与账户”的 Realtime 语音中完成配置。"));
            actionButton.setText("前往设置");
            actionButton.setVisibility(View.VISIBLE);
            return;
        }
        actionButton.setVisibility(View.GONE);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            waitingPermission = true;
            requestPermissions(
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    REQUEST_MICROPHONE);
            return;
        }
        startClient(config);
    }

    private void startClient(AppConfig config) {
        clientStarted = true;
        failed = false;
        store.touchCharacter(character.id);
        client = new RealtimeVoiceClient(
                this,
                config,
                secureStore.getRealtimeCredential(config.realtimeProvider),
                character,
                store.getMessages(character.id),
                this);
        client.start();
    }

    private void retryOrConfigure() {
        AppConfig config = store.getConfig();
        String configurationError = RealtimeVoiceClient.configurationError(
                config,
                secureStore.getRealtimeCredential(config.realtimeProvider));
        if (!configurationError.isEmpty()) {
            openedSettings = true;
            startActivity(new Intent(this, SettingsActivity.class));
            return;
        }
        failed = false;
        actionButton.setVisibility(View.GONE);
        transcript.setText("正在重新连接…");
        prepareCall();
    }

    private void toggleMute() {
        if (client == null) return;
        boolean muted = !client.isMuted();
        client.setMuted(muted);
        muteLabel.setText(muted ? "已静音" : "静音");
        muteIcon.setTintColor(muted ? Color.rgb(255, 190, 92) : Color.WHITE);
    }

    private void toggleSpeaker() {
        if (client == null) return;
        boolean speaker = !client.isSpeaker();
        client.setSpeaker(speaker);
        speakerLabel.setText(speaker ? "扬声器开" : "扬声器");
        speakerIcon.setTintColor(speaker ? MiuixUi.GREEN : Color.WHITE);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_MICROPHONE) return;
        waitingPermission = false;
        if (grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            prepareCall();
        } else {
            failed = true;
            status.setText("没有麦克风权限");
            transcript.setText("语音通话需要麦克风权限。你可以在系统应用设置中重新允许。");
            actionButton.setText("重试");
            actionButton.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onState(String state) {
        status.setText(L10n.tr(this, state));
    }

    @Override
    public void onTranscript(String value) {
        transcript.setText(TextUtils.isEmpty(value)
                ? "正在等待对方回应…"
                : value);
    }

    @Override
    public void onError(String error) {
        failed = true;
        clientStarted = false;
        RealtimeVoiceClient failedClient = client;
        client = null;
        if (failedClient != null) failedClient.stop();
        status.setText("通话连接失败");
        transcript.setText(L10n.tr(this, error));
        actionButton.setText("重试");
        actionButton.setVisibility(View.VISIBLE);
    }

    @Override
    public void onEnded() {
        clientStarted = false;
        RealtimeVoiceClient endedClient = client;
        client = null;
        if (endedClient != null) endedClient.stop();
    }

    @Override
    protected void onDestroy() {
        if (client != null) {
            client.stop();
            client = null;
        }
        super.onDestroy();
    }

    private static final class Control {
        final LinearLayout container;
        final LineIconView icon;
        final TextView label;

        Control(LinearLayout container, LineIconView icon, TextView label) {
            this.container = container;
            this.icon = icon;
            this.label = label;
        }
    }
}
