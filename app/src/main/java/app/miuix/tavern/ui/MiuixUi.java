package app.miuix.tavern.ui;

import android.annotation.SuppressLint;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityManager;
import android.widget.EditText;
import android.widget.AutoCompleteTextView;
import android.widget.Filterable;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatTextView;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import app.miuix.tavern.R;

import java.util.WeakHashMap;

public final class MiuixUi {
    public static final int BACKGROUND = Color.rgb(244, 244, 246);
    public static final int SURFACE = Color.WHITE;
    public static final int TEXT_PRIMARY = Color.rgb(24, 24, 26);
    public static final int TEXT_SECONDARY = Color.rgb(118, 118, 124);
    public static final int GREEN = Color.rgb(7, 193, 96);
    public static final int BUBBLE_GREEN = Color.rgb(149, 236, 105);
    public static final int HAIRLINE = Color.rgb(229, 229, 232);
    public static final int DANGER = Color.rgb(250, 81, 81);
    private static final int DARK_BACKGROUND = Color.rgb(16, 17, 20);
    private static final int DARK_SURFACE = Color.rgb(28, 29, 33);
    private static final int DARK_SURFACE_ALT = Color.rgb(38, 39, 44);
    private static final int DARK_TEXT_PRIMARY = Color.rgb(242, 242, 245);
    private static final int DARK_TEXT_SECONDARY = Color.rgb(168, 168, 177);
    private static final int DARK_HAIRLINE = Color.rgb(51, 52, 58);
    private static final int DARK_GREEN = Color.rgb(24, 199, 111);
    private static final int DARK_BUBBLE_GREEN = Color.rgb(40, 93, 55);
    private static final int DARK_DANGER = Color.rgb(255, 107, 107);

    private static final WeakHashMap<View, PressState> PRESSES = new WeakHashMap<>();

    private MiuixUi() {
    }

    public static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static boolean isDark(Context context) {
        int night = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return night == Configuration.UI_MODE_NIGHT_YES;
    }

    public static int color(Context context, int lightColor) {
        if (!isDark(context) || Color.alpha(lightColor) == 0) return lightColor;
        if (lightColor == BACKGROUND) return DARK_BACKGROUND;
        if (lightColor == SURFACE || lightColor == Color.WHITE) return DARK_SURFACE;
        if (lightColor == TEXT_PRIMARY) return DARK_TEXT_PRIMARY;
        if (lightColor == TEXT_SECONDARY) return DARK_TEXT_SECONDARY;
        if (lightColor == HAIRLINE) return DARK_HAIRLINE;
        if (lightColor == GREEN) return DARK_GREEN;
        if (lightColor == BUBBLE_GREEN) return DARK_BUBBLE_GREEN;
        if (lightColor == DANGER) return DARK_DANGER;
        if (lightColor == Color.rgb(180, 110, 20)) {
            return Color.rgb(255, 183, 77);
        }
        if (lightColor == Color.rgb(198, 52, 58)) return DARK_DANGER;
        if (lightColor == Color.rgb(233, 249, 240)) {
            return Color.rgb(23, 58, 40);
        }
        if (lightColor == Color.rgb(180, 239, 151)) {
            return Color.rgb(49, 95, 59);
        }
        int red = Color.red(lightColor);
        int green = Color.green(lightColor);
        int blue = Color.blue(lightColor);
        int spread = Math.max(red, Math.max(green, blue))
                - Math.min(red, Math.min(green, blue));
        if (spread <= 8 && red >= 250) return Color.rgb(21, 22, 26);
        if (spread <= 8 && red >= 242) return DARK_SURFACE;
        if (spread <= 8 && red >= 232) return DARK_SURFACE_ALT;
        return lightColor;
    }

    public static int background(Context context) {
        return isDark(context) ? DARK_BACKGROUND : BACKGROUND;
    }

    public static int surface(Context context) {
        return isDark(context) ? DARK_SURFACE : SURFACE;
    }

    public static int chatBackground(Context context) {
        return isDark(context)
                ? Color.rgb(20, 21, 24) : Color.rgb(237, 237, 237);
    }

