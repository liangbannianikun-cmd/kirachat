package app.miuix.tavern.ui;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import app.miuix.tavern.R;
import app.miuix.tavern.data.LocalStore;
import app.miuix.tavern.model.CharacterCard;
import app.miuix.tavern.model.GroupChat;

import java.util.ArrayList;
import java.util.List;

public final class CreateGroupActivity extends AppCompatActivity {
    private LocalStore store;
    private EditText nameField;
    private TextView createButton;
    private TextView selectionLabel;
    private final List<CharacterCard> selected = new ArrayList<>();
    private boolean creating;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MiuixUi.applySystemBars(
                this, Color.rgb(245, 245, 246), Color.rgb(248, 248, 249));
        store = new LocalStore(this);
        setContentView(buildContent());
        updateSelection();
    }

    private View buildContent() {
        LinearLayout root = MiuixUi.vertical(this);
        root.setBackgroundColor(MiuixUi.background(this));
        root.addView(buildToolbar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, MiuixUi.dp(this, 60)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout page = MiuixUi.vertical(this);
        page.setPadding(MiuixUi.dp(this, 14), MiuixUi.dp(this, 14),
                MiuixUi.dp(this, 14), MiuixUi.dp(this, 20));
        scroll.addView(page);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout nameCard = MiuixUi.card(this);
        nameCard.addView(MiuixUi.text(this, "群聊名称", 17, MiuixUi.TEXT_PRIMARY, true));
        nameField = MiuixUi.field(this, "例如：深夜电台", false);
        LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, MiuixUi.dp(this, 48));
        fieldParams.topMargin = MiuixUi.dp(this, 10);
        nameCard.addView(nameField, fieldParams);
        page.addView(nameCard);

        selectionLabel = MiuixUi.text(this, "", 17, MiuixUi.TEXT_PRIMARY, true);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.setMargins(MiuixUi.dp(this, 4), MiuixUi.dp(this, 20),
                MiuixUi.dp(this, 4), MiuixUi.dp(this, 9));
        page.addView(selectionLabel, labelParams);

        List<CharacterCard> available = new ArrayList<>(store.getCharacters());
        if (available.size() < 2) {
            LinearLayout notice = MiuixUi.card(this);
            TextView title = MiuixUi.text(this, "还需要一位角色", 17, MiuixUi.TEXT_PRIMARY, true);
            notice.addView(title);
            TextView body = MiuixUi.text(this,
                    "群聊至少需要两位角色。请从消息页右上角“＋”继续添加角色卡，"
                            + "不需要同步服务器。",
                    14, MiuixUi.TEXT_SECONDARY, false);
            body.setLineSpacing(MiuixUi.dp(this, 3), 1.05f);
            LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            bodyParams.topMargin = MiuixUi.dp(this, 8);
            notice.addView(body, bodyParams);
            page.addView(notice);
        } else {
            LinearLayout membersCard = MiuixUi.card(this);
            membersCard.setPadding(0, 0, 0, 0);
            for (int i = 0; i < available.size(); i++) {
                CharacterCard card = available.get(i);
                membersCard.addView(memberRow(card));
                if (i < available.size() - 1) {
                    membersCard.addView(MiuixUi.divider(this, 70));
                }
            }
            page.addView(membersCard);
        }

        LinearLayout actionBar = MiuixUi.horizontal(this);
        actionBar.setGravity(Gravity.CENTER_VERTICAL);
        actionBar.setPadding(MiuixUi.dp(this, 14), MiuixUi.dp(this, 8),
                MiuixUi.dp(this, 14), MiuixUi.dp(this, 8));
        actionBar.setBackgroundColor(
                MiuixUi.color(this, Color.rgb(248, 248, 249)));
        createButton = MiuixUi.pillButton(this, "创建群聊", true);
        createButton.setGravity(Gravity.CENTER);
        createButton.setOnClickListener(v -> createGroup());
        actionBar.addView(createButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, MiuixUi.dp(this, 46)));
        root.addView(actionBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, MiuixUi.dp(this, 62)));
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
        back.setContentDescription(L10n.tr(this, "返回"));
        MiuixUi.pressable(back, 0.9f);
        LineIconView icon = new LineIconView(this);
        icon.setType(LineIconView.BACK);
        back.addView(icon, new FrameLayout.LayoutParams(
                MiuixUi.dp(this, 25), MiuixUi.dp(this, 25), Gravity.CENTER));
        back.setOnClickListener(v -> finish());
        toolbar.addView(back, new LinearLayout.LayoutParams(
                MiuixUi.dp(this, 48), MiuixUi.dp(this, 52)));
        TextView title = MiuixUi.text(this, "创建群聊", 18, MiuixUi.TEXT_PRIMARY, true);
        title.setGravity(Gravity.CENTER);
        toolbar.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        toolbar.addView(new View(this), new LinearLayout.LayoutParams(
                MiuixUi.dp(this, 48), MiuixUi.dp(this, 52)));
        return toolbar;
    }

    private View memberRow(CharacterCard card) {
        LinearLayout row = MiuixUi.horizontal(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(MiuixUi.dp(this, 16), MiuixUi.dp(this, 11),
                MiuixUi.dp(this, 12), MiuixUi.dp(this, 11));
        MiuixUi.pressable(row, 0.985f);
        AvatarView avatar = new AvatarView(this);
        avatar.setCharacter(card);
        row.addView(avatar, new LinearLayout.LayoutParams(
                MiuixUi.dp(this, 48), MiuixUi.dp(this, 48)));
        LinearLayout text = MiuixUi.vertical(this);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        textParams.leftMargin = MiuixUi.dp(this, 12);
        row.addView(text, textParams);
        text.addView(MiuixUi.rawText(
                this, card.name, 16, MiuixUi.TEXT_PRIMARY, true));
        String detail = card.description == null || card.description.trim().isEmpty()
                ? "本地角色"
                : card.description.trim().replace('\n', ' ');
        TextView source = MiuixUi.rawText(this, detail,
                12, MiuixUi.TEXT_SECONDARY, false);
        source.setSingleLine(true);
        source.setEllipsize(android.text.TextUtils.TruncateAt.END);
        text.addView(source);
        CheckBox check = new CheckBox(this);
        check.setClickable(false);
        row.addView(check, new LinearLayout.LayoutParams(
                MiuixUi.dp(this, 44), MiuixUi.dp(this, 44)));
        row.setOnClickListener(v -> {
            if (selected.contains(card)) selected.remove(card);
            else selected.add(card);
            check.setChecked(selected.contains(card));
            updateSelection();
        });
        return row;
    }

    private void updateSelection() {
        if (selectionLabel != null) {
            selectionLabel.setText(getString(R.string.group_selected_count, selected.size()));
        }
        boolean enabled = !creating && selected.size() >= 2;
        if (createButton != null) {
            createButton.setEnabled(enabled);
            createButton.setAlpha(enabled ? 1f : 0.45f);
        }
    }

    private void createGroup() {
        if (creating || selected.size() < 2) return;
        String name = nameField.getText().toString().trim();
        if (name.isEmpty()) {
            StringBuilder fallback = new StringBuilder();
            for (int i = 0; i < Math.min(3, selected.size()); i++) {
                if (fallback.length() > 0) fallback.append("、");
                fallback.append(selected.get(i).name);
            }
            name = fallback + "的群聊";
        }
        creating = true;
        GroupChat group = new GroupChat();
        group.name = name;
        group.members.clear();
        for (CharacterCard card : selected) group.members.add(card.id);
        store.upsertGroup(group);
        LocalizedToast.makeText(this, "已创建“" + group.name + "”", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, GroupChatActivity.class);
        intent.putExtra(GroupChatActivity.EXTRA_GROUP_ID, group.id);
        startActivity(intent);
        finish();
    }
}
