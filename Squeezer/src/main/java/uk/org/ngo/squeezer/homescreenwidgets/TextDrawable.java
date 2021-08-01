package uk.org.ngo.squeezer.homescreenwidgets;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;

import androidx.annotation.ColorInt;

public class TextDrawable extends Drawable {
    private static final int DEFAULT_TEXTSIZE = 15;
    private final Paint mPaint;
    private final CharSequence mText;
    private final int mIntrinsicWidth;
    private final int mIntrinsicHeight;


    public TextDrawable(Resources res, CharSequence text, @ColorInt int color) {
        mText = text;
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        mPaint.setColor(color);
        mPaint.setTextAlign(Paint.Align.CENTER);
        float textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
                DEFAULT_TEXTSIZE, res.getDisplayMetrics());
        mPaint.setTextSize(textSize);
        mIntrinsicWidth = (int) (mPaint.measureText(mText, 0, mText.length()) + .5);
        mIntrinsicHeight = mPaint.getFontMetricsInt(null);
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();

        canvas.drawText(mText, 0, mText.length(),
                bounds.centerX(), bounds.centerY() - ((mPaint.descent() + mPaint.ascent()) / 2), mPaint);
    }

    @Override
    public int getOpacity() {
        return mPaint.getAlpha();
    }

    @Override
    public int getIntrinsicWidth() {
        return mIntrinsicWidth;
    }

    @Override
    public int getIntrinsicHeight() {
        return mIntrinsicHeight;
    }

    @Override
    public void setAlpha(int alpha) {
        mPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter filter) {
        mPaint.setColorFilter(filter);
    }
}