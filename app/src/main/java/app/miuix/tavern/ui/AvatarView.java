package app.miuix.tavern.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.View;

import app.miuix.tavern.model.CharacterCard;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AvatarView extends View {
    private static final int[] COLORS = new int[]{
            0xFF6C8AE4, 0xFFE78965, 0xFF63B69A, 0xFFAB7DCE, 0xFF5C9FB5, 0xFFD3718B
    };

    private final Paint background = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Path clip = new Path();
    private String initials = "角";
    private Bitmap bitmap;
    private int color = COLORS[0];
    private List<CharacterCard> groupCards = Collections.emptyList();
    private final List<Bitmap> groupBitmaps = new ArrayList<>();

    public AvatarView(Context context) {
        super(context);
        init();
    }

    public AvatarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        text.setColor(0xFFFFFFFF);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD));
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public void setCharacter(CharacterCard card) {
        groupCards = Collections.emptyList();
        groupBitmaps.clear();
        initials = card == null ? "角" : card.initials();
        color = COLORS[((card == null ? initials : card.id).hashCode()
                & Integer.MAX_VALUE) % COLORS.length];
        bitmap = null;
        if (card != null && card.avatarPath != null && !card.avatarPath.isEmpty()) {
            File file = new File(card.avatarPath);
            if (file.isFile()) bitmap = decodeAvatar(file);
        }
        invalidate();
    }

    public void setCharacters(List<CharacterCard> cards) {
        bitmap = null;
        groupBitmaps.clear();
        groupCards = new ArrayList<>();
        if (cards != null) {
            for (CharacterCard card : cards) {
                if (card == null || groupCards.size() >= 9) continue;
                groupCards.add(card);
                Bitmap memberBitmap = null;
                if (card.avatarPath != null && !card.avatarPath.trim().isEmpty()) {
                    File file = new File(card.avatarPath);
                    if (file.isFile()) memberBitmap = decodeAvatar(file);
                }
                groupBitmaps.add(memberBitmap);
            }
        }
        if (groupCards.isEmpty()) {
            CharacterCard fallback = new CharacterCard();
            fallback.id = "empty-group";
            fallback.name = "群";
            setCharacter(fallback);
            return;
        }
        invalidate();
    }

    private static Bitmap decodeAvatar(File file) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        int sample = 1;
        int largest = Math.max(bounds.outWidth, bounds.outHeight);
        while (largest / sample > 512) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float radius = getWidth() * 0.27f;
        RectF bounds = new RectF(0, 0, getWidth(), getHeight());
        clip.reset();
        clip.addRoundRect(bounds, radius, radius, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clip);
        if (!groupCards.isEmpty()) {
            drawGroup(canvas, bounds);
        } else if (bitmap != null) {
            Rect source = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
            float sourceRatio = bitmap.getWidth() / (float) bitmap.getHeight();
            float targetRatio = getWidth() / (float) getHeight();
            if (sourceRatio > targetRatio) {
                int desired = Math.round(bitmap.getHeight() * targetRatio);
                int left = (bitmap.getWidth() - desired) / 2;
                source.set(left, 0, left + desired, bitmap.getHeight());
            } else {
                int desired = Math.round(bitmap.getWidth() / targetRatio);
                int top = (bitmap.getHeight() - desired) / 2;
                source.set(0, top, bitmap.getWidth(), top + desired);
            }
            canvas.drawBitmap(bitmap, source, bounds, imagePaint);
        } else {
            background.setColor(color);
            canvas.drawRect(bounds, background);
            text.setTextSize(getWidth() * 0.42f);
            Paint.FontMetrics metrics = text.getFontMetrics();
            float center = getHeight() / 2f - (metrics.ascent + metrics.descent) / 2f;
            canvas.drawText(initials, getWidth() / 2f, center, text);
        }
        canvas.restore();
    }

    private void drawGroup(Canvas canvas, RectF bounds) {
        background.setColor(0xFFE3E3E5);
        canvas.drawRect(bounds, background);
        int count = groupCards.size();
        int rows = count <= 2 ? 1 : count <= 4 ? 2 : 3;
        float gap = Math.max(1f, getWidth() * 0.025f);
        float cell = (Math.min(getWidth(), getHeight()) - gap * 4f) / 3f;
        if (count == 1) cell = Math.min(getWidth(), getHeight()) - gap * 2f;
        else if (rows == 1) cell =
                (Math.min(getWidth(), getHeight()) - gap * 3f) / 2f;
        else if (count <= 4) cell = (Math.min(getWidth(), getHeight()) - gap * 3f) / 2f;
        float totalHeight = rows * cell + (rows - 1) * gap;
        float top = (getHeight() - totalHeight) / 2f;
        int index = 0;
        for (int row = 0; row < rows && index < count; row++) {
            int items = rowCount(count, rows, row);
            float totalWidth = items * cell + (items - 1) * gap;
            float left = (getWidth() - totalWidth) / 2f;
            for (int column = 0; column < items && index < count; column++, index++) {
                RectF tile = new RectF(
                        left + column * (cell + gap),
                        top + row * (cell + gap),
                        left + column * (cell + gap) + cell,
                        top + row * (cell + gap) + cell);
                drawMember(canvas, groupCards.get(index), groupBitmaps.get(index), tile);
            }
        }
    }

    private static int rowCount(int count, int rows, int row) {
        if (rows == 1) return count;
        if (rows == 2) {
            if (count == 3) return row == 0 ? 1 : 2;
            return 2;
        }
        if (count == 5) return row == 0 ? 2 : 3;
        if (count == 6) return row == 0 ? 3 : 3;
        if (count == 7) return row == 0 ? 1 : 3;
        if (count == 8) return row == 0 ? 2 : 3;
        return 3;
    }

    private void drawMember(Canvas canvas, CharacterCard card, Bitmap memberBitmap,
                            RectF target) {
        if (memberBitmap != null) {
            Rect source = centerCrop(memberBitmap, target.width() / target.height());
            canvas.drawBitmap(memberBitmap, source, target, imagePaint);
            return;
        }
        int memberColor =
                COLORS[(card.id.hashCode() & Integer.MAX_VALUE) % COLORS.length];
        background.setColor(memberColor);
        canvas.drawRect(target, background);
        text.setTextSize(target.width() * 0.42f);
        Paint.FontMetrics metrics = text.getFontMetrics();
        float center = target.centerY() - (metrics.ascent + metrics.descent) / 2f;
        canvas.drawText(card.initials(), target.centerX(), center, text);
    }

    private static Rect centerCrop(Bitmap source, float targetRatio) {
        Rect result = new Rect(0, 0, source.getWidth(), source.getHeight());
        float sourceRatio = source.getWidth() / (float) source.getHeight();
        if (sourceRatio > targetRatio) {
            int desired = Math.round(source.getHeight() * targetRatio);
            int left = (source.getWidth() - desired) / 2;
            result.set(left, 0, left + desired, source.getHeight());
        } else {
            int desired = Math.round(source.getWidth() / targetRatio);
            int top = (source.getHeight() - desired) / 2;
            result.set(0, top, source.getWidth(), top + desired);
        }
        return result;
    }
}
