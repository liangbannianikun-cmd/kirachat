package app.miuix.tavern.ui;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import app.miuix.tavern.model.CharacterCard;
import app.miuix.tavern.model.ChatMessage;

import java.util.List;

public final class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.Holder> {
    public interface Listener {
        void onMessageLongPress(ChatMessage message, int position, View anchor);
        void onAvatarClick(CharacterCard character);
    }

    private final Context context;
    private final CharacterCard character;
    private final CharacterCard persona;
    private final Listener listener;
    private final int maxBubbleWidth;
    private List<ChatMessage> messages;

    public MessageAdapter(Context context, CharacterCard character, String personaName,
                          String personaAvatarPath, List<ChatMessage> messages,
                          Listener listener) {
        this.context = context;
        this.character = character;
        persona = new CharacterCard();
        persona.id = "local-persona";
        persona.name = TextUtils.isEmpty(personaName)
                ? L10n.tr(context, "我") : personaName.trim();
        persona.avatarPath = TextUtils.isEmpty(personaAvatarPath)
                ? "" : personaAvatarPath.trim();
        this.messages = messages;
        this.listener = listener;
        maxBubbleWidth = (int) (context.getResources().getDisplayMetrics().widthPixels * 0.72f);
    }

    public void replace(List<ChatMessage> updated) {
        messages = updated;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return ChatMessage.USER.equals(messages.get(position).role) ? 1 : 0;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        boolean user = viewType == 1;
        FrameLayout root = new FrameLayout(context);
        root.setPadding(MiuixUi.dp(context, 12), MiuixUi.dp(context, 5),
                MiuixUi.dp(context, 12), MiuixUi.dp(context, 5));
        root.setLayoutParams(new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout line = MiuixUi.horizontal(context);
        line.setGravity((user ? Gravity.END : Gravity.START) | Gravity.TOP);
        FrameLayout.LayoutParams lineParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP);
        root.addView(line, lineParams);

        AvatarView avatar = null;
        if (!user) {
            avatar = new AvatarView(context);
            avatar.setCharacter(character);
            line.addView(avatar, new LinearLayout.LayoutParams(
                    MiuixUi.dp(context, 40), MiuixUi.dp(context, 40)));
        }

        MessageBubbleView bubble = new MessageBubbleView(context, user, maxBubbleWidth);
        LinearLayout.LayoutParams bubbleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (!user) bubbleParams.leftMargin = MiuixUi.dp(context, 9);
        line.addView(bubble, bubbleParams);
        if (user) {
            avatar = new AvatarView(context);
            avatar.setCharacter(persona);
            LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(
                    MiuixUi.dp(context, 40), MiuixUi.dp(context, 40));
            avatarParams.leftMargin = MiuixUi.dp(context, 9);
            line.addView(avatar, avatarParams);
        }
        return new Holder(root, avatar, bubble);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        ChatMessage message = messages.get(position);
        holder.bubble.bind(message);
        if (ChatMessage.ASSISTANT.equals(message.role)) {
            holder.avatar.setOnClickListener(v -> listener.onAvatarClick(character));
            holder.avatar.setContentDescription(
                    L10n.tr(context, "查看" + character.name + "的资料"));
            MiuixUi.pressable(holder.avatar, 0.94f);
        } else {
            holder.avatar.setOnClickListener(null);
        }
        holder.bubble.setBubbleLongClickListener(v -> {
            listener.onMessageLongPress(message, holder.getAdapterPosition(), holder.bubble);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final AvatarView avatar;
        final MessageBubbleView bubble;

        Holder(View itemView, AvatarView avatar, MessageBubbleView bubble) {
            super(itemView);
            this.avatar = avatar;
            this.bubble = bubble;
        }
    }
}
