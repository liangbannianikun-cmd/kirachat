package app.miuix.tavern.ui;

import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import app.miuix.tavern.data.LocalStore;
import app.miuix.tavern.model.CharacterCard;
import app.miuix.tavern.model.ChatMessage;
import app.miuix.tavern.util.CharacterCardImporter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public final class CharacterProfileActivity extends AppCompatActivity {
    public static final String EXTRA_CHARACTER_ID = "character_id";
    private static final int REQUEST_AVATAR = 4204;
    private static final int REQUEST_VOICE_CALL = 4205;
    private static final int REQUEST_REPLACE_CARD = 4206;
    private static final long MAX_AVATAR_BYTES = 15L * 1024L * 1024L;

    private static final int ACTION_BLUE = Color.rgb(87, 107, 149);
    private static final int PAGE_GRAY = Color.rgb(237, 237, 237);

    private LocalStore store;
    private CharacterCard character;
    private int displayedLoreEntryCount;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MiuixUi.applySystemBars(this, Color.WHITE, PAGE_GRAY);

        store = new LocalStore(this);
        String id = getIntent().getStringExtra(EXTRA_CHARACTER_ID);
        character = store.getCharacter(id == null ? "" : id);
        if (character == null) {
            LocalizedToast.makeText(this, "角色不存在", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        setContentView(buildContent());
    }

    private View buildContent() {
        displayedLoreEntryCount = character.loreEntryCount();
        LinearLayout root = MiuixUi.vertical(this);
        root.setBackgroundColor(MiuixUi.chatBackground(this));
        root.addView(buildToolbar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, MiuixUi.dp(this, 62)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        LinearLayout page = MiuixUi.vertical(this);
        page.setBackgroundColor(MiuixUi.chatBackground(this));
        scroll.addView(page, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        page.addView(buildProfileHeader());
        page.addView(divider(18));
        page.addView(detailRow(
                "角色资料", "描述、性格与场景", true, v -> showCharacterInfo()));
        page.addView(divider(18));
        page.addView(detailRow(
                "开场白", preview(displayText(character.firstMessage), "暂无开场白"), true,
                v -> showText("开场白", displayText(character.firstMessage), "这个角色还没有开场白。")));

        page.addView(sectionGap());
        page.addView(detailRow(
                "角色描述", preview(displayText(character.description), "暂无角色描述"), true,
                v -> showText("角色描述", displayText(character.description), "这个角色还没有描述。")));
        page.addView(divider(18));
        page.addView(detailRow(
                "世界书",
                displayedLoreEntryCount > 0
                        ? displayedLoreEntryCount + " 条条目"
                        : "未关联",
                displayedLoreEntryCount > 0,
                displayedLoreEntryCount > 0 ? v -> showWorldBookSummary() : null));
        page.addView(divider(18));
        page.addView(detailRow(
                "创作者备注", preview(displayText(character.creatorNotes), "暂无备注"), true,
                v -> showText("创作者备注", displayText(character.creatorNotes), "这个角色还没有创作者备注。")));

        page.addView(sectionGap());
        page.addView(actionRow(
                LineIconView.CHAT, "发消息", "开始与" + character.name + "聊天", v -> openChat()));
        page.addView(divider(0));
        page.addView(actionRow(
                LineIconView.PHONE, "语音通话", "与" + character.name + "进行语音通话",
                v -> openVoiceCall()));

        View footer = new View(this);
        page.addView(footer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, MiuixUi.dp(this, 160)));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        return root;
    }

    private View buildToolbar() {
        LinearLayout toolbar = MiuixUi.horizontal(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(MiuixUi.dp(this, 7), MiuixUi.dp(this, 3),
                MiuixUi.dp(this, 7), MiuixUi.dp(this, 3));
        toolbar.setBackgroundColor(MiuixUi.surface(this));

        FrameLayout back = iconButton(LineIconView.BACK, "返回");
        back.setOnClickListener(v -> finish());
        toolbar.addView(back, new LinearLayout.LayoutParams(
                MiuixUi.dp(this, 48), MiuixUi.dp(this, 52)));

        View spacer = new View(this);
        toolbar.addView(spacer, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1));

        FrameLayout more = iconButton(LineIconView.MORE, "更多");
        more.setOnClickListener(this::showMoreMenu);
        toolbar.addView(more, new LinearLayout.LayoutParams(
                MiuixUi.dp(this, 48), MiuixUi.dp(this, 52)));
        return toolbar;
    }

    private View buildProfileHeader() {
        LinearLayout header = MiuixUi.horizontal(this);
        header.setGravity(Gravity.TOP);
        header.setPadding(
                MiuixUi.dp(this, 18), MiuixUi.dp(this, 26),
                MiuixUi.dp(this, 18), MiuixUi.dp(this, 28));
        header.setBackgroundColor(MiuixUi.surface(this));

        FrameLayout avatarButton = new FrameLayout(this);
        avatarButton.setContentDescription(
                L10n.tr(this, "更换" + character.name + "的头像"));
        avatarButton.setClickable(true);
        avatarButton.setFocusable(true);
        avatarButton.setOnClickListener(v -> chooseAvatar());
        MiuixUi.pressable(avatarButton, 0.96f);

        AvatarView avatar = new AvatarView(this);
        avatar.setCharacter(character);
        avatarButton.addView(avatar, new FrameLayout.LayoutParams(
                MiuixUi.dp(this, 82), MiuixUi.dp(this, 82), Gravity.TOP | Gravity.START));

        TextView editBadge = MiuixUi.text(
                this, "✎", 15, MiuixUi.TEXT_PRIMARY, true);
        editBadge.setGravity(Gravity.CENTER);
        editBadge.setBackground(MiuixUi.outlinedShape(
                Color.WHITE, MiuixUi.HAIRLINE, 12, this));
        FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(
                MiuixUi.dp(this, 26), MiuixUi.dp(this, 26),
                Gravity.BOTTOM | Gravity.END);
        avatarButton.addView(editBadge, badgeParams);

        header.addView(avatarButton, new LinearLayout.LayoutParams(
                MiuixUi.dp(this, 86), MiuixUi.dp(this, 86)));

        LinearLayout details = MiuixUi.vertical(this);
        LinearLayout.LayoutParams detailsParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        detailsParams.leftMargin = MiuixUi.dp(this, 20);
        header.addView(details, detailsParams);

        TextView name = MiuixUi.rawText(
                this, character.name, 22, MiuixUi.TEXT_PRIMARY, true);
        name.setMaxLines(2);
        details.addView(name, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addMeta(details, "来源：" + sourceLabel());
        addMeta(details, "世界书：" + displayedLoreEntryCount + " 条");
        addMeta(details, "最近聊天：" + DateFormat.format(
                "yyyy-MM-dd HH:mm", new Date(character.lastUsed)));
        return header;
    }

    private void addMeta(LinearLayout parent, String value) {
        TextView line = MiuixUi.text(
                this, value, 14, MiuixUi.TEXT_SECONDARY, false);
        line.setMaxLines(1);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, MiuixUi.dp(this, 25));
        if (parent.getChildCount() == 1) params.topMargin = MiuixUi.dp(this, 7);
        parent.addView(line, params);
    }

    private View detailRow(String label, String value, boolean chevron,
                           @Nullable View.OnClickListener listener) {
        LinearLayout row = MiuixUi.horizontal(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(
                MiuixUi.dp(this, 18), MiuixUi.dp(this, 12),
                MiuixUi.dp(this, 12), MiuixUi.dp(this, 12));
        row.setMinimumHeight(MiuixUi.dp(this, 64));
        row.setBackgroundColor(MiuixUi.surface(this));

        TextView title = MiuixUi.text(
                this, label, 17, MiuixUi.TEXT_PRIMARY, false);
        row.addView(title, new LinearLayout.LayoutParams(
                MiuixUi.dp(this, 104), ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView summary = MiuixUi.text(
                this, value, 14, MiuixUi.TEXT_SECONDARY, false);
        summary.setMaxLines(2);
        summary.setLineSpacing(MiuixUi.dp(this, 2), 1f);
        row.addView(summary, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView arrow = MiuixUi.text(
                this, chevron ? "›" : "", 32, Color.rgb(180, 180, 184), false);
        arrow.setGravity(Gravity.CENTER);
        row.addView(arrow, new LinearLayout.LayoutParams(
                MiuixUi.dp(this, 28), MiuixUi.dp(this, 42)));

        if (listener != null) {
            row.setClickable(true);
            row.setFocusable(true);
            row.setOnClickListener(listener);
            MiuixUi.ripple(row, Color.WHITE, 0);
            MiuixUi.pressable(row, 0.992f);
        }
        return row;
    }

    private View actionRow(int iconType, String label, String description,
                           View.OnClickListener listener) {
        LinearLayout row = MiuixUi.horizontal(this);
        row.setGravity(Gravity.CENTER);
        row.setBackgroundColor(MiuixUi.surface(this));
        row.setContentDescription(L10n.tr(this, description));
        row.setOnClickListener(listener);
        row.setClickable(true);
        row.setFocusable(true);
        MiuixUi.ripple(row, Color.WHITE, 0);
        MiuixUi.pressable(row, 0.985f);

        LineIconView icon = new LineIconView(this);
        icon.setType(iconType);
        icon.setTintColor(ACTION_BLUE);
        row.addView(icon, new LinearLayout.LayoutParams(
                MiuixUi.dp(this, 34), MiuixUi.dp(this, 34)));

        TextView text = MiuixUi.text(this, label, 18, ACTION_BLUE, true);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, MiuixUi.dp(this, 72));
        textParams.leftMargin = MiuixUi.dp(this, 9);
        row.addView(text, textParams);
        row.setMinimumHeight(MiuixUi.dp(this, 76));
        return row;
    }

    private FrameLayout iconButton(int type, String description) {
        FrameLayout button = new FrameLayout(this);
        button.setContentDescription(L10n.tr(this, description));
        button.setClickable(true);
        button.setFocusable(true);
        LineIconView icon = new LineIconView(this);
        icon.setType(type);
        button.addView(icon, new FrameLayout.LayoutParams(
                MiuixUi.dp(this, 31), MiuixUi.dp(this, 31), Gravity.CENTER));
        MiuixUi.ripple(button, Color.TRANSPARENT, 24);
        MiuixUi.pressable(button, 0.9f);
        return button;
    }

    private View divider(int insetDp) {
        View divider = new View(this);
        divider.setBackgroundColor(MiuixUi.color(this, MiuixUi.HAIRLINE));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, MiuixUi.dp(this, 0.5f)));
        params.leftMargin = MiuixUi.dp(this, insetDp);
        divider.setLayoutParams(params);
        return divider;
    }

    private View sectionGap() {
        View gap = new View(this);
        gap.setBackgroundColor(MiuixUi.chatBackground(this));
        gap.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, MiuixUi.dp(this, 10)));
        return gap;
    }

    private void showMoreMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, L10n.tr(this, "更换头像"));
        if (!character.isBuiltIn()) {
            menu.getMenu().add(0, 7, 1, L10n.tr(this, "更换角色卡"));
        }
        menu.getMenu().add(0, 2, 2, L10n.tr(this, "发消息"));
        menu.getMenu().add(0, 3, 3, L10n.tr(this, "语音通话"));
        menu.getMenu().add(0, 4, 4, L10n.tr(this, "查看完整设定"));
        menu.getMenu().add(0, 5, 5, L10n.tr(this, "清空聊天记录"));
        menu.getMenu().add(0, 6, 6, L10n.tr(this, "删除角色"));
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) chooseAvatar();
            else if (item.getItemId() == 7) chooseReplacementCard();
            else if (item.getItemId() == 2) openChat();
            else if (item.getItemId() == 3) openVoiceCall();
            else if (item.getItemId() == 4) showCharacterInfo();
            else if (item.getItemId() == 5) confirmClearChat();
            else if (item.getItemId() == 6) confirmDeleteCharacter();
            return true;
        });
        menu.show();
    }

    private void confirmDeleteCharacter() {
        new LocalizedAlertDialogBuilder(this)
                .setTitle("删除角色“" + character.name + "”？")
                .setMessage("角色卡和单聊记录会从本机删除；包含该角色且不足两人的群聊也会被移除。此操作无法撤销。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    store.deleteCharacter(character.id);
                    LocalizedToast.makeText(this, "角色已删除", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .show();
    }

    private void openChat() {
        store.touchCharacter(character.id);
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra(ChatActivity.EXTRA_CHARACTER_ID, character.id);
        MiuixUi.startConversationActivity(this, intent);
    }

    private void openVoiceCall() {
        Intent intent = new Intent(this, VoiceCallActivity.class);
        intent.putExtra(VoiceCallActivity.EXTRA_CHARACTER_ID, character.id);
        startActivityForResult(intent, REQUEST_VOICE_CALL);
    }

    private void chooseAvatar() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_AVATAR);
    }

    private void chooseReplacementCard() {
        if (character.isBuiltIn()) return;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[]{"application/json", "image/png"});
        startActivityForResult(intent, REQUEST_REPLACE_CARD);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_VOICE_CALL) {
            if (resultCode == RESULT_OK && data != null) {
                addVoiceCallRecord(data.getLongExtra(
                        VoiceCallActivity.EXTRA_CALL_DURATION_SECONDS, 0));
            }
            return;
        }
        if (requestCode == REQUEST_REPLACE_CARD) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                Uri selected = data.getData();
                LocalizedToast.makeText(
                        this, "正在读取新角色卡…", Toast.LENGTH_SHORT).show();
                new Thread(() -> readReplacementCard(selected),
                        "character-card-replacement").start();
            }
            return;
        }
        if (requestCode != REQUEST_AVATAR
                || resultCode != RESULT_OK
                || data == null
                || data.getData() == null) {
            return;
        }
        Uri selected = data.getData();
        LocalizedToast.makeText(this, "正在更新头像…", Toast.LENGTH_SHORT).show();
        new Thread(() -> copyAvatar(selected), "avatar-import").start();
    }

    private void readReplacementCard(Uri selected) {
        try (InputStream input = getContentResolver().openInputStream(selected)) {
            if (input == null) throw new IllegalStateException("无法打开所选文件");
            CharacterCardImporter.Result result = CharacterCardImporter.parse(input);
            runOnUiThread(() -> confirmCardReplacement(result));
        } catch (Exception error) {
            String message = TextUtils.isEmpty(error.getMessage())
                    ? "无法读取角色卡" : error.getMessage();
            runOnUiThread(() -> LocalizedToast.makeText(
                    this,
                    L10n.tr(this, "更换角色卡失败") + "："
                            + L10n.tr(this, message),
                    Toast.LENGTH_LONG).show());
        }
    }

    private void confirmCardReplacement(CharacterCardImporter.Result result) {
        if (isFinishing() || character == null) return;
        CharacterCard conflict = null;
        String incomingName = result.card.name == null ? "" : result.card.name.trim();
        for (CharacterCard saved : store.getCharacters()) {
            String savedName = saved.name == null ? "" : saved.name.trim();
            if (!saved.id.equals(character.id)
                    && incomingName.equalsIgnoreCase(savedName)) {
                conflict = saved;
                break;
            }
        }
        String finalName = conflict == null ? incomingName : character.name;
        if (TextUtils.isEmpty(finalName)) finalName = character.name;
        String message = L10n.tr(this,
                "新角色卡将替换当前设定和头像；聊天记录、群聊成员、免打扰、置顶和聊天背景会保留。")
                + "\n\n" + character.name + " → " + finalName;
        if (conflict != null) {
            message += "\n\n" + L10n.tr(this,
                    "新角色卡名称与现有角色重复，因此将继续使用当前角色名称。");
        }
        String replacementName = finalName;
        new LocalizedAlertDialogBuilder(this)
                .setTitle("更换角色卡？")
                .setMessage(message)
                .setNegativeButton("取消", null)
                .setPositiveButton("更换", (dialog, which) -> {
                    LocalizedToast.makeText(
                            this, "正在更换角色卡…", Toast.LENGTH_SHORT).show();
                    new Thread(() -> replaceCharacterCard(result, replacementName),
                            "character-card-replacement-save").start();
                })
                .show();
    }

    private void replaceCharacterCard(
            CharacterCardImporter.Result result,
            String replacementName) {
        try {
            CharacterCard existing = store.getCharacter(character.id);
            if (existing == null) throw new IllegalStateException("角色不存在");
            CharacterCard updated = result.card;
            updated.id = existing.id;
            updated.name = replacementName;
            updated.lastUsed = existing.lastUsed;
            updated.unread = existing.unread;
            updated.muted = existing.muted;
            updated.pinned = existing.pinned;
            updated.chatBackgroundPath = existing.chatBackgroundPath;
            if (TextUtils.isEmpty(updated.sourceAvatar)) {
                updated.sourceAvatar = existing.sourceAvatar;
            }
            if (result.png) {
                File directory = new File(getFilesDir(), "avatars");
                if (!directory.isDirectory() && !directory.mkdirs()) {
                    throw new IllegalStateException("无法创建头像目录");
                }
                File avatar = new File(directory,
                        Integer.toHexString(existing.id.hashCode())
                                + "-card-" + System.currentTimeMillis() + ".png");
                try (FileOutputStream output = new FileOutputStream(avatar, false)) {
                    output.write(result.originalBytes);
                }
                updated.avatarPath = avatar.getAbsolutePath();
            } else {
                updated.avatarPath = existing.avatarPath;
            }
            store.upsertCharacter(updated);
            runOnUiThread(() -> {
                character = updated;
                setContentView(buildContent());
                LocalizedToast.makeText(
                        this, "角色卡已更换", Toast.LENGTH_SHORT).show();
            });
        } catch (Exception error) {
            String message = TextUtils.isEmpty(error.getMessage())
                    ? "无法保存角色卡" : error.getMessage();
            runOnUiThread(() -> LocalizedToast.makeText(
                    this,
                    L10n.tr(this, "更换角色卡失败") + "：" + message,
                    Toast.LENGTH_LONG).show());
        }
    }

    private void addVoiceCallRecord(long durationSeconds) {
        if (durationSeconds <= 0) return;
        List<ChatMessage> messages = store.getMessages(character.id);
        String duration = ChatMessage.formatCallDuration(durationSeconds);
        ChatMessage message = new ChatMessage(
                ChatMessage.USER, "通话时长 " + duration);
        message.attachmentType = ChatMessage.ATTACHMENT_VOICE_CALL;
        message.callDurationSeconds = durationSeconds;
        messages.add(message);
        store.saveMessages(character.id, messages);
        store.touchCharacter(character.id);
    }

    private void copyAvatar(Uri selected) {
        File temporary = null;
        try {
            File directory = new File(getFilesDir(), "avatars");
            if (!directory.isDirectory() && !directory.mkdirs()) {
                throw new IllegalStateException("无法创建头像目录");
            }
            String baseName = Integer.toHexString(character.id.hashCode());
            File target = new File(directory, baseName + ".avatar");
            temporary = new File(directory, baseName + ".avatar.tmp");
            long total = 0;
            try (InputStream input = getContentResolver().openInputStream(selected);
                 FileOutputStream output = new FileOutputStream(temporary, false)) {
                if (input == null) throw new IllegalStateException("无法读取所选图片");
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > MAX_AVATAR_BYTES) {
                        throw new IllegalStateException("头像图片不能超过 15 MB");
                    }
                    output.write(buffer, 0, count);
                }
                output.flush();
            }
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(temporary.getAbsolutePath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                throw new IllegalStateException("所选文件不是有效图片");
            }
            if (target.exists() && !target.delete()) {
                throw new IllegalStateException("无法替换旧头像");
            }
            if (!temporary.renameTo(target)) {
                throw new IllegalStateException("无法保存新头像");
            }
            character.avatarPath = target.getAbsolutePath();
            store.upsertCharacter(character);
            runOnUiThread(() -> {
                setContentView(buildContent());
                LocalizedToast.makeText(this, "头像已更新", Toast.LENGTH_SHORT).show();
            });
        } catch (Exception error) {
            if (temporary != null && temporary.exists()) temporary.delete();
            String message = error.getMessage();
            if (TextUtils.isEmpty(message)) message = "头像更新失败";
            final String display = message;
            runOnUiThread(() ->
                    LocalizedToast.makeText(this, display, Toast.LENGTH_LONG).show());
        }
    }

    private void showCharacterInfo() {
        StringBuilder detail = new StringBuilder();
        appendSection(detail, "描述", displayText(character.description));
        appendSection(detail, "性格", displayText(character.personality));
        appendSection(detail, "场景", displayText(character.scenario));
        appendSection(detail, "示例对话", displayText(character.exampleDialogue));
        if (displayedLoreEntryCount > 0) {
            appendSection(detail, "世界书", displayedLoreEntryCount + " 条条目");
        }
        if (detail.length() == 0) detail.append("这个角色还没有详细设定。");
        new LocalizedAlertDialogBuilder(this)
                .setTitle(character.name)
                .setMessage(detail.toString())
                .setPositiveButton("完成", null)
                .show();
    }

    private void showText(String title, String value, String fallback) {
        new LocalizedAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(TextUtils.isEmpty(value) ? fallback : value)
                .setPositiveButton("完成", null)
                .show();
    }

    private void showWorldBookSummary() {
        showText(
                "世界书",
                "此角色卡包含 " + displayedLoreEntryCount
                        + " 条世界书条目。聊天时会根据最近消息自动选取相关条目。",
                "这个角色没有关联世界书。");
    }

    private void confirmClearChat() {
        List<ChatMessage> saved = store.getMessages(character.id);
        if (saved.isEmpty()) {
            LocalizedToast.makeText(this, "当前没有聊天记录", Toast.LENGTH_SHORT).show();
            return;
        }
        new LocalizedAlertDialogBuilder(this)
                .setTitle("清空与 " + character.name + " 的聊天？")
                .setMessage("本机保存的消息会被删除，角色卡不受影响。")
                .setNegativeButton("取消", null)
                .setPositiveButton("清空", (dialog, which) -> {
                    store.saveMessages(character.id, new ArrayList<>());
                    LocalizedToast.makeText(this, "聊天记录已清空", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private String sourceLabel() {
        if (character.isBuiltIn()) return "应用内置";
        if (!TextUtils.isEmpty(character.sourceAvatar)) return "兼容角色卡";
        return "本地角色卡";
    }

    private String displayText(String value) {
        return character.replaceMacros(value, store.getConfig().persona);
    }

    private static String preview(String value, String fallback) {
        if (TextUtils.isEmpty(value)) return fallback;
        String singleLine = value.replace('\n', ' ').replace('\r', ' ').trim();
        return singleLine.length() > 46
                ? singleLine.substring(0, 46) + "…"
                : singleLine;
    }

    private static void appendSection(StringBuilder output, String label, String value) {
        if (TextUtils.isEmpty(value)) return;
        if (output.length() > 0) output.append("\n\n");
        output.append(label).append("\n").append(value);
    }
}
