package app.miuix.tavern.ui;

import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import app.miuix.tavern.data.LocalStore;
import app.miuix.tavern.model.CharacterCard;
import app.miuix.tavern.model.ChatMessage;
import app.miuix.tavern.model.GroupChat;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class ConversationAdapter extends RecyclerView.Adapter<ConversationAdapter.Holder> {
    public interface Listener {
        void onOpen(CharacterCard card);
    }

    private final Context context;
    private final LocalStore store;
    private final Listener listener;
    private List<CharacterCard> cards;

    public ConversationAdapter(Context context, LocalStore store, List<CharacterCard> cards, Listener listener) {
        this.context = context;
        this.store = store;
        this.cards = cards;
        this.listener = listener;
    }

    public void replace(List<CharacterCard> updated) {
        cards = updated;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LinearLayout row = MiuixUi.horizontal(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(MiuixUi.dp(context, 16), MiuixUi.dp(context, 11),
                MiuixUi.dp(context, 14), MiuixUi.dp(context, 11));
        row.setBackgroundColor(MiuixUi.surface(context));
        MiuixUi.pressable(row, 0.985f);

        AvatarView avatar = new AvatarView(context);
        row.addView(avatar, new LinearLayout.LayoutParams(MiuixUi.dp(context, 52), MiuixUi.dp(context, 52)));

        LinearLayout center = MiuixUi.vertical(context);
        LinearLayout.LayoutParams centerParams =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        centerParams.leftMargin = MiuixUi.dp(context, 12);
        row.addView(center, centerParams);

        TextView name = MiuixUi.text(context, "", 17, MiuixUi.TEXT_PRIMARY, true);
        L10n.setRaw(name);
        center.addView(name, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, MiuixUi.dp(context, 25)));
        TextView preview = MiuixUi.text(context, "", 14, MiuixUi.TEXT_SECONDARY, false);
        L10n.setRaw(preview);
        preview.setSingleLine(true);
        preview.setEllipsize(TextUtils.TruncateAt.END);
        center.addView(preview, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, MiuixUi.dp(context, 23)));

        LinearLayout trailing = MiuixUi.vertical(context);
        trailing.setGravity(Gravity.END);
        row.addView(trailing, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        TextView time = MiuixUi.text(context, "", 11, Color.rgb(174, 174, 180), false);
        time.setGravity(Gravity.END);
        trailing.addView(time, new LinearLayout.LayoutParams(MiuixUi.dp(context, 58), MiuixUi.dp(context, 23)));
        TextView unread = MiuixUi.text(context, "", 11, Color.WHITE, true);
        unread.setGravity(Gravity.CENTER);
        unread.setMinWidth(MiuixUi.dp(context, 20));
        unread.setBackground(MiuixUi.shape(MiuixUi.DANGER, 12, context));
        trailing.addView(unread, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, MiuixUi.dp(context, 20)));

        return new Holder(row, avatar, name, preview, time, unread);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        CharacterCard card = cards.get(position);
        if (card.id.startsWith(GroupChat.CONVERSATION_PREFIX)) {
            String groupId = card.id.substring(GroupChat.CONVERSATION_PREFIX.length());
            GroupChat group = store.getGroup(groupId);
            java.util.ArrayList<CharacterCard> members = new java.util.ArrayList<>();
            if (group != null) {
                for (String memberId : group.members) {
                    CharacterCard member = store.getCharacter(memberId);
                    if (member == null) {
                        member = store.getCharacterBySourceAvatar(memberId);
                    }
                    if (member != null) members.add(member);
                }
            }
            holder.avatar.setCharacters(members);
        } else {
            holder.avatar.setCharacter(card);
        }
        holder.name.setText(card.name);
        List<ChatMessage> messages = store.getMessages(card.id);
        String preview = messages.isEmpty()
                ? card.replaceMacros(card.firstMessage, store.getConfig().persona)
                : messages.get(messages.size() - 1).content;
        if (preview == null || preview.trim().isEmpty()) {
            preview = L10n.tr(context, "开始一段新对话");
        } else if ("[图片]".equals(preview) || "[位置]".equals(preview)) {
            preview = L10n.tr(context, preview);
        }
        String prefix = card.pinned ? L10n.tr(context, "[置顶] ") : "";
        if (card.muted) prefix += L10n.tr(context, "[免打扰] ");
        holder.preview.setText(prefix + preview.replace('\n', ' '));
        holder.time.setText(readableTime(card.lastUsed));
        holder.unread.setVisibility(
                card.unread > 0 && !card.muted ? View.VISIBLE : View.INVISIBLE);
        holder.unread.setText(card.unread > 99 ? "99+" : String.valueOf(card.unread));
        holder.itemView.setOnClickListener(v -> listener.onOpen(card));
    }

    @Override
    public int getItemCount() {
        return cards.size();
    }

    private String readableTime(long timestamp) {
        Calendar now = Calendar.getInstance();
        Calendar then = Calendar.getInstance();
        then.setTimeInMillis(timestamp);
        if (now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
                && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)) {
            return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(timestamp));
        }
        now.add(Calendar.DAY_OF_YEAR, -1);
        if (now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
                && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)) {
            return L10n.tr(context, "昨天");
        }
        String pattern = "en".equals(Locale.getDefault().getLanguage())
                ? "MMM d" : "M月d日";
        return new SimpleDateFormat(pattern, Locale.getDefault())
                .format(new Date(timestamp));
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final AvatarView avatar;
        final TextView name;
        final TextView preview;
        final TextView time;
        final TextView unread;

        Holder(View itemView, AvatarView avatar, TextView name, TextView preview,
               TextView time, TextView unread) {
            super(itemView);
            this.avatar = avatar;
            this.name = name;
            this.preview = preview;
            this.time = time;
            this.unread = unread;
        }
    }
}
