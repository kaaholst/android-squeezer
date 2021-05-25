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
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;

import android.text.SpannableString;
import android.text.format.DateFormat;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import java.text.DateFormatSymbols;
import java.util.List;


import uk.org.ngo.squeezer.Preferences;
import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.Util;
import uk.org.ngo.squeezer.framework.BaseListActivity;
import uk.org.ngo.squeezer.framework.ItemViewHolder;
import uk.org.ngo.squeezer.model.Alarm;
import uk.org.ngo.squeezer.model.AlarmPlaylist;
import uk.org.ngo.squeezer.util.CompoundButtonWrapper;
import uk.org.ngo.squeezer.widget.UndoBarController;

public class AlarmView extends ItemViewHolder<Alarm> {
    private final AlarmsActivity mActivity;
    private final Resources mResources;
    private final int mColorSelected;
    private final float mDensity;
    private final boolean is24HourFormat;
    private final String timeFormat;
    private final String am;
    private final String pm;
    private Alarm alarm;
    private final TextView time;
    private final TextView amPm;
    private final CompoundButtonWrapper enabled;
    private final CompoundButtonWrapper repeat;
    private final Button delete;
    private final Spinner playlist;
    private final LinearLayout dowHolder;
    private final TextView[] dowTexts = new TextView[7];

    // Only used for the loading view, is null otherwise
    private final TextView text1;

    public AlarmView(@NonNull AlarmsActivity activity, @NonNull View view) {
        super(activity, view);
        mActivity = activity;
        mResources = activity.getResources();
        mColorSelected = mResources.getColor(getActivity().getAttributeValue(R.attr.alarm_dow_selected));
        mDensity = mResources.getDisplayMetrics().density;

        is24HourFormat = DateFormat.is24HourFormat(getActivity());
        timeFormat = is24HourFormat ? "%02d:%02d" : "%d:%02d";
        String[] amPmStrings = new DateFormatSymbols().getAmPmStrings();
        am = amPmStrings[0];
        pm = amPmStrings[1];
        time = view.findViewById(R.id.time);
        amPm = view.findViewById(R.id.am_pm);
        amPm.setVisibility(is24HourFormat ? View.GONE : View.VISIBLE);
        enabled = new CompoundButtonWrapper(view.findViewById(R.id.enabled));
        enabled.setOncheckedChangeListener((compoundButton, b) -> {
            if (getActivity().getService() != null) {
                alarm.setEnabled(b);
                getActivity().getService().alarmEnable(alarm.getId(), b);
            }
        });
        repeat = new CompoundButtonWrapper(view.findViewById(R.id.repeat));
        dowHolder = view.findViewById(R.id.dow);
        repeat.setOncheckedChangeListener((compoundButton, b) -> {
            if (getActivity().getService() != null) {
                alarm.setRepeat(b);
                getActivity().getService().alarmRepeat(alarm.getId(), b);
                dowHolder.setVisibility(b ? View.VISIBLE : View.GONE);
            }
        });
        delete = view.findViewById(R.id.delete);
        playlist = view.findViewById(R.id.playlist);
        for (int day = 0; day < 7; day++) {
            ViewGroup dowButton = (ViewGroup) dowHolder.getChildAt(day);
            final int finalDay = day;
            dowButton.setOnClickListener(v -> {
                if (getActivity().getService() != null) {
                    boolean wasChecked = alarm.isDayActive(finalDay);
                    if (wasChecked) {
                        alarm.clearDay(finalDay);
                        getActivity().getService().alarmRemoveDay(alarm.getId(), finalDay);
                    } else {
                        alarm.setDay(finalDay);
                        getActivity().getService().alarmAddDay(alarm.getId(), finalDay);
                    }
                    setDowText(finalDay);
                }
            });
            dowTexts[day] = (TextView) dowButton.getChildAt(0);
        }
        delete.setOnClickListener(v -> {
            UndoBarController.show(getActivity(), R.string.ALARM_DELETING, new UndoListener(getAdapterPosition(), alarm));
            mActivity.getItemAdapter().removeItem(getAdapterPosition());
        });
        playlist.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
                final AlarmPlaylist selectedAlarmPlaylist = mActivity.getAlarmPlaylists().get(position);
                if (getActivity().getService() != null &&
                        selectedAlarmPlaylist.getId() != null &&
                        !selectedAlarmPlaylist.getId().equals(alarm.getPlayListId())) {
                    alarm.setPlayListId(selectedAlarmPlaylist.getId());
                    getActivity().getService().alarmSetPlaylist(alarm.getId(), selectedAlarmPlaylist);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        // For the loading view (otherwise the view won't have a R.id.text1)
        text1 = view.findViewById(R.id.text1);
    }

    @Override
    public void bindView(Alarm item) {
        long tod = item.getTod();
        int hour = (int) (tod / 3600);
        int minute = (int) ((tod / 60) % 60);
        int displayHour = hour;
        if (!is24HourFormat) {
            displayHour = displayHour % 12;
            if (displayHour == 0) displayHour = 12;
        }

        alarm = item;
        time.setText(String.format(timeFormat, displayHour, minute));
        BaseListActivity activity = (BaseListActivity) getActivity();
        time.setOnClickListener(view -> showTimePicker(activity, item, is24HourFormat));
        amPm.setText(hour < 12 ? am : pm);
        enabled.setChecked(item.isEnabled());
        repeat.setChecked(item.isRepeat());
        if (!mActivity.getAlarmPlaylists().isEmpty()) {
            List<AlarmPlaylist> alarmPlaylists = mActivity.getAlarmPlaylists();
            playlist.setAdapter(new AlarmPlaylistSpinnerAdapter(alarmPlaylists));
            for (int i = 0; i < alarmPlaylists.size(); i++) {
                AlarmPlaylist alarmPlaylist = alarmPlaylists.get(i);
                if (alarmPlaylist.getId() != null && alarmPlaylist.getId().equals(item.getPlayListId())) {
                    playlist.setSelection(i);
                    break;
                }
            }

        }

        dowHolder.setVisibility(item.isRepeat() ? View.VISIBLE : View.GONE);
        for (int day = 0; day < 7; day++) {
            setDowText(day);
        }
    }

    @Override
    public void bindView(String text) {
        text1.setText(text);
    }


    private void setDowText(int day) {
        SpannableString text = new SpannableString(getAlarmShortDayText(day));
        if (alarm.isDayActive(day)) {
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

    private CharSequence getAlarmShortDayText(int day) {
        switch (day) {
            default: return getActivity().getString(R.string.ALARM_SHORT_DAY_0);
            case 1: return getActivity().getString(R.string.ALARM_SHORT_DAY_1);
            case 2: return getActivity().getString(R.string.ALARM_SHORT_DAY_2);
            case 3: return getActivity().getString(R.string.ALARM_SHORT_DAY_3);
            case 4: return getActivity().getString(R.string.ALARM_SHORT_DAY_4);
            case 5: return getActivity().getString(R.string.ALARM_SHORT_DAY_5);
            case 6: return getActivity().getString(R.string.ALARM_SHORT_DAY_6);
        }
    }

    public static void showTimePicker(BaseListActivity activity, Alarm alarm, boolean is24HourFormat) {
        Preferences preferences = new Preferences(activity);
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
                activity.getItemAdapter().notifyDataSetChanged();
            }
        });
        picker.show(activity.getSupportFragmentManager(), AlarmView.class.getSimpleName());

    }

