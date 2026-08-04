package app.miuix.tavern.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import app.miuix.tavern.data.SyncSettings;
import app.miuix.tavern.network.RemoteSyncClient;
import app.miuix.tavern.network.RemoteSyncManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SyncSettingsActivity extends AppCompatActivity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private SyncSettings settings;
    private EditText serverUrl;
    private EditText token;
    private EditText encryptionPassword;
    private SwitchCompat autoSync;
    private TextView status;
    private TextView saveButton;
    private TextView testButton;
    private TextView syncButton;
    private TextView uploadButton;
    private TextView downloadButton;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MiuixUi.applySystemBars(this, MiuixUi.BACKGROUND, MiuixUi.BACKGROUND);
        settings = new SyncSettings(this);
        setContentView(buildContent());
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private View buildContent() {
        LinearLayout root = MiuixUi.vertical(this);
        root.setBackgroundColor(MiuixUi.background(this));
        root.addView(buildToolbar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, MiuixUi.dp(this, 58)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        LinearLayout page = MiuixUi.vertical(this);
        page.setPadding(0, 0, 0, MiuixUi.dp(this, 34));
        scroll.addView(page);

        addWithMargins(page, MiuixUi.text(
                this, "服务器同步", 31, MiuixUi.TEXT_PRIMARY, true), 18, 12, 18, 5);
        TextView intro = MiuixUi.text(this,
                "连接自部署的澄语同步服务器，在设备间自动同步角色、群聊、消息、头像、背景和普通设置。",
                14, MiuixUi.TEXT_SECONDARY, false);
        intro.setLineSpacing(MiuixUi.dp(this, 3), 1.05f);
        addWithMargins(page, intro, 18, 0, 18, 16);

        LinearLayout connection = MiuixUi.card(this);
        connection.addView(MiuixUi.text(
                this, "服务器连接", 18, MiuixUi.TEXT_PRIMARY, true));
        serverUrl = addField(connection, "https://sync.example.com", false);
        serverUrl.setText(settings.serverUrl());
        token = addField(connection, "同步令牌（至少 24 个字符）", true);
        token.setText(settings.token());
        encryptionPassword = addField(connection, "端到端加密密码（至少 8 个字符）", true);
        encryptionPassword.setText(settings.encryptionPassword());
        connection.addView(switchRow());

        LinearLayout primaryActions = MiuixUi.horizontal(this);
        testButton = MiuixUi.pillButton(this, "测试连接", false);
        testButton.setOnClickListener(v -> testConnection());
        primaryActions.addView(testButton, weightedButtonParams(0));
        saveButton = MiuixUi.pillButton(this, "保存设置", true);
        saveButton.setOnClickListener(v -> saveConfiguration(true));
        primaryActions.addView(saveButton, weightedButtonParams(8));
        addToCard(connection, primaryActions, 14);
        addWithMargins(page, connection, 14, 0, 14, 12);

        LinearLayout syncCard = MiuixUi.card(this);
        syncCard.addView(MiuixUi.text(
                this, "同步操作", 18, MiuixUi.TEXT_PRIMARY, true));
        TextView syncHelp = MiuixUi.text(this,
                "首次连接或发生冲突时，请明确选择保留本机内容或服务器内容。正常情况下“立即同步”会自动判断上传或下载。",
                13, MiuixUi.TEXT_SECONDARY, false);
        syncHelp.setLineSpacing(MiuixUi.dp(this, 2), 1.04f);
        addToCard(syncCard, syncHelp, 7);
        syncButton = MiuixUi.pillButton(this, "立即同步", true);
        syncButton.setOnClickListener(v -> runSync(RemoteSyncClient.Mode.AUTOMATIC));
        addToCard(syncCard, syncButton, 14);
        LinearLayout overwriteActions = MiuixUi.horizontal(this);
        uploadButton = MiuixUi.pillButton(this, "上传本机", false);
        uploadButton.setOnClickListener(v -> confirmForce(true));
        overwriteActions.addView(uploadButton, weightedButtonParams(0));
        downloadButton = MiuixUi.pillButton(this, "下载服务器", false);
        downloadButton.setOnClickListener(v -> confirmForce(false));
        overwriteActions.addView(downloadButton, weightedButtonParams(8));
        addToCard(syncCard, overwriteActions, 9);
        addWithMargins(page, syncCard, 14, 0, 14, 12);

        LinearLayout security = MiuixUi.card(this);
        security.addView(MiuixUi.text(
                this, "安全与冲突保护", 17, MiuixUi.TEXT_PRIMARY, true));
        TextView securityBody = MiuixUi.text(this,
                "同步内容会在本机加密后上传；加密密码不会发送给服务器。API Key、GPT/Copilot 令牌、本地模型和语音凭据不会同步。两台设备同时修改时会暂停并要求手动选择，避免静默覆盖。生产环境请使用 HTTPS。",
                13, MiuixUi.TEXT_SECONDARY, false);
        securityBody.setLineSpacing(MiuixUi.dp(this, 2), 1.04f);
        addToCard(security, securityBody, 7);
        addWithMargins(page, security, 14, 0, 14, 12);

        status = MiuixUi.rawText(
                this, statusText(), 13, MiuixUi.TEXT_SECONDARY, false);
        status.setGravity(Gravity.CENTER);
        addWithMargins(page, status, 18, 2, 18, 0);
        return root;
    }

    private View buildToolbar() {
        LinearLayout toolbar = MiuixUi.horizontal(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(MiuixUi.dp(this, 7), MiuixUi.dp(this, 3),
                MiuixUi.dp(this, 7), MiuixUi.dp(this, 3));
        FrameLayout back = new FrameLayout(this);
        back.setContentDescription(L10n.tr(this, "返回"));
        MiuixUi.ripple(back, Color.WHITE, 14);
        MiuixUi.pressable(back, 0.92f);
        LineIconView icon = new LineIconView(this);
        icon.setType(LineIconView.BACK);
        back.addView(icon, new FrameLayout.LayoutParams(
                MiuixUi.dp(this, 24), MiuixUi.dp(this, 24), Gravity.CENTER));
        back.setOnClickListener(v -> finish());
        toolbar.addView(back, new LinearLayout.LayoutParams(
                MiuixUi.dp(this, 48), MiuixUi.dp(this, 52)));
        return toolbar;
    }

    private EditText addField(LinearLayout card, String hint, boolean secret) {
        EditText field = MiuixUi.field(this, hint, secret);
        addToCard(card, field, 10);
        return field;
    }

    private View switchRow() {
        LinearLayout row = MiuixUi.horizontal(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = MiuixUi.text(
                this, "自动同步", 16, MiuixUi.TEXT_PRIMARY, false);
        row.addView(label, new LinearLayout.LayoutParams(
                0, MiuixUi.dp(this, 50), 1));
        autoSync = new SwitchCompat(this);
        autoSync.setChecked(settings.autoSync());
        row.addView(autoSync, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, MiuixUi.dp(this, 50)));
        row.setOnClickListener(v -> autoSync.setChecked(!autoSync.isChecked()));
        return row;
    }

    private void testConnection() {
        if (!saveConfiguration(false)) return;
        setBusy(true, "正在测试服务器…");
        executor.execute(() -> {
            try {
                String message = new RemoteSyncClient(this).testConnection();
                settings.setLastStatus(message);
                runOnUiThread(() -> setBusy(false, message));
            } catch (Exception error) {
                showError(error);
            }
        });
    }

    private void runSync(RemoteSyncClient.Mode mode) {
        if (!saveConfiguration(false)) return;
        setBusy(true, mode == RemoteSyncClient.Mode.FORCE_UPLOAD
                ? "正在上传本机内容…" : mode == RemoteSyncClient.Mode.FORCE_DOWNLOAD
                ? "正在下载服务器内容…" : "正在同步…");
        RemoteSyncManager.runNow(this, mode, (result, error) -> {
            if (error != null) {
                showError(error);
                return;
            }
            setBusy(false, result.message);
            LocalizedToast.makeText(this, result.message, Toast.LENGTH_LONG).show();
        });
    }

    private void confirmForce(boolean upload) {
        new LocalizedAlertDialogBuilder(this)
                .setTitle(upload ? "用本机内容覆盖服务器？" : "用服务器内容替换本机？")
                .setMessage(upload
                        ? "服务器当前快照会被本机内容替换。其他设备下次同步会下载此版本。"
                        : "本机角色、群聊、消息、头像、背景和普通设置会被服务器快照替换。此操作无法撤销。")
                .setNegativeButton("取消", null)
                .setPositiveButton(upload ? "上传本机" : "下载服务器",
                        (dialog, which) -> runSync(upload
                                ? RemoteSyncClient.Mode.FORCE_UPLOAD
                                : RemoteSyncClient.Mode.FORCE_DOWNLOAD))
                .show();
    }

    private boolean saveConfiguration(boolean showToast) {
        String url = serverUrl.getText().toString().trim();
        String tokenValue = token.getText().toString().trim();
        String password = encryptionPassword.getText().toString();
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            serverUrl.setError(L10n.tr(this, "服务器地址必须以 https:// 或 http:// 开头"));
            return false;
        }
        if (tokenValue.length() < 24) {
            token.setError(L10n.tr(this, "同步令牌至少需要 24 个字符"));
            return false;
        }
        if (password.length() < 8) {
            encryptionPassword.setError(L10n.tr(this, "加密密码至少需要 8 个字符"));
            return false;
        }
        settings.save(url, tokenValue, password, autoSync.isChecked());
        if (showToast) {
            setBusy(false, "同步设置已保存");
            LocalizedToast.makeText(this, "同步设置已保存", Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    private void setBusy(boolean busy, String message) {
        for (TextView button : new TextView[]{saveButton, testButton, syncButton,
                uploadButton, downloadButton}) {
            button.setEnabled(!busy);
            button.setAlpha(busy ? 0.5f : 1f);
        }
        status.setText(message);
        status.setTextColor(MiuixUi.color(
                this, busy ? MiuixUi.TEXT_SECONDARY : MiuixUi.GREEN));
    }

    private void showError(Exception error) {
        runOnUiThread(() -> {
            String message = error.getMessage() == null
                    ? error.getClass().getSimpleName() : error.getMessage();
            setBusy(false, message);
            status.setTextColor(MiuixUi.color(this, MiuixUi.DANGER));
            LocalizedToast.makeText(this, message, Toast.LENGTH_LONG).show();
        });
    }

    private String statusText() {
        if (settings.lastSyncAt() <= 0) return settings.lastStatus();
        String time = new SimpleDateFormat(
                "yyyy-MM-dd HH:mm", Locale.getDefault()).format(
                new Date(settings.lastSyncAt()));
        return settings.lastStatus() + " · " + time;
    }

    private LinearLayout.LayoutParams weightedButtonParams(int leftMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, MiuixUi.dp(this, 48), 1);
        params.leftMargin = MiuixUi.dp(this, leftMargin);
        return params;
    }

    private void addToCard(LinearLayout card, View child, int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = MiuixUi.dp(this, top);
        card.addView(child, params);
    }

    private void addWithMargins(
            LinearLayout parent, View child,
            int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(MiuixUi.dp(this, left), MiuixUi.dp(this, top),
                MiuixUi.dp(this, right), MiuixUi.dp(this, bottom));
        parent.addView(child, params);
    }
}
