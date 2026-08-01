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

import app.miuix.tavern.data.LocalStore;
import app.miuix.tavern.data.SecureStore;
import app.miuix.tavern.model.AppConfig;
import app.miuix.tavern.model.CharacterCard;
import app.miuix.tavern.model.ChatMessage;
import app.miuix.tavern.network.ApiClient;
import app.miuix.tavern.util.ChatNotificationManager;
import app.miuix.tavern.util.LocationLocator;
import app.miuix.tavern.util.MediaAttachmentStore;
import app.miuix.tavern.util.PromptBuilder;

import org.json.JSONArray;

import java.util.ArrayList;
import java.io.File;
import java.io.IOException;
import java.util.List;

public final class ChatActivity extends AppCompatActivity implements MessageAdapter.Listener {
    public static final String EXTRA_CHARACTER_ID = "character_id";
    public static final String EXTRA_MESSAGE_ID = "message_id";
    private static final int REQUEST_ALBUM = 801;
    private static final int REQUEST_CAMERA = 802;
    private static final int REQUEST_LOCATION = 803;
    private static final String STATE_CAMERA_PATH = "camera_path";

    private LocalStore store;
    private SecureStore secureStore;
    private CharacterCard character;
    private List<ChatMessage> messages;
    private RecyclerView recycler;
    private MessageAdapter adapter;
    private EditText composer;
    private TextView sendButton;
    private TextView connectionLabel;
    private ImageView backgroundView;
    private ChatMorePanel morePanel;
    private File pendingCameraFile;
    private ApiClient.Call activeCall;
    private boolean generating;
    private boolean chatVisible;
    private int assistantIndex = -1;
    private int assistantInsertIndex = -1;
    private boolean queuedUserMessages;
    private long generationEpoch;
    private ChatMessage pendingAssistant;

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
        String id = getIntent().getStringExtra(EXTRA_CHARACTER_ID);
        character = store.getCharacter(id == null ? "" : id);
        if (character == null) {
            LocalizedToast.makeText(this, "角色不存在", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        messages = new ArrayList<>(store.getMessages(character.id));
        if (messages.isEmpty() && character.firstMessage != null && !character.firstMessage.trim().isEmpty()) {
            messages.add(new ChatMessage(ChatMessage.ASSISTANT,
                    character.replaceMacros(character.firstMessage, store.getConfig().persona)));
            store.saveMessages(character.id, messages);
        }
        setContentView(buildContent());
        ChatNotificationManager.prepare(this);
        recycler.post(() -> scrollToBottom(false));
        String messageId = getIntent().getStringExtra(EXTRA_MESSAGE_ID);
        if (!TextUtils.isEmpty(messageId)) {
            recycler.post(() -> scrollToMessage(messageId));
        }
    }

    private View buildContent() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(MiuixUi.chatBackground(this));
        backgroundView = new ImageView(this);
        backgroundView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backgroundView.setContentDescription(L10n.tr(this, "当前聊天背景"));
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
        adapter = new MessageAdapter(
                this, character, store.getConfig().persona,
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
        toolbar.addView(back, new LinearLayout.LayoutParams(MiuixUi.dp(this, 48), MiuixUi.dp(this, 52)));

        LinearLayout titles = MiuixUi.vertical(this);
        titles.setGravity(Gravity.CENTER);
        toolbar.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        TextView name = MiuixUi.rawText(
                this, character.name, 17, MiuixUi.TEXT_PRIMARY, true);
        name.setGravity(Gravity.CENTER);
        titles.addView(name, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, MiuixUi.dp(this, 29)));
        connectionLabel = MiuixUi.text(this, "", 11,
                MiuixUi.TEXT_SECONDARY, false);
        connectionLabel.setGravity(Gravity.CENTER);
        connectionLabel.setVisibility(View.GONE);
        titles.addView(connectionLabel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, MiuixUi.dp(this, 19)));
        titles.setContentDescription(
                L10n.tr(this, "查看" + character.name + "的角色资料"));
        titles.setOnClickListener(v -> showCharacterInfo());
        MiuixUi.pressable(titles, 0.985f);