    private class AlarmPlaylistSpinnerAdapter extends ArrayAdapter<AlarmPlaylist> {
        private final List<AlarmPlaylist> alarmPlaylists;

        public AlarmPlaylistSpinnerAdapter(List<AlarmPlaylist> alarmPlaylists) {
            super(getActivity(), android.R.layout.simple_spinner_dropdown_item, alarmPlaylists);
            this.alarmPlaylists = alarmPlaylists;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public boolean isEnabled(int position) {
            return (alarmPlaylists.get(position).getId() != null);
        }

        @Override
        public @NonNull View getView(int position, View convertView, @NonNull ViewGroup parent) {
           return Util.getSpinnerItemView(getActivity(), convertView, parent, getItem(position).getName());
        }

        @Override
        public View getDropDownView(int position, View convertView, @NonNull ViewGroup parent) {
            if (!isEnabled(position)) {
                FrameLayout view = (FrameLayout) getActivity().getLayoutInflater().inflate(R.layout.alarm_playlist_category_dropdown_item, parent, false);
                CheckedTextView spinnerItemView = view.findViewById(R.id.text);
                spinnerItemView.setText(getItem(position).getCategory());
                spinnerItemView.setTypeface(spinnerItemView.getTypeface(), Typeface.BOLD);
                // Hide the checkmark for headings.
                spinnerItemView.setCheckMarkDrawable(new ColorDrawable(Color.TRANSPARENT));
                return view;
            } else {
                FrameLayout view = (FrameLayout) getActivity().getLayoutInflater().inflate(R.layout.alarm_playlist_dropdown_item, parent, false);
                TextView spinnerItemView = view.findViewById(R.id.text);
                spinnerItemView.setText(getItem(position).getName());
                return view;
            }
        }
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
            mActivity.getItemAdapter().insertItem(position, alarm);
        }

        @Override
        public void onDone() {
            if (mActivity.getService() != null) {
                mActivity.getService().alarmDelete(alarm.getId());
            }
        }
    }
}
