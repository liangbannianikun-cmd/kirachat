package app.miuix.tavern.ui;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import app.miuix.tavern.data.LocalStore;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Manual local backup and full restore. Credentials intentionally stay outside the archive. */
public final class BackupRestoreActivity extends AppCompatActivity {
    private static final int REQUEST_EXPORT = 4101;
    private static final int REQUEST_IMPORT = 4102;
    private static final int MAX_BACKUP_BYTES = 180 * 1024 * 1024;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private LocalStore store;
    private TextView status;
    private TextView exportButton;
    private TextView restoreButton;
    private JSONObject pendingRestore;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MiuixUi.applySystemBars(this, MiuixUi.BACKGROUND, MiuixUi.BACKGROUND);
        store = new LocalStore(this);
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

        TextView title = MiuixUi.text(
                this, "备份与还原", 31, MiuixUi.TEXT_PRIMARY, true);
        title.setLetterSpacing(-0.025f);
        addWithMargins(page, title, 18, 12, 18, 5);
        TextView intro = MiuixUi.text(
                this,
                "把角色、群聊、聊天记录、世界书、头像、聊天背景和普通设置保存为一个 JSON 文件。",
                14, MiuixUi.TEXT_SECONDARY, false);
        intro.setLineSpacing(MiuixUi.dp(this, 3), 1.05f);
        addWithMargins(page, intro, 18, 0, 18, 16);

        LinearLayout backupCard = MiuixUi.card(this);
        backupCard.addView(MiuixUi.text(
                this, "创建备份", 18, MiuixUi.TEXT_PRIMARY, true));
        TextView backupBody = MiuixUi.text(
                this,
                "选择保存位置后生成备份文件。API Key、GPT/Copilot 登录令牌和语音服务凭据不会写入文件。",
                13, MiuixUi.TEXT_SECONDARY, false);
        backupBody.setLineSpacing(MiuixUi.dp(this, 2), 1.04f);
        addToCard(backupCard, backupBody, 7);
        exportButton = MiuixUi.pillButton(this, "导出备份", true);
        exportButton.setOnClickListener(v -> chooseExportDestination());
        addButton(backupCard, exportButton);
        addWithMargins(page, backupCard, 14, 0, 14, 12);

        LinearLayout restoreCard = MiuixUi.card(this);
        restoreCard.addView(MiuixUi.text(
                this, "还原备份", 18, MiuixUi.TEXT_PRIMARY, true));
        TextView restoreBody = MiuixUi.text(
                this,
                "还原会替换本机现有角色、群聊、聊天记录和普通设置。选择文件后会再次确认。",
                13, MiuixUi.TEXT_SECONDARY, false);
        restoreBody.setLineSpacing(MiuixUi.dp(this, 2), 1.04f);
        addToCard(restoreCard, restoreBody, 7);
        restoreButton = MiuixUi.pillButton(this, "选择备份文件", false);
        restoreButton.setOnClickListener(v -> chooseBackupFile());
        addButton(restoreCard, restoreButton);
        addWithMargins(page, restoreCard, 14, 0, 14, 12);

        LinearLayout privacyCard = MiuixUi.card(this);
        privacyCard.addView(MiuixUi.text(
                this, "安全说明", 17, MiuixUi.TEXT_PRIMARY, true));
        TextView privacy = MiuixUi.text(
                this,
                "备份未加密，可能包含私人聊天、图片和角色设定。请保存到可信位置，不要公开分享。还原不会覆盖当前设备 KeyStore 中的密钥和账户令牌。",
                13, MiuixUi.TEXT_SECONDARY, false);
        privacy.setLineSpacing(MiuixUi.dp(this, 2), 1.04f);
        addToCard(privacyCard, privacy, 7);
        addWithMargins(page, privacyCard, 14, 0, 14, 12);

