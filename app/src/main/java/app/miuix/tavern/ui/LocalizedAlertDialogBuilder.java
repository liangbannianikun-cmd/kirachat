package app.miuix.tavern.ui;

import android.content.Context;
import android.content.DialogInterface;

import androidx.appcompat.app.AlertDialog;

public final class LocalizedAlertDialogBuilder
        extends AlertDialog.Builder {
    private final Context context;

    public LocalizedAlertDialogBuilder(Context context) {
        super(context);
        this.context = context;
    }

    @Override
    public LocalizedAlertDialogBuilder setTitle(CharSequence title) {
        super.setTitle(L10n.tr(context, title));
        return this;
    }

    @Override
    public LocalizedAlertDialogBuilder setMessage(CharSequence message) {
        super.setMessage(L10n.tr(context, message));
        return this;
    }

    @Override
    public LocalizedAlertDialogBuilder setPositiveButton(
            CharSequence text, DialogInterface.OnClickListener listener) {
        super.setPositiveButton(L10n.tr(context, text), listener);
        return this;
    }

    @Override
    public LocalizedAlertDialogBuilder setNegativeButton(
            CharSequence text, DialogInterface.OnClickListener listener) {
        super.setNegativeButton(L10n.tr(context, text), listener);
        return this;
    }

    @Override
    public LocalizedAlertDialogBuilder setNeutralButton(
            CharSequence text, DialogInterface.OnClickListener listener) {
        super.setNeutralButton(L10n.tr(context, text), listener);
        return this;
    }

    @Override
    public LocalizedAlertDialogBuilder setItems(
            CharSequence[] items, DialogInterface.OnClickListener listener) {
        super.setItems(L10n.tr(context, items), listener);
        return this;
    }

    @Override
    public LocalizedAlertDialogBuilder setSingleChoiceItems(
            CharSequence[] items,
            int checkedItem,
            DialogInterface.OnClickListener listener) {
        super.setSingleChoiceItems(
                L10n.tr(context, items), checkedItem, listener);
        return this;
    }
}
