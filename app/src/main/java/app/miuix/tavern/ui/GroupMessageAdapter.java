package app.miuix.tavern.ui;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import app.miuix.tavern.model.CharacterCard;
import app.miuix.tavern.model.ChatMessage;

import java.util.List;

public final class GroupMessageAdapter
        extends RecyclerView.Adapter<GroupMessageAdapter.Holder> {
    public interface Listener {
        void onMessageLongPress(ChatMessage message, int position, View anchor);
        void onAvatarClick(CharacterCard character);
    }

    private final Context context;
    private final List<CharacterCard> members;
    private final CharacterCard persona;
    private final Listener listener;
    private final int maxBubbleWidth;
    private List<ChatMessage> messages;

    public GroupMessageAdapter(
            Context context,
            List<CharacterCard> members,
            String personaName,
            String personaAvatarPath,
            List<ChatMessage> messages,
            Listener listener) {
        this.context = context;
        this.members = members;
        this.messages = messages;
        this.listener = listener;
        persona = new CharacterCard();
        persona.id = "local-persona";
        persona.name = TextUtils.isEmpty(personaName)
                ? L10n.tr(context, "我") : personaName.trim();
        persona.avatarPath = TextUtils.isEmpty(personaAvatarPath)
                ? "" : personaAvatarPath.trim();
        maxBubbleWidth = (int) (context.getResources().getDisplayMetrics().widthPixels * 0.72f);
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
        root.addView(line, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AvatarView avatar = new AvatarView(context);
        LinearLayout content = MiuixUi.vertical(context);
        TextView speaker = MiuixUi.text(
                context, "", 12, MiuixUi.TEXT_SECONDARY, false);
        L10n.setRaw(speaker);
        MessageBubbleView bubble =
                new MessageBubbleView(context, user, maxBubbleWidth);

        if (user) {
            LinearLayout.LayoutParams bubbleParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            line.addView(bubble, bubbleParams);
            avatar.setCharacter(persona);
            LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(
                    MiuixUi.dp(context, 40), MiuixUi.dp(context, 40));
            avatarParams.leftMargin = MiuixUi.dp(context, 9);
            line.addView(avatar, avatarParams);
            speaker.setVisibility(View.GONE);
        } else {
            line.addView(avatar, new LinearLayout.LayoutParams(
                    MiuixUi.dp(context, 40), MiuixUi.dp(context, 40)));
            content.addView(speaker, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, MiuixUi.dp(context, 20)));
            content.addView(bubble);
            LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            contentParams.leftMargin = MiuixUi.dp(context, 9);
            line.addView(content, contentParams);
        }
        return new Holder(root, avatar, speaker, bubble);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        ChatMessage message = messages.get(position);
        boolean user = ChatMessage.USER.equals(message.role);
        if (!user) {
            CharacterCard card = findMember(message.speaker);
            holder.avatar.setCharacter(card);
            holder.avatar.setOnClickListener(card == null
                    ? null : v -> listener.onAvatarClick(card));
            if (card != null) {
                holder.avatar.setContentDescription(
                        L10n.tr(context, "查看" + card.name + "的资料"));
                MiuixUi.pressable(holder.avatar, 0.94f);
            }
            holder.speaker.setText(
                    TextUtils.isEmpty(message.speaker)
                            ? (card == null ? "群成员" : card.name)
                            : message.speaker);
        } else {
            holder.avatar.setOnClickListener(null);
        }
        holder.bubble.bind(message);
        holder.bubble.setBubbleLongClickListener(v -> {
            listener.onMessageLongPress(message, holder.getAdapterPosition(), holder.bubble);
            return true;
        });
    }

    private CharacterCard findMember(String name) {
        for (CharacterCard card : members) {
            if (card.name.equals(name)) return card;
        }
        return members.isEmpty() ? null : members.get(0);
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final AvatarView avatar;
        final TextView speaker;
        final MessageBubbleView bubble;

        Holder(View itemView, AvatarView avatar, TextView speaker,
               MessageBubbleView bubble) {
            super(itemView);
            this.avatar = avatar;
            this.speaker = speaker;
            this.bubble = bubble;
        }
    }
}
