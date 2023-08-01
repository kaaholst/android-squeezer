package uk.org.ngo.squeezer.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.util.AttributeSet;

import uk.org.ngo.squeezer.R;

public class Croller extends com.sdsmdg.harjot.crollerTest.Croller {
    final int textSize;

    public Croller(Context context) {
        super(context);
        textSize = initTextSize();
    }

    public Croller(Context context, AttributeSet attrs) {
        super(context, attrs);
        textSize = initTextSize();
    }

    public Croller(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        textSize = initTextSize();
    }

    private int initTextSize() {
        int[] textSizeAttr = new int[] { android.R.attr.textSize };
        TypedArray a = getContext().obtainStyledAttributes(R.style.SqueezerTextAppearance_Medium, textSizeAttr);
        int textSize = a.getDimensionPixelSize(0, -1);
        a.recycle();
        return textSize;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        setIndicatorWidth((float) ((float) getWidth() / 64.0));
        setLabelSize(textSize);
        super.onDraw(canvas);
    }
}
