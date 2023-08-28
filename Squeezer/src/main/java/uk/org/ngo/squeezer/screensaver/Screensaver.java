package uk.org.ngo.squeezer.screensaver;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.WindowManager;
import android.widget.TextClock;

import uk.org.ngo.squeezer.R;

public class Screensaver extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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