    public static void applySystemBars(
            Activity activity, int statusBarLight, int navigationBarLight) {
        activity.getWindow().setStatusBarColor(color(activity, statusBarLight));
        activity.getWindow().setNavigationBarColor(
                color(activity, navigationBarLight));
        int flags = 0;
        if (!isDark(activity)) {
            flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        activity.getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    public static void startConversationActivity(
            Activity activity, Intent intent) {
        activity.startActivity(intent);
        if (shouldReduceMotion(activity)) {
            activity.overridePendingTransition(
                    R.anim.screen_fade_in, R.anim.screen_fade_out);
        } else {
            activity.overridePendingTransition(
                    R.anim.conversation_enter, R.anim.host_recede);
        }
    }

    public static void applyConversationReturnTransition(Activity activity) {
        if (shouldReduceMotion(activity)) {
            activity.overridePendingTransition(
                    R.anim.screen_fade_in, R.anim.screen_fade_out);
        } else {
            activity.overridePendingTransition(
                    R.anim.host_return, R.anim.conversation_exit);
        }
    }

    public static void revealDockPage(View page, int direction) {
        page.animate().cancel();
        if (shouldReduceMotion(page.getContext())) {
            page.setTranslationX(0f);
            page.setAlpha(0f);
            page.animate()
                    .alpha(1f)
                    .setDuration(120)
                    .start();
            return;
        }
        page.setAlpha(0.82f);
        page.setTranslationX(dp(page.getContext(), 18) * direction);
        SpringAnimation translation =
                new SpringAnimation(page, SpringAnimation.TRANSLATION_X, 0f);
        SpringForce force = new SpringForce(0f);
        force.setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY);
        force.setStiffness(760f);
        translation.setSpring(force);
        translation.start();
        page.animate()
                .alpha(1f)
                .setDuration(150)
                .start();
    }

    public static GradientDrawable shape(int color, float radiusDp, Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color(context, color));
        drawable.setCornerRadius(dp(context, radiusDp));
        return drawable;
    }

