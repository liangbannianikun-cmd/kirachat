package app.miuix.tavern.ui;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.util.List;

public final class WechatMessageMenu {
    public static final class Item {
        final String label;
        final Runnable action;

        public Item(String label, Runnable action) {
            this.label = label;
            this.action = action;
        }
    }

    private WechatMessageMenu() {
    }

    public static void show(Activity activity, View anchor, List<Item> items) {
        if (items == null || items.isEmpty()) return;
        final int panelColor = Color.rgb(67, 67, 69);
        LinearLayout root = MiuixUi.vertical(activity);
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        LinearLayout menu = MiuixUi.horizontal(activity);
        menu.setGravity(Gravity.CENTER_VERTICAL);
        menu.setPadding(MiuixUi.dp(activity, 4), 0,
                MiuixUi.dp(activity, 4), 0);
        menu.setBackground(MiuixUi.shape(panelColor, 10, activity));
        root.addView(menu, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, MiuixUi.dp(activity, 48)));

        PopupWindow popup = new PopupWindow(
                root,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true);
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                View divider = new View(activity);
                divider.setBackgroundColor(Color.rgb(92, 92, 95));
                menu.addView(divider, new LinearLayout.LayoutParams(
                        MiuixUi.dp(activity, 1), MiuixUi.dp(activity, 22)));
            }
            Item item = items.get(i);
            TextView action = MiuixUi.text(
                    activity, item.label, 14, Color.WHITE, false);
            action.setGravity(Gravity.CENTER);
            action.setMinWidth(MiuixUi.dp(activity, 58));
            action.setPadding(MiuixUi.dp(activity, 12), 0,
                    MiuixUi.dp(activity, 12), 0);
            action.setOnClickListener(view -> {
                popup.dismiss();
                item.action.run();
            });
            MiuixUi.pressable(action, 0.92f);
            menu.addView(action, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        }

        TriangleView arrow = new TriangleView(activity, panelColor);
        root.addView(arrow, new LinearLayout.LayoutParams(
                MiuixUi.dp(activity, 18), MiuixUi.dp(activity, 8)));

        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setClippingEnabled(true);
        popup.setElevation(MiuixUi.dp(activity, 8));

        root.measure(
                View.MeasureSpec.makeMeasureSpec(
                        activity.getResources().getDisplayMetrics().widthPixels
                                - MiuixUi.dp(activity, 16),
                        View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int[] location = new int[2];
        anchor.getLocationOnScreen(location);
        int popupWidth = root.getMeasuredWidth();
        int popupHeight = root.getMeasuredHeight();
        int screenWidth =
                activity.getResources().getDisplayMetrics().widthPixels;
        int x = location[0] + anchor.getWidth() / 2 - popupWidth / 2;
        x = Math.max(
                MiuixUi.dp(activity, 8),
                Math.min(x, screenWidth - popupWidth - MiuixUi.dp(activity, 8)));
        int y = location[1] - popupHeight - MiuixUi.dp(activity, 5);
        if (y < MiuixUi.dp(activity, 8)) {
            y = location[1] + anchor.getHeight() + MiuixUi.dp(activity, 5);
            arrow.setVisibility(View.GONE);
        }
        popup.showAtLocation(
                activity.getWindow().getDecorView(),
                Gravity.TOP | Gravity.START,
                x,
                y);
    }

    private static final class TriangleView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        TriangleView(Activity activity, int color) {
            super(activity);
            paint.setColor(color);
            paint.setStyle(Paint.Style.FILL);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            path.reset();
            path.moveTo(0, 0);
            path.lineTo(getWidth(), 0);
            path.lineTo(getWidth() / 2f, getHeight());
            path.close();
            canvas.drawPath(path, paint);
        }
    }
}
