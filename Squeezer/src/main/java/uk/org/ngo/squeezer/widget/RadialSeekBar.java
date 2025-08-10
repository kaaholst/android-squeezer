package uk.org.ngo.squeezer.widget;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import uk.org.ngo.squeezer.R;

public class RadialSeekBar extends View {

    private float midx, midy;
    private Paint textPaint, circlePaint, circlePaint2, linePaint;
    private float deg = 3;
    private float downdeg = 0;

    private boolean isContinuous = false;

    private int backCircleColor = Color.parseColor("#222222");
    private int mainCircleColor = Color.parseColor("#000000");
    private int indicatorColor = Color.parseColor("#FFA036");
    private int progressPrimaryColor = Color.parseColor("#FFA036");
    private int progressSecondaryColor = Color.parseColor("#111111");

    private float progressPrimaryCircleSize = -1;
    private float progressSecondaryCircleSize = -1;

    private float progressPrimaryStrokeWidth = 25;
    private float progressSecondaryStrokeWidth = 10;

    private float mainCircleRadius = -1;
    private float backCircleRadius = -1;
    private float progressRadius = -1;

    private int max = 25;
    private int min = 1;

    private String label = "Label";
    private String labelFont;
    private int labelStyle = 0;
    private int labelColor = Color.WHITE;

    private int startOffset = 30;
    private int sweepAngle = -1;

    private boolean isEnabled = true;

    private boolean isAntiClockwise = false;

    private boolean startEventSent = false;

    RectF oval;

    private OnProgressChangedListener progressChangedListener;
    private OnRadialSeekBarChangeListener seekBarChangeListener;

    public interface OnProgressChangedListener {
        void onProgressChanged(int progress);
    }

    public interface OnRadialSeekBarChangeListener {
        void onProgressChanged(RadialSeekBar seekBar, int progress);
        void onStartTrackingTouch(RadialSeekBar seekBar);
        void onStopTrackingTouch(RadialSeekBar seekBar);
    }

    public void setOnProgressChangedListener(OnProgressChangedListener mProgressChangeListener) {
        this.progressChangedListener = mProgressChangeListener;
    }

    public void setOnRadialSeekBarChangeListener(OnRadialSeekBarChangeListener onRadialSeekBarChangeListener) {
        this.seekBarChangeListener = onRadialSeekBarChangeListener;
    }

    public RadialSeekBar(Context context) {
        super(context);
        init();
    }

