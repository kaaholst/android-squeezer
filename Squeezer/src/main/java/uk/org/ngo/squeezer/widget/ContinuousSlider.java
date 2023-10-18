package uk.org.ngo.squeezer.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatSeekBar;

/**
 * Slider which updates the value based only on movements, regardless of the position of the {@link MotionEvent}.
 * <p>
 * This eliminates sudden movements of slider, when tapping it.
 */
public class ContinuousSlider extends AppCompatSeekBar {
    private OnSeekBarChangeListener listener = null;
    private int lastPointerId = 0;
    private int lastValue = 0;

    public ContinuousSlider(@NonNull Context context) {
        super(context);
    }

    public ContinuousSlider(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public ContinuousSlider(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public int getMin() {
        return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O ? super.getMin() : 0;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                lastValue = calculateValue(event);
                listener.onStartTrackingTouch(this);
                lastPointerId = event.getPointerId(event.getActionIndex());
                break;
            case MotionEvent.ACTION_MOVE:
                setPressed(true);

                // If pointer id has changed set last value instead of moving
                int currentPointerId = event.getPointerId(event.getActionIndex());
                if (lastPointerId != currentPointerId) {
                    lastValue = calculateValue(event);
                    lastPointerId = currentPointerId;
                    return true;
                }

                int value = calculateValue(event);
                move(value);
                listener.onProgressChanged(this, getProgress(), true);
                lastValue = value;
                break;
            case MotionEvent.ACTION_UP:
                setPressed(false);
                performClick();
                listener.onStopTrackingTouch(this);
                break;
            case MotionEvent.ACTION_CANCEL:
                setPressed(false);
                listener.onStopTrackingTouch(this);
                break;
        }
        return true;
    }

    @Override
    public void setOnSeekBarChangeListener(OnSeekBarChangeListener l) {
        listener = l;
    }

    private int calculateValue(MotionEvent event) {
        double ratio = event.getX() / getWidth();
        int range = getMax() - getMin();
        return (int) Math.round(ratio * range + getMin());
    }

    private void move(int newValue) {
        int move = newValue - lastValue;
        if (move != 0) {
            int to = getProgress() + move;
            if (to >= getMin() && to <= getMax()) setProgress(to);
        }
    }
}
