package app.miuix.tavern.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import app.miuix.tavern.data.LocalStore;
import app.miuix.tavern.model.CharacterCard;
import app.miuix.tavern.model.GroupChat;
import app.miuix.tavern.util.MediaAttachmentStore;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class ChatInfoActivity extends AppCompatActivity {
    public static final String EXTRA_CHARACTER_ID = "character_id";
    public static final String EXTRA_GROUP_ID = "group_id";
    private static final int REQUEST_BACKGROUND = 9201;
    private static final int PAGE_GRAY = Color.rgb(237, 237, 237);

    private LocalStore store;
    private CharacterCard character;
    private GroupChat group;
    private final List<CharacterCard> members = new ArrayList<>();
    private TextView groupNameValue;
    private TextView groupSummary;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MiuixUi.applySystemBars(
                this, Color.rgb(245, 245, 246), PAGE_GRAY);
        store = new LocalStore(this);
        String characterId = getIntent().getStringExtra(EXTRA_CHARACTER_ID);
        String groupId = getIntent().getStringExtra(EXTRA_GROUP_ID);
        if (!TextUtils.isEmpty(groupId)) {
            group = store.getGroup(groupId);
            if (group != null) loadMembers();
        } else if (!TextUtils.isEmpty(characterId)) {
            character = store.getCharacter(characterId);
        }
        if (character == null && group == null) {
            LocalizedToast.makeText(this, "找不到这个会话", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        setContentView(buildContent());
    }

    private void loadMembers() {
        members.clear();
        for (String memberId : group.members) {
            CharacterCard card = store.getCharacter(memberId);
            if (card == null) card = store.getCharacterBySourceAvatar(memberId);
            if (card != null) members.add(card);
        }
    }

    private View buildContent() {
        LinearLayout root = MiuixUi.vertical(this);
        root.setBackgroundColor(MiuixUi.chatBackground(this));
        root.addView(buildToolbar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, MiuixUi.dp(this, 62)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout page = MiuixUi.vertical(this);
        page.setBackgroundColor(MiuixUi.chatBackground(this));
        scroll.addView(page, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        page.addView(buildMembers());
        page.addView(sectionGap());
        if (group != null) {
            page.addView(actionValueRow(
                    "群聊名称", group.name, v -> showRenameGroupDialog()));
            page.addView(sectionGap());
        }
        page.addView(actionRow("查找聊天记录", v -> openSearch()));
        page.addView(divider());
        page.addView(switchRow("消息免打扰", isMuted(), checked -> {
            if (group != null) group.muted = checked;
            else character.muted = checked;
            persistSettings();
        }));
        page.addView(divider());
        page.addView(switchRow("置顶聊天", isPinned(), checked -> {
            if (group != null) group.pinned = checked;
            else character.pinned = checked;
            persistSettings();
        }));
        page.addView(sectionGap());
        page.addView(actionRow("设置当前聊天背景", v -> chooseBackground()));
        page.addView(sectionGap());
        TextView clear = MiuixUi.text(this, "清空聊天记录", 17, MiuixUi.DANGER, false);
        clear.setGravity(Gravity.CENTER);
        clear.setBackgroundColor(MiuixUi.surface(this));
        clear.setOnClickListener(v -> confirmClear());
        clear.setClickable(true);
        clear.setFocusable(true);
        MiuixUi.ripple(clear, Color.WHITE, 0);
        MiuixUi.pressable(clear, 0.985f);
        page.addView(clear, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, MiuixUi.dp(this, 58)));
        page.addView(new View(this), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, MiuixUi.dp(this, 120)));

        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        return root;
    }

    private View buildToolbar() {
        LinearLayout toolbar = MiuixUi.horizontal(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(MiuixUi.dp(this, 7), MiuixUi.dp(this, 3),
                MiuixUi.dp(this, 7), MiuixUi.dp(this, 3));
        toolbar.setBackgroundColor(
                MiuixUi.color(this, Color.rgb(245, 245, 246)));

        FrameLayout back = iconButton(LineIconView.BACK, "返回");
        back.setOnClickListener(v -> finish());
        toolbar.addView(back, new LinearLayout.LayoutParams(
                MiuixUi.dp(this, 48), MiuixUi.dp(this, 52)));

        TextView title = MiuixUi.text(this, "聊天信息", 17, MiuixUi.TEXT_PRIMARY, true);
        title.setGravity(Gravity.CENTER);
        toolbar.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        toolbar.addView(new View(this), new LinearLayout.LayoutParams(
                MiuixUi.dp(this, 48), MiuixUi.dp(this, 52)));
        return toolbar;
    }

    private View buildMembers() {
        if (group == null) {
            LinearLayout row = MiuixUi.horizontal(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(MiuixUi.dp(this, 18), MiuixUi.dp(this, 22),
                    MiuixUi.dp(this, 18), MiuixUi.dp(this, 22));
            row.setBackgroundColor(MiuixUi.surface(this));
            AvatarView avatar = memberAvatar(character);
            row.addView(avatar, new LinearLayout.LayoutParams(
                    MiuixUi.dp(this, 62), MiuixUi.dp(this, 62)));
            TextView name = MiuixUi.rawText(
                    this, character.name, 18, MiuixUi.TEXT_PRIMARY, true);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            params.leftMargin = MiuixUi.dp(this, 15);
            row.addView(name, params);
            TextView arrow = MiuixUi.text(
                    this, "›", 32, Color.rgb(180, 180, 184), false);
            arrow.setGravity(Gravity.CENTER);
            row.addView(arrow, new LinearLayout.LayoutParams(
                    MiuixUi.dp(this, 28), MiuixUi.dp(this, 42)));
            row.setOnClickListener(v -> openCharacter(character));
            row.setClickable(true);
            MiuixUi.ripple(row, Color.WHITE, 0);
            MiuixUi.pressable(row, 0.985f);
            return row;
        }

        LinearLayout section = MiuixUi.vertical(this);
        section.setPadding(MiuixUi.dp(this, 11), MiuixUi.dp(this, 18),
                MiuixUi.dp(this, 11), MiuixUi.dp(this, 14));
        section.setBackgroundColor(MiuixUi.surface(this));
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(5);
        for (CharacterCard member : members) {
            LinearLayout item = MiuixUi.vertical(this);
            item.setGravity(Gravity.CENTER_HORIZONTAL);
            AvatarView avatar = memberAvatar(member);
            item.addView(avatar, new LinearLayout.LayoutParams(
                    MiuixUi.dp(this, 52), MiuixUi.dp(this, 52)));
            TextView name = MiuixUi.rawText(
                    this, member.name, 12, MiuixUi.TEXT_SECONDARY, false);
            name.setGravity(Gravity.CENTER);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, MiuixUi.dp(this, 28));
            nameParams.topMargin = MiuixUi.dp(this, 4);
            item.addView(name, nameParams);
            item.setOnClickListener(v -> openCharacter(member));
            item.setClickable(true);
            MiuixUi.pressable(item, 0.95f);
            GridLayout.LayoutParams itemParams = new GridLayout.LayoutParams();
            itemParams.width = getResources().getDisplayMetrics().widthPixels / 5
                    - MiuixUi.dp(this, 5);
            itemParams.height = MiuixUi.dp(this, 92);
            grid.addView(item, itemParams);
        }
        section.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        groupSummary = MiuixUi.text(
                this, group.name + " · " + members.size() + " 人",
                14, MiuixUi.TEXT_SECONDARY, false);
        groupSummary.setGravity(Gravity.CENTER);
        section.addView(groupSummary, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, MiuixUi.dp(this, 28)));
        return section;
    }

    private AvatarView memberAvatar(CharacterCard card) {
        AvatarView avatar = new AvatarView(this);
        avatar.setCharacter(card);
        avatar.setContentDescription(
                L10n.tr(this, "查看" + card.name + "的资料"));
        return avatar;
    }

    private View actionRow(String label, View.OnClickListener listener) {
        LinearLayout row = MiuixUi.horizontal(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(MiuixUi.dp(this, 18), 0, MiuixUi.dp(this, 12), 0);
        row.setBackgroundColor(MiuixUi.surface(this));
        TextView text = MiuixUi.text(this, label, 17, MiuixUi.TEXT_PRIMARY, false);
        row.addView(text, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        TextView arrow = MiuixUi.text(
                this, "›", 32, Color.rgb(180, 180, 184), false);
        arrow.setGravity(Gravity.CENTER);
        row.addView(arrow, new LinearLayout.LayoutParams(
                MiuixUi.dp(this, 28), MiuixUi.dp(this, 42)));
        row.setOnClickListener(listener);
        row.setClickable(true);
        row.setFocusable(true);
        MiuixUi.ripple(row, Color.WHITE, 0);
        MiuixUi.pressable(row, 0.99f);
        row.setMinimumHeight(MiuixUi.dp(this, 58));
        return row;
    }

    private View actionValueRow(
            String label, String value, View.OnClickListener listener) {
        LinearLayout row = MiuixUi.horizontal(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(MiuixUi.dp(this, 18), 0, MiuixUi.dp(this, 12), 0);
        row.setBackgroundColor(MiuixUi.surface(this));
        TextView text = MiuixUi.text(
                this, label, 17, MiuixUi.TEXT_PRIMARY, false);
        row.addView(text, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        groupNameValue = MiuixUi.rawText(
                this, value, 15, MiuixUi.TEXT_SECONDARY, false);
        groupNameValue.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        groupNameValue.setSingleLine(true);
        groupNameValue.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(groupNameValue, new LinearLayout.LayoutParams(
                MiuixUi.dp(this, 160), ViewGroup.LayoutParams.MATCH_PARENT));
        TextView arrow = MiuixUi.text(
                this, "›", 32, Color.rgb(180, 180, 184), false);
        arrow.setGravity(Gravity.CENTER);
        row.addView(arrow, new LinearLayout.LayoutParams(
                MiuixUi.dp(this, 28), MiuixUi.dp(this, 42)));
        row.setOnClickListener(listener);
        row.setClickable(true);
        row.setFocusable(true);
        MiuixUi.ripple(row, Color.WHITE, 0);
        MiuixUi.pressable(row, 0.99f);
        row.setMinimumHeight(MiuixUi.dp(this, 58));
        return row;
    }

    private void showRenameGroupDialog() {
        if (group == null) return;
        EditText input = MiuixUi.field(this, "输入新的群聊名称", false);
        input.setText(group.name);
        input.setSelection(input.length());
        input.setSingleLine(true);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(50)});
        LinearLayout container = MiuixUi.vertical(this);
        int horizontal = MiuixUi.dp(this, 20);
        container.setPadding(horizontal, MiuixUi.dp(this, 6),
                horizontal, MiuixUi.dp(this, 2));
        container.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, MiuixUi.dp(this, 50)));

        LocalizedAlertDialogBuilder builder = new LocalizedAlertDialogBuilder(this);
        builder.setTitle("修改群聊名称");
        builder.setView(container);
        builder.setNegativeButton("取消", null);
        builder.setPositiveButton("保存", null);
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String name = input.getText().toString().trim();
                if (name.isEmpty()) {
                    input.setError(L10n.tr(this, "群聊名称不能为空"));
                    return;
                }
                group.name = name;
                store.upsertGroup(group);
                if (groupNameValue != null) groupNameValue.setText(name);
                if (groupSummary != null) {
                    String summary = name + " · " + members.size() + " 人";
                    groupSummary.setText(L10n.tr(this, summary));
                }
                setResult(Activity.RESULT_OK);
                LocalizedToast.makeText(
                        this, "群聊名称已修改", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });
            input.requestFocus();
            dialog.getWindow().setSoftInputMode(
                    android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        });
        dialog.show();
    }

    private View switchRow(String label, boolean checked, SwitchListener listener) {
        LinearLayout row = MiuixUi.horizontal(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(MiuixUi.dp(this, 18), 0, MiuixUi.dp(this, 14), 0);
        row.setBackgroundColor(MiuixUi.surface(this));
        TextView text = MiuixUi.text(this, label, 17, MiuixUi.TEXT_PRIMARY, false);
        row.addView(text, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        SwitchCompat toggle = new SwitchCompat(this);
        toggle.setChecked(checked);
        toggle.setOnCheckedChangeListener((button, value) -> listener.onChanged(value));
        row.addView(toggle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, MiuixUi.dp(this, 48)));
        row.setOnClickListener(v -> toggle.setChecked(!toggle.isChecked()));
        row.setClickable(true);
        row.setMinimumHeight(MiuixUi.dp(this, 58));
        return row;
    }

    private void openCharacter(CharacterCard card) {
        Intent intent = new Intent(this, CharacterProfileActivity.class);
        intent.putExtra(CharacterProfileActivity.EXTRA_CHARACTER_ID, card.id);
        startActivity(intent);
    }

    private void openSearch() {
        Intent intent = new Intent(this, ChatSearchActivity.class);
        if (group != null) intent.putExtra(ChatSearchActivity.EXTRA_GROUP_ID, group.id);
        else intent.putExtra(ChatSearchActivity.EXTRA_CHARACTER_ID, character.id);
        startActivity(intent);
    }

    private void chooseBackground() {
        if (TextUtils.isEmpty(backgroundPath())) {
            openBackgroundPicker();
            return;
        }
        new LocalizedAlertDialogBuilder(this)
                .setTitle("设置当前聊天背景")
                .setItems(new String[]{"从相册选择", "恢复默认背景"}, (dialog, which) -> {
                    if (which == 0) openBackgroundPicker();
                    else {
                        setBackgroundPath("");
                        persistSettings();
                        LocalizedToast.makeText(this, "已恢复默认背景", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void openBackgroundPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_BACKGROUND);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_BACKGROUND || resultCode != Activity.RESULT_OK
                || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        LocalizedToast.makeText(this, "正在设置聊天背景…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                String path = MediaAttachmentStore.saveGalleryImage(this, uri);
                runOnUiThread(() -> {
                    setBackgroundPath(path);
                    persistSettings();
                    LocalizedToast.makeText(this, "聊天背景已更新", Toast.LENGTH_SHORT).show();
                });
            } catch (IOException | RuntimeException error) {
                runOnUiThread(() -> LocalizedToast.makeText(
                        this, error.getMessage(), Toast.LENGTH_LONG).show());
            }
        }, "chat-background-import").start();
    }

    private void confirmClear() {
        String title = group == null
                ? "清空与 " + character.name + " 的聊天？"
                : "清空“" + group.name + "”的聊天？";
        new LocalizedAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage("本机保存的消息会被删除，角色卡和聊天设置不受影响。")
                .setNegativeButton("取消", null)
                .setPositiveButton("清空", (dialog, which) -> {
                    store.saveMessages(conversationId(), new ArrayList<>());
                    LocalizedToast.makeText(this, "聊天记录已清空", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private String conversationId() {
        return group == null ? character.id : group.conversationId();
    }

    private boolean isMuted() {
        return group == null ? character.muted : group.muted;
    }

    private boolean isPinned() {
        return group == null ? character.pinned : group.pinned;
    }

    private String backgroundPath() {
        return group == null ? character.chatBackgroundPath : group.chatBackgroundPath;
    }

    private void setBackgroundPath(String path) {
        if (group == null) character.chatBackgroundPath = path;
        else group.chatBackgroundPath = path;
    }

    private void persistSettings() {
        if (group == null) store.upsertCharacter(character);
        else store.upsertGroup(group);
    }

    private FrameLayout iconButton(int type, String description) {
        FrameLayout button = new FrameLayout(this);
        button.setContentDescription(L10n.tr(this, description));
        LineIconView icon = new LineIconView(this);
        icon.setType(type);
        button.addView(icon, new FrameLayout.LayoutParams(
                MiuixUi.dp(this, 27), MiuixUi.dp(this, 27), Gravity.CENTER));
        MiuixUi.ripple(button, Color.TRANSPARENT, 22);
        MiuixUi.pressable(button, 0.9f);
        return button;
    }

    private View divider() {
        return MiuixUi.divider(this, 18);
    }

    private View sectionGap() {
        View gap = new View(this);
        gap.setBackgroundColor(MiuixUi.chatBackground(this));
        gap.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, MiuixUi.dp(this, 10)));
        return gap;
    }

    private interface SwitchListener {
        void onChanged(boolean checked);
    }
}
