package app.miuix.tavern.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import app.miuix.tavern.data.LocalStore;
import app.miuix.tavern.data.SecureStore;
import app.miuix.tavern.model.AppConfig;
import app.miuix.tavern.model.RealtimeProvider;
import app.miuix.tavern.network.ApiClient;
import app.miuix.tavern.network.CodexAuthClient;
import app.miuix.tavern.network.GitHubCopilotAuthClient;
import app.miuix.tavern.network.LocalModelManager;
import app.miuix.tavern.network.RealtimeModelClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SettingsActivity extends AppCompatActivity {
    private LocalStore store;
    private SecureStore secureStore;
    private LocalModelManager localModels;
    private String selectedGeneration;
    private final TextView[] generationButtons = new TextView[3];
    private final TextView[] accountProviderButtons = new TextView[2];
    private String selectedDirectApiFormat = AppConfig.DIRECT_FORMAT_CHAT;
    private String selectedAccountProvider = AppConfig.ACCOUNT_GPT;
    private String stagedGptModel = "gpt-5.4";
    private String stagedCopilotModel = "gpt-5.4";
    private String selectedLocalModel = AppConfig.LOCAL_MODEL_QWEN_08;
    private String selectedRealtimeProvider = RealtimeProvider.QWEN;
    private TextView directFormatField;
    private TextView realtimeProviderField;
    private TextView realtimeNote;

    private EditText baseUrl;
    private EditText apiKey;
    private AutoCompleteTextView directModel;
    private AutoCompleteTextView gptModel;
    private EditText githubOAuthClientId;
    private EditText copilotEndpoint;
    private EditText persona;
    private AutoCompleteTextView realtimeModel;
    private EditText realtimeEndpoint;
    private EditText realtimeCredential;
    private EditText realtimeVoice;
    private EditText realtimeExtra;
    private SwitchCompat reasoningSwitch;
    private SwitchCompat webSearchSwitch;
    private SwitchCompat groupAutonomousSwitch;

    private TextView directModelStatus;
    private TextView gptModelStatus;
    private TextView realtimeModelStatus;
    private TextView directModelsButton;
    private TextView gptModelsButton;
    private TextView realtimeModelsButton;
    private TextView generationHelp;
    private TextView accountStatus;
    private TextView accountBody;
    private TextView accountButton;
    private TextView logoutButton;
    private TextView testButton;
    private TextView saveButton;
    private LinearLayout deviceCodePanel;
    private LinearLayout copilotConfigSection;
    private TextView deviceCodeLabel;
    private TextView deviceCodeText;
    private TextView deviceOpenButton;

    private String verificationCode = "";
    private String verificationUrl = "";
    private CodexAuthClient.LoginCall gptLoginCall;
    private GitHubCopilotAuthClient.LoginCall copilotLoginCall;
    private boolean directModelsLoading;
    private boolean gptModelsLoading;
    private int realtimeModelsRequest;
    private final Map<String, LocalModelViews> localModelViews =
            new LinkedHashMap<>();
    private final LocalModelManager.Listener localModelListener = snapshot ->
            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed()) updateLocalModel(snapshot);
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MiuixUi.applySystemBars(
                this, MiuixUi.BACKGROUND, MiuixUi.BACKGROUND);
        store = new LocalStore(this);
        secureStore = new SecureStore(this);
        localModels = LocalModelManager.get(this);
        setContentView(buildContent(store.getConfig()));
    }

    private View buildContent(AppConfig config) {
        selectedAccountProvider = config.accountProvider;
        selectedLocalModel = config.localModel;
        stagedGptModel = config.gptModel;
        stagedCopilotModel = config.copilotModel;
        selectedRealtimeProvider = RealtimeProvider.find(
                config.realtimeProvider).id;
        LinearLayout root = MiuixUi.vertical(this);
        root.setBackgroundColor(MiuixUi.background(this));
        root.addView(buildToolbar());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout page = MiuixUi.vertical(this);
        page.setPadding(0, 0, 0, MiuixUi.dp(this, 30));
        scroll.addView(page);

        TextView title = MiuixUi.text(this, "连接与账户", 31, MiuixUi.TEXT_PRIMARY, true);
        title.setLetterSpacing(-0.025f);
        addWithMargins(page, title, 18, 12, 18, 5);
        TextView intro = MiuixUi.text(this,
                "可使用 GPT 兼容、Claude、Gemini 等 API 直连，"
                        + "也可选择已配置的 GPT / GitHub Copilot 账户或本地 Qwen3.5 生成聊天。",
                14, MiuixUi.TEXT_SECONDARY, false);
        intro.setLineSpacing(MiuixUi.dp(this, 3), 1.05f);
        addWithMargins(page, intro, 18, 0, 18, 16);

        LinearLayout connection = MiuixUi.card(this);
        connection.addView(cardHeader("直连 API", "多协议"));
        TextView connectionBody = bodyText(
                "可填写 API 根地址或完整请求地址；支持 GPT 兼容、Responses、"
                        + "Claude Messages、Gemini GenerateContent 等协议，"
                        + "并自动尝试相应的模型列表端点。");
        connection.addView(connectionBody, bodyParams(3, 7));
        baseUrl = addField(connection, "API 地址", "https://api.openai.com/v1",
                config.baseUrl, false);
        baseUrl.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        apiKey = addField(connection, "API Key", "sk-…（本地服务可留空）",
                secureStore.getDirectApiKey(), true);
        connection.addView(buildDirectFormatSelector(), bodyParams(8, 0));
        directModel = addModelField(connection, "模型", "点按选择或输入模型 ID",
                config.model, false);
        LinearLayout reasoningRow = MiuixUi.horizontal(this);
        reasoningRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout reasoningLabels = MiuixUi.vertical(this);
        reasoningLabels.addView(MiuixUi.text(
                this, "显示模型思考过程", 15, MiuixUi.TEXT_PRIMARY, true));
        TextView reasoningDetail = MiuixUi.text(
                this,
                "默认关闭；过滤 reasoning_content、reasoning 等内部推理字段",
                12.5f,
                MiuixUi.TEXT_SECONDARY,
                false);
        reasoningLabels.addView(reasoningDetail);
        reasoningRow.addView(reasoningLabels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        reasoningSwitch = new SwitchCompat(this);
        reasoningSwitch.setChecked(config.showReasoning);
        reasoningSwitch.setShowText(false);
        reasoningSwitch.setContentDescription(
                L10n.tr(this, "显示模型思考过程"));
        reasoningRow.addView(reasoningSwitch, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, MiuixUi.dp(this, 48)));
        connection.addView(reasoningRow, bodyParams(9, 0));
        LinearLayout webSearchRow = MiuixUi.horizontal(this);
        webSearchRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout webSearchLabels = MiuixUi.vertical(this);
        webSearchLabels.addView(MiuixUi.text(
                this, "允许角色联网搜索", 15, MiuixUi.TEXT_PRIMARY, true));
        TextView webSearchDetail = MiuixUi.text(
                this,
                "优先调用模型或厂商的原生搜索；不支持时才由应用检索并注入结果。应用兜底时，查询会发送到公共搜索服务",
                12.5f,
                MiuixUi.TEXT_SECONDARY,
                false);
        webSearchDetail.setMaxLines(3);
        webSearchLabels.addView(webSearchDetail);
        webSearchRow.addView(webSearchLabels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        webSearchSwitch = new SwitchCompat(this);
        webSearchSwitch.setChecked(config.webSearch);
        webSearchSwitch.setShowText(false);
        webSearchSwitch.setContentDescription(
                L10n.tr(this, "允许角色联网搜索"));
        webSearchRow.addView(webSearchSwitch, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, MiuixUi.dp(this, 48)));
        connection.addView(webSearchRow, bodyParams(9, 0));
        LinearLayout autonomousRow = MiuixUi.horizontal(this);
        autonomousRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout autonomousLabels = MiuixUi.vertical(this);
        autonomousLabels.addView(MiuixUi.text(
                this, "群聊主动发言", 15, MiuixUi.TEXT_PRIMARY, true));
        TextView autonomousDetail = MiuixUi.text(
                this,
                "群聊空闲时，角色会偶尔自行发起话题",
                12.5f,
                MiuixUi.TEXT_SECONDARY,
                false);
        autonomousDetail.setMaxLines(2);
        autonomousLabels.addView(autonomousDetail);
        autonomousRow.addView(autonomousLabels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        groupAutonomousSwitch = new SwitchCompat(this);
        groupAutonomousSwitch.setChecked(config.groupAutonomousMessages);
        groupAutonomousSwitch.setShowText(false);
        groupAutonomousSwitch.setContentDescription(
                L10n.tr(this, "群聊主动发言"));
        autonomousRow.addView(groupAutonomousSwitch, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, MiuixUi.dp(this, 48)));
        connection.addView(autonomousRow, bodyParams(9, 0));
        addWithMargins(page, connection, 14, 0, 14, 12);

        LinearLayout account = MiuixUi.card(this);
        account.addView(cardHeader("账户", "GPT / GitHub Copilot"));
        account.addView(buildAccountProviderSelector(), bodyParams(5, 2));
        accountStatus = MiuixUi.text(this, "", 14, MiuixUi.TEXT_SECONDARY, true);
        account.addView(accountStatus, bodyParams(5, 4));
        accountBody = bodyText(
                "兼容 Hermes 的 Codex 设备授权。应用不会要求或读取 ChatGPT 密码；"
                        + "登录只在 OpenAI 官方页面完成。");
        account.addView(accountBody, bodyParams(3, 7));
        copilotConfigSection = MiuixUi.vertical(this);
        githubOAuthClientId = addField(
                copilotConfigSection,
                "GitHub OAuth Client ID",
                "OAuth App 中启用 Device Flow 后填写",
                config.githubOAuthClientId,
                false);
        copilotEndpoint = addField(
                copilotConfigSection,
                "Copilot SDK 网关",
                "https://example.com/v1",
                config.copilotEndpoint,
                false);
        copilotEndpoint.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        account.addView(copilotConfigSection, bodyParams(0, 2));
        gptModel = addModelField(
                account,
                "账户模型",
                "点按选择账户可用模型",
                AppConfig.ACCOUNT_COPILOT.equals(config.accountProvider)
                        ? config.copilotModel : config.gptModel,
                true);

        LinearLayout accountActions = MiuixUi.horizontal(this);
        accountButton = MiuixUi.pillButton(this, "登录 GPT 账户", true);
        accountButton.setOnClickListener(v -> handleAccountAction());
        accountActions.addView(accountButton, new LinearLayout.LayoutParams(
                0, MiuixUi.dp(this, 46), 1));
        logoutButton = MiuixUi.pillButton(this, "退出", false);
        logoutButton.setOnClickListener(v -> confirmLogout());
        LinearLayout.LayoutParams logoutParams =
                new LinearLayout.LayoutParams(0, MiuixUi.dp(this, 46), 0.38f);
        logoutParams.leftMargin = MiuixUi.dp(this, 9);
        accountActions.addView(logoutButton, logoutParams);
        account.addView(accountActions, bodyParams(7, 0));

        deviceCodePanel = MiuixUi.vertical(this);
        deviceCodePanel.setPadding(MiuixUi.dp(this, 13), MiuixUi.dp(this, 12),
                MiuixUi.dp(this, 13), MiuixUi.dp(this, 13));
        deviceCodePanel.setBackground(MiuixUi.shape(
                Color.rgb(244, 247, 252), 14, this));
        deviceCodeLabel = MiuixUi.text(this,
                "在 OpenAI 页面输入此验证码",
                12.5f, MiuixUi.TEXT_SECONDARY, true);
        deviceCodePanel.addView(deviceCodeLabel);
        deviceCodeText = MiuixUi.text(this, "—", 24, MiuixUi.TEXT_PRIMARY, true);
        deviceCodeText.setLetterSpacing(0.12f);
        deviceCodeText.setTextIsSelectable(true);
        deviceCodePanel.addView(deviceCodeText, bodyParams(4, 8));
        deviceOpenButton = MiuixUi.pillButton(this, "复制并打开 OpenAI", false);
        deviceOpenButton.setOnClickListener(v -> launchAuthorization());
        deviceCodePanel.addView(deviceOpenButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, MiuixUi.dp(this, 44)));
        deviceCodePanel.setVisibility(View.GONE);
        account.addView(deviceCodePanel, bodyParams(10, 0));
        addWithMargins(page, account, 14, 0, 14, 12);

        LinearLayout local = MiuixUi.card(this);
        local.addView(cardHeader("本地模型", "端侧运行"));
        TextView localBody = bodyText(
                "模型下载后会在设备上生成聊天，不把完整提示词或历史消息发送给模型服务。"
                        + "启用联网搜索时，本轮查询会发送到公共搜索服务。下载支持断点续传，"
                        + "建议连接 Wi-Fi 并预留充足存储和内存。");
        local.addView(localBody, bodyParams(3, 4));
        if (!LocalModelManager.isRuntimeSupported()) {
            TextView warning = MiuixUi.text(
                    this,
                    LocalModelManager.runtimeSupportMessage(),
                    13,
                    Color.rgb(180, 110, 20),
                    true);
            local.addView(warning, bodyParams(5, 4));
        }
        LocalModelManager.ModelSpec[] models = LocalModelManager.models();
        for (int i = 0; i < models.length; i++) {
            if (i > 0) local.addView(MiuixUi.divider(this, 0), bodyParams(10, 7));
            local.addView(buildLocalModelRow(models[i]));
        }
        TextView localLimit = bodyText(
                "本地 Qwen 支持图片理解；模型包会同时下载对应视觉组件。"
                        + "联网搜索结果由应用注入，本地和 API 模式均可使用。"
                        + "首次回答会花较长时间载入模型。");
        local.addView(localLimit, bodyParams(9, 0));
        addWithMargins(page, local, 14, 0, 14, 12);

        LinearLayout realtime = MiuixUi.card(this);
        realtime.addView(cardHeader("Realtime 语音", "多厂商"));
        TextView realtimeBody = bodyText(
                "选择实时语音厂商与模型。支持原生 WebSocket 的服务会直接连接；"
                        + "需要云端签名或专用 SDK 的服务使用实时适配器 WSS。");
        realtime.addView(realtimeBody, bodyParams(3, 7));
        realtime.addView(buildRealtimeProviderSelector(), bodyParams(1, 0));
        realtimeModel = addRealtimeModelField(
                realtime,
                "实时模型",
                config.realtimeModel);
        realtimeEndpoint = addField(
                realtime,
                "连接地址 / 适配器 WSS",
                "留空使用厂商默认地址",
                config.realtimeEndpoint,
                true);
        realtimeCredential = addField(
                realtime,
                "API Key / 会话凭据",
                "按厂商独立加密保存在 Android Keystore",
                secureStore.getRealtimeCredential(selectedRealtimeProvider),
                true);
        realtimeVoice = addField(
                realtime,
                "音色 / Agent ID",
                "可选；ElevenAgents 在此填写 Agent ID",
                config.realtimeVoice,
                false);
        realtimeExtra = addField(
                realtime,
                "厂商参数 JSON",
                "可选，例如房间号、工作空间或适配器参数",
                config.realtimeExtra,
                false);
        View.OnFocusChangeListener refreshRealtimeOnCommit = (view, focused) -> {
            if (!focused) refreshRealtimeModels(false);
        };
        realtimeCredential.setOnFocusChangeListener(refreshRealtimeOnCommit);
        realtimeEndpoint.setOnFocusChangeListener(refreshRealtimeOnCommit);
        realtimeNote = bodyText("");
        realtime.addView(realtimeNote, bodyParams(8, 0));
        applyRealtimeProvider(false);
        addWithMargins(page, realtime, 14, 0, 14, 12);

        LinearLayout route = MiuixUi.card(this);
        route.addView(sectionTitle("聊天生成方式"));
        LinearLayout segmented = MiuixUi.horizontal(this);
        segmented.setPadding(MiuixUi.dp(this, 4), MiuixUi.dp(this, 4),
                MiuixUi.dp(this, 4), MiuixUi.dp(this, 4));
        segmented.setBackground(MiuixUi.shape(Color.rgb(229, 229, 233), 15, this));
        String[] labels = {"直连 API", "账户", "本地"};
        String[] values = {
                AppConfig.GENERATION_DIRECT_API,
                AppConfig.GENERATION_ACCOUNT,
                AppConfig.GENERATION_LOCAL
        };
        for (int i = 0; i < labels.length; i++) {
            final String value = values[i];
            TextView button = MiuixUi.text(
                    this, labels[i], 14, MiuixUi.TEXT_PRIMARY, true);
            button.setGravity(Gravity.CENTER);
            button.setOnClickListener(v -> selectGeneration(value));
            MiuixUi.pressable(button, 0.96f);
            segmented.addView(button, new LinearLayout.LayoutParams(
                    0, MiuixUi.dp(this, 40), 1));
            generationButtons[i] = button;
        }
        route.addView(segmented, bodyParams(3, 0));
        generationHelp = bodyText("");
        route.addView(generationHelp, bodyParams(8, 0));
        addWithMargins(page, route, 14, 0, 14, 12);

        LinearLayout identity = MiuixUi.card(this);
        identity.addView(sectionTitle("聊天身份"));
        String personaDisplay = "你".equals(config.persona)
                ? L10n.tr(this, "你") : config.persona;
        persona = addField(identity, "我的称呼", "你", personaDisplay, false);
        identity.addView(bodyText("头像请在“我的”页面点击个人头像更换。"),
                bodyParams(7, 0));
        addWithMargins(page, identity, 14, 0, 14, 12);

        LinearLayout security = MiuixUi.card(this);
        security.addView(sectionTitle("本地密钥保护"));
        security.addView(bodyText(
                "直连 API Key、GPT OAuth 令牌、GitHub OAuth 令牌和实时语音凭据"
                        + "均由 Android Keystore 生成的 AES-GCM 密钥加密。"),
                bodyParams(3, 0));
        addWithMargins(page, security, 14, 0, 14, 14);

        LinearLayout actions = MiuixUi.horizontal(this);
        testButton = MiuixUi.pillButton(this, "测试 API", false);
        testButton.setOnClickListener(v -> testConnection());
        actions.addView(testButton, new LinearLayout.LayoutParams(
                0, MiuixUi.dp(this, 48), 1));
        saveButton = MiuixUi.pillButton(this, "保存设置", true);
        saveButton.setOnClickListener(v -> save());
        LinearLayout.LayoutParams saveParams =
                new LinearLayout.LayoutParams(0, MiuixUi.dp(this, 48), 1);
        saveParams.leftMargin = MiuixUi.dp(this, 10);
        actions.addView(saveButton, saveParams);
        addWithMargins(page, actions, 14, 0, 14, 8);

        selectDirectApiFormat(config.directApiFormat);
        selectAccountProvider(config.accountProvider);
        selectGeneration(config.generation);
        refreshAccountState();
        loadCachedModels(false);
        loadCachedModels(true);
        View.OnFocusChangeListener refreshOnCommit = (view, focused) -> {
            if (!focused) refreshModels(false, false);
        };
        baseUrl.setOnFocusChangeListener(refreshOnCommit);
        apiKey.setOnFocusChangeListener(refreshOnCommit);
        page.post(() -> {
            if (!baseUrl.getText().toString().trim().isEmpty()) {
                refreshModels(false, false);
            }
            if (hasSelectedAccount()) refreshModels(true, false);
        });
        return root;
    }

    private View buildToolbar() {
        LinearLayout toolbar = MiuixUi.horizontal(this);
        toolbar.setPadding(MiuixUi.dp(this, 8), MiuixUi.dp(this, 4),
                MiuixUi.dp(this, 12), 0);
        FrameLayout back = new FrameLayout(this);
        back.setContentDescription(L10n.tr(this, "返回"));
        MiuixUi.ripple(back, MiuixUi.BACKGROUND, 14);
        MiuixUi.pressable(back, 0.9f);
        LineIconView icon = new LineIconView(this);
        icon.setType(LineIconView.BACK);
        back.addView(icon, new FrameLayout.LayoutParams(
                MiuixUi.dp(this, 25), MiuixUi.dp(this, 25), Gravity.CENTER));
        back.setOnClickListener(v -> finish());
        toolbar.addView(back, new LinearLayout.LayoutParams(
                MiuixUi.dp(this, 48), MiuixUi.dp(this, 48)));
        toolbar.addView(MiuixUi.text(this, "设置", 16, MiuixUi.TEXT_PRIMARY, true),
                new LinearLayout.LayoutParams(0, MiuixUi.dp(this, 48), 1));
        return toolbar;
    }

    private LinearLayout cardHeader(String titleValue, String badgeValue) {
        LinearLayout row = MiuixUi.horizontal(this);
        TextView title = MiuixUi.text(this, titleValue, 19, MiuixUi.TEXT_PRIMARY, true);
        row.addView(title, new LinearLayout.LayoutParams(0, MiuixUi.dp(this, 30), 1));
        TextView badge = MiuixUi.text(this, badgeValue, 11.5f, MiuixUi.GREEN, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(MiuixUi.shape(Color.rgb(233, 249, 240), 12, this));
        badge.setPadding(MiuixUi.dp(this, 10), 0, MiuixUi.dp(this, 10), 0);
        row.addView(badge, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, MiuixUi.dp(this, 25)));
        return row;
    }

    private View buildLocalModelRow(LocalModelManager.ModelSpec model) {
        LinearLayout wrapper = MiuixUi.vertical(this);

        LinearLayout titleRow = MiuixUi.horizontal(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = MiuixUi.text(
                this, model.displayName, 16, MiuixUi.TEXT_PRIMARY, true);
        titleRow.addView(title, new LinearLayout.LayoutParams(
                0, MiuixUi.dp(this, 30), 1));
        TextView quant = MiuixUi.text(
                this, model.quantization, 11.5f, MiuixUi.TEXT_SECONDARY, true);
        quant.setGravity(Gravity.CENTER);
        quant.setPadding(MiuixUi.dp(this, 9), 0, MiuixUi.dp(this, 9), 0);
        quant.setBackground(MiuixUi.shape(Color.rgb(242, 242, 245), 11, this));
        titleRow.addView(quant, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, MiuixUi.dp(this, 24)));
        wrapper.addView(titleRow);

        TextView description = bodyText(model.description);
        wrapper.addView(description, bodyParams(1, 0));
        TextView status = MiuixUi.text(
                this, "尚未下载", 12.5f, MiuixUi.TEXT_SECONDARY, false);
        wrapper.addView(status, bodyParams(5, 0));

        ProgressBar progress = new ProgressBar(
                this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, MiuixUi.dp(this, 3));
        progressParams.topMargin = MiuixUi.dp(this, 7);
        wrapper.addView(progress, progressParams);

        LinearLayout actions = MiuixUi.horizontal(this);
        TextView action = MiuixUi.pillButton(this, "下载", true);
        action.setOnClickListener(v -> handleLocalModelAction(model.id));
        actions.addView(action, new LinearLayout.LayoutParams(
                0, MiuixUi.dp(this, 43), 1));
        TextView delete = MiuixUi.pillButton(this, "删除", false);
        delete.setOnClickListener(v -> confirmDeleteLocalModel(model.id));
        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(
                0, MiuixUi.dp(this, 43), 0.46f);
        deleteParams.leftMargin = MiuixUi.dp(this, 8);
        actions.addView(delete, deleteParams);
        LinearLayout.LayoutParams actionsParams = bodyParams(8, 0);
        wrapper.addView(actions, actionsParams);

        localModelViews.put(model.id,
                new LocalModelViews(status, progress, action, delete));
        updateLocalModel(localModels.snapshot(model.id));
        return wrapper;
    }

    private void handleLocalModelAction(String modelId) {
        LocalModelManager.Snapshot snapshot = localModels.snapshot(modelId);
        if (snapshot.state == LocalModelManager.STATE_DOWNLOADING
                || snapshot.state == LocalModelManager.STATE_VERIFYING) {
            localModels.pauseDownload(modelId);
            return;
        }
        if (snapshot.state == LocalModelManager.STATE_INSTALLED) {
            selectedLocalModel = modelId;
            selectGeneration(AppConfig.GENERATION_LOCAL);
            AppConfig saved = readForm();
            saved.localModel = modelId;
            saved.generation = AppConfig.GENERATION_LOCAL;
            store.saveConfig(saved);
            LocalModelViews views = localModelViews.get(modelId);
            if (views != null) {
                views.action.performHapticFeedback(
                        android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            }
            refreshLocalModelRows();
            LocalizedToast.makeText(
                    this,
                    "已切换到 " + snapshot.model.displayName + " 本地生成",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        localModels.startDownload(modelId);
    }

    private void confirmDeleteLocalModel(String modelId) {
        LocalModelManager.ModelSpec model = LocalModelManager.find(modelId);
        new LocalizedAlertDialogBuilder(this)
                .setTitle("删除 " + model.displayName + "？")
                .setMessage("将删除已下载模型和未完成的下载；聊天记录不会受到影响。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    boolean deleted = localModels.deleteModel(modelId);
                    if (modelId.equals(selectedLocalModel)
                            && AppConfig.GENERATION_LOCAL.equals(selectedGeneration)) {
                        selectGeneration(AppConfig.GENERATION_DIRECT_API);
                        AppConfig saved = readForm();
                        saved.generation = AppConfig.GENERATION_DIRECT_API;
                        store.saveConfig(saved);
                    }
                    LocalizedToast.makeText(
                            this,
                            deleted ? "本地模型已删除" : "部分模型文件无法删除，请稍后重试",
                            Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void refreshLocalModelRows() {
        for (LocalModelManager.ModelSpec model : LocalModelManager.models()) {
            updateLocalModel(localModels.snapshot(model.id));
        }
    }

    private void updateLocalModel(LocalModelManager.Snapshot snapshot) {
        LocalModelViews views = localModelViews.get(snapshot.model.id);
        if (views == null) return;
        boolean selected = snapshot.model.id.equals(selectedLocalModel)
                && AppConfig.GENERATION_LOCAL.equals(selectedGeneration);
        boolean hasBytes = snapshot.downloadedBytes > 0L;
        views.delete.setVisibility(hasBytes
                || snapshot.state == LocalModelManager.STATE_INSTALLED
                ? View.VISIBLE : View.GONE);
        views.progress.setVisibility(
                snapshot.state == LocalModelManager.STATE_DOWNLOADING || hasBytes
                        ? View.VISIBLE : View.GONE);
        views.progress.setProgress(snapshot.percent());
        views.action.setEnabled(LocalModelManager.isRuntimeSupported());

        if (snapshot.state == LocalModelManager.STATE_DOWNLOADING) {
            views.status.setText("下载中 " + snapshot.percent() + "% · "
                    + LocalModelManager.readableBytes(snapshot.downloadedBytes)
                    + " / " + LocalModelManager.readableBytes(snapshot.model.totalBytes()));
            views.status.setTextColor(MiuixUi.color(this, MiuixUi.GREEN));
            views.action.setText("暂停");
        } else if (snapshot.state == LocalModelManager.STATE_VERIFYING) {
            views.status.setText("正在校验模型完整性…");
            views.status.setTextColor(MiuixUi.color(this, MiuixUi.GREEN));
            views.action.setText("暂停");
        } else if (snapshot.state == LocalModelManager.STATE_INSTALLED) {
            views.status.setText(selected ? "已下载 · 当前正在使用" : "已下载 · 可离线使用");
            views.status.setTextColor(MiuixUi.color(this, MiuixUi.GREEN));
            views.action.setText(selected ? "使用中" : "使用");
            views.action.setEnabled(!selected);
            views.progress.setProgress(100);
        } else if (snapshot.state == LocalModelManager.STATE_ERROR) {
            views.status.setText("未完成 · " + snapshot.error);
            views.status.setTextColor(MiuixUi.color(
                    this, Color.rgb(198, 52, 58)));
            views.action.setText(hasBytes ? "继续" : "重试");
        } else {
            views.status.setText(hasBytes
                    ? "已暂停 · " + LocalModelManager.readableBytes(
                    snapshot.downloadedBytes) + " / "
                    + LocalModelManager.readableBytes(snapshot.model.totalBytes())
                    : "尚未下载");
            views.status.setTextColor(MiuixUi.color(
                    this, MiuixUi.TEXT_SECONDARY));
            views.action.setText(hasBytes ? "继续" : "下载");
        }
    }

    private TextView sectionTitle(String value) {
        TextView title = MiuixUi.text(this, value, 18, MiuixUi.TEXT_PRIMARY, true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, MiuixUi.dp(this, 30));
        params.bottomMargin = MiuixUi.dp(this, 4);
        title.setLayoutParams(params);
        return title;
    }

    private TextView bodyText(String value) {
        TextView body = MiuixUi.text(this, value, 13.5f, MiuixUi.TEXT_SECONDARY, false);
        body.setLineSpacing(MiuixUi.dp(this, 3), 1.05f);
        return body;
    }

    private LinearLayout.LayoutParams bodyParams(int top, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = MiuixUi.dp(this, top);
        params.bottomMargin = MiuixUi.dp(this, bottom);
        return params;
    }

    private EditText addField(
            LinearLayout parent,
            String labelValue,
            String hint,
            String value,
            boolean secret) {
        TextView label = MiuixUi.text(this, labelValue, 13, MiuixUi.TEXT_SECONDARY, true);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, MiuixUi.dp(this, 28));
        labelParams.topMargin = MiuixUi.dp(this, 7);
        parent.addView(label, labelParams);
        EditText field = MiuixUi.field(this, hint, secret);
        field.setText(value == null ? "" : value);
        parent.addView(field, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, MiuixUi.dp(this, 48)));
        return field;
    }

    private AutoCompleteTextView addRealtimeModelField(
            LinearLayout parent, String labelValue, String value) {
        LinearLayout labelRow = MiuixUi.horizontal(this);
        TextView label = MiuixUi.text(
                this, labelValue, 13, MiuixUi.TEXT_SECONDARY, true);
        labelRow.addView(label, new LinearLayout.LayoutParams(
                0, MiuixUi.dp(this, 28), 1));
        realtimeModelsButton = MiuixUi.text(
                this, "刷新列表", 12.5f, MiuixUi.GREEN, true);
        realtimeModelsButton.setGravity(Gravity.CENTER);
        realtimeModelsButton.setPadding(
                MiuixUi.dp(this, 10), 0, MiuixUi.dp(this, 4), 0);
        realtimeModelsButton.setOnClickListener(
                v -> refreshRealtimeModels(true));
        MiuixUi.pressable(realtimeModelsButton, 0.95f);
        labelRow.addView(realtimeModelsButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, MiuixUi.dp(this, 28)));
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, MiuixUi.dp(this, 28));
        labelParams.topMargin = MiuixUi.dp(this, 7);
        parent.addView(labelRow, labelParams);
        AutoCompleteTextView field = MiuixUi.modelField(
                this, "选择厂商支持的实时模型");
        field.setText(value == null ? "" : value);
        field.setOnClickListener(v -> field.showDropDown());
        parent.addView(field, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, MiuixUi.dp(this, 48)));
        realtimeModelStatus = MiuixUi.text(
                this, "等待获取模型列表", 11.5f,
                MiuixUi.TEXT_SECONDARY, false);
        parent.addView(realtimeModelStatus, bodyParams(5, 0));
        return field;
    }

    private View buildRealtimeProviderSelector() {
        LinearLayout wrapper = MiuixUi.vertical(this);
        wrapper.addView(MiuixUi.text(
                this, "语音服务", 13, MiuixUi.TEXT_SECONDARY, true),
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, MiuixUi.dp(this, 28)));
        LinearLayout field = MiuixUi.horizontal(this);
        field.setGravity(Gravity.CENTER_VERTICAL);
        field.setPadding(MiuixUi.dp(this, 14), 0, MiuixUi.dp(this, 10), 0);
        field.setBackground(MiuixUi.outlinedShape(
                Color.rgb(248, 248, 250), MiuixUi.HAIRLINE, 13, this));
        realtimeProviderField = MiuixUi.rawText(
                this,
                RealtimeProvider.find(selectedRealtimeProvider).displayName,
                15,
                MiuixUi.color(this, MiuixUi.TEXT_PRIMARY),
                false);
        field.addView(realtimeProviderField, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        TextView arrow = MiuixUi.rawText(
                this, "⌄", 22,
                MiuixUi.color(this, MiuixUi.TEXT_SECONDARY), false);
        arrow.setGravity(Gravity.CENTER);
        field.addView(arrow, new LinearLayout.LayoutParams(
                MiuixUi.dp(this, 32), ViewGroup.LayoutParams.MATCH_PARENT));
        field.setOnClickListener(v -> showRealtimeProviderPicker());
        field.setClickable(true);
        field.setFocusable(true);
        MiuixUi.pressable(field, 0.985f);
        wrapper.addView(field, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, MiuixUi.dp(this, 48)));
        return wrapper;
    }

    private void showRealtimeProviderPicker() {
        RealtimeProvider.Spec[] specs = RealtimeProvider.all();
        String[] labels = new String[specs.length];
        int checked = 0;
        for (int i = 0; i < specs.length; i++) {
            labels[i] = specs[i].displayName;
            if (specs[i].id.equals(selectedRealtimeProvider)) checked = i;
        }
        new LocalizedAlertDialogBuilder(this)
                .setTitle("选择实时语音服务")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    if (realtimeCredential != null) {
                        secureStore.setRealtimeCredential(
                                selectedRealtimeProvider,
                                realtimeCredential.getText().toString());
                    }
                    selectedRealtimeProvider = specs[which].id;
                    applyRealtimeProvider(true);
                    dialog.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void applyRealtimeProvider(boolean resetValues) {
        RealtimeProvider.Spec spec = RealtimeProvider.find(
                selectedRealtimeProvider);
        if (realtimeProviderField != null) {
            realtimeProviderField.setText(spec.displayName);
        }
        if (realtimeModel != null) {
            realtimeModel.setAdapter(new ArrayAdapter<>(
                    this,
                    android.R.layout.simple_dropdown_item_1line,
                    spec.models));
            String current = realtimeModel.getText().toString().trim();
            realtimeModel.setText(resetValues
                    ? spec.models[0]
                    : RealtimeProvider.normalizeModel(spec.id, current));
        }
        if (resetValues && realtimeEndpoint != null) {
            realtimeEndpoint.setText(spec.defaultEndpoint);
        }
        if (resetValues && realtimeVoice != null) {
            realtimeVoice.setText(spec.defaultVoice);
        }
        if (resetValues && realtimeExtra != null) realtimeExtra.setText("");
        if (resetValues && realtimeCredential != null) {
            realtimeCredential.setText(
                    secureStore.getRealtimeCredential(spec.id));
        }
        if (realtimeNote != null) {
            String mode = spec.adapterRequired
                    ? "此项使用澄语实时适配器协议。"
                    : "此项由澄语原生直连。";
            realtimeNote.setText(L10n.tr(this, mode)
                    + L10n.tr(this, spec.note)
                    + L10n.tr(this, " 请勿把云厂商 SecretKey 写入厂商参数。"));
        }
        if (realtimeModel != null && realtimeCredential != null) {
            realtimeModel.post(() -> refreshRealtimeModels(false));
        }
    }

    private void refreshRealtimeModels(boolean userInitiated) {
        RealtimeProvider.Spec spec = RealtimeProvider.find(
                selectedRealtimeProvider);
        String credential = realtimeCredential == null
                ? secureStore.getRealtimeCredential(spec.id)
                : realtimeCredential.getText().toString().trim();
        String endpoint = realtimeEndpoint == null
                ? "" : realtimeEndpoint.getText().toString().trim();
        int request = ++realtimeModelsRequest;
        if (spec.credentialRequired && credential.isEmpty()) {
            applyRealtimeFallback(spec, "填写 API Key 后自动获取");
            if (userInitiated) {
                LocalizedToast.makeText(
                        this, "请先填写 API Key", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        realtimeModelStatus.setText("正在从厂商获取模型…");
        realtimeModelStatus.setTextColor(MiuixUi.color(
                this, MiuixUi.TEXT_SECONDARY));
        realtimeModelsButton.setEnabled(false);
        RealtimeModelClient.fetch(
                spec, endpoint, credential,
                new RealtimeModelClient.Callback() {
                    @Override
                    public void onSuccess(List<String> models) {
                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()
                                    || request != realtimeModelsRequest
                                    || !spec.id.equals(selectedRealtimeProvider)) {
                                return;
                            }
                            realtimeModelsButton.setEnabled(true);
                            applyRealtimeModelList(models);
                            store.saveModelCache(
                                    realtimeModelScope(spec.id), models);
                            realtimeModelStatus.setText(
                                    "已从厂商获取 " + models.size() + " 个模型");
                            realtimeModelStatus.setTextColor(MiuixUi.color(
                                    SettingsActivity.this, MiuixUi.GREEN));
                            if (userInitiated) {
                                realtimeModel.requestFocus();
                                realtimeModel.performClick();
                                realtimeModelsButton.performHapticFeedback(
                                        android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                            }
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()
                                    || request != realtimeModelsRequest
                                    || !spec.id.equals(selectedRealtimeProvider)) {
                                return;
                            }
                            realtimeModelsButton.setEnabled(true);
                            List<String> cached = store.getModelCache(
                                    realtimeModelScope(spec.id));
                            if (!cached.isEmpty()) {
                                applyRealtimeModelList(cached);
                                realtimeModelStatus.setText(
                                        "厂商列表暂不可用 · 保留 "
                                                + cached.size() + " 个缓存模型");
                            } else {
                                applyRealtimeFallback(
                                        spec, "厂商列表暂不可用 · 使用内置列表");
                            }
                            realtimeModelStatus.setTextColor(MiuixUi.color(
                                    SettingsActivity.this,
                                    Color.rgb(180, 110, 20)));
                            if (userInitiated) {
                                LocalizedToast.makeText(
                                        SettingsActivity.this,
                                        L10n.tr(SettingsActivity.this,
                                                "获取厂商模型失败：") + message,
                                        Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                });
    }

    private void applyRealtimeFallback(
            RealtimeProvider.Spec spec, String status) {
        List<String> cached = store.getModelCache(realtimeModelScope(spec.id));
        applyRealtimeModelList(cached.isEmpty()
                ? java.util.Arrays.asList(spec.models) : cached);
        realtimeModelStatus.setText(status);
        realtimeModelStatus.setTextColor(MiuixUi.color(
                this, Color.rgb(180, 110, 20)));
        if (realtimeModelsButton != null) realtimeModelsButton.setEnabled(true);
    }

    private void applyRealtimeModelList(List<String> models) {
        if (models == null || models.isEmpty()) return;
        String current = realtimeModel.getText().toString().trim();
        realtimeModel.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                new ArrayList<>(models)));
        if (current.isEmpty() || !models.contains(current)) {
            realtimeModel.setText(models.get(0), false);
        }
    }

    private static String realtimeModelScope(String provider) {
        return "realtime:" + provider;
    }

    private AutoCompleteTextView addModelField(
            LinearLayout parent,
            String labelValue,
            String hint,
            String value,
            boolean gpt) {
        LinearLayout labelRow = MiuixUi.horizontal(this);
        TextView label = MiuixUi.text(
                this, labelValue, 13, MiuixUi.TEXT_SECONDARY, true);
        labelRow.addView(label, new LinearLayout.LayoutParams(
                0, MiuixUi.dp(this, 28), 1));
        TextView refresh = MiuixUi.text(
                this, "刷新列表", 12.5f, MiuixUi.GREEN, true);
        refresh.setGravity(Gravity.CENTER);
        refresh.setPadding(MiuixUi.dp(this, 10), 0, MiuixUi.dp(this, 4), 0);
        refresh.setOnClickListener(v -> refreshModels(gpt, true));
        MiuixUi.pressable(refresh, 0.95f);
        labelRow.addView(refresh, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, MiuixUi.dp(this, 28)));
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, MiuixUi.dp(this, 28));
        labelParams.topMargin = MiuixUi.dp(this, 7);
        parent.addView(labelRow, labelParams);

        AutoCompleteTextView field = MiuixUi.modelField(this, hint);
        field.setText(value == null ? "" : value);
        parent.addView(field, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, MiuixUi.dp(this, 48)));
        TextView status = MiuixUi.text(
                this, "等待获取模型列表", 11.5f, MiuixUi.TEXT_SECONDARY, false);
        parent.addView(status, bodyParams(5, 0));
        if (gpt) {
            gptModelStatus = status;
            gptModelsButton = refresh;
        } else {
            directModelStatus = status;
            directModelsButton = refresh;
        }
        return field;
    }

    private View buildDirectFormatSelector() {
        LinearLayout wrapper = MiuixUi.vertical(this);
        wrapper.addView(MiuixUi.text(
                this, "接口协议", 13, MiuixUi.TEXT_SECONDARY, true),
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, MiuixUi.dp(this, 28)));
        LinearLayout field = MiuixUi.horizontal(this);
        field.setGravity(Gravity.CENTER_VERTICAL);
        field.setPadding(MiuixUi.dp(this, 14), 0, MiuixUi.dp(this, 10), 0);
        field.setBackground(MiuixUi.outlinedShape(
                Color.rgb(248, 248, 250), MiuixUi.HAIRLINE, 13, this));
        directFormatField = MiuixUi.text(
                this, directFormatLabel(selectedDirectApiFormat),
                15, MiuixUi.TEXT_PRIMARY, false);
        field.addView(directFormatField, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        TextView arrow = MiuixUi.text(
                this, "⌄", 22, MiuixUi.TEXT_SECONDARY, false);
        arrow.setGravity(Gravity.CENTER);
        field.addView(arrow, new LinearLayout.LayoutParams(
                MiuixUi.dp(this, 32), ViewGroup.LayoutParams.MATCH_PARENT));
        field.setOnClickListener(v -> showDirectApiFormatPicker());
        field.setClickable(true);
        field.setFocusable(true);
        MiuixUi.pressable(field, 0.985f);
        wrapper.addView(field, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, MiuixUi.dp(this, 48)));
        return wrapper;
    }

    private View buildAccountProviderSelector() {
        LinearLayout segmented = MiuixUi.horizontal(this);
        segmented.setPadding(MiuixUi.dp(this, 4), MiuixUi.dp(this, 4),
                MiuixUi.dp(this, 4), MiuixUi.dp(this, 4));
        segmented.setBackground(MiuixUi.shape(
                Color.rgb(229, 229, 233), 15, this));
        String[] labels = {"GPT", "GitHub Copilot"};
        String[] values = {AppConfig.ACCOUNT_GPT, AppConfig.ACCOUNT_COPILOT};
        for (int i = 0; i < labels.length; i++) {
            final String value = values[i];
            TextView button = MiuixUi.text(
                    this, labels[i], 14, MiuixUi.TEXT_PRIMARY, true);
            button.setGravity(Gravity.CENTER);
            button.setOnClickListener(v -> selectAccountProvider(value));
            MiuixUi.pressable(button, 0.96f);
            segmented.addView(button, new LinearLayout.LayoutParams(
                    0, MiuixUi.dp(this, 40), 1));
            accountProviderButtons[i] = button;
        }
        return segmented;
    }

    private void syncCurrentAccountModel() {
        if (gptModel == null) return;
        String value = gptModel.getText().toString().trim();
        if (AppConfig.ACCOUNT_COPILOT.equals(selectedAccountProvider)) {
            stagedCopilotModel = value;
        } else {
            stagedGptModel = value;
        }
    }

    private void selectAccountProvider(String provider) {
        syncCurrentAccountModel();
        String normalized = AppConfig.ACCOUNT_COPILOT.equals(provider)
                ? AppConfig.ACCOUNT_COPILOT : AppConfig.ACCOUNT_GPT;
        if (!normalized.equals(selectedAccountProvider)) {
            cancelActiveLogin(false);
        }
        selectedAccountProvider = normalized;
        String[] values = {AppConfig.ACCOUNT_GPT, AppConfig.ACCOUNT_COPILOT};
        for (int i = 0; i < accountProviderButtons.length; i++) {
            TextView button = accountProviderButtons[i];
            if (button == null) continue;
            boolean selected = values[i].equals(selectedAccountProvider);
            button.setTextColor(MiuixUi.color(
                    this,
                    selected ? MiuixUi.TEXT_PRIMARY : MiuixUi.TEXT_SECONDARY));
            button.setBackground(selected
                    ? MiuixUi.shape(Color.WHITE, 12, this)
                    : MiuixUi.shape(Color.TRANSPARENT, 12, this));
            button.setElevation(selected ? MiuixUi.dp(this, 1) : 0);
        }
        if (gptModel != null) {
            String model = AppConfig.ACCOUNT_COPILOT.equals(
                    selectedAccountProvider)
                    ? stagedCopilotModel : stagedGptModel;
            gptModel.setText(model);
        }
        if (copilotConfigSection != null) {
            copilotConfigSection.setVisibility(
                    AppConfig.ACCOUNT_COPILOT.equals(selectedAccountProvider)
                            ? View.VISIBLE : View.GONE);
        }
        if (deviceCodePanel != null) {
            deviceCodePanel.setVisibility(View.GONE);
        }
        if (accountStatus != null) refreshAccountState();
        if (gptModel != null) {
            gptModelStatus.setText("等待获取模型列表");
            gptModelStatus.setTextColor(MiuixUi.color(
                    this, MiuixUi.TEXT_SECONDARY));
            loadCachedModels(true);
            if (hasSelectedAccount()) {
                gptModel.post(() -> refreshModels(true, false));
            }
        }
    }

    private void selectDirectApiFormat(String format) {
        if (AppConfig.DIRECT_FORMAT_CHAT.equals(format)
                || AppConfig.DIRECT_FORMAT_RESPONSES.equals(format)
                || AppConfig.DIRECT_FORMAT_AZURE.equals(format)
                || AppConfig.DIRECT_FORMAT_OLLAMA.equals(format)
                || AppConfig.DIRECT_FORMAT_CLAUDE.equals(format)
                || AppConfig.DIRECT_FORMAT_GEMINI.equals(format)
                || AppConfig.DIRECT_FORMAT_AUTO.equals(format)) {
            selectedDirectApiFormat = format;
        } else {
            selectedDirectApiFormat = AppConfig.DIRECT_FORMAT_CHAT;
        }
        if (directFormatField != null) {
            directFormatField.setText(directFormatLabel(selectedDirectApiFormat));
        }
    }

    private void showDirectApiFormatPicker() {
        String[] labels = {
                "GPT 兼容接口（默认）",
                "OpenAI Responses",
                "Claude Messages",
                "Gemini GenerateContent",
                "Azure OpenAI",
                "Ollama 原生接口",
                "自动识别"
        };
        String[] values = {
                AppConfig.DIRECT_FORMAT_CHAT,
                AppConfig.DIRECT_FORMAT_RESPONSES,
                AppConfig.DIRECT_FORMAT_CLAUDE,
                AppConfig.DIRECT_FORMAT_GEMINI,
                AppConfig.DIRECT_FORMAT_AZURE,
                AppConfig.DIRECT_FORMAT_OLLAMA,
                AppConfig.DIRECT_FORMAT_AUTO
        };
        int checked = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(selectedDirectApiFormat)) checked = i;
        }
        new LocalizedAlertDialogBuilder(this)
                .setTitle("选择接口协议")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    selectDirectApiFormat(values[which]);
                    applyDefaultBaseUrl(values[which]);
                    dialog.dismiss();
                    directFormatField.post(
                            () -> refreshModels(false, false));
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private static String directFormatLabel(String format) {
        if (AppConfig.DIRECT_FORMAT_RESPONSES.equals(format)) {
            return "OpenAI Responses";
        }
        if (AppConfig.DIRECT_FORMAT_CLAUDE.equals(format)) {
            return "Claude Messages";
        }
        if (AppConfig.DIRECT_FORMAT_GEMINI.equals(format)) {
            return "Gemini GenerateContent";
        }
        if (AppConfig.DIRECT_FORMAT_AZURE.equals(format)) return "Azure OpenAI";
        if (AppConfig.DIRECT_FORMAT_OLLAMA.equals(format)) return "Ollama 原生接口";
        if (AppConfig.DIRECT_FORMAT_AUTO.equals(format)) return "自动识别";
        return "GPT 兼容接口（默认）";
    }

    private void applyDefaultBaseUrl(String format) {
        if (baseUrl == null) return;
        String current = baseUrl.getText().toString().trim();
        boolean replaceable = current.isEmpty()
                || "https://api.openai.com/v1".equals(current)
                || "https://api.anthropic.com".equals(current)
                || "https://generativelanguage.googleapis.com/v1beta"
                .equals(current);
        if (!replaceable) return;
        if (AppConfig.DIRECT_FORMAT_CLAUDE.equals(format)) {
            baseUrl.setText("https://api.anthropic.com");
        } else if (AppConfig.DIRECT_FORMAT_GEMINI.equals(format)) {
            baseUrl.setText(
                    "https://generativelanguage.googleapis.com/v1beta");
        } else if (!AppConfig.DIRECT_FORMAT_OLLAMA.equals(format)) {
            baseUrl.setText("https://api.openai.com/v1");
        }
        if (directModel == null) return;
        String currentModel = directModel.getText().toString().trim();
        if (AppConfig.DIRECT_FORMAT_CLAUDE.equals(format)
                && (currentModel.isEmpty()
                || currentModel.startsWith("gpt-")
                || currentModel.startsWith("gemini-"))) {
            directModel.setText("claude-sonnet-5");
        } else if (AppConfig.DIRECT_FORMAT_GEMINI.equals(format)
                && (currentModel.isEmpty()
                || currentModel.startsWith("gpt-")
                || currentModel.startsWith("claude-"))) {
            directModel.setText("gemini-3.5-flash");
        } else if (!AppConfig.DIRECT_FORMAT_CLAUDE.equals(format)
                && !AppConfig.DIRECT_FORMAT_GEMINI.equals(format)
                && !AppConfig.DIRECT_FORMAT_OLLAMA.equals(format)
                && (currentModel.startsWith("claude-")
                || currentModel.startsWith("gemini-"))) {
            directModel.setText("gpt-5.4-mini");
        }
    }

    private void selectGeneration(String generation) {
        if (AppConfig.GENERATION_ACCOUNT.equals(generation)
                || "gpt_account".equals(generation)) {
            selectedGeneration = AppConfig.GENERATION_ACCOUNT;
        } else if (AppConfig.GENERATION_LOCAL.equals(generation)) {
            selectedGeneration = AppConfig.GENERATION_LOCAL;
        } else {
            selectedGeneration = AppConfig.GENERATION_DIRECT_API;
        }
        String[] values = {
                AppConfig.GENERATION_DIRECT_API,
                AppConfig.GENERATION_ACCOUNT,
                AppConfig.GENERATION_LOCAL
        };
        for (int i = 0; i < generationButtons.length; i++) {
            boolean selected = values[i].equals(selectedGeneration);
            generationButtons[i].setTextColor(MiuixUi.color(
                    this,
                    selected ? MiuixUi.TEXT_PRIMARY : MiuixUi.TEXT_SECONDARY));
            generationButtons[i].setBackground(selected
                    ? MiuixUi.shape(Color.WHITE, 12, this)
                    : MiuixUi.shape(Color.TRANSPARENT, 12, this));
            generationButtons[i].setElevation(selected ? MiuixUi.dp(this, 1) : 0);
        }
        if (AppConfig.GENERATION_LOCAL.equals(selectedGeneration)) {
            LocalModelManager.ModelSpec model =
                    LocalModelManager.find(selectedLocalModel);
            generationHelp.setText(localModels.isInstalled(model.id)
                    ? "聊天由 " + model.displayName + " 在本机生成；启用联网搜索时仅发送本轮查询。"
                    : "请先下载 " + model.displayName + "，再点按“使用”。");
        } else if (AppConfig.GENERATION_ACCOUNT.equals(selectedGeneration)) {
            String provider = AppConfig.ACCOUNT_COPILOT.equals(
                    selectedAccountProvider) ? "GitHub Copilot" : "GPT";
            generationHelp.setText(hasSelectedAccount()
                    ? "聊天由已配置的 " + provider + " 账户生成。"
                    : "请先配置 " + provider + " 账户；"
                    + "未配置时发送会提示前往设置。");
        } else {
            generationHelp.setText("聊天直接发送到上方 API 地址，使用所选模型。");
        }
        if (!localModelViews.isEmpty()) refreshLocalModelRows();
    }

    private AppConfig readForm() {
        syncCurrentAccountModel();
        AppConfig previous = store.getConfig();
        AppConfig config = new AppConfig();
        config.mode = AppConfig.MODE_DIRECT_API;
        config.generation = selectedGeneration;
        config.baseUrl = baseUrl.getText().toString().trim();
        config.directApiFormat = selectedDirectApiFormat;
        config.model = directModel.getText().toString().trim();
        config.accountProvider = selectedAccountProvider;
        config.gptModel = stagedGptModel;
        config.copilotModel = stagedCopilotModel;
        config.copilotEndpoint = copilotEndpoint == null
                ? previous.copilotEndpoint
                : copilotEndpoint.getText().toString().trim();
        config.githubOAuthClientId = githubOAuthClientId == null
                ? previous.githubOAuthClientId
                : githubOAuthClientId.getText().toString().trim();
        config.localModel = selectedLocalModel;
        String personaValue = persona.getText().toString().trim();
        config.persona = "你".equals(previous.persona)
                && L10n.tr(this, "你").equals(personaValue)
                ? "你" : personaValue;
        config.personaAvatarPath = previous.personaAvatarPath;
        config.realtimeProvider = selectedRealtimeProvider;
        config.realtimeModel = RealtimeProvider.normalizeModel(
                selectedRealtimeProvider,
                realtimeModel.getText().toString().trim());
        config.realtimeEndpoint = realtimeEndpoint.getText().toString().trim();
        config.realtimeVoice = realtimeVoice.getText().toString().trim();
        config.realtimeExtra = realtimeExtra.getText().toString().trim();
        config.reduceTransparency = previous.reduceTransparency;
        config.showReasoning = reasoningSwitch != null
                && reasoningSwitch.isChecked();
        config.webSearch = webSearchSwitch != null
                && webSearchSwitch.isChecked();
        config.groupAutonomousMessages = groupAutonomousSwitch == null
                || groupAutonomousSwitch.isChecked();
        if (config.baseUrl.isEmpty()) config.baseUrl = "https://api.openai.com/v1";
        if (config.persona.isEmpty()) config.persona = "你";
        if (config.gptModel.isEmpty()) config.gptModel = "gpt-5.4";
        if (config.copilotModel.isEmpty()) {
            config.copilotModel = "gpt-5.4";
        }
        return config;
    }

    private void save() {
        AppConfig config = readForm();
        if (RealtimeProvider.ELEVENLABS.equals(config.realtimeProvider)
                && config.realtimeEndpoint.startsWith("wss://")
                && config.realtimeEndpoint.contains("token=")) {
            secureStore.setRealtimeCredential(
                    selectedRealtimeProvider, config.realtimeEndpoint);
            realtimeCredential.setText(config.realtimeEndpoint);
            config.realtimeEndpoint = "";
            realtimeEndpoint.setText("");
        }
        store.saveConfig(config);
        secureStore.setDirectApiKey(apiKey.getText().toString());
        secureStore.setRealtimeCredential(
                selectedRealtimeProvider,
                realtimeCredential.getText().toString());
        saveButton.performHapticFeedback(
                android.view.HapticFeedbackConstants.VIRTUAL_KEY);
        LocalizedToast.makeText(this, "设置已保存在本机", Toast.LENGTH_SHORT).show();
    }

    private void loadCachedModels(boolean gpt) {
        List<String> models = store.getModelCache(modelScope(gpt));
        if (models.isEmpty() && gpt
                && AppConfig.ACCOUNT_GPT.equals(selectedAccountProvider)) {
            models = ApiClient.codexFallbackModels();
        }
        if (models.isEmpty()) return;
        applyModelList(gpt, models);
        TextView status = gpt ? gptModelStatus : directModelStatus;
        status.setText((gpt ? "兼容列表" : "上次列表")
                + " · " + models.size() + " 个模型");
        status.setTextColor(MiuixUi.color(this, MiuixUi.TEXT_SECONDARY));
    }

    private void refreshModels(boolean gpt, boolean userInitiated) {
        TextView status = gpt ? gptModelStatus : directModelStatus;
        TextView refresh = gpt ? gptModelsButton : directModelsButton;
        if (gpt ? gptModelsLoading : directModelsLoading) return;
        if (gpt && !hasSelectedAccount()) {
            String provider = AppConfig.ACCOUNT_COPILOT.equals(
                    selectedAccountProvider) ? "GitHub Copilot" : "GPT";
            status.setText("配置 " + provider + " 账户后自动获取");
            status.setTextColor(MiuixUi.color(
                    this, Color.rgb(180, 110, 20)));
            if (userInitiated) {
                LocalizedToast.makeText(
                        this,
                        "请先配置 " + provider + " 账户",
                        Toast.LENGTH_SHORT).show();
            }
            return;
        }
        if (gpt
                && AppConfig.ACCOUNT_COPILOT.equals(selectedAccountProvider)
                && copilotEndpoint.getText().toString().trim().isEmpty()) {
            status.setText("填写 Copilot SDK 网关后自动获取");
            status.setTextColor(MiuixUi.color(
                    this, Color.rgb(180, 110, 20)));
            if (userInitiated) {
                copilotEndpoint.setError("请先填写 Copilot SDK 网关地址");
            }
            return;
        }
        if (!gpt && baseUrl.getText().toString().trim().isEmpty()) {
            status.setText("填写 API 地址后自动获取");
            status.setTextColor(MiuixUi.color(
                    this, Color.rgb(180, 110, 20)));
            if (userInitiated) {
                LocalizedToast.makeText(this, "请先填写 API 地址", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        if (!gpt
                && baseUrl.getText().toString().contains("api.openai.com")
                && apiKey.getText().toString().trim().isEmpty()) {
            status.setText("填写 API Key 后自动获取");
            status.setTextColor(MiuixUi.color(
                    this, Color.rgb(180, 110, 20)));
            if (userInitiated) {
                LocalizedToast.makeText(this, "请先填写 API Key", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        status.setText("正在获取模型…");
        status.setTextColor(MiuixUi.color(this, MiuixUi.TEXT_SECONDARY));
        refresh.setEnabled(false);
        if (gpt) gptModelsLoading = true;
        else directModelsLoading = true;

        ApiClient.ModelsCallback callback = new ApiClient.ModelsCallback() {
            @Override
            public void onSuccess(List<String> models) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (gpt) gptModelsLoading = false;
                    else directModelsLoading = false;
                    refresh.setEnabled(true);
                    applyModelList(gpt, models);
                    store.saveModelCache(modelScope(gpt), models);
                    status.setText("已获取 " + models.size() + " 个模型 · 可输入筛选");
                    status.setTextColor(MiuixUi.color(
                            SettingsActivity.this, MiuixUi.GREEN));
                    if (userInitiated) {
                        AutoCompleteTextView field = gpt ? gptModel : directModel;
                        field.requestFocus();
                        field.performClick();
                        refresh.performHapticFeedback(
                                android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (gpt) gptModelsLoading = false;
                    else directModelsLoading = false;
                    refresh.setEnabled(true);
                    List<String> cached = store.getModelCache(modelScope(gpt));
                    if (cached.isEmpty() && gpt
                            && AppConfig.ACCOUNT_GPT.equals(
                            selectedAccountProvider)) {
                        cached = ApiClient.codexFallbackModels();
                    }
                    if (!cached.isEmpty()) {
                        applyModelList(gpt, cached);
                        status.setText("获取失败 · 保留 " + cached.size() + " 个缓存模型");
                        status.setTextColor(MiuixUi.color(
                                SettingsActivity.this,
                                Color.rgb(180, 110, 20)));
                    } else {
                        status.setText("获取失败 · " + message);
                        status.setTextColor(MiuixUi.color(
                                SettingsActivity.this,
                                Color.rgb(198, 52, 58)));
                    }
                });
            }
        };
        if (gpt) {
            ApiClient.fetchAccountModels(readForm(), secureStore, callback);
        } else {
            ApiClient.fetchDirectModels(
                    readForm(), apiKey.getText().toString(), callback);
        }
    }

    private void applyModelList(boolean gpt, List<String> models) {
        AutoCompleteTextView field = gpt ? gptModel : directModel;
        String current = field.getText().toString().trim();
        List<String> display = new ArrayList<>(models);
        if (!current.isEmpty() && !display.contains(current)) display.add(0, current);
        field.setAdapter(new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, display));
    }

    private String modelScope(boolean gpt) {
        if (gpt) return "account|" + selectedAccountProvider;
        return "direct|" + selectedDirectApiFormat + "|"
                + baseUrl.getText().toString().trim();
    }

    private void testConnection() {
        testButton.setEnabled(false);
        testButton.setText("正在测试…");
        ApiClient.testDirectApi(
                readForm(), apiKey.getText().toString(), new ApiClient.ResultCallback() {
                    @Override
                    public void onSuccess(String message) {
                        runOnUiThread(() -> {
                            testButton.setEnabled(true);
                            testButton.setText("测试 API");
                            testButton.performHapticFeedback(
                                    android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                            LocalizedToast.makeText(SettingsActivity.this,
                                    message, Toast.LENGTH_SHORT).show();
                            refreshModels(false, false);
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> {
                            testButton.setEnabled(true);
                            testButton.setText("测试 API");
                            LocalizedToast.makeText(SettingsActivity.this,
                                    "连接失败：" + message, Toast.LENGTH_LONG).show();
                        });
                    }
                });
    }

    private boolean hasSelectedAccount() {
        return AppConfig.ACCOUNT_COPILOT.equals(selectedAccountProvider)
                ? secureStore.hasCopilotAccount()
                : secureStore.hasGptAccount();
    }

    private void handleAccountAction() {
        if (AppConfig.ACCOUNT_COPILOT.equals(selectedAccountProvider)) {
            toggleCopilotLogin();
            return;
        }
        toggleGptLogin();
    }

    private void toggleGptLogin() {
        if (gptLoginCall != null) {
            cancelActiveLogin(true);
            return;
        }
        accountButton.setEnabled(false);
        accountButton.setText("正在获取验证码…");
        logoutButton.setEnabled(false);
        accountStatus.setText("正在连接 OpenAI…");
        accountStatus.setTextColor(MiuixUi.color(
                this, MiuixUi.TEXT_SECONDARY));
        gptLoginCall = CodexAuthClient.startDeviceLogin(
                secureStore, new CodexAuthClient.LoginCallback() {
                    @Override
                    public void onCode(String userCode, String url) {
                        runOnUiThread(() -> {
                            verificationCode = userCode;
                            verificationUrl = url;
                            deviceCodeLabel.setText("在 OpenAI 页面输入此验证码");
                            deviceOpenButton.setText("复制并打开 OpenAI");
                            deviceCodeText.setText(userCode);
                            deviceCodePanel.setVisibility(View.VISIBLE);
                            accountStatus.setText("等待你在 OpenAI 页面确认");
                            accountButton.setEnabled(true);
                            accountButton.setText("取消登录");
                        });
                    }

                    @Override
                    public void onSuccess(String summary) {
                        runOnUiThread(() -> {
                            gptLoginCall = null;
                            verificationCode = "";
                            verificationUrl = "";
                            deviceCodePanel.setVisibility(View.GONE);
                            refreshAccountState();
                            refreshModels(true, false);
                            accountButton.performHapticFeedback(
                                    android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                            LocalizedToast.makeText(SettingsActivity.this,
                                    summary, Toast.LENGTH_SHORT).show();
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> {
                            gptLoginCall = null;
                            verificationCode = "";
                            verificationUrl = "";
                            deviceCodePanel.setVisibility(View.GONE);
                            refreshAccountState();
                            accountStatus.setText("登录失败 · " + message);
                            accountStatus.setTextColor(MiuixUi.color(
                                    SettingsActivity.this,
                                    Color.rgb(198, 52, 58)));
                            LocalizedToast.makeText(SettingsActivity.this,
                                    "登录失败：" + message, Toast.LENGTH_LONG).show();
                        });
                    }
                });
    }

    private void toggleCopilotLogin() {
        if (copilotLoginCall != null) {
            cancelActiveLogin(true);
            return;
        }
        String clientId = githubOAuthClientId.getText().toString().trim();
        if (clientId.isEmpty()) {
            githubOAuthClientId.setError("请先填写 GitHub OAuth Client ID");
            return;
        }
        AppConfig saved = readForm();
        saved.accountProvider = AppConfig.ACCOUNT_COPILOT;
        store.saveConfig(saved);
        accountButton.setEnabled(false);
        accountButton.setText("正在获取验证码…");
        logoutButton.setEnabled(false);
        accountStatus.setText("正在连接 GitHub…");
        accountStatus.setTextColor(MiuixUi.color(
                this, MiuixUi.TEXT_SECONDARY));
        copilotLoginCall = GitHubCopilotAuthClient.startDeviceLogin(
                clientId,
                secureStore,
                new GitHubCopilotAuthClient.LoginCallback() {
                    @Override
                    public void onCode(String userCode, String url) {
                        runOnUiThread(() -> {
                            verificationCode = userCode;
                            verificationUrl = url;
                            deviceCodeLabel.setText("在 GitHub 页面输入此验证码");
                            deviceOpenButton.setText("复制并打开 GitHub");
                            deviceCodeText.setText(userCode);
                            deviceCodePanel.setVisibility(View.VISIBLE);
                            accountStatus.setText("等待你在 GitHub 页面确认");
                            accountButton.setEnabled(true);
                            accountButton.setText("取消登录");
                        });
                    }

                    @Override
                    public void onSuccess(String summary) {
                        runOnUiThread(() -> {
                            copilotLoginCall = null;
                            verificationCode = "";
                            verificationUrl = "";
                            deviceCodePanel.setVisibility(View.GONE);
                            refreshAccountState();
                            if (!copilotEndpoint.getText().toString().trim().isEmpty()) {
                                refreshModels(true, false);
                            }
                            accountButton.performHapticFeedback(
                                    android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                            LocalizedToast.makeText(
                                    SettingsActivity.this,
                                    summary,
                                    Toast.LENGTH_SHORT).show();
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> {
                            copilotLoginCall = null;
                            verificationCode = "";
                            verificationUrl = "";
                            deviceCodePanel.setVisibility(View.GONE);
                            refreshAccountState();
                            accountStatus.setText("登录失败 · " + message);
                            accountStatus.setTextColor(MiuixUi.color(
                                    SettingsActivity.this,
                                    Color.rgb(198, 52, 58)));
                            LocalizedToast.makeText(
                                    SettingsActivity.this,
                                    "登录失败：" + message,
                                    Toast.LENGTH_LONG).show();
                        });
                    }
                });
    }

    private void cancelActiveLogin(boolean showToast) {
        boolean cancelled = false;
        if (gptLoginCall != null) {
            gptLoginCall.cancel();
            gptLoginCall = null;
            cancelled = true;
        }
        if (copilotLoginCall != null) {
            copilotLoginCall.cancel();
            copilotLoginCall = null;
            cancelled = true;
        }
        verificationCode = "";
        verificationUrl = "";
        if (deviceCodePanel != null) deviceCodePanel.setVisibility(View.GONE);
        if (showToast && cancelled) {
            refreshAccountState();
            LocalizedToast.makeText(
                    this, "已取消登录", Toast.LENGTH_SHORT).show();
        }
    }

    private void launchAuthorization() {
        if (verificationCode.isEmpty() || verificationUrl.isEmpty()) return;
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(
                    ClipData.newPlainText(
                            AppConfig.ACCOUNT_COPILOT.equals(selectedAccountProvider)
                                    ? "GitHub 设备验证码" : "OpenAI 设备验证码",
                            verificationCode));
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(verificationUrl)));
            LocalizedToast.makeText(this, "验证码已复制", Toast.LENGTH_SHORT).show();
        } catch (Exception error) {
            LocalizedToast.makeText(this,
                    "无法打开浏览器，请访问 " + verificationUrl,
                    Toast.LENGTH_LONG).show();
        }
    }

    private void confirmLogout() {
        boolean copilot =
                AppConfig.ACCOUNT_COPILOT.equals(selectedAccountProvider);
        new LocalizedAlertDialogBuilder(this)
                .setTitle(copilot ? "退出 GitHub Copilot？" : "退出 GPT 账户？")
                .setMessage(copilot
                        ? "只会删除本机加密保存的 GitHub OAuth 令牌，"
                        + "不会影响 GitHub 或 Copilot 订阅。"
                        : "只会删除本机保存的 OAuth 令牌，不会影响 OpenAI 账户。")
                .setNegativeButton("取消", null)
                .setPositiveButton("退出", (dialog, which) -> {
                    if (copilot) {
                        secureStore.clearCopilotAccount();
                    } else {
                        secureStore.clearGptAccount();
                    }
                    if (AppConfig.GENERATION_ACCOUNT.equals(selectedGeneration)) {
                        selectGeneration(AppConfig.GENERATION_DIRECT_API);
                    }
                    AppConfig saved = store.getConfig();
                    saved.generation = AppConfig.GENERATION_DIRECT_API;
                    store.saveConfig(saved);
                    refreshAccountState();
                    LocalizedToast.makeText(
                            this,
                            copilot
                                    ? "已删除本机 GitHub Copilot 登录"
                                    : "已删除本机 GPT 登录",
                            Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void refreshAccountState() {
        boolean copilot =
                AppConfig.ACCOUNT_COPILOT.equals(selectedAccountProvider);
        boolean loggedIn = copilot
                ? secureStore.hasCopilotAccount() : secureStore.hasGptAccount();
        accountStatus.setText(copilot
                ? GitHubCopilotAuthClient.accountSummary(secureStore)
                : CodexAuthClient.accountSummary(secureStore));
        accountStatus.setTextColor(MiuixUi.color(
                this,
                loggedIn ? MiuixUi.GREEN : MiuixUi.TEXT_SECONDARY));
        accountBody.setText(copilot
                ? "使用 GitHub 官方 OAuth 设备授权和用户自己的 Copilot 订阅。"
                + "安卓端通过你部署的官方 Copilot SDK 网关生成聊天；"
                + "OAuth App 必须启用 Device Flow。"
                : "兼容 Hermes 的 Codex 设备授权。应用不会要求或读取 "
                + "ChatGPT 密码；登录只在 OpenAI 官方页面完成。");
        accountButton.setEnabled(true);
        accountButton.setText(copilot
                ? (loggedIn ? "重新登录 GitHub" : "登录 GitHub Copilot")
                : (loggedIn ? "重新登录" : "登录 GPT 账户"));
        logoutButton.setEnabled(true);
        logoutButton.setVisibility(loggedIn ? View.VISIBLE : View.GONE);
        if (generationHelp != null && selectedGeneration != null) {
            selectGeneration(selectedGeneration);
        }
    }

    private void addWithMargins(
            LinearLayout parent,
            View child,
            int left,
            int top,
            int right,
            int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(MiuixUi.dp(this, left), MiuixUi.dp(this, top),
                MiuixUi.dp(this, right), MiuixUi.dp(this, bottom));
        parent.addView(child, params);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (localModels != null) {
            localModels.addListener(localModelListener);
            refreshLocalModelRows();
        }
    }

    @Override
    protected void onStop() {
        if (localModels != null) localModels.removeListener(localModelListener);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        cancelActiveLogin(false);
        super.onDestroy();
    }

    private static final class LocalModelViews {
        final TextView status;
        final ProgressBar progress;
        final TextView action;
        final TextView delete;

        LocalModelViews(
                TextView status,
                ProgressBar progress,
                TextView action,
                TextView delete) {
            this.status = status;
            this.progress = progress;
            this.action = action;
            this.delete = delete;
        }
    }
}
