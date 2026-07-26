package app.miuix.tavern.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

public final class LineIconView extends View {
    public static final int CHAT = 0;
    public static final int CHARACTER = 1;
    public static final int BOOK = 2;
    public static final int PERSON = 3;
    public static final int SEARCH = 4;
    public static final int PLUS = 5;
    public static final int BACK = 6;
    public static final int MORE = 7;
    public static final int PHONE = 8;
    public static final int MIC = 9;
    public static final int SPEAKER = 10;
    public static final int HANGUP = 11;
    public static final int IMAGE = 12;
    public static final int CAMERA = 13;
    public static final int LOCATION = 14;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private int type = CHAT;
    private boolean selected;
    private Integer tintColor;

    public LineIconView(Context context) {
        super(context);
        init();
    }

    public LineIconView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    public void setType(int type) {
        this.type = type;
        invalidate();
    }

    public void setSelectedState(boolean selected) {
        this.selected = selected;
        invalidate();
    }

    public void setTintColor(int color) {
        tintColor = color;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float s = Math.min(w, h);
        float cx = w / 2f;
        float cy = h / 2f;
        paint.setColor(tintColor != null
                ? (tintColor == android.graphics.Color.WHITE
                ? android.graphics.Color.WHITE
                : MiuixUi.color(getContext(), tintColor))
                : MiuixUi.color(
                        getContext(),
                        selected ? MiuixUi.GREEN : MiuixUi.TEXT_SECONDARY));
        paint.setStrokeWidth(Math.max(2f, s * 0.075f));
        paint.setStyle(Paint.Style.STROKE);
        path.reset();

        if (type == CHAT) {
            canvas.drawRoundRect(cx - s * .37f, cy - s * .28f, cx + s * .37f, cy + s * .23f,
                    s * .17f, s * .17f, paint);
            path.moveTo(cx - s * .12f, cy + s * .23f);
            path.lineTo(cx - s * .25f, cy + s * .38f);
            path.lineTo(cx + s * .04f, cy + s * .23f);
            canvas.drawPath(path, paint);
        } else if (type == CHARACTER) {
            canvas.drawCircle(cx, cy - s * .18f, s * .18f, paint);
            canvas.drawArc(cx - s * .35f, cy + s * .02f, cx + s * .35f, cy + s * .48f,
                    200, 140, false, paint);
        } else if (type == BOOK) {
            canvas.drawRoundRect(cx - s * .36f, cy - s * .34f, cx + s * .36f, cy + s * .34f,
                    s * .08f, s * .08f, paint);
            canvas.drawLine(cx, cy - s * .31f, cx, cy + s * .31f, paint);
            canvas.drawLine(cx - s * .24f, cy - s * .15f, cx - s * .08f, cy - s * .15f, paint);
            canvas.drawLine(cx + s * .08f, cy - s * .15f, cx + s * .24f, cy - s * .15f, paint);
        } else if (type == PERSON) {
            canvas.drawCircle(cx, cy - s * .18f, s * .17f, paint);
            canvas.drawCircle(cx, cy, s * .39f, paint);
            canvas.drawArc(cx - s * .3f, cy + s * .03f, cx + s * .3f, cy + s * .42f,
                    195, 150, false, paint);
        } else if (type == SEARCH) {
            canvas.drawCircle(cx - s * .09f, cy - s * .09f, s * .24f, paint);
            canvas.drawLine(cx + s * .09f, cy + s * .09f, cx + s * .34f, cy + s * .34f, paint);
        } else if (type == PLUS) {
            canvas.drawLine(cx, cy - s * .3f, cx, cy + s * .3f, paint);
            canvas.drawLine(cx - s * .3f, cy, cx + s * .3f, cy, paint);
        } else if (type == BACK) {
            path.moveTo(cx + s * .2f, cy - s * .34f);
            path.lineTo(cx - s * .17f, cy);
            path.lineTo(cx + s * .2f, cy + s * .34f);
            canvas.drawPath(path, paint);
        } else if (type == MORE) {
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(cx - s * .26f, cy, s * .07f, paint);
            canvas.drawCircle(cx, cy, s * .07f, paint);
            canvas.drawCircle(cx + s * .26f, cy, s * .07f, paint);
        } else if (type == PHONE || type == HANGUP) {
            float direction = type == HANGUP ? -1f : 1f;
            canvas.save();
            canvas.rotate(type == HANGUP ? 135f : 0f, cx, cy);
            path.moveTo(cx - s * .29f, cy - direction * s * .28f);
            path.cubicTo(
                    cx - s * .42f, cy - direction * s * .04f,
                    cx - s * .08f, cy + direction * s * .35f,
                    cx + s * .18f, cy + direction * s * .35f);
            path.cubicTo(
                    cx + s * .30f, cy + direction * s * .35f,
                    cx + s * .36f, cy + direction * s * .23f,
                    cx + s * .28f, cy + direction * s * .14f);
            canvas.drawPath(path, paint);
            canvas.drawLine(
                    cx - s * .29f, cy - direction * s * .28f,
                    cx - s * .15f, cy - direction * s * .16f, paint);
            canvas.drawLine(
                    cx + s * .28f, cy + direction * s * .14f,
                    cx + s * .13f, cy + direction * s * .25f, paint);
            canvas.restore();
        } else if (type == MIC) {
            canvas.drawRoundRect(
                    cx - s * .15f, cy - s * .34f,
                    cx + s * .15f, cy + s * .12f,
                    s * .15f, s * .15f, paint);
            canvas.drawArc(
                    cx - s * .27f, cy - s * .02f,
                    cx + s * .27f, cy + s * .31f,
                    0, 180, false, paint);
            canvas.drawLine(cx, cy + s * .30f, cx, cy + s * .42f, paint);
            canvas.drawLine(cx - s * .18f, cy + s * .42f,
                    cx + s * .18f, cy + s * .42f, paint);
        } else if (type == SPEAKER) {
            path.moveTo(cx - s * .34f, cy - s * .12f);
            path.lineTo(cx - s * .16f, cy - s * .12f);
            path.lineTo(cx + s * .04f, cy - s * .31f);
            path.lineTo(cx + s * .04f, cy + s * .31f);
            path.lineTo(cx - s * .16f, cy + s * .12f);
            path.lineTo(cx - s * .34f, cy + s * .12f);
            path.close();
            canvas.drawPath(path, paint);
            canvas.drawArc(
                    cx - s * .12f, cy - s * .26f,
                    cx + s * .32f, cy + s * .26f,
                    -55, 110, false, paint);
            canvas.drawArc(
                    cx - s * .03f, cy - s * .38f,
                    cx + s * .48f, cy + s * .38f,
                    -52, 104, false, paint);
        } else if (type == IMAGE) {
            canvas.drawRoundRect(
                    cx - s * .36f, cy - s * .31f,
                    cx + s * .36f, cy + s * .31f,
                    s * .07f, s * .07f, paint);
            canvas.drawCircle(cx + s * .18f, cy - s * .13f, s * .07f, paint);
            path.moveTo(cx - s * .28f, cy + s * .19f);
            path.lineTo(cx - s * .08f, cy - s * .03f);
            path.lineTo(cx + s * .05f, cy + s * .11f);
            path.lineTo(cx + s * .15f, cy + s * .01f);
            path.lineTo(cx + s * .30f, cy + s * .19f);
            canvas.drawPath(path, paint);
        } else if (type == CAMERA) {
            canvas.drawRoundRect(
                    cx - s * .38f, cy - s * .24f,
                    cx + s * .38f, cy + s * .29f,
                    s * .09f, s * .09f, paint);
            path.moveTo(cx - s * .20f, cy - s * .24f);
            path.lineTo(cx - s * .11f, cy - s * .36f);
            path.lineTo(cx + s * .11f, cy - s * .36f);
            path.lineTo(cx + s * .20f, cy - s * .24f);
            canvas.drawPath(path, paint);
            canvas.drawCircle(cx, cy + s * .02f, s * .17f, paint);
        } else if (type == LOCATION) {
            path.moveTo(cx, cy + s * .39f);
            path.cubicTo(
                    cx - s * .09f, cy + s * .25f,
                    cx - s * .31f, cy + s * .02f,
                    cx - s * .31f, cy - s * .13f);
            path.cubicTo(
                    cx - s * .31f, cy - s * .34f,
                    cx - s * .16f, cy - s * .45f,
                    cx, cy - s * .45f);
            path.cubicTo(
                    cx + s * .16f, cy - s * .45f,
                    cx + s * .31f, cy - s * .34f,
                    cx + s * .31f, cy - s * .13f);
            path.cubicTo(
                    cx + s * .31f, cy + s * .02f,
                    cx + s * .09f, cy + s * .25f,
                    cx, cy + s * .39f);
            canvas.drawPath(path, paint);
            canvas.drawCircle(cx, cy - s * .13f, s * .10f, paint);
        }
    }
}
