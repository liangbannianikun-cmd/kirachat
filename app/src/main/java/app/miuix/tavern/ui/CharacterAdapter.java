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

import java.util.List;

public final class CharacterAdapter extends RecyclerView.Adapter<CharacterAdapter.Holder> {
    public interface Listener {
        void onOpen(CharacterCard card);
    }

    private final Context context;
    private final LocalStore store;
    private final Listener listener;
    private List<CharacterCard> cards;

    public CharacterAdapter(Context context, List<CharacterCard> cards, Listener listener) {
        this.context = context;
        this.store = new LocalStore(context);
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
        FrameLayout frame = new FrameLayout(context);
        frame.setPadding(MiuixUi.dp(context, 5), MiuixUi.dp(context, 5),
                MiuixUi.dp(context, 5), MiuixUi.dp(context, 5));

        LinearLayout card = MiuixUi.vertical(context);
        card.setMinimumHeight(MiuixUi.dp(context, 174));
        card.setPadding(MiuixUi.dp(context, 14), MiuixUi.dp(context, 13),
                MiuixUi.dp(context, 14), MiuixUi.dp(context, 13));
        MiuixUi.ripple(card, Color.WHITE, 18);
        MiuixUi.pressable(card, 0.98f);
        frame.addView(card, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout top = MiuixUi.horizontal(context);
        top.setGravity(Gravity.TOP);
        card.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, MiuixUi.dp(context, 58)));

        AvatarView avatar = new AvatarView(context);
        top.addView(avatar, new LinearLayout.LayoutParams(
                MiuixUi.dp(context, 58), MiuixUi.dp(context, 58)));

        TextView name = MiuixUi.text(context, "", 18, MiuixUi.TEXT_PRIMARY, true);
        L10n.setRaw(name);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, MiuixUi.dp(context, 28));
        nameParams.topMargin = MiuixUi.dp(context, 9);
        card.addView(name, nameParams);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);

        TextView description = MiuixUi.text(
                context, "", 13, MiuixUi.TEXT_SECONDARY, false);
        L10n.setRaw(description);
        description.setMaxLines(2);
        description.setEllipsize(TextUtils.TruncateAt.END);
        description.setLineSpacing(0, 1.08f);
        card.addView(description, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, MiuixUi.dp(context, 39)));

        TextView meta = MiuixUi.text(context, "", 11, MiuixUi.GREEN, true);
        meta.setGravity(Gravity.CENTER);
        meta.setPadding(MiuixUi.dp(context, 9), 0, MiuixUi.dp(context, 9), 0);
        meta.setBackground(MiuixUi.shape(Color.rgb(233, 249, 240), 12, context));
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, MiuixUi.dp(context, 25));
        metaParams.weight = 0;
        metaParams.leftMargin = MiuixUi.dp(context, 8);
        top.addView(new View(context), new LinearLayout.LayoutParams(
                0, 1, 1));
        top.addView(meta, metaParams);
        return new Holder(frame, card, avatar, name, description, meta);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        CharacterCard card = cards.get(position);
        holder.avatar.setCharacter(card);
        holder.name.setText(card.name);
        String description = card.description == null || card.description.trim().isEmpty()
                ? L10n.tr(context, "还没有角色描述")
                : card.replaceMacros(card.description, store.getConfig().persona);
        holder.description.setText(description.replace('\n', ' '));
        int lore = card.loreEntryCount();
        holder.meta.setText(card.isBuiltIn()
                ? "应用内置"
                : (lore > 0 ? lore + " 条设定" : "角色卡"));
        holder.card.setOnClickListener(v -> listener.onOpen(card));
    }

    @Override
    public int getItemCount() {
        return cards.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final View card;
        final AvatarView avatar;
        final TextView name;
        final TextView description;
        final TextView meta;

        Holder(View itemView, View card, AvatarView avatar, TextView name,
               TextView description, TextView meta) {
            super(itemView);
            this.card = card;
            this.avatar = avatar;
            this.name = name;
            this.description = description;
            this.meta = meta;
        }
    }
}
