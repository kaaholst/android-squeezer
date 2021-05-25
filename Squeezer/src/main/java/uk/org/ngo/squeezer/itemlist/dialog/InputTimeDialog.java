package uk.org.ngo.squeezer.itemlist.dialog;

import android.text.format.DateFormat;

import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import java.util.Calendar;

import uk.org.ngo.squeezer.Preferences;
import uk.org.ngo.squeezer.framework.BaseActivity;
import uk.org.ngo.squeezer.model.JiveItem;

public class InputTimeDialog {
    public static void show(BaseActivity activity, JiveItem item, int alreadyPopped) {
        int hour;
        int minute;
        try {
            int tod = Integer.parseInt(item.input.initialText);
            hour = tod / 3600;
            minute = (tod / 60) % 60;
        } catch (NumberFormatException nfe) {
            // Fall back to current time as the default values for the picker
            final Calendar c = Calendar.getInstance();
            hour = c.get(Calendar.HOUR_OF_DAY);
            minute = c.get(Calendar.MINUTE);
        }

        Preferences preferences = new Preferences(activity);
        MaterialTimePicker picker = new MaterialTimePicker.Builder()
                .setHour(hour)
                .setMinute(minute)
                .setTimeFormat(DateFormat.is24HourFormat(activity) ? TimeFormat.CLOCK_24H : TimeFormat.CLOCK_12H)
                .setTitleText(item.getName())
                .setInputMode(preferences.getTimeInputMode())
                .build();
        picker.addOnPositiveButtonClickListener(view -> {
            preferences.setTimeInputMode(picker.getInputMode());
            item.inputValue = String.valueOf((picker.getHour() * 60 + picker.getMinute()) * 60);
            activity.action(item, item.goAction, alreadyPopped);
        });
        picker.show(activity.getSupportFragmentManager(), InputTimeDialog.class.getSimpleName());
    }
}