        status = MiuixUi.text(
                this, "尚未执行备份或还原", 13, MiuixUi.TEXT_SECONDARY, false);
        status.setGravity(Gravity.CENTER);
        addWithMargins(page, status, 18, 2, 18, 0);
        return root;
    }

    private View buildToolbar() {
        LinearLayout toolbar = MiuixUi.horizontal(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(MiuixUi.dp(this, 7), MiuixUi.dp(this, 3),
                MiuixUi.dp(this, 7), MiuixUi.dp(this, 3));
        toolbar.setBackgroundColor(MiuixUi.background(this));
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

    private void chooseExportDestination() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        String stamp = new SimpleDateFormat(
                "yyyyMMdd-HHmmss", Locale.US).format(new Date());
        intent.putExtra(Intent.EXTRA_TITLE, "KiraChat-backup-" + stamp + ".json");
        startActivityForResult(intent, REQUEST_EXPORT);
    }

    private void chooseBackupFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        startActivityForResult(intent, REQUEST_IMPORT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        if (requestCode == REQUEST_EXPORT) exportBackup(data.getData());
        else if (requestCode == REQUEST_IMPORT) readBackup(data.getData());
    }

    private void exportBackup(Uri target) {
        setBusy(true, "正在创建备份…");
        executor.execute(() -> {
            try (OutputStream output = getContentResolver().openOutputStream(target, "w")) {
                if (output == null) throw new Exception("无法打开保存位置");
                byte[] data = store.createBackup().toString().getBytes(StandardCharsets.UTF_8);
                output.write(data);
                output.flush();
                runOnUiThread(() -> {
                    setBusy(false, "备份已保存");
                    LocalizedToast.makeText(
                            this, "备份已导出", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception error) {
                showError("备份失败：" + readable(error));
            }
        });
    }

    private void readBackup(Uri source) {
        setBusy(true, "正在读取备份…");
        executor.execute(() -> {
            try (InputStream input = getContentResolver().openInputStream(source)) {
                if (input == null) throw new Exception("无法打开所选文件");
                byte[] buffer = new byte[8192];
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (output.size() + count > MAX_BACKUP_BYTES) {
                        throw new Exception("备份文件不能超过 180 MB");
                    }
                    output.write(buffer, 0, count);
                }
                JSONObject root = new JSONObject(
                        new String(output.toByteArray(), StandardCharsets.UTF_8));
                if (!"kirachat-android-backup".equals(root.optString("format", ""))) {
                    throw new Exception("这不是澄语 Android 备份文件");
                }
                pendingRestore = root;
                String summary = LocalStore.backupSummary(root);
                runOnUiThread(() -> confirmRestore(summary));
            } catch (Exception error) {
                showError("读取备份失败：" + readable(error));
            }
        });
    }

    private void confirmRestore(String summary) {
        setBusy(false, "备份已读取 · " + summary);
        new LocalizedAlertDialogBuilder(this)
                .setTitle("还原此备份？")
                .setMessage(summary + "\n\n"
                        + "本机现有角色、群聊、聊天记录和普通设置将被替换。"
                        + "API Key 与账户令牌保持不变。此操作无法撤销。")
                .setNegativeButton("取消", (dialog, which) -> pendingRestore = null)
                .setPositiveButton("还原", (dialog, which) -> performRestore())
                .show();
    }

    private void performRestore() {
        JSONObject backup = pendingRestore;
        pendingRestore = null;
        if (backup == null) return;
        setBusy(true, "正在还原备份…");
        executor.execute(() -> {
            try {
                store.restoreBackup(backup);
                runOnUiThread(() -> {
                    setBusy(false, "备份还原完成");
                    LocalizedToast.makeText(
                            this, "已还原备份", Toast.LENGTH_LONG).show();
                });
            } catch (Exception error) {
                showError("还原失败：" + readable(error));
            }
        });
    }

    private void setBusy(boolean busy, String message) {
        exportButton.setEnabled(!busy);
        restoreButton.setEnabled(!busy);
        exportButton.setAlpha(busy ? 0.5f : 1f);
        restoreButton.setAlpha(busy ? 0.5f : 1f);
        status.setText(message);
        status.setTextColor(MiuixUi.color(
                this, busy ? MiuixUi.TEXT_SECONDARY : MiuixUi.GREEN));
    }

    private void showError(String message) {
        runOnUiThread(() -> {
            setBusy(false, message);
            status.setTextColor(Color.rgb(198, 52, 58));
            LocalizedToast.makeText(this, message, Toast.LENGTH_LONG).show();
        });
    }

    private static String readable(Exception error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
                ? error.getClass().getSimpleName() : message.trim();
    }

    private void addButton(LinearLayout card, View button) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, MiuixUi.dp(this, 48));
        params.topMargin = MiuixUi.dp(this, 14);
        card.addView(button, params);
    }

    private void addWithMargins(
            LinearLayout parent, View child,
            int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(MiuixUi.dp(this, left), MiuixUi.dp(this, top),
                MiuixUi.dp(this, right), MiuixUi.dp(this, bottom));
        parent.addView(child, params);
    }

    private void addToCard(LinearLayout card, View child, int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = MiuixUi.dp(this, top);
        card.addView(child, params);
    }
}