    public static GradientDrawable shape(int color, float[] radiiDp, Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color(context, color));
        float density = context.getResources().getDisplayMetrics().density;
        float[] radii = new float[radiiDp.length];
        for (int i = 0; i < radiiDp.length; i++) radii[i] = radiiDp[i] * density;
        drawable.setCornerRadii(radii);
        return drawable;
    }

    public static GradientDrawable outlinedShape(int color, int stroke, float radiusDp, Context context) {
        GradientDrawable drawable = shape(color, radiusDp, context);
        drawable.setStroke(dp(context, 1), color(context, stroke));
        return drawable;
    }

    public static View ripple(View view, int baseColor, float radiusDp) {
        Context context = view.getContext();
        GradientDrawable content = shape(baseColor, radiusDp, context);
        view.setBackground(new RippleDrawable(
                new ColorStateList(
                        new int[][]{new int[]{}},
                        new int[]{isDark(context) ? 0x24FFFFFF : 0x16000000}),
                content,
                null));
        return view;
    }

    public static TextView text(Context context, String value, float sizeSp, int color, boolean medium) {
        TextView text = new LocalizedTextView(context);
        text.setText(value);
        return styleText(text, context, sizeSp, color, medium);
    }

    public static TextView rawText(Context context, String value, float sizeSp, int color, boolean medium) {
        LocalizedTextView text = new LocalizedTextView(context);
        text.setAutoLocalize(false);
        text.setText(value);
        return styleText(text, context, sizeSp, color, medium);
    }

    private static TextView styleText(
            TextView text, Context context, float sizeSp, int color, boolean medium) {
        text.setTextSize(sizeSp);
        text.setTextColor(color == Color.WHITE
                ? Color.WHITE : color(context, color));
        text.setGravity(Gravity.CENTER_VERTICAL);
        text.setIncludeFontPadding(false);
        if (medium) text.setTypeface(Typeface.create("sans", Typeface.BOLD));
        return text;
    }

    public static TextView pillButton(Context context, String label, boolean primary) {
        TextView button = text(context, label, 15, primary ? Color.WHITE : TEXT_PRIMARY, true);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(context, 44));
        button.setPadding(dp(context, 18), 0, dp(context, 18), 0);
        ripple(button, primary ? GREEN : Color.rgb(235, 235, 238), 13);
        pressable(button, 0.97f);
        return button;
    }

    public static EditText field(Context context, String hint, boolean secret) {
        EditText edit = new EditText(context);
        styleField(edit, context, hint);
        if (secret) {
            edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }
        return edit;
    }

    public static AutoCompleteTextView modelField(Context context, String hint) {
        AutoCompleteTextView field = new AutoCompleteTextView(context);
        styleField(field, context, hint);
        field.setThreshold(0);
        field.setDropDownBackgroundDrawable(shape(Color.WHITE, 13, context));
        field.setDropDownVerticalOffset(dp(context, 4));
        field.setOnClickListener(v -> showAllModels(field));
        field.setOnFocusChangeListener((v, focused) -> {
            if (focused) showAllModels(field);
        });
        return field;
    }

    private static void showAllModels(AutoCompleteTextView field) {
        if (field.getAdapter() instanceof Filterable) {
            ((Filterable) field.getAdapter()).getFilter().filter(
                    "", count -> field.showDropDown());
        } else {
            field.showDropDown();
        }
    }

    private static void styleField(EditText edit, Context context, String hint) {
        edit.setTextSize(16);
        edit.setTextColor(color(context, TEXT_PRIMARY));
        edit.setHintTextColor(isDark(context)
                ? Color.rgb(126, 126, 136) : Color.rgb(170, 170, 176));
        edit.setHint(L10n.tr(context, hint));
        edit.setSingleLine(true);
        edit.setPadding(dp(context, 14), 0, dp(context, 14), 0);
        edit.setMinHeight(dp(context, 48));
        edit.setBackground(outlinedShape(Color.rgb(248, 248, 250), HAIRLINE, 13, context));
    }

    public static LinearLayout vertical(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    public static LinearLayout horizontal(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    public static LinearLayout card(Context context) {
        LinearLayout card = vertical(context);
        card.setPadding(dp(context, 18), dp(context, 16), dp(context, 18), dp(context, 16));
        card.setBackground(shape(SURFACE, 18, context));
        card.setElevation(dp(context, 0.6f));
        return card;
    }

    public static View divider(Context context, int startInset) {
        View divider = new View(context);
        divider.setBackgroundColor(color(context, HAIRLINE));
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(context, 0.5f)));
        params.leftMargin = dp(context, startInset);
        divider.setLayoutParams(params);
        return divider;
    }

    @SuppressLint("ClickableViewAccessibility")
    public static void pressable(View view, float downScale) {
        PressState state = new PressState(view);
        PRESSES.put(view, state);
        view.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View target, MotionEvent event) {
                if (!target.isEnabled()) return false;
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    state.to(downScale);
                } else if (event.getActionMasked() == MotionEvent.ACTION_UP
                        || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    state.to(1f);
                }
                return false;
            }
        });
    }

    public static boolean shouldReduceMotion(Context context) {
        AccessibilityManager manager =
                (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        boolean touchExploration = manager != null && manager.isTouchExplorationEnabled();
        return touchExploration || !ValueAnimator.areAnimatorsEnabled();
    }

    public static void setMargins(View view, int left, int top, int right, int bottom) {
        ViewGroup.LayoutParams current = view.getLayoutParams();
        if (!(current instanceof ViewGroup.MarginLayoutParams)) return;
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) current;
        params.setMargins(dp(view.getContext(), left), dp(view.getContext(), top),
                dp(view.getContext(), right), dp(view.getContext(), bottom));
        view.setLayoutParams(params);
    }

    private static final class PressState {
        private final View view;
        private final SpringAnimation x;
        private final SpringAnimation y;
        private final boolean reduced;

        PressState(View view) {
            this.view = view;
            reduced = shouldReduceMotion(view.getContext());
            x = spring(view, SpringAnimation.SCALE_X);
            y = spring(view, SpringAnimation.SCALE_Y);
        }

        private static SpringAnimation spring(View view,
                                              androidx.dynamicanimation.animation.FloatPropertyCompat<View> property) {
            SpringAnimation animation = new SpringAnimation(view, property);
            SpringForce force = new SpringForce(1f);
            force.setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY);
            force.setStiffness(900f);
            animation.setSpring(force);
            return animation;
        }

        void to(float value) {
            if (reduced) {
                view.setScaleX(value);
                view.setScaleY(value);
                return;
            }
            x.animateToFinalPosition(value);
            y.animateToFinalPosition(value);
        }
    }

    public static class LocalizedTextView extends AppCompatTextView {
        private boolean autoLocalize = true;

        public LocalizedTextView(Context context) {
            super(context);
        }

        public LocalizedTextView(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        public void setAutoLocalize(boolean autoLocalize) {
            this.autoLocalize = autoLocalize;
        }

        @Override
        public void setText(CharSequence text, BufferType type) {
            super.setText(autoLocalize ? L10n.tr(getContext(), text) : text, type);
        }
    }

}
