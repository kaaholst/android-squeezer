/*
 * Copyright (c) 2014 Kurt Aaholst <kaaholst@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.org.ngo.squeezer.itemlist;

import android.content.res.Resources;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatCheckedTextView;

import android.text.SpannableString;
import android.text.format.DateFormat;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;


import uk.org.ngo.squeezer.Preferences;
import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.Squeezer;
import uk.org.ngo.squeezer.framework.ItemViewHolder;
import uk.org.ngo.squeezer.model.Alarm;
import uk.org.ngo.squeezer.model.AlarmPlaylist;
import uk.org.ngo.squeezer.util.CompoundButtonWrapper;
import uk.org.ngo.squeezer.util.TimeUtil;
import uk.org.ngo.squeezer.widget.UndoBarController;

public class AlarmView extends ItemViewHolder<Alarm> {
    private static final int[] DAY_TEXT_IDS = {
            R.id.day_sunday, R.id.day_monday, R.id.day_tuesday, R.id.day_wednesday,
            R.id.day_thursday, R.id.day_friday, R.id.day_saturday
    };
    private final Resources mResources;
    private final int mColorSelected;
    private final float mDensity;
    private final boolean is24HourFormat;
    private final TextView time;
    private final TextView amPm;
    private final CompoundButtonWrapper enabled;
    private final AppCompatCheckedTextView repeat;
    private final TextView playlist;
    private final TextView[] dowTexts = new TextView[DAY_TEXT_IDS.length];
    private final CharSequence[] dayTexts = new String[DAY_TEXT_IDS.length];

    public AlarmView(@NonNull AlarmsActivity activity, @NonNull View view) {
        super(activity, view);
        mResources = activity.getResources();
        mColorSelected = mResources.getColor(getActivity().getAttributeValue(R.attr.alarm_dow_selected));
        mDensity = mResources.getDisplayMetrics().density;

        is24HourFormat = DateFormat.is24HourFormat(getActivity());
        time = view.findViewById(R.id.time);
        amPm = view.findViewById(R.id.am_pm);
        amPm.setVisibility(is24HourFormat ? View.GONE : View.VISIBLE);
        enabled = new CompoundButtonWrapper(view.findViewById(R.id.enabled));
        enabled.setOncheckedChangeListener((compoundButton, b) -> {
            if (getActivity().getService() != null) {
                item.setEnabled(b);
                getActivity().getService().alarmEnable(item.getId(), b);
            }
        });

        repeat = view.findViewById(R.id.repeat);
        repeat.setOnClickListener(v -> {
            boolean nowChecked = !repeat.isChecked();
            if (getActivity().getService() != null) {
                item.setRepeat(nowChecked);
                repeat.setChecked(nowChecked);
                getActivity().getService().alarmRepeat(item.getId(), nowChecked);
                activity.getItemAdapter().notifyItemChanged(getBindingAdapterPosition());
            }
        });

        playlist = view.findViewById(R.id.playlist);
        playlist.setOnClickListener(v -> activity.selectAlarmPlaylist(getBindingAdapterPosition()));

        for (int day = 0; day < DAY_TEXT_IDS.length; day++) {
            dowTexts[day] = view.findViewById(DAY_TEXT_IDS[day]);
            dayTexts[day] = dowTexts[day].getText();
            final int finalDay = day;
            dowTexts[day].setOnClickListener(v -> {
                if (getActivity().getService() != null) {
                    boolean wasChecked = item.isDayActive(finalDay);
                    if (wasChecked) {
                        item.clearDay(finalDay);
                        getActivity().getService().alarmRemoveDay(item.getId(), finalDay);
                    } else {
                        item.setDay(finalDay);
                        getActivity().getService().alarmAddDay(item.getId(), finalDay);
                    }
                    setDowText(finalDay);
                }
            });
        }

        View delete = view.findViewById(R.id.delete);
        delete.setOnClickListener(v -> {
            UndoBarController.show(getActivity(), R.string.ALARM_DELETING, new UndoListener(getBindingAdapterPosition(), item));
            activity.getItemAdapter().removeItem(getBindingAdapterPosition());
        });
    }

    @Override
    public AlarmsActivity getActivity() {
        return (AlarmsActivity) super.getActivity();
    }

    @Override
    public void bindView(Alarm item) {
        super.bindView(item);
        long tod = item.getTod();
        int hour = (int) (tod / 3600);
        int minute = (int) ((tod / 60) % 60);

        time.setText(TimeUtil.timeFormat(hour, minute, is24HourFormat));
        time.setOnClickListener(view -> showTimePicker(getActivity(), item, getBindingAdapterPosition(), is24HourFormat));
        amPm.setText(TimeUtil.formatAmPm(hour));
        enabled.setChecked(item.isEnabled());
        repeat.setChecked(item.isRepeat());

        for (AlarmPlaylist alarmPlaylist : getActivity().getAlarmPlaylists()) {
            if (alarmPlaylist.getId() != null && alarmPlaylist.getId().equals(item.getPlayListId())) {
                playlist.setText(alarmPlaylist.getName());
                break;
            }
        }

        for (int day = 0; day < 7; day++) {
            if (item.isRepeat()) {
                dowTexts[day].setVisibility(View.VISIBLE);
                setDowText(day);
            } else {
                dowTexts[day].setVisibility(View.GONE);
            }
        }
    }


    private void setDowText(int day) {
        SpannableString text = new SpannableString(dayTexts[day]);
        if (item.isDayActive(day)) {
            text.setSpan(new StyleSpan(Typeface.BOLD), 0, text.length(), 0);
            text.setSpan(new ForegroundColorSpan(mColorSelected), 0, text.length(), 0);
            Drawable underline = mResources.getDrawable(R.drawable.underline);
            float textSize = (new Paint()).measureText(text.toString());
            underline.setBounds(0, 0, (int) (textSize * mDensity), (int) (1 * mDensity));
            dowTexts[day].setCompoundDrawables(null, null, null, underline);
        } else
            dowTexts[day].setCompoundDrawables(null, null, null, null);
        dowTexts[day].setText(text);
    }

    public static void showTimePicker(AlarmsActivity activity, Alarm alarm, int position, boolean is24HourFormat) {
        Preferences preferences = Squeezer.getPreferences();
        long tod = alarm.getTod();
        MaterialTimePicker picker = new MaterialTimePicker.Builder()
                .setHour((int) (tod / 3600))
                .setMinute((int) ((tod / 60) % 60))
                .setTimeFormat(is24HourFormat ? TimeFormat.CLOCK_24H : TimeFormat.CLOCK_12H)
                .setTitleText(R.string.ALARM_SET_TIME)
                .setInputMode(preferences.getTimeInputMode())
                .build();
        picker.addOnPositiveButtonClickListener(view -> {
            preferences.setTimeInputMode(picker.getInputMode());
            if (activity.getService() != null) {
                int time = (picker.getHour() * 60 + picker.getMinute()) * 60;
                alarm.setTod(time);
                activity.getService().alarmSetTime(alarm.getId(), time);
                if (!alarm.isEnabled()) {
                    alarm.setEnabled(true);
                    activity.getService().alarmEnable(alarm.getId(), true);
                }
                activity.getItemAdapter().notifyItemChanged(position);
            }
        });
        picker.show(activity.getSupportFragmentManager(), AlarmView.class.getSimpleName());

    }

    private class UndoListener implements UndoBarController.UndoListener {
        private final int position;
        private final Alarm alarm;

        public UndoListener(int position, Alarm alarm) {
            this.position = position;
            this.alarm = alarm;
        }

        @Override
        public void onUndo() {
            getActivity().getItemAdapter().insertItem(position, alarm);
        }

        @Override
        public void onDone() {
            if (getActivity().getService() != null) {
                getActivity().getService().alarmDelete(alarm.getId());
            }
        }
    }
}
