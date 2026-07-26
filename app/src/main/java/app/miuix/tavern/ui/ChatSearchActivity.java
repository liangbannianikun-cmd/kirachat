package app.miuix.tavern.ui;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import app.miuix.tavern.data.LocalStore;
import app.miuix.tavern.model.CharacterCard;
import app.miuix.tavern.model.ChatMessage;
import app.miuix.tavern.model.GroupChat;

import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class ChatSearchActivity extends AppCompatActivity {
    public static final String EXTRA_CHARACTER_ID = "character_id";
    public static final String EXTRA_GROUP_ID = "group_id";

    private LocalStore store;
    private CharacterCard character;
    private GroupChat group;
    private List<ChatMessage> messages;
    private LinearLayout results;
    private EditText search;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MiuixUi.applySystemBars(
                this, Color.rgb(245, 245, 246), Color.rgb(237, 237, 237));
        store = new LocalStore(this);
        String groupId = getIntent().getStringExtra(EXTRA_GROUP_ID);
        String characterId = getIntent().getStringExtra(EXTRA_CHARACTER_ID);
        if (!TextUtils.isEmpty(groupId)) group = store.getGroup(groupId);
        else if (!TextUtils.isEmpty(characterId)) character = store.getCharacter(characterId);
        if (group == null && character == null) {
            LocalizedToast.makeText(this, "找不到这个会话", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        messages = store.getMessages(conversationId());
        setContentView(buildContent());
        search.post(() -> {
            search.requestFocus();
            InputMethodManager keyboard =
                    (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (keyboard != null) {
                keyboard.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT);
            }
        });
    }

    private View buildContent() {
        LinearLayout root = MiuixUi.vertical(this);
        root.setBackgroundColor(MiuixUi.chatBackground(this));
        root.addView(buildToolbar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, MiuixUi.dp(this, 62)));

        LinearLayout searchBox = MiuixUi.horizontal(this);
        searchBox.setPadding(MiuixUi.dp(this, 14), MiuixUi.dp(this, 8),
                MiuixUi.dp(this, 14), MiuixUi.dp(this, 10));
        searchBox.setBackgroundColor(
                MiuixUi.color(this, Color.rgb(245, 245, 246)));
        search = MiuixUi.field(this, "搜索聊天记录", false);
        search.setSingleLine(true);
        searchBox.addView(search, new LinearLayout.LayoutParams(
                0, MiuixUi.dp(this, 44), 1));
        root.addView(searchBox, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        results = MiuixUi.vertical(this);
        results.setBackgroundColor(MiuixUi.surface(this));
        scroll.addView(results, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        showHint("输入关键词查找当前会话中的消息");
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderResults(s == null ? "" : s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        return root;
    }

    private View buildToolbar() {
        LinearLayout toolbar = MiuixUi.horizontal(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(MiuixUi.dp(this, 7), MiuixUi.dp(this, 3),
                MiuixUi.dp(this, 7), MiuixUi.dp(this, 3));
        toolbar.setBackgroundColor(
                MiuixUi.color(this, Color.rgb(245, 245, 246)));
        FrameLayout back = new FrameLayout(this);
        LineIconView icon = new LineIconView(this);
        icon.setType(LineIconView.BACK);
        back.addView(icon, new FrameLayout.LayoutParams(
                MiuixUi.dp(this, 27), MiuixUi.dp(this, 27), Gravity.CENTER));
        back.setOnClickListener(v -> finish());
        back.setContentDescription(L10n.tr(this, "返回"));
        MiuixUi.ripple(back, Color.TRANSPARENT, 22);
        toolbar.addView(back, new LinearLayout.LayoutParams(
                MiuixUi.dp(this, 48), MiuixUi.dp(this, 52)));
        TextView title = MiuixUi.text(this, "查找聊天记录", 17,
                MiuixUi.TEXT_PRIMARY, true);
        title.setGravity(Gravity.CENTER);
        toolbar.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        toolbar.addView(new View(this), new LinearLayout.LayoutParams(
                MiuixUi.dp(this, 48), MiuixUi.dp(this, 52)));
        return toolbar;
    }

    private void renderResults(String query) {
        results.removeAllViews();
        String needle = query.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            showHint("输入关键词查找当前会话中的消息");
            return;
        }
        int count = 0;
        for (ChatMessage message : messages) {
            String content = searchableContent(message);
            if (!content.toLowerCase(Locale.ROOT).contains(needle)) continue;
            if (count > 0) results.addView(MiuixUi.divider(this, 18));
            results.addView(resultRow(message, content));
            count++;
        }
        if (count == 0) showHint("没有找到相关聊天记录");
    }

    private View resultRow(ChatMessage message, String content) {
        LinearLayout row = MiuixUi.vertical(this);
        row.setPadding(MiuixUi.dp(this, 18), MiuixUi.dp(this, 13),
                MiuixUi.dp(this, 18), MiuixUi.dp(this, 13));
        row.setBackgroundColor(MiuixUi.surface(this));
        LinearLayout header = MiuixUi.horizontal(this);
        TextView speaker = MiuixUi.rawText(
                this, speakerName(message), 14, MiuixUi.TEXT_PRIMARY, true);
        header.addView(speaker, new LinearLayout.LayoutParams(
                0, MiuixUi.dp(this, 24), 1));
        TextView time = MiuixUi.text(this,
                DateFormat.format("yyyy-MM-dd HH:mm", new Date(message.timestamp)).toString(),
                12, MiuixUi.TEXT_SECONDARY, false);
        time.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        header.addView(time, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, MiuixUi.dp(this, 24)));
        row.addView(header);
        TextView body = MiuixUi.rawText(
                this, content, 15, MiuixUi.TEXT_SECONDARY, false);
        body.setMaxLines(3);
        body.setEllipsize(TextUtils.TruncateAt.END);
        body.setLineSpacing(MiuixUi.dp(this, 2), 1f);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bodyParams.topMargin = MiuixUi.dp(this, 4);
        row.addView(body, bodyParams);
        row.setOnClickListener(v -> jumpToMessage(message.id));
        row.setClickable(true);
        row.setFocusable(true);
        MiuixUi.ripple(row, Color.WHITE, 0);
        MiuixUi.pressable(row, 0.99f);
        row.setMinimumHeight(MiuixUi.dp(this, 74));
        return row;
    }

    private String searchableContent(ChatMessage message) {
        if (message.hasImage()) {
            String label = L10n.tr(this, "[图片]");
            return TextUtils.isEmpty(message.content)
                    ? label : label + " " + message.content;
        }
        if (message.hasLocation()) {
            String label = L10n.tr(this, "[位置]");
            return TextUtils.isEmpty(message.content)
                    ? label : label + " " + message.content;
        }
        return message.content == null ? "" : message.content;
    }

    private String speakerName(ChatMessage message) {
        if (ChatMessage.USER.equals(message.role)) {
            String persona = store.getConfig().persona;
            return TextUtils.isEmpty(persona) ? L10n.tr(this, "我") : persona;
        }
        if (!TextUtils.isEmpty(message.speaker)) return message.speaker;
        return character == null ? L10n.tr(this, "群成员") : character.name;
    }

    private void jumpToMessage(String messageId) {
        Intent intent;
        if (group != null) {
            intent = new Intent(this, GroupChatActivity.class);
            intent.putExtra(GroupChatActivity.EXTRA_GROUP_ID, group.id);
            intent.putExtra(GroupChatActivity.EXTRA_MESSAGE_ID, messageId);
        } else {
            intent = new Intent(this, ChatActivity.class);
            intent.putExtra(ChatActivity.EXTRA_CHARACTER_ID, character.id);
            intent.putExtra(ChatActivity.EXTRA_MESSAGE_ID, messageId);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }

    private String conversationId() {
        return group == null ? character.id : group.conversationId();
    }

    private void showHint(String value) {
        TextView hint = MiuixUi.text(
                this, value, 15, MiuixUi.TEXT_SECONDARY, false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(MiuixUi.dp(this, 20), MiuixUi.dp(this, 48),
                MiuixUi.dp(this, 20), MiuixUi.dp(this, 48));
        results.addView(hint, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }
}
