package uk.org.ngo.squeezer.screensaver;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.WindowManager;
import android.widget.TextClock;

import uk.org.ngo.squeezer.R;

public class Screensaver extends AppCompatActivity {
    private boolean systemBarVisible = true;

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

        TextClock date = findViewById(R.id.date);
        String pattern = DateFormat.getBestDateTimePattern(getResources().getConfiguration().locale, "EdMMM");
        date.setFormat12Hour(pattern);
        date.setFormat24Hour(pattern);
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        finish();
    }

}