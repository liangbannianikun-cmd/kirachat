package app.miuix.tavern.ui;

import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import app.miuix.tavern.data.LocalStore;
import app.miuix.tavern.model.AppConfig;
import app.miuix.tavern.model.CharacterCard;
import app.miuix.tavern.model.GroupChat;
import app.miuix.tavern.util.CharacterCardImporter;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends AppCompatActivity {
    private static final int IMPORT_CARD = 4102;
    private static final int CHANGE_PERSONA_AVATAR = 4103;
    private static final int[] NAV_TYPES = {
            LineIconView.CHAT, LineIconView.CHARACTER, LineIconView.BOOK, LineIconView.PERSON
    };
    private static final String[] NAV_LABELS = {"消息", "角色", "世界书", "我的"};

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final LineIconView[] navIcons = new LineIconView[4];
    private final TextView[] navLabels = new TextView[4];
    private final LinearLayout[] navItems = new LinearLayout[4];

    private LocalStore store;
    private FrameLayout content;
    private int activeTab;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MiuixUi.applySystemBars(
                this, MiuixUi.BACKGROUND, MiuixUi.BACKGROUND);
        store = new LocalStore(this);
        setContentView(buildShell());
        showTab(0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (content != null) showTab(activeTab);
    }

    private View buildShell() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(MiuixUi.background(this));

        content = new FrameLayout(this);
        FrameLayout.LayoutParams contentParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        contentParams.bottomMargin = MiuixUi.dp(this, 84);
        root.addView(content, contentParams);

        LinearLayout nav = MiuixUi.horizontal(this);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(MiuixUi.dp(this, 5), MiuixUi.dp(this, 5),
                MiuixUi.dp(this, 5), MiuixUi.dp(this, 5));
        nav.setBackground(MiuixUi.outlinedShape(
                Color.WHITE, MiuixUi.HAIRLINE, 25, this));
        nav.setElevation(MiuixUi.dp(this, 14));
        FrameLayout.LayoutParams navParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, MiuixUi.dp(this, 68),
                Gravity.BOTTOM);
        navParams.setMargins(
                MiuixUi.dp(this, 14), 0,
                MiuixUi.dp(this, 14), MiuixUi.dp(this, 10));
        root.addView(nav, navParams);

        for (int i = 0; i < 4; i++) {
            final int index = i;
            LinearLayout item = MiuixUi.vertical(this);
            item.setGravity(Gravity.CENTER);
            item.setContentDescription(L10n.tr(this, NAV_LABELS[i]));
            MiuixUi.pressable(item, 0.93f);
            LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
            itemParams.setMargins(
                    MiuixUi.dp(this, 2), 0,
                    MiuixUi.dp(this, 2), 0);
            nav.addView(item, itemParams);
            navItems[i] = item;

            LineIconView icon = new LineIconView(this);
            icon.setType(NAV_TYPES[i]);
            item.addView(icon, new LinearLayout.LayoutParams(
                    MiuixUi.dp(this, 24), MiuixUi.dp(this, 24)));
            navIcons[i] = icon;

            TextView label = MiuixUi.text(
                    this, NAV_LABELS[i], 10.5f, MiuixUi.TEXT_SECONDARY, true);
            label.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams labelParams =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            MiuixUi.dp(this, 18));
            labelParams.topMargin = MiuixUi.dp(this, 1);
            item.addView(label, labelParams);
            navLabels[i] = label;
            item.setOnClickListener(v -> showTab(index));
        }
        return root;
    }

    private void showTab(int index) {
        int previousTab = activeTab;
        boolean animate = content.getChildCount() > 0 && previousTab != index;
        activeTab = index;
        for (int i = 0; i < navIcons.length; i++) {
            boolean selected = i == index;
            navIcons[i].setSelectedState(selected);
            navLabels[i].setTextColor(MiuixUi.color(
                    this,
                    selected ? MiuixUi.GREEN : MiuixUi.TEXT_SECONDARY));
            navItems[i].setBackground(MiuixUi.shape(
                    selected
                            ? Color.rgb(233, 249, 240)
                            : Color.TRANSPARENT,
                    20,
                    this));
        }
        content.removeAllViews();
        View page;
        if (index == 0) page = buildChatsPage();
        else if (index == 1) page = buildCharactersPage();
        else if (index == 2) page = buildLorePage();
        else page = buildProfilePage();
        content.addView(page);
        if (animate) {
            page.performHapticFeedback(
                    android.view.HapticFeedbackConstants.CLOCK_TICK);
            MiuixUi.revealDockPage(
                    page, index > previousTab ? 1 : -1);
        }
    }

    private View buildChatsPage() {
        LinearLayout page = MiuixUi.vertical(this);
        List<CharacterCard> allCharacters = store.getCharacters();
        List<CharacterCard> conversations = new ArrayList<>();
        for (CharacterCard card : allCharacters) {
            if (!store.getMessages(card.id).isEmpty()) conversations.add(card);
        }
        for (GroupChat group : store.getGroups()) {
            conversations.add(groupConversationCard(group));
        }
        Collections.sort(conversations, new Comparator<CharacterCard>() {
            @Override
            public int compare(CharacterCard left, CharacterCard right) {
                if (left.pinned != right.pinned) return left.pinned ? -1 : 1;
                return Long.compare(right.lastUsed, left.lastUsed);
            }
        });
        TextView route = MiuixUi.text(
                this, store.getConfig().readableMode(), 11.5f, MiuixUi.GREEN, true);
        route.setGravity(Gravity.CENTER);
        route.setMinHeight(MiuixUi.dp(this, 27));
        route.setPadding(MiuixUi.dp(this, 10), 0, MiuixUi.dp(this, 10), 0);
        route.setBackground(MiuixUi.shape(Color.rgb(233, 249, 240), 12, this));
        LinearLayout actions = MiuixUi.horizontal(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.addView(route);
        View add = iconButton(LineIconView.PLUS, "新建会话", this::showNewConversationMenu);
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(
                MiuixUi.dp(this, 44), MiuixUi.dp(this, 44));
        addParams.leftMargin = MiuixUi.dp(this, 5);
        actions.addView(add, addParams);
        page.addView(buildHeader(
                "消息",
                conversations.size() + " 个最近会话",
                actions));

        EditText search = null;
        if (!conversations.isEmpty()) {
            search = buildSearch("搜索最近会话内容");
            LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, MiuixUi.dp(this, 46));
            searchParams.setMargins(
                    MiuixUi.dp(this, 14), 0,
                    MiuixUi.dp(this, 14), MiuixUi.dp(this, 10));
            page.addView(search, searchParams);
        }

        FrameLayout listCard = new FrameLayout(this);
        listCard.setBackground(MiuixUi.shape(Color.WHITE, 18, this));
        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        cardParams.setMargins(MiuixUi.dp(this, 14), 0, MiuixUi.dp(this, 14), MiuixUi.dp(this, 12));
        page.addView(listCard, cardParams);

        if (conversations.isEmpty()) {
            listCard.addView(buildEmptyChats(), new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            RecyclerView recycler = new RecyclerView(this);
            recycler.setClipToOutline(true);
            recycler.setLayoutManager(new LinearLayoutManager(this));
            recycler.setOverScrollMode(View.OVER_SCROLL_NEVER);
            ConversationAdapter adapter = new ConversationAdapter(
                    this, store, conversations, this::openChat);
            recycler.setAdapter(adapter);
            listCard.addView(recycler, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            search.addTextChangedListener(filterWatcher(search, conversations, adapter));
        }
        return page;
    }

    private View buildCharactersPage() {
        LinearLayout page = MiuixUi.vertical(this);
        List<CharacterCard> all = store.getCharacters();
        int worldBooks = 0;
        for (CharacterCard card : all) {
            if (card.loreEntryCount() > 0) worldBooks++;
        }
        page.addView(buildHeader(
                "角色库",
                all.size() + " 位角色 · " + worldBooks + " 本世界书",
                null));

        EditText search = buildSearch("搜索角色名称或描述");
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, MiuixUi.dp(this, 46));
        searchParams.setMargins(MiuixUi.dp(this, 14), 0, MiuixUi.dp(this, 14), MiuixUi.dp(this, 6));
        page.addView(search, searchParams);

        RecyclerView recycler = new RecyclerView(this);
        int widthDp = Math.round(
                getResources().getDisplayMetrics().widthPixels
                        / getResources().getDisplayMetrics().density);
        int columns = widthDp >= 840 ? 4 : (widthDp >= 600 ? 3 : 2);
        recycler.setLayoutManager(new GridLayoutManager(this, columns));
        recycler.setOverScrollMode(View.OVER_SCROLL_NEVER);
        recycler.setPadding(MiuixUi.dp(this, 8), MiuixUi.dp(this, 2),
                MiuixUi.dp(this, 8), MiuixUi.dp(this, 12));
        recycler.setClipToPadding(false);
        CharacterAdapter adapter = new CharacterAdapter(this, all, this::openCharacterProfile);
        recycler.setAdapter(adapter);
        page.addView(recycler, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        search.addTextChangedListener(filterWatcher(search, all, adapter));
        return page;
    }

    private View buildEmptyChats() {
        LinearLayout empty = MiuixUi.vertical(this);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(
                MiuixUi.dp(this, 26), MiuixUi.dp(this, 30),
                MiuixUi.dp(this, 26), MiuixUi.dp(this, 30));
        TextView title = MiuixUi.text(
                this, "还没有最近会话", 20, MiuixUi.TEXT_PRIMARY, true);
        title.setGravity(Gravity.CENTER);
        empty.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, MiuixUi.dp(this, 34)));
        TextView body = MiuixUi.text(
                this,
                "点右上角“＋”添加角色或创建群聊；单聊会在第一次发送消息后出现在这里。",
                14, MiuixUi.TEXT_SECONDARY, false);
        body.setGravity(Gravity.CENTER);
        body.setLineSpacing(MiuixUi.dp(this, 3), 1.05f);
        empty.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView action = MiuixUi.pillButton(this, "新建会话", true);
        action.setOnClickListener(this::showNewConversationMenu);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, MiuixUi.dp(this, 46));
        actionParams.gravity = Gravity.CENTER_HORIZONTAL;
        actionParams.topMargin = MiuixUi.dp(this, 18);
        empty.addView(action, actionParams);
        return empty;
    }

    private View buildLorePage() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout page = MiuixUi.vertical(this);
        scroll.addView(page, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        page.addView(buildHeader("世界书", "按最近对话关键词自动激活", null));

        LinearLayout explainer = MiuixUi.card(this);
        TextView title = MiuixUi.text(this, "安静地补充上下文", 18, MiuixUi.TEXT_PRIMARY, true);
        explainer.addView(title);
        TextView body = MiuixUi.text(this,
                "导入 Tavern V2 角色卡时，内嵌的 character_book 会一并保存。每次生成前只选择命中关键词的条目，避免把整本设定塞进提示词。",
                14, MiuixUi.TEXT_SECONDARY, false);
        body.setLineSpacing(MiuixUi.dp(this, 3), 1.05f);
        LinearLayout.LayoutParams bodyParams =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bodyParams.topMargin = MiuixUi.dp(this, 9);
        explainer.addView(body, bodyParams);
        addWithPageMargins(page, explainer, 0, 12);

        int books = 0;
        for (CharacterCard card : store.getCharacters()) {
            if (card.loreEntryCount() <= 0) continue;
            books++;
            LinearLayout item = MiuixUi.card(this);
            LinearLayout row = MiuixUi.horizontal(this);
            item.addView(row);
            AvatarView avatar = new AvatarView(this);
            avatar.setCharacter(card);
            row.addView(avatar, new LinearLayout.LayoutParams(MiuixUi.dp(this, 46), MiuixUi.dp(this, 46)));
            LinearLayout labels = MiuixUi.vertical(this);
            LinearLayout.LayoutParams labelsParams =
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            labelsParams.leftMargin = MiuixUi.dp(this, 12);
            row.addView(labels, labelsParams);
            labels.addView(MiuixUi.rawText(
                    this, card.name, 17, MiuixUi.TEXT_PRIMARY, true));
            labels.addView(MiuixUi.text(this, card.loreEntryCount() + " 条可触发设定",
                    13, MiuixUi.TEXT_SECONDARY, false));
            TextView status = MiuixUi.text(this, "已启用", 12, MiuixUi.GREEN, true);
            status.setGravity(Gravity.CENTER);
            status.setBackground(MiuixUi.shape(Color.rgb(233, 249, 240), 12, this));
            status.setPadding(MiuixUi.dp(this, 10), 0, MiuixUi.dp(this, 10), 0);
            row.addView(status, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, MiuixUi.dp(this, 26)));
            addWithPageMargins(page, item, 0, 10);
        }
        if (books == 0) {
            TextView empty = MiuixUi.text(this, "还没有带世界书的角色卡", 15, MiuixUi.TEXT_SECONDARY, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, MiuixUi.dp(this, 28), 0, MiuixUi.dp(this, 28));
            page.addView(empty);
        }
        return scroll;
    }

    private View buildProfilePage() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout page = MiuixUi.vertical(this);
        scroll.addView(page);
        page.addView(buildHeader("我的", "本地优先 · 可选 GPT 登录", null));

        LinearLayout profile = MiuixUi.card(this);
        LinearLayout row = MiuixUi.horizontal(this);
        profile.addView(row);
        String personaDisplay = "你".equals(store.getConfig().persona)
                ? L10n.tr(this, "你") : store.getConfig().persona;
        CharacterCard persona = new CharacterCard();
        persona.id = "persona-local";
        persona.name = personaDisplay;
        persona.avatarPath = store.getConfig().personaAvatarPath;
        AvatarView avatar = new AvatarView(this);
        avatar.setCharacter(persona);
        avatar.setContentDescription(L10n.tr(this, "更换我的头像"));
        avatar.setOnClickListener(v -> changePersonaAvatar());
        MiuixUi.pressable(avatar, 0.94f);
        row.addView(avatar, new LinearLayout.LayoutParams(MiuixUi.dp(this, 64), MiuixUi.dp(this, 64)));
        LinearLayout names = MiuixUi.vertical(this);
        LinearLayout.LayoutParams namesParams =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        namesParams.leftMargin = MiuixUi.dp(this, 14);
        row.addView(names, namesParams);
        names.addView(MiuixUi.rawText(
                this, personaDisplay, 21, MiuixUi.TEXT_PRIMARY, true));
        TextView mode = MiuixUi.text(this,
                store.getConfig().readableMode() + " · 点击头像可更换",
                14, MiuixUi.TEXT_SECONDARY, false);
        names.addView(mode);
        addWithPageMargins(page, profile, 0, 14);

        LinearLayout settings = MiuixUi.card(this);
        settings.setPadding(0, 0, 0, 0);
        settings.addView(settingsRow("连接与账户", "直连 API、GPT / GitHub Copilot 账户、人格", v ->
                startActivity(new Intent(this, SettingsActivity.class))));
        settings.addView(MiuixUi.divider(this, 18));
        settings.addView(settingsRow("隐私与存储", "密钥由 Android Keystore 加密", null));
        settings.addView(MiuixUi.divider(this, 18));
        settings.addView(settingsRow("关于澄语", "0.9.0 · 原生角色聊天客户端", null));
        addWithPageMargins(page, settings, 0, 14);

        LinearLayout note = MiuixUi.card(this);
        TextView noteTitle = MiuixUi.text(this, "设计说明", 17, MiuixUi.TEXT_PRIMARY, true);
        note.addView(noteTitle);
        TextView noteBody = MiuixUi.text(this,
                "微信式信息架构负责熟悉感；MIUIX 的大标题、分组卡片与圆角负责层级；按下即反馈和可中断弹簧负责触感。所有动效都会尊重系统“移除动画”设置。",
                14, MiuixUi.TEXT_SECONDARY, false);
        noteBody.setLineSpacing(MiuixUi.dp(this, 3), 1.05f);
        LinearLayout.LayoutParams noteParams =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        noteParams.topMargin = MiuixUi.dp(this, 8);
        note.addView(noteBody, noteParams);
        addWithPageMargins(page, note, 0, 20);
        return scroll;
    }

    private View buildHeader(String titleText, String subtitleText, View action) {
        LinearLayout header = MiuixUi.horizontal(this);
        header.setGravity(Gravity.BOTTOM);
        header.setPadding(MiuixUi.dp(this, 18), MiuixUi.dp(this, 20),
                MiuixUi.dp(this, 14), MiuixUi.dp(this, 14));

        LinearLayout titles = MiuixUi.vertical(this);
        header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView title = MiuixUi.text(this, titleText, 31, MiuixUi.TEXT_PRIMARY, true);
        title.setLetterSpacing(-0.025f);
        titles.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, MiuixUi.dp(this, 42)));
        TextView subtitle = MiuixUi.text(this, subtitleText, 13, MiuixUi.TEXT_SECONDARY, false);
        titles.addView(subtitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, MiuixUi.dp(this, 22)));
        if (action != null) header.addView(action);
        return header;
    }

    private View iconButton(int type, String description, View.OnClickListener listener) {
        FrameLayout holder = new FrameLayout(this);
        holder.setContentDescription(L10n.tr(this, description));
        MiuixUi.ripple(holder, Color.WHITE, 14);
        MiuixUi.pressable(holder, 0.92f);
        LineIconView icon = new LineIconView(this);
        icon.setType(type);
        icon.setSelectedState(false);
        FrameLayout.LayoutParams iconParams =
                new FrameLayout.LayoutParams(MiuixUi.dp(this, 24), MiuixUi.dp(this, 24), Gravity.CENTER);
        holder.addView(icon, iconParams);
        holder.setOnClickListener(listener);
        holder.setLayoutParams(new LinearLayout.LayoutParams(MiuixUi.dp(this, 44), MiuixUi.dp(this, 44)));
        return holder;
    }

    private EditText buildSearch(String hint) {
        EditText search = MiuixUi.field(this, "⌕  " + hint, false);
        search.setTextSize(15);
        search.setBackground(MiuixUi.shape(Color.rgb(235, 235, 238), 14, this));
        return search;
    }

    private TextWatcher filterWatcher(EditText search, List<CharacterCard> all, Object adapter) {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim().toLowerCase(Locale.ROOT);
                List<CharacterCard> filtered = new ArrayList<>();
                for (CharacterCard card : all) {
                    String lastMessage = "";
                    if (adapter instanceof ConversationAdapter) {
                        List<app.miuix.tavern.model.ChatMessage> messages =
                                store.getMessages(card.id);
                        if (!messages.isEmpty()) {
                            String content = messages.get(messages.size() - 1).content;
                            lastMessage = content == null
                                    ? ""
                                    : content.toLowerCase(Locale.ROOT);
                        }
                    }
                    if (query.isEmpty()
                            || card.name.toLowerCase(Locale.ROOT).contains(query)
                            || (card.description != null
                            && card.description.toLowerCase(Locale.ROOT).contains(query))
                            || lastMessage.contains(query)) {
                        filtered.add(card);
                    }
                }
                if (adapter instanceof ConversationAdapter) {
                    ((ConversationAdapter) adapter).replace(filtered);
                } else {
                    ((CharacterAdapter) adapter).replace(filtered);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        };
    }

    private View settingsRow(String titleValue, String detailValue, @Nullable View.OnClickListener click) {
        LinearLayout row = MiuixUi.horizontal(this);
        row.setPadding(MiuixUi.dp(this, 18), MiuixUi.dp(this, 13),
                MiuixUi.dp(this, 16), MiuixUi.dp(this, 13));
        if (click != null) {
            MiuixUi.ripple(row, Color.WHITE, 18);
            MiuixUi.pressable(row, 0.985f);
            row.setOnClickListener(click);
        }
        LinearLayout labels = MiuixUi.vertical(this);
        row.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        labels.addView(MiuixUi.text(this, titleValue, 16, MiuixUi.TEXT_PRIMARY, true));
        labels.addView(MiuixUi.text(this, detailValue, 13, MiuixUi.TEXT_SECONDARY, false));
        if (click == null) {
            FrameLayout statusHolder = new FrameLayout(this);
            TextView status = MiuixUi.text(
                    this, "✓", 14, Color.WHITE, true);
            status.setGravity(Gravity.CENTER);
            status.setBackground(MiuixUi.shape(MiuixUi.GREEN, 13, this));
            status.setContentDescription(L10n.tr(this, "安全"));
            statusHolder.addView(status, new FrameLayout.LayoutParams(
                    MiuixUi.dp(this, 26), MiuixUi.dp(this, 26), Gravity.CENTER));
            row.addView(statusHolder, new LinearLayout.LayoutParams(
                    MiuixUi.dp(this, 42), MiuixUi.dp(this, 48)));
        } else {
            TextView chevron = MiuixUi.text(
                    this, "›", 25, Color.rgb(180, 180, 185), false);
            chevron.setGravity(Gravity.CENTER);
            row.addView(chevron, new LinearLayout.LayoutParams(
                    MiuixUi.dp(this, 42), MiuixUi.dp(this, 48)));
        }
        return row;
    }

    private void addWithPageMargins(LinearLayout page, View view, int top, int bottom) {
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(MiuixUi.dp(this, 14), MiuixUi.dp(this, top),
                MiuixUi.dp(this, 14), MiuixUi.dp(this, bottom));
        page.addView(view, params);
    }

    private void openChat(CharacterCard card) {
        if (card.id.startsWith(GroupChat.CONVERSATION_PREFIX)) {
            String groupId = card.id.substring(GroupChat.CONVERSATION_PREFIX.length());
            store.touchGroup(groupId);
            Intent intent = new Intent(this, GroupChatActivity.class);
            intent.putExtra(GroupChatActivity.EXTRA_GROUP_ID, groupId);
            MiuixUi.startConversationActivity(this, intent);
            return;
        }
        store.touchCharacter(card.id);
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra(ChatActivity.EXTRA_CHARACTER_ID, card.id);
        MiuixUi.startConversationActivity(this, intent);
    }

    private CharacterCard groupConversationCard(GroupChat group) {
        CharacterCard card = new CharacterCard();
        card.id = group.conversationId();
        card.name = group.name;
        card.description = L10n.tr(
                this, "本地群聊 · " + group.members.size() + " 人");
        card.firstMessage = L10n.tr(
                this, "群聊已创建，发一条消息开始聊天");
        card.lastUsed = group.lastUsed;
        card.unread = group.unread;
        card.muted = group.muted;
        card.pinned = group.pinned;
        card.chatBackgroundPath = group.chatBackgroundPath;
        if (!group.members.isEmpty()) {
            CharacterCard first = store.getCharacter(group.members.get(0));
            if (first == null) {
                first = store.getCharacterBySourceAvatar(group.members.get(0));
            }
            if (first != null) card.avatarPath = first.avatarPath;
        }
        return card;
    }

    private void showNewConversationMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, L10n.tr(this, "创建群聊"));
        menu.getMenu().add(0, 2, 1, L10n.tr(this, "添加角色"));
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                startActivity(new Intent(this, CreateGroupActivity.class));
            } else if (item.getItemId() == 2) {
                importCharacter();
            }
            return true;
        });
        menu.show();
    }

    private void openCharacterProfile(CharacterCard card) {
        Intent intent = new Intent(this, CharacterProfileActivity.class);
        intent.putExtra(CharacterProfileActivity.EXTRA_CHARACTER_ID, card.id);
        startActivity(intent);
    }

    private void importCharacter() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "image/png"});
        startActivityForResult(intent, IMPORT_CARD);
    }

    private void changePersonaAvatar() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, CHANGE_PERSONA_AVATAR);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if ((requestCode != IMPORT_CARD && requestCode != CHANGE_PERSONA_AVATAR)
                || resultCode != RESULT_OK
                || data == null
                || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        if (requestCode == CHANGE_PERSONA_AVATAR) {
            savePersonaAvatar(uri);
            return;
        }
        LocalizedToast.makeText(this, "正在读取角色卡…", Toast.LENGTH_SHORT).show();
        io.execute(() -> {
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) throw new IllegalStateException("无法打开所选文件");
                CharacterCardImporter.Result result = CharacterCardImporter.parse(input);
                runOnUiThread(() -> chooseCharacterImportMode(result));
            } catch (Exception error) {
                runOnUiThread(() -> LocalizedToast.makeText(this,
                        "导入失败：" + error.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void chooseCharacterImportMode(CharacterCardImporter.Result result) {
        CharacterCard existing = null;
        String incomingName = result.card.name == null
                ? "" : result.card.name.trim();
        for (CharacterCard card : store.getCharacters()) {
            if (incomingName.equalsIgnoreCase(
                    card.name == null ? "" : card.name.trim())) {
                existing = card;
                break;
            }
        }
        if (existing == null) {
            finishCharacterImport(result, null);
            return;
        }
        CharacterCard matched = existing;
        new LocalizedAlertDialogBuilder(this)
                .setTitle("检测到同名角色")
                .setMessage("本机已有“" + matched.name + "”。覆盖会保留原聊天记录和会话设置；新增角色必须使用不同名称。")
                .setPositiveButton("覆盖原角色",
                        (dialog, which) -> finishCharacterImport(result, matched))
                .setNeutralButton("新增角色",
                        (dialog, which) -> showNewCharacterNameDialog(result))
                .setNegativeButton("取消", null)
                .show();
    }

    private void showNewCharacterNameDialog(CharacterCardImporter.Result result) {
        EditText name = MiuixUi.field(this, "输入新的角色名称", false);
        name.setText(suggestUniqueCharacterName(result.card.name));
        name.setSelectAllOnFocus(true);
        FrameLayout wrapper = new FrameLayout(this);
        wrapper.setPadding(MiuixUi.dp(this, 22), MiuixUi.dp(this, 8),
                MiuixUi.dp(this, 22), 0);
        wrapper.addView(name, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, MiuixUi.dp(this, 50)));
        androidx.appcompat.app.AlertDialog dialog =
                new LocalizedAlertDialogBuilder(this)
                        .setTitle("为新角色修改名称")
                        .setMessage("角色名称不能与本机已有角色重复。")
                        .setView(wrapper)
                        .setPositiveButton("新增", null)
                        .setNegativeButton("取消", null)
                        .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(
                androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String value = name.getText().toString().trim();
                    if (value.isEmpty()) {
                        name.setError("请输入角色名称");
                        return;
                    }
                    if (isCharacterNameTaken(value)) {
                        name.setError("该名称已存在，请换一个名称");
                        return;
                    }
                    result.card.name = value;
                    dialog.dismiss();
                    finishCharacterImport(result, null);
                }));
        dialog.show();
    }

    private boolean isCharacterNameTaken(String name) {
        String candidate = name == null ? "" : name.trim();
        for (CharacterCard card : store.getCharacters()) {
            String saved = card.name == null ? "" : card.name.trim();
            if (candidate.equalsIgnoreCase(saved)) return true;
        }
        return false;
    }

    private String suggestUniqueCharacterName(String original) {
        String base = TextUtils.isEmpty(original) ? "新角色" : original.trim();
        int suffix = 2;
        String candidate = base + "（" + suffix + "）";
        while (isCharacterNameTaken(candidate)) {
            suffix++;
            candidate = base + "（" + suffix + "）";
        }
        return candidate;
    }

    private void finishCharacterImport(
            CharacterCardImporter.Result result,
            @Nullable CharacterCard existing) {
        LocalizedToast.makeText(this, existing == null
                ? "正在导入角色…" : "正在覆盖角色…", Toast.LENGTH_SHORT).show();
        io.execute(() -> {
            try {
                CharacterCard card = result.card;
                if (existing != null) {
                    card.id = existing.id;
                    card.lastUsed = existing.lastUsed;
                    card.unread = existing.unread;
                    card.muted = existing.muted;
                    card.pinned = existing.pinned;
                    card.chatBackgroundPath = existing.chatBackgroundPath;
                    if (!TextUtils.isEmpty(existing.sourceAvatar)) {
                        card.sourceAvatar = existing.sourceAvatar;
                    }
                    if (!result.png) card.avatarPath = existing.avatarPath;
                } else if (isCharacterNameTaken(card.name)) {
                    throw new IllegalStateException("角色名称已存在，请重新命名");
                }
                if (result.png) {
                    File directory = new File(getFilesDir(), "avatars");
                    if (!directory.exists() && !directory.mkdirs()) {
                        throw new IllegalStateException("无法创建头像目录");
                    }
                    File avatar = new File(directory,
                            Integer.toHexString(card.id.hashCode()) + ".png");
                    try (FileOutputStream output = new FileOutputStream(avatar, false)) {
                        output.write(result.originalBytes);
                    }
                    card.avatarPath = avatar.getAbsolutePath();
                }
                store.upsertCharacter(card);
                runOnUiThread(() -> {
                    LocalizedToast.makeText(this,
                            existing == null
                                    ? "已导入 " + card.name
                                    : "已覆盖 " + card.name,
                            Toast.LENGTH_SHORT).show();
                    showTab(1);
                });
            } catch (Exception error) {
                runOnUiThread(() -> LocalizedToast.makeText(this,
                        "导入失败：" + error.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void savePersonaAvatar(Uri uri) {
        LocalizedToast.makeText(this, "正在更换头像…", Toast.LENGTH_SHORT).show();
        io.execute(() -> {
            try (InputStream input = getContentResolver().openInputStream(uri);
                 ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                if (input == null) throw new IllegalStateException("无法打开所选图片");
                byte[] buffer = new byte[8192];
                int count;
                int total = 0;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > 15 * 1024 * 1024) {
                        throw new IllegalStateException("图片不能超过 15 MB");
                    }
                    bytes.write(buffer, 0, count);
                }
                byte[] image = bytes.toByteArray();
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(image, 0, image.length, bounds);
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                    throw new IllegalStateException("所选文件不是有效图片");
                }
                File directory = new File(getFilesDir(), "persona");
                if (!directory.exists() && !directory.mkdirs()) {
                    throw new IllegalStateException("无法创建头像目录");
                }
                File target = new File(directory, "avatar-" + System.currentTimeMillis());
                try (FileOutputStream output = new FileOutputStream(target)) {
                    output.write(image);
                }
                AppConfig config = store.getConfig();
                config.personaAvatarPath = target.getAbsolutePath();
                store.saveConfig(config);
                runOnUiThread(() -> {
                    LocalizedToast.makeText(this, "头像已更换", Toast.LENGTH_SHORT).show();
                    showTab(3);
                });
            } catch (Exception error) {
                runOnUiThread(() -> LocalizedToast.makeText(this,
                        "更换失败：" + error.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }
}