    public RadialSeekBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        initXMLAttrs(context, attrs);
        init();
    }

    public RadialSeekBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initXMLAttrs(context, attrs);
        init();
    }

    private void init() {
        textPaint = new Paint();
        textPaint.setAntiAlias(true);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setFakeBoldText(true);
        textPaint.setTextAlign(Paint.Align.CENTER);

        generateTypeface();

        circlePaint = new Paint();
        circlePaint.setAntiAlias(true);
        circlePaint.setStrokeWidth(progressSecondaryStrokeWidth);
        circlePaint.setStyle(Paint.Style.FILL);

        circlePaint2 = new Paint();
        circlePaint2.setAntiAlias(true);
        circlePaint2.setStrokeWidth(progressPrimaryStrokeWidth);
        circlePaint2.setStyle(Paint.Style.FILL);

        linePaint = new Paint();
        linePaint.setAntiAlias(true);

        circlePaint2.setColor(progressPrimaryColor);
        circlePaint.setColor(progressSecondaryColor);
        linePaint.setColor(indicatorColor);
        textPaint.setColor(labelColor);

        oval = new RectF();
    }

    private int calcTextSize() {
        int[] textSizeAttr = new int[] { android.R.attr.textSize };
        try (TypedArray a = getContext().obtainStyledAttributes(R.style.SqueezerTextAppearance_Medium, textSizeAttr)) {
            return a.getDimensionPixelSize(0, -1);
        }
    }

    private void generateTypeface() {
        Typeface plainLabel = Typeface.DEFAULT;
        if (getLabelFont() != null && !getLabelFont().isEmpty()) {
            AssetManager assetMgr = getContext().getAssets();
            plainLabel = Typeface.createFromAsset(assetMgr, getLabelFont());
        }

        switch (getLabelStyle()) {
            case 0:
                textPaint.setTypeface(plainLabel);
                break;
            case 1:
                textPaint.setTypeface(Typeface.create(plainLabel, Typeface.BOLD));
                break;
            case 2:
                textPaint.setTypeface(Typeface.create(plainLabel, Typeface.ITALIC));
                break;
            case 3:
                textPaint.setTypeface(Typeface.create(plainLabel, Typeface.BOLD_ITALIC));
                break;

        }

    }

    private void initXMLAttrs(Context context, AttributeSet attrs) {
        try (TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.RadialSeekBar)) {
            setEnabled(a.getBoolean(R.styleable.RadialSeekBar_enabled, true));
            setProgress(a.getInt(R.styleable.RadialSeekBar_start_progress, 1));
            setLabel(a.getString(R.styleable.RadialSeekBar_label));

            setBackCircleColor(a.getColor(R.styleable.RadialSeekBar_back_circle_color, backCircleColor));
            setMainCircleColor(a.getColor(R.styleable.RadialSeekBar_main_circle_color, mainCircleColor));
            setIndicatorColor(a.getColor(R.styleable.RadialSeekBar_indicator_color, indicatorColor));
            setProgressPrimaryColor(a.getColor(R.styleable.RadialSeekBar_progress_primary_color, progressPrimaryColor));
            setProgressSecondaryColor(a.getColor(R.styleable.RadialSeekBar_progress_secondary_color, progressSecondaryColor));

            setLabelColor(a.getColor(R.styleable.RadialSeekBar_label_color, labelColor));
            setLabelFont(a.getString(R.styleable.RadialSeekBar_label_font));
            setLabelStyle(a.getInt(R.styleable.RadialSeekBar_label_style, 0));
            setIsContinuous(a.getBoolean(R.styleable.RadialSeekBar_is_continuous, false));
            setProgressPrimaryCircleSize(a.getFloat(R.styleable.RadialSeekBar_progress_primary_circle_size, -1));
            setProgressSecondaryCircleSize(a.getFloat(R.styleable.RadialSeekBar_progress_secondary_circle_size, -1));
            setProgressPrimaryStrokeWidth(a.getFloat(R.styleable.RadialSeekBar_progress_primary_stroke_width, 25));
            setProgressSecondaryStrokeWidth(a.getFloat(R.styleable.RadialSeekBar_progress_secondary_stroke_width, 10));
            setSweepAngle(a.getInt(R.styleable.RadialSeekBar_sweep_angle, -1));
            setStartOffset(a.getInt(R.styleable.RadialSeekBar_start_offset, 30));
            setMax(a.getInt(R.styleable.RadialSeekBar_max, 25));
            setMin(a.getInt(R.styleable.RadialSeekBar_min, 1));
            deg = min + 2;
            setBackCircleRadius(a.getFloat(R.styleable.RadialSeekBar_back_circle_radius, -1));
            setProgressRadius(a.getFloat(R.styleable.RadialSeekBar_progress_radius, -1));
            setAntiClockwise(a.getBoolean(R.styleable.RadialSeekBar_anticlockwise, false));
        }
    }

    private static float getDistance(float x1, float y1, float x2, float y2) {
        return (float) Math.sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2));
    }

    private static int dpToPx(int dp, Context context) {
        Resources r = context.getResources();
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, r.getDisplayMetrics()));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int minWidth = dpToPx(160, getContext());
        int minHeight = dpToPx(160, getContext());

        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int width;
        int height;

        if (widthMode == MeasureSpec.EXACTLY) {
            width = widthSize;
        } else if (widthMode == MeasureSpec.AT_MOST) {
            width = Math.min(minWidth, widthSize);
        } else {
            // only in case of ScrollViews, otherwise MeasureSpec.UNSPECIFIED is never triggered
            // If width is wrap_content i.e. MeasureSpec.UNSPECIFIED, then make width equal to height
            width = heightSize;
        }

        if (heightMode == MeasureSpec.EXACTLY) {
            height = heightSize;
        } else if (heightMode == MeasureSpec.AT_MOST) {
            height = Math.min(minHeight, heightSize);
        } else {
            // only in case of ScrollViews, otherwise MeasureSpec.UNSPECIFIED is never triggered
            // If height is wrap_content i.e. MeasureSpec.UNSPECIFIED, then make height equal to width
            height = widthSize;
        }

        if (widthMode == MeasureSpec.UNSPECIFIED && heightMode == MeasureSpec.UNSPECIFIED) {
            width = minWidth;
            height = minHeight;
        }

        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        midx = (float) getWidth() / 2;
        midy = (float) getHeight() / 2;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        int textSize = calcTextSize();
        float indicatorWidth = (float) ((float) getWidth() / 64.0);

        if (progressChangedListener != null)
            progressChangedListener.onProgressChanged((int) (deg - 2));

        if (seekBarChangeListener != null)
            seekBarChangeListener.onProgressChanged(this, (int) (deg - 2));

        circlePaint2.setColor(ColorUtils.setAlphaComponent(progressPrimaryColor, isEnabled ? 255 : 63));
        circlePaint.setColor(ColorUtils.setAlphaComponent(progressSecondaryColor, isEnabled ? 255 : 63));
        linePaint.setColor(ColorUtils.setAlphaComponent(indicatorColor, isEnabled ? 255 : 63));
        textPaint.setColor(ColorUtils.setAlphaComponent(labelColor, isEnabled ? 255 : 63));

        if (!isContinuous) {
            int startOffset2 = startOffset - 15;

            linePaint.setStrokeWidth(indicatorWidth);
            textPaint.setTextSize(textSize);

            int radius = (int) (Math.min(midx, midy) * ((float) 14.5 / 16));

            if (sweepAngle == -1) {
                sweepAngle = 360 - (2 * startOffset2);
            }

            if (mainCircleRadius == -1) {
                mainCircleRadius = radius * ((float) 11 / 15);
            }
            if (backCircleRadius == -1) {
                backCircleRadius = radius * ((float) 13 / 15);
            }
            if (progressRadius == -1) {
                progressRadius = radius;
            }

            float x, y;
            float deg2 = Math.max(3, deg);
            float deg3 = Math.min(deg, max + 2);
            for (int i = (int) (deg2); i < max + 3; i++) {
                float tmp = ((float) startOffset2 / 360) + ((float) sweepAngle / 360) * (float) i / (max + 5);

                if (isAntiClockwise) {
                    tmp = 1.0f - tmp;
                }

                x = midx + (float) (progressRadius * Math.sin(2 * Math.PI * (1.0 - tmp)));
                y = midy + (float) (progressRadius * Math.cos(2 * Math.PI * (1.0 - tmp)));
                if (progressSecondaryCircleSize == -1)
                    canvas.drawCircle(x, y, ((float) radius / 30 * ((float) 20 / max) * ((float) sweepAngle / 270)), circlePaint);
                else
                    canvas.drawCircle(x, y, progressSecondaryCircleSize, circlePaint);
            }
            for (int i = 3; i <= deg3; i++) {
                float tmp = ((float) startOffset2 / 360) + ((float) sweepAngle / 360) * (float) i / (max + 5);

                if (isAntiClockwise) {
                    tmp = 1.0f - tmp;
                }

                x = midx + (float) (progressRadius * Math.sin(2 * Math.PI * (1.0 - tmp)));
                y = midy + (float) (progressRadius * Math.cos(2 * Math.PI * (1.0 - tmp)));
                if (progressPrimaryCircleSize == -1)
                    canvas.drawCircle(x, y, (progressRadius / 15 * ((float) 20 / max) * ((float) sweepAngle / 270)), circlePaint2);
                else
                    canvas.drawCircle(x, y, progressPrimaryCircleSize, circlePaint2);
            }

            float tmp2 = ((float) startOffset2 / 360) + ((float) sweepAngle / 360) * deg / (max + 5);

            if (isAntiClockwise) {
                tmp2 = 1.0f - tmp2;
            }

            float x1 = midx + (float) (radius * ((float) 2 / 5) * Math.sin(2 * Math.PI * (1.0 - tmp2)));
            float y1 = midy + (float) (radius * ((float) 2 / 5) * Math.cos(2 * Math.PI * (1.0 - tmp2)));
            float x2 = midx + (float) (radius * ((float) 3 / 5) * Math.sin(2 * Math.PI * (1.0 - tmp2)));
            float y2 = midy + (float) (radius * ((float) 3 / 5) * Math.cos(2 * Math.PI * (1.0 - tmp2)));

            circlePaint.setColor(backCircleColor);
            canvas.drawCircle(midx, midy, backCircleRadius, circlePaint);
            circlePaint.setColor(mainCircleColor);
            canvas.drawCircle(midx, midy, mainCircleRadius, circlePaint);

            canvas.drawText(label, midx, midy + (float) (radius * 1.1)-textPaint.getFontMetrics().descent, textPaint);
            canvas.drawLine(x1, y1, x2, y2, linePaint);

        } else {

            int radius = (int) (Math.min(midx, midy) * ((float) 14.5 / 16));

            if (sweepAngle == -1) {
                sweepAngle = 360 - (2 * startOffset);
            }

            if (mainCircleRadius == -1) {
                mainCircleRadius = radius * ((float) 11 / 15);
            }
            if (backCircleRadius == -1) {
                backCircleRadius = radius * ((float) 13 / 15);
            }
            if (progressRadius == -1) {
                progressRadius = radius;
            }

            circlePaint.setStrokeWidth(progressSecondaryStrokeWidth);
            circlePaint.setStyle(Paint.Style.STROKE);
            circlePaint2.setStrokeWidth(progressPrimaryStrokeWidth);
            circlePaint2.setStyle(Paint.Style.STROKE);
            linePaint.setStrokeWidth(indicatorWidth);
            textPaint.setTextSize(textSize);

            float deg3 = Math.min(deg, max + 2);

            oval.set(midx - progressRadius, midy - progressRadius, midx + progressRadius, midy + progressRadius);

            canvas.drawArc(oval, (float) 90 + startOffset, (float) sweepAngle, false, circlePaint);
            if (isAntiClockwise) {
                canvas.drawArc(oval, (float) 90 - startOffset, -1 * ((deg3 - 2) * ((float) sweepAngle / max)), false, circlePaint2);
            } else {
                canvas.drawArc(oval, (float) 90 + startOffset, ((deg3 - 2) * ((float) sweepAngle / max)), false, circlePaint2);
            }

            float tmp2 = ((float) startOffset / 360) + (((float) sweepAngle / 360) * ((deg - 2) / (max)));

            if (isAntiClockwise) {
                tmp2 = 1.0f - tmp2;
            }

            float x1 = midx + (float) (radius * ((float) 2 / 5) * Math.sin(2 * Math.PI * (1.0 - tmp2)));
            float y1 = midy + (float) (radius * ((float) 2 / 5) * Math.cos(2 * Math.PI * (1.0 - tmp2)));
            float x2 = midx + (float) (radius * ((float) 3 / 5) * Math.sin(2 * Math.PI * (1.0 - tmp2)));
            float y2 = midy + (float) (radius * ((float) 3 / 5) * Math.cos(2 * Math.PI * (1.0 - tmp2)));

            circlePaint.setStyle(Paint.Style.FILL);
            circlePaint.setColor(backCircleColor);
            canvas.drawCircle(midx, midy, backCircleRadius, circlePaint);
            circlePaint.setColor(mainCircleColor);
            canvas.drawCircle(midx, midy, mainCircleRadius, circlePaint);

            canvas.drawText(label, midx, midy + (float) (radius * 1.1)-textPaint.getFontMetrics().descent, textPaint);
            canvas.drawLine(x1, y1, x2, y2, linePaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {

        if (!isEnabled)
            return false;

        if (getDistance(e.getX(), e.getY(), midx, midy) > Math.max(mainCircleRadius, Math.max(backCircleRadius, progressRadius))) {
            if (startEventSent && seekBarChangeListener != null) {
                seekBarChangeListener.onStopTrackingTouch(this);
                startEventSent = false;
            }
            return super.onTouchEvent(e);
        }

        if (e.getAction() == MotionEvent.ACTION_DOWN) {

            float dx = e.getX() - midx;
            float dy = e.getY() - midy;
            downdeg = (float) ((Math.atan2(dy, dx) * 180) / Math.PI);
            downdeg -= 90;
            if (downdeg < 0) {
                downdeg += 360;
            }
            downdeg = (float) Math.floor((downdeg / 360) * (max + 5));

            if (seekBarChangeListener != null) {
                seekBarChangeListener.onStartTrackingTouch(this);
                startEventSent = true;
            }

            return true;
        }
        if (e.getAction() == MotionEvent.ACTION_MOVE) {
            float dx = e.getX() - midx;
            float dy = e.getY() - midy;
            float currdeg = (float) ((Math.atan2(dy, dx) * 180) / Math.PI);
            currdeg -= 90;
            if (currdeg < 0) {
                currdeg += 360;
            }
            currdeg = (float) Math.floor((currdeg / 360) * (max + 5));

            if ((currdeg / (max + 4)) > 0.75f && ((downdeg - 0) / (max + 4)) < 0.25f) {
                if (isAntiClockwise) {
                    deg++;
                    if (deg > max + 2) {
                        deg = max + 2;
                    }
                } else {
                    deg--;
                    if (deg < (min + 2)) {
                        deg = (min + 2);
                    }
                }
            } else if ((downdeg / (max + 4)) > 0.75f && ((currdeg - 0) / (max + 4)) < 0.25f) {
                if (isAntiClockwise) {
                    deg--;
                    if (deg < (min + 2)) {
                        deg = (min + 2);
                    }
                } else {
                    deg++;
                    if (deg > max + 2) {
                        deg = max + 2;
                    }
                }
            } else {
                if (isAntiClockwise) {
                    deg -= (currdeg - downdeg);
                } else {
                    deg += (currdeg - downdeg);
                }
                if (deg > max + 2) {
                    deg = max + 2;
                }
                if (deg < (min + 2)) {
                    deg = (min + 2);
                }
            }

            downdeg = currdeg;

            invalidate();
            return true;

        }
        if (e.getAction() == MotionEvent.ACTION_UP) {
            if (seekBarChangeListener != null) {
                seekBarChangeListener.onStopTrackingTouch(this);
                startEventSent = false;
            }
            return true;
        }
        return super.onTouchEvent(e);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (getParent() != null && event.getAction() == MotionEvent.ACTION_DOWN) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }
        return super.dispatchTouchEvent(event);
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    public void setEnabled(boolean enabled) {
        this.isEnabled = enabled;
        invalidate();
    }

    public int getProgress() {
        return (int) (deg - 2);
    }

    public void setProgress(int x) {
        deg = x + 2;
        invalidate();
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String txt) {
        label = txt;
        invalidate();
    }

    public int getBackCircleColor() {
        return backCircleColor;
    }

    public void setBackCircleColor(int backCircleColor) {
        this.backCircleColor = backCircleColor;
        invalidate();
    }

    public int getMainCircleColor() {
        return mainCircleColor;
    }

    public void setMainCircleColor(int mainCircleColor) {
        this.mainCircleColor = mainCircleColor;
        invalidate();
    }

    public int getIndicatorColor() {
        return indicatorColor;
    }

    public void setIndicatorColor(int indicatorColor) {
        this.indicatorColor = indicatorColor;
        invalidate();
    }

    public int getProgressPrimaryColor() {
        return progressPrimaryColor;
    }

    public void setProgressPrimaryColor(int progressPrimaryColor) {
        this.progressPrimaryColor = progressPrimaryColor;
        invalidate();
    }

    public int getProgressSecondaryColor() {
        return progressSecondaryColor;
    }

    public void setProgressSecondaryColor(int progressSecondaryColor) {
        this.progressSecondaryColor = progressSecondaryColor;
        invalidate();
    }

    public int getLabelColor() {
        return labelColor;
    }

    public void setLabelColor(int labelColor) {
        this.labelColor = labelColor;
        invalidate();
    }

    public String getLabelFont() {
        return labelFont;
    }

    public void setLabelFont(String labelFont) {
        this.labelFont = labelFont;
        if (textPaint != null)
            generateTypeface();
        invalidate();
    }

    public int getLabelStyle() {
        return labelStyle;
    }

    public void setLabelStyle(int labelStyle) {
        this.labelStyle = labelStyle;
        invalidate();
    }

    public boolean isContinuous() {
        return isContinuous;
    }

    public void setIsContinuous(boolean isContinuous) {
        this.isContinuous = isContinuous;
        invalidate();
    }

    public float getProgressPrimaryCircleSize() {
        return progressPrimaryCircleSize;
    }

    public void setProgressPrimaryCircleSize(float progressPrimaryCircleSize) {
        this.progressPrimaryCircleSize = progressPrimaryCircleSize;
        invalidate();
    }

    public float getProgressSecondaryCircleSize() {
        return progressSecondaryCircleSize;
    }

    public void setProgressSecondaryCircleSize(float progressSecondaryCircleSize) {
        this.progressSecondaryCircleSize = progressSecondaryCircleSize;
        invalidate();
    }

    public float getProgressPrimaryStrokeWidth() {
        return progressPrimaryStrokeWidth;
    }

    public void setProgressPrimaryStrokeWidth(float progressPrimaryStrokeWidth) {
        this.progressPrimaryStrokeWidth = progressPrimaryStrokeWidth;
        invalidate();
    }

    public float getProgressSecondaryStrokeWidth() {
        return progressSecondaryStrokeWidth;
    }

    public void setProgressSecondaryStrokeWidth(float progressSecondaryStrokeWidth) {
        this.progressSecondaryStrokeWidth = progressSecondaryStrokeWidth;
        invalidate();
    }

    public int getSweepAngle() {
        return sweepAngle;
    }

    public void setSweepAngle(int sweepAngle) {
        this.sweepAngle = sweepAngle;
        invalidate();
    }

    public int getStartOffset() {
        return startOffset;
    }

    public void setStartOffset(int startOffset) {
        this.startOffset = startOffset;
        invalidate();
    }

    public int getMax() {
        return max;
    }

    public void setMax(int max) {
        this.max = Math.max(max, min);
        invalidate();
    }

    public int getMin() {
        return min;
    }

    public void setMin(int min) {
        this.min = min < 0 ? 0 : Math.min(min, max);
        invalidate();
    }

    public float getMainCircleRadius() {
        return mainCircleRadius;
    }

    public void setMainCircleRadius(float mainCircleRadius) {
        this.mainCircleRadius = mainCircleRadius;
        invalidate();
    }

    public float getBackCircleRadius() {
        return backCircleRadius;
    }

    public void setBackCircleRadius(float backCircleRadius) {
        this.backCircleRadius = backCircleRadius;
        invalidate();
    }

    public float getProgressRadius() {
        return progressRadius;
    }

    public void setProgressRadius(float progressRadius) {
        this.progressRadius = progressRadius;
        invalidate();
    }

    public boolean isAntiClockwise() {
        return isAntiClockwise;
    }

    public void setAntiClockwise(boolean antiClockwise) {
        isAntiClockwise = antiClockwise;
        invalidate();
    }
}

