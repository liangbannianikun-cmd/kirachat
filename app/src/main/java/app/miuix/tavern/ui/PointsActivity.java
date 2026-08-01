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
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import app.miuix.tavern.data.SecureStore;
import app.miuix.tavern.network.PointsClient;

import java.text.NumberFormat;
import java.util.Locale;

public final class PointsActivity extends AppCompatActivity {
    private PointsClient client;
    private TextView balance;
    private TextView status;
    private TextView claimButton;
    private TextView address;
    private TextView network;
    private TextView explorerButton;
    private PointsClient.PointsAccount account;
    private boolean loading;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MiuixUi.applySystemBars(
                this, MiuixUi.BACKGROUND, MiuixUi.BACKGROUND);
        client = new PointsClient(new SecureStore(this));
        setContentView(buildContent());
        refresh();
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
                this, "链上积分", 31, MiuixUi.TEXT_PRIMARY, true);
        title.setLetterSpacing(-0.025f);
        addWithMargins(page, title, 18, 12, 18, 5);
        TextView intro = MiuixUi.text(
                this,
                "签到积分由澄语服务验证并写入区块链。积分不可转账或交易，"
                        + "用于应用内权益，链上记录可以公开核验。",
                14, MiuixUi.TEXT_SECONDARY, false);
        intro.setLineSpacing(MiuixUi.dp(this, 3), 1.05f);
        addWithMargins(page, intro, 18, 0, 18, 16);

        LinearLayout balanceCard = MiuixUi.card(this);
        balanceCard.addView(MiuixUi.text(
                this, "可用积分", 14, MiuixUi.TEXT_SECONDARY, true));
        balance = MiuixUi.rawText(
                this, "—", 38, MiuixUi.TEXT_PRIMARY, true);
        LinearLayout.LayoutParams balanceParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        balanceParams.topMargin = MiuixUi.dp(this, 5);
        balanceCard.addView(balance, balanceParams);
        status = MiuixUi.text(
                this, "正在连接积分服务…", 13, MiuixUi.TEXT_SECONDARY, false);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = MiuixUi.dp(this, 6);
        balanceCard.addView(status, statusParams);
        addWithMargins(page, balanceCard, 14, 0, 14, 12);

        LinearLayout taskCard = MiuixUi.card(this);
        taskCard.addView(MiuixUi.text(
                this, "每日签到", 18, MiuixUi.TEXT_PRIMARY, true));
        TextView taskBody = MiuixUi.text(
                this,
                "每天可领取一次。服务端使用唯一流水号提交交易，"
                        + "网络重试不会重复发放。",
                13, MiuixUi.TEXT_SECONDARY, false);
        taskBody.setLineSpacing(MiuixUi.dp(this, 2), 1.04f);
        LinearLayout.LayoutParams taskBodyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        taskBodyParams.topMargin = MiuixUi.dp(this, 6);
        taskCard.addView(taskBody, taskBodyParams);
        claimButton = MiuixUi.pillButton(this, "领取积分", true);
        claimButton.setOnClickListener(v -> claim());
        LinearLayout.LayoutParams claimParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, MiuixUi.dp(this, 48));
        claimParams.topMargin = MiuixUi.dp(this, 14);
        taskCard.addView(claimButton, claimParams);
        addWithMargins(page, taskCard, 14, 0, 14, 12);

        LinearLayout chainCard = MiuixUi.card(this);
        chainCard.addView(MiuixUi.text(
                this, "链上账户", 18, MiuixUi.TEXT_PRIMARY, true));
        network = MiuixUi.text(
                this, "网络 · —", 13, MiuixUi.TEXT_SECONDARY, false);
        addToCard(chainCard, network, 7);
        address = MiuixUi.text(
                this, "地址 · —", 13, MiuixUi.TEXT_SECONDARY, false);
        address.setSingleLine(false);
        address.setOnClickListener(v -> copyAddress());
        addToCard(chainCard, address, 4);
        explorerButton = MiuixUi.pillButton(this, "在区块浏览器中查看", false);
        explorerButton.setOnClickListener(v -> openExplorer());
        LinearLayout.LayoutParams explorerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, MiuixUi.dp(this, 44));
        explorerParams.topMargin = MiuixUi.dp(this, 13);
        chainCard.addView(explorerButton, explorerParams);
        addWithMargins(page, chainCard, 14, 0, 14, 12);

        LinearLayout privacyCard = MiuixUi.card(this);
        privacyCard.addView(MiuixUi.text(
                this, "隐私说明", 17, MiuixUi.TEXT_PRIMARY, true));
        TextView privacy = MiuixUi.text(
                this,
                "链上只保存匿名地址、积分余额和流水标识，不上传聊天内容、"
                        + "角色卡、API Key 或 GPT / GitHub Copilot 登录信息。"
                        + "区块链记录公开且通常无法删除。当前匿名账户保存在本机，"
                        + "卸载应用前尚未提供恢复入口。",
                13, MiuixUi.TEXT_SECONDARY, false);
        privacy.setLineSpacing(MiuixUi.dp(this, 2), 1.04f);
        addToCard(privacyCard, privacy, 7);
        addWithMargins(page, privacyCard, 14, 0, 14, 0);
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

    private void refresh() {
        if (loading) return;
        if (!client.isConfigured()) {
            showConfigurationMissing();
            return;
        }
        setLoading(true, "正在读取链上余额…");
        client.load(callback(false));
    }

    private void claim() {
        if (loading || account == null || account.checkedInToday) return;
        setLoading(true, "正在提交链上交易，请稍候…");
        client.claimDaily(callback(true));
    }

    private PointsClient.AccountCallback callback(boolean claiming) {
        return new PointsClient.AccountCallback() {
            @Override
            public void onSuccess(PointsClient.PointsAccount value) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    account = value;
                    renderAccount();
                    setLoading(false, value.checkedInToday
                            ? "今日积分已领取 · 链上余额已同步"
                            : "链上余额已同步");
                    if (claiming && value.awarded > 0) {
                        claimButton.performHapticFeedback(
                                android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                        LocalizedToast.makeText(
                                PointsActivity.this,
                                "已领取 " + value.awarded + " 积分",
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    setLoading(false, message);
                    LocalizedToast.makeText(
                            PointsActivity.this, message, Toast.LENGTH_LONG).show();
                });
            }
        };
    }

    private void renderAccount() {
        if (account == null) return;
        balance.setText(NumberFormat.getIntegerInstance(
                Locale.getDefault()).format(account.balance));
        network.setText("网络 · " + account.networkName
                + (account.chainId.isEmpty() ? "" : "（" + account.chainId + "）"));
        address.setText("地址 · " + account.address + "\n点按复制");
        claimButton.setText(account.checkedInToday
                ? "今日已领取"
                : "领取 +" + account.dailyReward + " 积分");
        claimButton.setEnabled(!account.checkedInToday && !loading);
        claimButton.setAlpha(claimButton.isEnabled() ? 1f : 0.5f);
        explorerButton.setEnabled(
                !account.accountUrl.isEmpty() || !account.latestTxUrl.isEmpty());
        explorerButton.setAlpha(explorerButton.isEnabled() ? 1f : 0.5f);
    }

    private void setLoading(boolean value, String text) {
        loading = value;
        status.setText(text);
        status.setTextColor(MiuixUi.color(
                this, value ? MiuixUi.TEXT_SECONDARY : MiuixUi.GREEN));
        if (account != null) renderAccount();
        else {
            claimButton.setEnabled(false);
            claimButton.setAlpha(0.5f);
        }
    }

    private void showConfigurationMissing() {
        balance.setText("—");
        status.setText("这个 APK 尚未配置积分服务地址");
        status.setTextColor(Color.rgb(180, 110, 20));
        claimButton.setText("积分服务未配置");
        claimButton.setEnabled(false);
        claimButton.setAlpha(0.5f);
        network.setText("网络 · 待配置");
        address.setText("地址 · 创建积分账户后显示");
        explorerButton.setEnabled(false);
        explorerButton.setAlpha(0.5f);
    }

    private void copyAddress() {
        if (account == null || account.address.isEmpty()) return;
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(
                    ClipData.newPlainText("澄语链上积分地址", account.address));
            LocalizedToast.makeText(
                    this, "积分地址已复制", Toast.LENGTH_SHORT).show();
        }
    }

    private void openExplorer() {
        if (account == null) return;
        String url = account.latestTxUrl.isEmpty()
                ? account.accountUrl : account.latestTxUrl;
        if (url.isEmpty()) return;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception error) {
            LocalizedToast.makeText(
                    this, "无法打开区块浏览器", Toast.LENGTH_SHORT).show();
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
