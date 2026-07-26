package app.miuix.tavern.ui;

import android.content.Context;
import android.widget.Toast;

public final class LocalizedToast {
    private LocalizedToast() {
    }

    public static Toast makeText(
            Context context, CharSequence text, int duration) {
        return Toast.makeText(
                context, L10n.tr(context, text), duration);
    }

    public static Toast makeText(
            Context context, int textResource, int duration) {
        return Toast.makeText(context, textResource, duration);
    }
}
