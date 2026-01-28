package uk.org.ngo.squeezer.screensaver;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.RelativeLayout;
import android.widget.TextClock;

import java.util.Random;

import uk.org.ngo.squeezer.R;

public class Screensaver extends AppCompatActivity {
    private static final long MOVE_INTERVAL = 10 * 60 * 1000; // 10 minutes

    private boolean systemBarVisible = true;
    private final Handler moveHandler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private View clockContainer;
    private ViewGroup screensaverContainer;

    private final Runnable moveRunnable = new Runnable() {
        @Override
        public void run() {
            moveClock();
            moveHandler.postDelayed(this, MOVE_INTERVAL);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        WindowInsetsControllerCompat windowInsetsController = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars());
        super.onCreate(savedInstanceState);
        ViewCompat.setOnApplyWindowInsetsListener(getWindow().getDecorView(), (view, insets) -> {
            boolean systemBarVisible = insets.isVisible(WindowInsetsCompat.Type.navigationBars()) || insets.isVisible(WindowInsetsCompat.Type.statusBars());
            if (systemBarVisible && !this.systemBarVisible) finish();
            this.systemBarVisible = systemBarVisible;
            return WindowInsetsCompat.CONSUMED;
        });
        setContentView(R.layout.clock);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        screensaverContainer = findViewById(R.id.screensaver_container);
        clockContainer = findViewById(R.id.clock_container);

        TextClock date = findViewById(R.id.date);
        String pattern = DateFormat.getBestDateTimePattern(getResources().getConfiguration().locale, "EdMMM");
        date.setFormat12Hour(pattern);
        date.setFormat24Hour(pattern);

        moveHandler.postDelayed(moveRunnable, MOVE_INTERVAL);
    }

    private void moveClock() {
        int paddingLeft = screensaverContainer.getPaddingLeft();
        int paddingRight = screensaverContainer.getPaddingRight();
        int paddingTop = screensaverContainer.getPaddingTop();
        int paddingBottom = screensaverContainer.getPaddingBottom();

        int usableWidth = screensaverContainer.getWidth() - paddingLeft - paddingRight;
        int usableHeight = screensaverContainer.getHeight() - paddingTop - paddingBottom;

        int clockWidth = clockContainer.getWidth();
        int clockHeight = clockContainer.getHeight();

        if (clockWidth > 0 && clockHeight > 0 && usableWidth > clockWidth && usableHeight > clockHeight) {
            int maxWidth = usableWidth - clockWidth;
            int maxHeight = usableHeight - clockHeight;

            int randomX = random.nextInt(maxWidth);
            int randomY = random.nextInt(maxHeight);

            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) clockContainer.getLayoutParams();
            params.removeRule(RelativeLayout.CENTER_IN_PARENT);
            params.leftMargin = randomX;
            params.topMargin = randomY;
            clockContainer.setLayoutParams(params);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        moveHandler.removeCallbacks(moveRunnable);
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        finish();
    }

}