        FrameLayout more = iconButton(LineIconView.MORE, "更多");
        more.setOnClickListener(this::showChatMenu);
        toolbar.addView(more, new LinearLayout.LayoutParams(MiuixUi.dp(this, 48), MiuixUi.dp(this, 52)));
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
        bar.addView(plus, new LinearLayout.LayoutParams(MiuixUi.dp(this, 42), MiuixUi.dp(this, 44)));

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
        LinearLayout.LayoutParams composerParams =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        composerParams.leftMargin = MiuixUi.dp(this, 7);
        bar.addView(composer, composerParams);

        sendButton = MiuixUi.pillButton(this, "发送", true);
        sendButton.setContentDescription(L10n.tr(this, "发送消息"));
        sendButton.setEnabled(false);
        sendButton.setAlpha(0.45f);
        sendButton.setOnClickListener(v -> sendOrStop());
        LinearLayout.LayoutParams sendParams =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, MiuixUi.dp(this, 42));
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
                openVoiceCall();
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

    private void startGeneration() {
        if (generating) return;
        AppConfig config = store.getConfig();
        pendingAssistant = new ChatMessage(ChatMessage.ASSISTANT, "");
        assistantIndex = -1;
        assistantInsertIndex = messages.size();
        long epoch = ++generationEpoch;
        generating = true;
        connectionLabel.setText(character.name + "正在输入…");
        connectionLabel.setVisibility(View.VISIBLE);
        updateSendButton();

        JSONArray prompt = PromptBuilder.build(character, config,
                new ArrayList<>(messages));
        activeCall = ApiClient.generate(this, config, secureStore,
                secureStore.getDirectApiKey(),
                prompt, new ApiClient.StreamCallback() {
                    @Override
                    public void onDelta(String delta) {
                        runOnUiThread(() -> appendDelta(epoch, delta));
                    }

                    @Override
                    public void onComplete() {
                        runOnUiThread(() -> finishGeneration(epoch, null));
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> finishGeneration(epoch, message));
                    }
                });
    }

    private void appendDelta(long epoch, String delta) {
        if (epoch != generationEpoch || !generating || pendingAssistant == null
                || delta == null || delta.isEmpty()) return;
        if (assistantIndex < 0) {
            assistantIndex = Math.max(0,
                    Math.min(assistantInsertIndex, messages.size()));
            messages.add(assistantIndex, pendingAssistant);
            adapter.notifyItemInserted(assistantIndex);
        }
        pendingAssistant.content += delta;
        adapter.notifyItemChanged(assistantIndex);
        scrollToBottom(false);
    }

    private void finishGeneration(long epoch, @Nullable String error) {
        if (epoch != generationEpoch || !generating) return;
        generating = false;
        activeCall = null;
        ChatMessage completed = null;
        ChatMessage assistant = assistantIndex >= 0 && assistantIndex < messages.size()
                ? messages.get(assistantIndex) : pendingAssistant;
        if (assistant != null) {
            if (error != null) {
                assistant.failed = true;
                if (assistant.content.trim().isEmpty()) {
                    assistant.content = "请求失败：" + error;
                } else {
                    assistant.content += "\n\n请求中断：" + error;
                }
                if (assistantIndex < 0) {
                    assistantIndex = Math.max(0,
                            Math.min(assistantInsertIndex, messages.size()));
                    messages.add(assistantIndex, assistant);
                    adapter.notifyItemInserted(assistantIndex);
                } else {
                    adapter.notifyItemChanged(assistantIndex);
                }
            } else if (assistant.content.trim().isEmpty()) {
                assistant.failed = true;
                assistant.content = "服务没有返回文字内容";
                assistantIndex = Math.max(0,
                        Math.min(assistantInsertIndex, messages.size()));
                messages.add(assistantIndex, assistant);
                adapter.notifyItemInserted(assistantIndex);
            } else {
                completed = assistant;
            }
        }
        boolean continueForQueuedMessages = queuedUserMessages;
        queuedUserMessages = false;
        assistantIndex = -1;
        assistantInsertIndex = -1;
        pendingAssistant = null;
        connectionLabel.setVisibility(View.GONE);
        store.saveMessages(character.id, messages);
        if (completed != null && !chatVisible) notifyReply(completed);
        updateSendButton();
        scrollToBottom(false);
        if (continueForQueuedMessages) startGeneration();
    }

    private void stopGeneration() {
        if (activeCall != null) activeCall.cancel();
        generationEpoch++;
        generating = false;
        activeCall = null;
        if (assistantIndex >= 0 && assistantIndex < messages.size()) {
            ChatMessage assistant = messages.get(assistantIndex);
            if (assistant.content.trim().isEmpty()) {
                messages.remove(assistantIndex);
                adapter.notifyItemRemoved(assistantIndex);
            } else {
                assistant.content += "\n\n（已停止）";
                adapter.notifyItemChanged(assistantIndex);
            }
        }
        assistantIndex = -1;
        assistantInsertIndex = -1;
        queuedUserMessages = false;
        pendingAssistant = null;
        store.saveMessages(character.id, messages);
        connectionLabel.setVisibility(View.GONE);
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
                "复制", () -> copy(message.content)));
        if (!message.hasImage() && !message.hasLocation()) {
            items.add(new WechatMessageMenu.Item(
                    "编辑", () -> editMessage(message, position)));
        }
        if (message.failed) {
            items.add(new WechatMessageMenu.Item(
                    "重试", () -> retryFailed(message, position)));
        } else if (ChatMessage.ASSISTANT.equals(message.role)) {
            items.add(new WechatMessageMenu.Item(
                    "重新生成", () -> regenerate(position)));
        }
        items.add(new WechatMessageMenu.Item(
                "删除", () -> deleteMessage(position)));
        WechatMessageMenu.show(this, anchor, items);
    }

    @Override
    public void onAvatarClick(CharacterCard card) {
        showCharacterInfo();
    }

    private void copy(String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("聊天消息", text));
        LocalizedToast.makeText(this, "已复制", Toast.LENGTH_SHORT).show();
    }

    private void editMessage(ChatMessage message, int position) {
        EditText edit = MiuixUi.field(this, "", false);
        edit.setSingleLine(false);
        edit.setMaxLines(8);
        edit.setText(message.content);
        edit.setSelection(edit.length());
        FrameLayout wrapper = new FrameLayout(this);
        wrapper.setPadding(MiuixUi.dp(this, 20), MiuixUi.dp(this, 8),
                MiuixUi.dp(this, 20), 0);
        wrapper.addView(edit, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        new LocalizedAlertDialogBuilder(this)
                .setTitle("编辑消息")
                .setView(wrapper)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (dialog, which) -> {
                    message.content = edit.getText().toString().trim();
                    message.failed = false;
                    adapter.notifyItemChanged(position);
                    store.saveMessages(character.id, messages);
                })
                .show();
    }

    private void regenerate(int position) {
        if (generating) return;
        while (messages.size() > position) messages.remove(messages.size() - 1);
        adapter.notifyDataSetChanged();
        store.saveMessages(character.id, messages);
        startGeneration();
    }

    private void retryFailed(ChatMessage message, int position) {
        if (generating || !message.failed
                || position < 0 || position >= messages.size()
                || messages.get(position) != message) {
            return;
        }
        messages.remove(position);
        adapter.notifyItemRemoved(position);
        store.saveMessages(character.id, messages);
        startGeneration();
    }

    private void deleteMessage(int position) {
        if (generating || position < 0 || position >= messages.size()) return;
        messages.remove(position);
        adapter.notifyItemRemoved(position);
        store.saveMessages(character.id, messages);
    }

    private void showChatMenu(View anchor) {
        Intent intent = new Intent(this, ChatInfoActivity.class);
        intent.putExtra(ChatInfoActivity.EXTRA_CHARACTER_ID, character.id);
        startActivity(intent);
    }

    private void showCharacterInfo() {
        Intent intent = new Intent(this, CharacterProfileActivity.class);
        intent.putExtra(CharacterProfileActivity.EXTRA_CHARACTER_ID, character.id);
        startActivity(intent);
    }

    private void openVoiceCall() {
        Intent intent = new Intent(this, VoiceCallActivity.class);
        intent.putExtra(VoiceCallActivity.EXTRA_CHARACTER_ID, character.id);
        startActivity(intent);
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
                LocalizedToast.makeText(ChatActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
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
        }, "miutavern-gallery").start();
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
        }, "miutavern-camera").start();
    }

    private void sendImage(String path) {
        ChatMessage message = new ChatMessage(ChatMessage.USER, "[图片]");
        message.attachmentType = ChatMessage.ATTACHMENT_IMAGE;
        message.attachmentPath = path;
        message.attachmentMime = "image/jpeg";
        addUserMessage(message);
    }

    private void addUserMessage(ChatMessage message) {
        boolean generationInProgress = generating;
        messages.add(message);
        adapter.notifyItemInserted(messages.size() - 1);
        store.saveMessages(character.id, messages);
        scrollToBottom(true);
        if (generationInProgress) {
            queuedUserMessages = true;
        } else {
            startGeneration();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ALBUM && resultCode == Activity.RESULT_OK
                && data != null && data.getData() != null) {
            importImage(data.getData());
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
                .setTitle("清空与 " + character.name + " 的聊天？")
                .setMessage("本机保存的消息会被删除，角色卡不受影响。")
                .setNegativeButton("取消", null)
                .setPositiveButton("清空", (dialog, which) -> {
                    if (generating) stopGeneration();
                    messages.clear();
                    if (!TextUtils.isEmpty(character.firstMessage)) {
                        messages.add(new ChatMessage(ChatMessage.ASSISTANT,
                                character.replaceMacros(character.firstMessage, store.getConfig().persona)));
                    }
                    adapter.notifyDataSetChanged();
                    store.saveMessages(character.id, messages);
                    scrollToBottom(false);
                })
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        store.touchCharacter(character.id);
        CharacterCard updated = store.getCharacter(character.id);
        if (updated != null) character = updated;
        if (!generating && recycler != null) {
            messages = new ArrayList<>(store.getMessages(character.id));
            adapter = new MessageAdapter(
                    this, character, store.getConfig().persona,
                    store.getConfig().personaAvatarPath, messages, this);
            recycler.setAdapter(adapter);
        }
        applyChatBackground();
        if (connectionLabel != null && !generating) {
            connectionLabel.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        chatVisible = true;
    }

    @Override
    protected void onStop() {
        chatVisible = false;
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
        if (backgroundView == null || character == null) return;
        android.graphics.Bitmap bitmap =
                MediaAttachmentStore.decodePreview(character.chatBackgroundPath);
        backgroundView.setImageBitmap(bitmap);
        backgroundView.setBackgroundColor(MiuixUi.chatBackground(this));
        backgroundView.setAlpha(bitmap == null ? 1f : 0.82f);
    }

    private void notifyReply(ChatMessage message) {
        CharacterCard saved = store.getCharacter(character.id);
        if (saved == null || saved.muted) return;
        saved.unread = Math.max(1, saved.unread + 1);
        store.upsertCharacter(saved);
        String content = message.content == null ? "收到一条新回复"
                : message.content.replace('\n', ' ').trim();
        if (content.length() > 160) content = content.substring(0, 160) + "…";
        Intent open = new Intent(this, ChatActivity.class);
        open.putExtra(EXTRA_CHARACTER_ID, saved.id);
        open.putExtra(EXTRA_MESSAGE_ID, message.id);
        ChatNotificationManager.notifyReply(
                this, saved.id, saved.name, content, open);
    }

    @Override
    protected void onDestroy() {
        if (isFinishing() && activeCall != null) activeCall.cancel();
        super.onDestroy();
    }
}
