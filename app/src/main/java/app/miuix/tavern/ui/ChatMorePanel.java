package app.miuix.tavern.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

@SuppressLint("ViewConstructor")
public final class ChatMorePanel extends LinearLayout {
    public interface Listener {
        void onAlbum();

        void onCamera();

        void onVoiceCall();

        void onLocation();
    }

    public ChatMorePanel(Context context, Listener listener) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.TOP);
        setPadding(MiuixUi.dp(context, 10), MiuixUi.dp(context, 18),
                MiuixUi.dp(context, 10), MiuixUi.dp(context, 14));
        setBackgroundColor(MiuixUi.color(
                context, Color.rgb(245, 245, 247)));
        addItem("相册", LineIconView.IMAGE, listener::onAlbum);
        addItem("拍摄", LineIconView.CAMERA, listener::onCamera);
        addItem("语音通话", LineIconView.PHONE, listener::onVoiceCall);
        addItem("位置", LineIconView.LOCATION, listener::onLocation);
    }

    private void addItem(String label, int iconType, Runnable action) {
        Context context = getContext();
        LinearLayout item = MiuixUi.vertical(context);
        item.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        item.setContentDescription(L10n.tr(getContext(), label));
        MiuixUi.pressable(item, 0.94f);

        FrameLayout iconHolder = new FrameLayout(context);
        iconHolder.setBackground(MiuixUi.shape(Color.WHITE, 14, context));
        LineIconView icon = new LineIconView(context);
        icon.setType(iconType);
        icon.setTintColor(Color.rgb(62, 62, 66));
        iconHolder.addView(icon, new FrameLayout.LayoutParams(
                MiuixUi.dp(context, 29), MiuixUi.dp(context, 29), Gravity.CENTER));
        item.addView(iconHolder, new LinearLayout.LayoutParams(
                MiuixUi.dp(context, 58), MiuixUi.dp(context, 58)));

        TextView text = MiuixUi.text(
                context, label, 12, MiuixUi.TEXT_SECONDARY, false);
        text.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, MiuixUi.dp(context, 30));
        textParams.topMargin = MiuixUi.dp(context, 5);
        item.addView(text, textParams);
        item.setOnClickListener(v -> action.run());
        addView(item, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
    }
}
