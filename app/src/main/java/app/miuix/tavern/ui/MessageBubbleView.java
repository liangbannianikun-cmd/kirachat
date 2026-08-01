package app.miuix.tavern.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import app.miuix.tavern.model.ChatMessage;
import app.miuix.tavern.util.MediaAttachmentStore;

import java.util.Locale;

@SuppressLint("ViewConstructor")
public final class MessageBubbleView extends LinearLayout {
    private final ImageView image;
    private final TextView location;
    private final LinearLayout voiceCall;
    private final LineIconView voiceCallIcon;
    private final TextView voiceCallText;
    private final TextView text;

    public MessageBubbleView(Context context, boolean user, int maxWidth) {
        super(context);
        setOrientation(VERTICAL);
        setGravity(user ? Gravity.END : Gravity.START);
        setMinimumWidth(MiuixUi.dp(context, 56));
        setPadding(MiuixUi.dp(context, 7), MiuixUi.dp(context, 7),
                MiuixUi.dp(context, 7), MiuixUi.dp(context, 7));
        float[] radii = user
                ? new float[]{16, 16, 5, 5, 16, 16, 16, 16}
                : new float[]{5, 5, 16, 16, 16, 16, 16, 16};
        setBackground(MiuixUi.shape(
                user ? MiuixUi.BUBBLE_GREEN : Color.WHITE, radii, context));

        image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackground(MiuixUi.shape(Color.rgb(235, 235, 237), 10, context));
        addView(image, new LinearLayout.LayoutParams(
                Math.min(maxWidth, MiuixUi.dp(context, 220)),
                MiuixUi.dp(context, 168)));

        location = MiuixUi.text(context, "", 14, MiuixUi.TEXT_PRIMARY, true);
        location.setGravity(Gravity.CENTER_VERTICAL);
        location.setPadding(MiuixUi.dp(context, 9), MiuixUi.dp(context, 9),
                MiuixUi.dp(context, 9), MiuixUi.dp(context, 9));
        location.setBackground(MiuixUi.shape(
                user ? Color.rgb(180, 239, 151) : Color.rgb(244, 244, 246),
                10, context));
        addView(location, new LinearLayout.LayoutParams(
                Math.min(maxWidth, MiuixUi.dp(context, 220)),
                ViewGroup.LayoutParams.WRAP_CONTENT));

        voiceCall = MiuixUi.horizontal(context);
        voiceCall.setGravity(Gravity.CENTER_VERTICAL);
        voiceCall.setMinimumWidth(MiuixUi.dp(context, 132));
        voiceCall.setPadding(MiuixUi.dp(context, 5), MiuixUi.dp(context, 4),
                MiuixUi.dp(context, 7), MiuixUi.dp(context, 4));
        voiceCallIcon = new LineIconView(context);
        voiceCallIcon.setType(LineIconView.PHONE);
        voiceCallIcon.setTintColor(MiuixUi.color(context, MiuixUi.TEXT_PRIMARY));
        voiceCallText = MiuixUi.text(
                context, "", 16, MiuixUi.TEXT_PRIMARY, false);
        L10n.setRaw(voiceCallText);
        LinearLayout.LayoutParams callTextParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        LinearLayout.LayoutParams callIconParams = new LinearLayout.LayoutParams(
                MiuixUi.dp(context, 24), MiuixUi.dp(context, 24));
        if (user) {
            callTextParams.rightMargin = MiuixUi.dp(context, 7);
            voiceCall.addView(voiceCallText, callTextParams);
            voiceCall.addView(voiceCallIcon, callIconParams);
        } else {
            callTextParams.leftMargin = MiuixUi.dp(context, 7);
            voiceCall.addView(voiceCallIcon, callIconParams);
            voiceCall.addView(voiceCallText, callTextParams);
        }
        addView(voiceCall, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        text = MiuixUi.text(context, "", 16, MiuixUi.TEXT_PRIMARY, false);
        L10n.setRaw(text);
        text.setGravity(Gravity.CENTER_VERTICAL);
        text.setLineSpacing(MiuixUi.dp(context, 2), 1.04f);
        text.setPadding(MiuixUi.dp(context, 6), MiuixUi.dp(context, 3),
                MiuixUi.dp(context, 6), MiuixUi.dp(context, 3));
        text.setMaxWidth(maxWidth);
        text.setMovementMethod(LinkMovementMethod.getInstance());
        addView(text, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        MiuixUi.pressable(this, 0.985f);
    }

    public void bind(ChatMessage message) {
        image.setVisibility(View.GONE);
        image.setImageDrawable(null);
        location.setVisibility(View.GONE);
        voiceCall.setVisibility(View.GONE);
        text.setVisibility(View.VISIBLE);

        if (message.hasImage()) {
            Bitmap bitmap = MediaAttachmentStore.decodePreview(message.attachmentPath);
            if (bitmap != null) {
                image.setImageBitmap(bitmap);
                image.setVisibility(View.VISIBLE);
            }
        } else if (message.hasLocation()) {
            location.setText(String.format(
                    Locale.US,
                    "当前位置\n%.5f, %.5f",
                    message.latitude,
                    message.longitude));
            location.setVisibility(View.VISIBLE);
        } else if (message.hasVoiceCall()) {
            voiceCallText.setText(L10n.tr(getContext(), "通话时长")
                    + " " + ChatMessage.formatCallDuration(message.callDurationSeconds));
            voiceCall.setVisibility(View.VISIBLE);
        }

        String content = message.content == null ? "" : message.content.trim();
        boolean defaultAttachmentText = (message.hasImage()
                && ("图片".equals(content) || "[图片]".equals(content)
                || "请看这张图片".equals(content)))
                || (message.hasLocation() && "我分享了当前位置".equals(content));
        if (message.failed) {
            content = L10n.tr(getContext(), content);
            content += (content.isEmpty() ? "" : "\n\n")
                    + L10n.tr(getContext(), "发送失败，长按可重试");
        }
        text.setText(content);
        Linkify.addLinks(text, Linkify.WEB_URLS);
        text.setTextColor(MiuixUi.color(
                getContext(),
                message.failed ? MiuixUi.DANGER : MiuixUi.TEXT_PRIMARY));
        text.setVisibility((content.isEmpty() || defaultAttachmentText
                || message.hasVoiceCall())
                ? View.GONE : View.VISIBLE);
    }

    public void setBubbleLongClickListener(OnLongClickListener listener) {
        setOnLongClickListener(listener);
        image.setOnLongClickListener(listener);
        location.setOnLongClickListener(listener);
        voiceCall.setOnLongClickListener(listener);
        voiceCallIcon.setOnLongClickListener(listener);
        voiceCallText.setOnLongClickListener(listener);
        text.setOnLongClickListener(listener);
        setLongClickable(true);
        image.setLongClickable(true);
        location.setLongClickable(true);
        voiceCall.setLongClickable(true);
        voiceCallIcon.setLongClickable(true);
        voiceCallText.setLongClickable(true);
        text.setLongClickable(true);
    }
}
