package app.miuix.tavern.ui;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import app.miuix.tavern.R;
import app.miuix.tavern.data.LocalStore;
import app.miuix.tavern.data.SecureStore;
import app.miuix.tavern.model.AppConfig;
import app.miuix.tavern.model.CharacterCard;
import app.miuix.tavern.model.ChatMessage;
import app.miuix.tavern.model.GroupChat;
import app.miuix.tavern.network.ApiClient;
import app.miuix.tavern.util.ChatNotificationManager;
import app.miuix.tavern.util.GroupPromptBuilder;
import app.miuix.tavern.util.LocationLocator;
import app.miuix.tavern.util.MediaAttachmentStore;

import org.json.JSONArray;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class GroupChatActivity extends AppCompatActivity
        implements GroupMessageAdapter.Listener {
    public static final String EXTRA_GROUP_ID = "group_id";
    public static final String EXTRA_MESSAGE_ID = "message_id";
    private static final int REQUEST_ALBUM = 811;
    private static final int REQUEST_CAMERA = 812;
    private static final int REQUEST_LOCATION = 813;
    private static final int REQUEST_VOICE_CALL = 814;
    private static final String STATE_CAMERA_PATH = "camera_path";

    private LocalStore store;
    private SecureStore secureStore;
    private GroupChat group;
    private final List<CharacterCard> members = new ArrayList<>();
    private List<ChatMessage> messages;
    private RecyclerView recycler;
    private GroupMessageAdapter adapter;
    private EditText composer;
    private TextView sendButton;
    private TextView connectionLabel;
    private ImageView backgroundView;
    private ChatMorePanel morePanel;
    private File pendingCameraFile;
    private boolean generating;
    private boolean chatVisible;
    private int roundReplyCount;
    private long generationEpoch;
    private boolean autonomousRound;
    private final List<MemberReply> pendingReplies = new ArrayList<>();
    private final Handler autonomousHandler =
            new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final Runnable autonomousRunnable = this::maybeStartAutonomousRound;

    @Override
    public void finish() {
        super.finish();
        MiuixUi.applyConversationReturnTransition(this);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            String cameraPath = savedInstanceState.getString(STATE_CAMERA_PATH, "");
            if (!cameraPath.isEmpty()) pendingCameraFile = new File(cameraPath);
        }
        MiuixUi.applySystemBars(
                this, Color.rgb(245, 245, 246), Color.rgb(248, 248, 249));
        store = new LocalStore(this);
        secureStore = new SecureStore(this);
        String id = getIntent().getStringExtra(EXTRA_GROUP_ID);
        group = store.getGroup(id == null ? "" : id);
        if (group == null) {
            LocalizedToast.makeText(this, "群聊不存在", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        refreshMembers();
        if (members.isEmpty()) {
            LocalizedToast.makeText(this, "找不到群成员，请重新添加角色卡",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        messages = new ArrayList<>(store.getMessages(group.conversationId()));
        if (messages.isEmpty()) addOpeningMessage();
        setContentView(buildContent());
        ChatNotificationManager.prepare(this);
        recycler.post(() -> scrollToBottom(false));
        String messageId = getIntent().getStringExtra(EXTRA_MESSAGE_ID);
        if (!TextUtils.isEmpty(messageId)) {
            recycler.post(() -> scrollToMessage(messageId));
        }
    }

    private void refreshMembers() {
        members.clear();
        for (String memberId : group.members) {
            CharacterCard card = store.getCharacter(memberId);
            if (card == null) card = store.getCharacterBySourceAvatar(memberId);
            if (card != null) members.add(card);
        }
    }

    private void addOpeningMessage() {
        for (CharacterCard card : members) {
            if (card.firstMessage == null || card.firstMessage.trim().isEmpty()) continue;
            ChatMessage opening = new ChatMessage(
                    ChatMessage.ASSISTANT,
                    card.replaceMacros(card.firstMessage, store.getConfig().persona));
            opening.speaker = card.name;
            messages.add(opening);
            store.saveMessages(group.conversationId(), messages);
            break;
        }
    }

    private View buildContent() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(MiuixUi.chatBackground(this));
        backgroundView = new ImageView(this);
        backgroundView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backgroundView.setContentDescription(
                L10n.tr(this, "当前群聊背景"));
        root.addView(backgroundView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout content = MiuixUi.vertical(this);
        content.addView(buildToolbar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, MiuixUi.dp(this, 62)));
        recycler = new RecyclerView(this);
        LinearLayoutManager manager = new LinearLayoutManager(this);
        manager.setStackFromEnd(true);
        recycler.setLayoutManager(manager);
        recycler.setClipToPadding(false);
        recycler.setPadding(0, MiuixUi.dp(this, 8), 0, MiuixUi.dp(this, 8));
        recycler.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        adapter = new GroupMessageAdapter(
                this, members, store.getConfig().persona,
                store.getConfig().personaAvatarPath, messages, this);
        recycler.setAdapter(adapter);
        content.addView(recycler, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        content.addView(buildComposer(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        applyChatBackground();
        return root;
    }

    private View buildToolbar() {
        LinearLayout toolbar = MiuixUi.horizontal(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(MiuixUi.dp(this, 7), MiuixUi.dp(this, 3),
                MiuixUi.dp(this, 7), MiuixUi.dp(this, 3));
        toolbar.setBackgroundColor(
                MiuixUi.color(this, Color.rgb(245, 245, 246)));
        toolbar.setElevation(MiuixUi.dp(this, 1));
        FrameLayout back = iconButton(LineIconView.BACK, "返回");
        back.setOnClickListener(v -> finish());
        toolbar.addView(back, new LinearLayout.LayoutParams(
                MiuixUi.dp(this, 48), MiuixUi.dp(this, 52)));
        LinearLayout titles = MiuixUi.vertical(this);
        titles.setGravity(Gravity.CENTER);
        toolbar.addView(titles, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        TextView name = MiuixUi.rawText(
                this, group.name, 17, MiuixUi.TEXT_PRIMARY, true);
        name.setGravity(Gravity.CENTER);
        titles.addView(name, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, MiuixUi.dp(this, 29)));
        connectionLabel = MiuixUi.text(
                this,
                members.size() + " 位成员",
                11,
                MiuixUi.TEXT_SECONDARY,
                false);
        connectionLabel.setGravity(Gravity.CENTER);
        titles.addView(connectionLabel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, MiuixUi.dp(this, 19)));
        titles.setOnClickListener(v -> openChatInfo());
        MiuixUi.pressable(titles, 0.985f);
        FrameLayout more = iconButton(LineIconView.MORE, "更多");
        more.setOnClickListener(this::showChatMenu);
        toolbar.addView(more, new LinearLayout.LayoutParams(
                MiuixUi.dp(this, 48), MiuixUi.dp(this, 52)));
        return toolbar;
    }

    private View buildComposer() {
        LinearLayout container = MiuixUi.vertical(this);
        LinearLayout bar = MiuixUi.horizontal(this);
        bar.setGravity(Gravity.BOTTOM);
        bar.setPadding(MiuixUi.dp(this, 10), MiuixUi.dp(this, 8),
                MiuixUi.dp(this, 10), MiuixUi.dp(this, 8));
        bar.setBackgroundColor(
                MiuixUi.color(this, Color.rgb(248, 248, 249)));
        bar.setElevation(MiuixUi.dp(this, 5));
        FrameLayout plus = iconButton(LineIconView.PLUS, "更多功能");
        plus.setOnClickListener(v -> toggleMorePanel());
        bar.addView(plus, new LinearLayout.LayoutParams(
                MiuixUi.dp(this, 42), MiuixUi.dp(this, 44)));
        TextView mention = MiuixUi.text(
                this, "@", 23, MiuixUi.TEXT_PRIMARY, true);
        mention.setGravity(Gravity.CENTER);
        mention.setContentDescription(L10n.tr(this, "提及群成员"));
        mention.setOnClickListener(v -> showMentionPicker());
        MiuixUi.ripple(mention, Color.TRANSPARENT, 14);
        MiuixUi.pressable(mention, 0.9f);
        bar.addView(mention, new LinearLayout.LayoutParams(
                MiuixUi.dp(this, 38), MiuixUi.dp(this, 44)));
        composer = new EditText(this);
        composer.setTextSize(16);
        composer.setTextColor(MiuixUi.color(this, MiuixUi.TEXT_PRIMARY));
        composer.setHintTextColor(MiuixUi.isDark(this)
                ? Color.rgb(126, 126, 136) : Color.rgb(170, 170, 175));
        composer.setHint(L10n.tr(this, "发消息"));
        composer.setGravity(Gravity.CENTER_VERTICAL);
        composer.setMaxLines(5);
        composer.setMinLines(1);
        composer.setPadding(MiuixUi.dp(this, 13), MiuixUi.dp(this, 8),
                MiuixUi.dp(this, 13), MiuixUi.dp(this, 8));
        composer.setBackground(MiuixUi.shape(Color.WHITE, 11, this));
        composer.setImeOptions(EditorInfo.IME_ACTION_SEND);
        composer.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        composer.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && !event.isShiftPressed())) {
                sendOrStop();
                return true;
            }
            return false;
        });
        LinearLayout.LayoutParams composerParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        composerParams.leftMargin = MiuixUi.dp(this, 7);
        bar.addView(composer, composerParams);
        sendButton = MiuixUi.pillButton(this, "发送", true);
        sendButton.setEnabled(false);
        sendButton.setAlpha(0.45f);
        sendButton.setOnClickListener(v -> sendOrStop());
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, MiuixUi.dp(this, 42));
        sendParams.leftMargin = MiuixUi.dp(this, 7);
        bar.addView(sendButton, sendParams);
        composer.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateSendButton();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        composer.setOnFocusChangeListener((view, focused) -> {
            if (focused && morePanel != null) morePanel.setVisibility(View.GONE);
        });
        container.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        morePanel = new ChatMorePanel(this, new ChatMorePanel.Listener() {
            @Override
            public void onAlbum() {
                chooseFromAlbum();
            }

            @Override
            public void onCamera() {
                takePhoto();
            }

            @Override
            public void onVoiceCall() {
                closeMorePanel();
                openGroupVoiceCall();
            }

            @Override
            public void onLocation() {
                requestCurrentLocation();
            }
        });
        morePanel.setVisibility(View.GONE);
        container.addView(morePanel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, MiuixUi.dp(this, 126)));
        return container;
    }

    private FrameLayout iconButton(int type, String description) {
        FrameLayout holder = new FrameLayout(this);
        holder.setContentDescription(L10n.tr(this, description));
        MiuixUi.ripple(holder, Color.TRANSPARENT, 14);
        MiuixUi.pressable(holder, 0.9f);
        LineIconView icon = new LineIconView(this);
        icon.setType(type);
        holder.addView(icon, new FrameLayout.LayoutParams(
                MiuixUi.dp(this, 25), MiuixUi.dp(this, 25), Gravity.CENTER));
        return holder;
    }

    private void sendOrStop() {
        String text = composer.getText().toString().trim();
        if (text.isEmpty()) return;
        composer.setText("");
        addUserMessage(new ChatMessage(ChatMessage.USER, text));
    }

    private void showMentionPicker() {
        if (members.isEmpty() || composer == null) return;
        String[] names = new String[members.size() + 1];
        names[0] = "所有人";
        for (int i = 0; i < members.size(); i++) {
            names[i + 1] = members.get(i).name;
        }
        new LocalizedAlertDialogBuilder(this)
                .setTitle("@ 群成员")
                .setItems(names, (dialog, which) -> {
                    String mention = "@" + names[which] + " ";
                    int cursor = Math.max(0, composer.getSelectionStart());
                    composer.getText().insert(cursor, mention);
                    composer.requestFocus();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void startConcurrentRound(
            List<CharacterCard> participants, boolean spontaneous) {
        if (participants == null || participants.isEmpty()) return;
        if (generating) stopGeneration();
        autonomousHandler.removeCallbacks(autonomousRunnable);
        long epoch = ++generationEpoch;
        generating = true;
        autonomousRound = spontaneous;
        roundReplyCount = 0;
        pendingReplies.clear();
        List<CharacterCard> shuffled = new ArrayList<>(participants);
        Collections.shuffle(shuffled);
        List<ChatMessage> snapshot = new ArrayList<>(messages);
        updateSendButton();
        for (CharacterCard member : shuffled) {
            MemberReply state = new MemberReply(member);
            pendingReplies.add(state);
            JSONArray prompt = GroupPromptBuilder.build(
                    group,
                    member,
                    members,
                    store.getConfig(),
                    snapshot,
                    spontaneous);
            state.call = ApiClient.generate(
                    this,
                    store.getConfig(),
                    secureStore,
                    secureStore.getDirectApiKey(),
                    prompt,
                    new ApiClient.StreamCallback() {
                        @Override
                        public void onDelta(String delta) {
                            runOnUiThread(() ->
                                    appendDelta(epoch, state, delta));
                        }

                        @Override
                        public void onComplete() {
                            runOnUiThread(() ->
                                    completeMember(epoch, state, null));
                        }

                        @Override
                        public void onError(String message) {
                            runOnUiThread(() ->
                                    completeMember(epoch, state, message));
                        }
                    });
        }
    }

    private void appendDelta(
            long epoch, MemberReply state, String delta) {
        if (epoch != generationEpoch || !generating
                || state.completed || delta == null) return;
        state.reply.append(delta);
    }

    private void completeMember(
            long epoch, MemberReply state, @Nullable String error) {
        if (epoch != generationEpoch || !generating || state.completed) return;
        state.completed = true;
        state.call = null;
        String content = state.reply.toString().trim();
        if (error != null) {
            ChatMessage assistant = new ChatMessage(
                    ChatMessage.ASSISTANT, "请求失败：" + error);
            assistant.speaker = state.member.name;
            assistant.failed = true;
            messages.add(assistant);
            adapter.notifyItemInserted(messages.size() - 1);
            scrollToBottom(true);
        } else if (!GroupPromptBuilder.shouldSkipOutput(content)) {
            ChatMessage assistant =
                    new ChatMessage(ChatMessage.ASSISTANT, content);
            assistant.speaker = state.member.name;
            messages.add(assistant);
            adapter.notifyItemInserted(messages.size() - 1);
            roundReplyCount++;
            scrollToBottom(true);
        }
        store.saveMessages(group.conversationId(), messages);
        for (MemberReply reply : pendingReplies) {
            if (!reply.completed) return;
        }
        finishRound();
    }

    private void finishRound() {
        generating = false;
        pendingReplies.clear();
        resetConnectionLabel();
        store.saveMessages(group.conversationId(), messages);
        if (!chatVisible && roundReplyCount > 0) {
            for (int i = messages.size() - 1; i >= 0; i--) {
                ChatMessage message = messages.get(i);
                if (ChatMessage.ASSISTANT.equals(message.role) && !message.failed) {
                    notifyReply(message);
                    break;
                }
            }
        }
        updateSendButton();
        scrollToBottom(false);
        if (!autonomousRound && roundReplyCount == 0 && chatVisible) {
            LocalizedToast.makeText(
                    this,
                    "这轮没有成员选择回复",
                    Toast.LENGTH_SHORT).show();
        }
        autonomousRound = false;
        scheduleAutonomousRound();
    }

    private void stopGeneration() {
        for (MemberReply reply : pendingReplies) {
            if (reply.call != null) reply.call.cancel();
        }
        generationEpoch++;
        generating = false;
        autonomousRound = false;
        pendingReplies.clear();
        resetConnectionLabel();
        store.saveMessages(group.conversationId(), messages);
        updateSendButton();
    }

    private void updateSendButton() {
        if (sendButton == null) return;
        boolean enabled = composer != null
                && !composer.getText().toString().trim().isEmpty();
        sendButton.setText("发送");
        sendButton.setEnabled(enabled);
        sendButton.setAlpha(enabled ? 1f : 0.45f);
        sendButton.setBackground(MiuixUi.shape(MiuixUi.GREEN, 13, this));
    }

    private void resetConnectionLabel() {
        connectionLabel.setText(members.size() + " 位成员");
    }

    private void scheduleAutonomousRound() {
        autonomousHandler.removeCallbacks(autonomousRunnable);
        if (!chatVisible || store == null
                || !store.getConfig().groupAutonomousMessages) {
            return;
        }
        long delay = 60_000L + random.nextInt(90_001);
        autonomousHandler.postDelayed(autonomousRunnable, delay);
    }

    private void maybeStartAutonomousRound() {
        if (!chatVisible || store == null
                || !store.getConfig().groupAutonomousMessages) {
            return;
        }
        if (generating || members.isEmpty()) {
            scheduleAutonomousRound();
            return;
        }
        List<CharacterCard> candidates = new ArrayList<>(members);
        Collections.shuffle(candidates);
        int count = candidates.size() > 1 && random.nextInt(100) < 25
                ? 2 : 1;
        startConcurrentRound(
                new ArrayList<>(candidates.subList(0, count)), true);
    }

    private void scrollToBottom(boolean smooth) {
        if (recycler == null || messages.isEmpty()) return;
        int position = messages.size() - 1;
        if (smooth) recycler.smoothScrollToPosition(position);
        else recycler.scrollToPosition(position);
    }

    private void scrollToMessage(String messageId) {
        if (TextUtils.isEmpty(messageId) || recycler == null) return;
        for (int i = 0; i < messages.size(); i++) {
            if (messageId.equals(messages.get(i).id)) {
                recycler.scrollToPosition(i);
                return;
            }
        }
    }

    @Override
    public void onMessageLongPress(ChatMessage message, int position, View anchor) {
        if (position < 0 || position >= messages.size()) return;
        anchor.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        List<WechatMessageMenu.Item> items = new ArrayList<>();
        items.add(new WechatMessageMenu.Item(
                "复制", () -> copyGroupMessage(message.content)));
        if (message.failed) {
            items.add(new WechatMessageMenu.Item(
                    "重试", () -> retryFailed(message, position)));
        }
        items.add(new WechatMessageMenu.Item(
                "删除", () -> deleteGroupMessage(message, position)));
        WechatMessageMenu.show(this, anchor, items);
    }

    @Override
    public void onAvatarClick(CharacterCard card) {
        if (card == null) return;
        Intent intent = new Intent(this, CharacterProfileActivity.class);
        intent.putExtra(CharacterProfileActivity.EXTRA_CHARACTER_ID, card.id);
        startActivity(intent);
    }

    private void copyGroupMessage(String content) {
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(
                ClipData.newPlainText("群聊消息", content == null ? "" : content));
        LocalizedToast.makeText(this, "已复制", Toast.LENGTH_SHORT).show();
    }

    private void deleteGroupMessage(ChatMessage message, int position) {
        if (generating || position < 0 || position >= messages.size()
                || messages.get(position) != message) {
            return;
        }
        messages.remove(position);
        adapter.notifyItemRemoved(position);
        store.saveMessages(group.conversationId(), messages);
    }

    private void retryFailed(ChatMessage message, int position) {
        if (generating || !message.failed
                || position < 0 || position >= messages.size()
                || messages.get(position) != message) {
            return;
        }
        CharacterCard retryMember = null;
        for (CharacterCard member : members) {
            if (member.name.equals(message.speaker)) {
                retryMember = member;
                break;
            }
        }
        if (retryMember == null) return;
        messages.remove(position);
        adapter.notifyItemRemoved(position);
        store.saveMessages(group.conversationId(), messages);
        startConcurrentRound(
                Collections.singletonList(retryMember), false);
    }

    private void showChatMenu(View anchor) {
        openChatInfo();
    }

    private void openChatInfo() {
        Intent intent = new Intent(this, ChatInfoActivity.class);
        intent.putExtra(ChatInfoActivity.EXTRA_GROUP_ID, group.id);
        startActivity(intent);
    }

    private void showGroupInfo() {
        StringBuilder names = new StringBuilder();
        for (CharacterCard member : members) {
            if (names.length() > 0) names.append("\n");
            names.append("• ").append(member.name);
        }
        new LocalizedAlertDialogBuilder(this)
                .setTitle(group.name)
                .setMessage("本地群聊\n\n" + names)
                .setPositiveButton("完成", null)
                .show();
    }

    private void toggleMorePanel() {
        if (morePanel.getVisibility() == View.VISIBLE) {
            morePanel.setVisibility(View.GONE);
            composer.requestFocus();
            InputMethodManager keyboard =
                    (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (keyboard != null) {
                keyboard.showSoftInput(composer, InputMethodManager.SHOW_IMPLICIT);
            }
            return;
        }
        composer.clearFocus();
        InputMethodManager keyboard =
                (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (keyboard != null) {
            keyboard.hideSoftInputFromWindow(composer.getWindowToken(), 0);
        }
        morePanel.setVisibility(View.VISIBLE);
        recycler.post(() -> scrollToBottom(false));
    }

    private void closeMorePanel() {
        if (morePanel != null) morePanel.setVisibility(View.GONE);
    }

    private void chooseFromAlbum() {
        closeMorePanel();
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_ALBUM);
    }

    private void takePhoto() {
        closeMorePanel();
        try {
            pendingCameraFile = MediaAttachmentStore.createCameraFile(this);
            Uri output = FileProvider.getUriForFile(
                    this, getPackageName() + ".files", pendingCameraFile);
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, output);
            intent.setClipData(ClipData.newRawUri("output", output));
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (intent.resolveActivity(getPackageManager()) == null) {
                pendingCameraFile.delete();
                pendingCameraFile = null;
                LocalizedToast.makeText(this, "没有可用的相机应用", Toast.LENGTH_SHORT).show();
                return;
            }
            getPackageManager().queryIntentActivities(intent, 0).forEach(info ->
                    grantUriPermission(
                            info.activityInfo.packageName,
                            output,
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                    | Intent.FLAG_GRANT_READ_URI_PERMISSION));
            startActivityForResult(intent, REQUEST_CAMERA);
        } catch (IOException | RuntimeException error) {
            if (pendingCameraFile != null) pendingCameraFile.delete();
            pendingCameraFile = null;
            LocalizedToast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void requestCurrentLocation() {
        closeMorePanel();
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }, REQUEST_LOCATION);
            return;
        }
        locateAndSend();
    }

    private void locateAndSend() {
        LocalizedToast.makeText(this, "正在获取当前位置…", Toast.LENGTH_SHORT).show();
        LocationLocator.locate(this, new LocationLocator.Callback() {
            @Override
            public void onLocation(android.location.Location location) {
                ChatMessage message =
                        new ChatMessage(ChatMessage.USER, "我分享了当前位置");
                message.attachmentType = ChatMessage.ATTACHMENT_LOCATION;
                message.latitude = location.getLatitude();
                message.longitude = location.getLongitude();
                addUserMessage(message);
            }

            @Override
            public void onError(String message) {
                LocalizedToast.makeText(
                        GroupChatActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void openGroupVoiceCall() {
        String[] names = new String[members.size()];
        for (int i = 0; i < members.size(); i++) names[i] = members.get(i).name;
        new LocalizedAlertDialogBuilder(this)
                .setTitle("选择通话角色")
                .setItems(names, (dialog, which) -> {
                    Intent intent = new Intent(this, VoiceCallActivity.class);
                    intent.putExtra(
                            VoiceCallActivity.EXTRA_CHARACTER_ID, members.get(which).id);
                    startActivityForResult(intent, REQUEST_VOICE_CALL);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void importImage(Uri uri) {
        new Thread(() -> {
            try {
                String path = MediaAttachmentStore.saveGalleryImage(this, uri);
                runOnUiThread(() -> sendImage(path));
            } catch (IOException | RuntimeException | OutOfMemoryError error) {
                runOnUiThread(() -> LocalizedToast.makeText(
                        this, error.getMessage(), Toast.LENGTH_LONG).show());
            }
        }, "miutavern-group-gallery").start();
    }

    private void importCameraImage(File source) {
        new Thread(() -> {
            try {
                String path = MediaAttachmentStore.normalizeCameraImage(this, source);
                runOnUiThread(() -> sendImage(path));
            } catch (IOException | RuntimeException | OutOfMemoryError error) {
                runOnUiThread(() -> LocalizedToast.makeText(
                        this, error.getMessage(), Toast.LENGTH_LONG).show());
            }
        }, "miutavern-group-camera").start();
    }

    private void sendImage(String path) {
        ChatMessage message = new ChatMessage(ChatMessage.USER, "[图片]");
        message.attachmentType = ChatMessage.ATTACHMENT_IMAGE;
        message.attachmentPath = path;
        message.attachmentMime = "image/jpeg";
        addUserMessage(message);
    }

    private void addUserMessage(ChatMessage message) {
        if (generating) stopGeneration();
        messages.add(message);
        adapter.notifyItemInserted(messages.size() - 1);
        store.saveMessages(group.conversationId(), messages);
        scrollToBottom(true);
        startConcurrentRound(members, false);
    }

    private void addVoiceCallRecord(long durationSeconds) {
        if (durationSeconds <= 0) return;
        String duration = ChatMessage.formatCallDuration(durationSeconds);
        ChatMessage message = new ChatMessage(
                ChatMessage.USER, "通话时长 " + duration);
        message.attachmentType = ChatMessage.ATTACHMENT_VOICE_CALL;
        message.callDurationSeconds = durationSeconds;
        messages.add(message);
        adapter.notifyItemInserted(messages.size() - 1);
        store.saveMessages(group.conversationId(), messages);
        scrollToBottom(true);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ALBUM && resultCode == Activity.RESULT_OK
                && data != null && data.getData() != null) {
            importImage(data.getData());
        } else if (requestCode == REQUEST_VOICE_CALL
                && resultCode == Activity.RESULT_OK && data != null) {
            addVoiceCallRecord(data.getLongExtra(
                    VoiceCallActivity.EXTRA_CALL_DURATION_SECONDS, 0));
        } else if (requestCode == REQUEST_CAMERA) {
            File captured = pendingCameraFile;
            pendingCameraFile = null;
            if (resultCode == Activity.RESULT_OK && captured != null) {
                importCameraImage(captured);
            } else if (captured != null) {
                captured.delete();
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (pendingCameraFile != null) {
            outState.putString(
                    STATE_CAMERA_PATH, pendingCameraFile.getAbsolutePath());
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_LOCATION) return;
        for (int result : grantResults) {
            if (result == PackageManager.PERMISSION_GRANTED) {
                locateAndSend();
                return;
            }
        }
        LocalizedToast.makeText(this, "需要位置权限才能发送当前位置", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onBackPressed() {
        if (morePanel != null && morePanel.getVisibility() == View.VISIBLE) {
            morePanel.setVisibility(View.GONE);
            return;
        }
        super.onBackPressed();
    }

    private void confirmClear() {
        new LocalizedAlertDialogBuilder(this)
                .setTitle("清空“" + group.name + "”的聊天？")
                .setMessage("本机保存的群聊消息会被清空，群组与角色卡仍会保留。")
                .setNegativeButton("取消", null)
                .setPositiveButton("清空", (dialog, which) -> {
                    if (generating) stopGeneration();
                    messages.clear();
                    adapter.notifyDataSetChanged();
                    store.saveMessages(group.conversationId(), messages);
                })
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        store.touchGroup(group.id);
        GroupChat updated = store.getGroup(group.id);
        if (updated != null) group = updated;
        refreshMembers();
        if (!generating && recycler != null) {
            messages = new ArrayList<>(store.getMessages(group.conversationId()));
            adapter = new GroupMessageAdapter(
                    this, members, store.getConfig().persona,
                    store.getConfig().personaAvatarPath, messages, this);
            recycler.setAdapter(adapter);
        }
        applyChatBackground();
        if (connectionLabel != null && !generating) resetConnectionLabel();
    }

    @Override
    protected void onStart() {
        super.onStart();
        chatVisible = true;
        scheduleAutonomousRound();
    }

    @Override
    protected void onStop() {
        chatVisible = false;
        autonomousHandler.removeCallbacks(autonomousRunnable);
        super.onStop();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String messageId = intent.getStringExtra(EXTRA_MESSAGE_ID);
        if (!TextUtils.isEmpty(messageId) && recycler != null) {
            recycler.post(() -> scrollToMessage(messageId));
        }
    }

    private void applyChatBackground() {
        if (backgroundView == null || group == null) return;
        android.graphics.Bitmap bitmap =
                MediaAttachmentStore.decodePreview(group.chatBackgroundPath);
        backgroundView.setImageBitmap(bitmap);
        backgroundView.setBackgroundColor(MiuixUi.chatBackground(this));
        backgroundView.setAlpha(bitmap == null ? 1f : 0.82f);
    }

    private void notifyReply(ChatMessage message) {
        GroupChat saved = store.getGroup(group.id);
        if (saved == null || saved.muted) return;
        saved.unread = Math.max(1, saved.unread + 1);
        store.upsertGroup(saved);
        String prefix = TextUtils.isEmpty(message.speaker)
                ? "" : message.speaker + "：";
        String content = message.content == null ? "收到一条新回复"
                : prefix + message.content.replace('\n', ' ').trim();
        if (content.length() > 160) content = content.substring(0, 160) + "…";
        Intent open = new Intent(this, GroupChatActivity.class);
        open.putExtra(EXTRA_GROUP_ID, saved.id);
        open.putExtra(EXTRA_MESSAGE_ID, message.id);
        ChatNotificationManager.notifyReply(
                this, saved.conversationId(), saved.name, content, open);
    }

    @Override
    protected void onDestroy() {
        autonomousHandler.removeCallbacks(autonomousRunnable);
        if (generating) stopGeneration();
        super.onDestroy();
    }

    private static final class MemberReply {
        final CharacterCard member;
        final StringBuilder reply = new StringBuilder();
        ApiClient.Call call;
        boolean completed;

        MemberReply(CharacterCard member) {
            this.member = member;
        }
    }
}
