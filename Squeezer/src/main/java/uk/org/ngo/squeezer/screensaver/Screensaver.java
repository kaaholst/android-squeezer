package uk.org.ngo.squeezer.screensaver;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import java.util.Calendar;

import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.util.Intents;
import uk.org.ngo.squeezer.util.ThemeManager;
import uk.org.ngo.squeezer.util.TimeUtil;

public class Screensaver extends AppCompatActivity {
    private static final int DATE_FORMAT = (DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_ALL | DateUtils.FORMAT_NO_YEAR);
    public static final String CLOCK_ACTION = Screensaver.class.getName();

    private final ThemeManager mTheme = new ThemeManager();

    private TextView time;
    private TextView amPm;
    private TextView date;
    private boolean is24HourFormat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.clock);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        time = findViewById(R.id.time);
        amPm = findViewById(R.id.am_pm);
        date = findViewById(R.id.date);
        is24HourFormat = DateFormat.is24HourFormat(this);
        amPm.setVisibility(is24HourFormat ? View.GONE : View.VISIBLE);
        bindView();

        registerReceiver(broadcastReceiver, new IntentFilter(CLOCK_ACTION));
        setNextAlarm();
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            setNextAlarm();
        } else {
            finish();
        }
    }

    private void bindView() {
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);
        time.setText(TimeUtil.timeFormat(hour, minute, is24HourFormat));

        amPm.setText(TimeUtil.formatAmPm(hour));

        date.setText(DateUtils.formatDateTime(this, calendar.getTimeInMillis(), DATE_FORMAT));
    }

    private void setNextAlarm() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM));
        }
        PendingIntent broadcast = PendingIntent.getBroadcast(this, 0, new Intent(CLOCK_ACTION), Intents.immutablePendingIntent());

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE, 1);
        calendar.set(Calendar.SECOND, 0);

        alarmManager.setExact(
                AlarmManager.RTC,
                calendar.getTimeInMillis(),
                broadcast
        );
    }

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            setNextAlarm();
            bindView();
        }
    };

}