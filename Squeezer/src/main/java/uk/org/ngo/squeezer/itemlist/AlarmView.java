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
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatCheckedTextView;

import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import java.util.List;


import uk.org.ngo.squeezer.Preferences;
import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.Squeezer;
import uk.org.ngo.squeezer.framework.BaseListActivity;
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
    private final AlarmsActivity mActivity;
    private final Resources mResources;
    private final int mColorSelected;
    private final float mDensity;
    private final boolean is24HourFormat;
    private final TextView time;
    private final TextView amPm;
    private final CompoundButtonWrapper enabled;
    private final AppCompatCheckedTextView repeat;
    private final Spinner playlist;
    private final TextView[] dowTexts = new TextView[DAY_TEXT_IDS.length];

    public AlarmView(@NonNull AlarmsActivity activity, @NonNull View view) {
        super(activity, view);
        mActivity = activity;
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
                activity.getItemAdapter().notifyItemChanged(getAbsoluteAdapterPosition());
            }
        });
        playlist = view.findViewById(R.id.playlist);
        for (int day = 0; day < DAY_TEXT_IDS.length; day++) {
            dowTexts[day] = view.findViewById(DAY_TEXT_IDS[day]);
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
            UndoBarController.show(getActivity(), R.string.ALARM_DELETING, new UndoListener(getAbsoluteAdapterPosition(), item));
            mActivity.getItemAdapter().removeItem(getAbsoluteAdapterPosition());
        });
        playlist.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                final AlarmPlaylist selectedAlarmPlaylist = mActivity.getAlarmPlaylists().get(position);
                if (getActivity().getService() != null &&
                        !TextUtils.equals(selectedAlarmPlaylist.getId(), item.getPlayListId())) {
                    item.setPlayListId(selectedAlarmPlaylist.getId());
                    getActivity().getService().alarmSetPlaylist(item.getId(), selectedAlarmPlaylist);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    @Override
    public void bindView(Alarm item) {
        super.bindView(item);
        long tod = item.getTod();
        int hour = (int) (tod / 3600);
        int minute = (int) ((tod / 60) % 60);

        time.setText(TimeUtil.timeFormat(hour, minute, is24HourFormat));
        BaseListActivity activity = (BaseListActivity) getActivity();
        time.setOnClickListener(view -> showTimePicker(activity, item, is24HourFormat));
        amPm.setText(TimeUtil.formatAmPm(hour));
        enabled.setChecked(item.isEnabled());
        repeat.setChecked(item.isRepeat());

        List<AlarmPlaylist> alarmPlaylists = mActivity.getAlarmPlaylists();
        if (!alarmPlaylists.isEmpty()) {
            playlist.setAdapter(new AlarmPlaylistSpinnerAdapter(alarmPlaylists));
            for (int i = 0; i < alarmPlaylists.size(); i++) {
                AlarmPlaylist alarmPlaylist = alarmPlaylists.get(i);
                if (alarmPlaylist.getId() != null && alarmPlaylist.getId().equals(item.getPlayListId())) {
                    playlist.setSelection(i);
                    break;
                }
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
        SpannableString text = new SpannableString(dowTexts[day].getText());
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

    public static void showTimePicker(BaseListActivity activity, Alarm alarm, boolean is24HourFormat) {
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
                activity.getItemAdapter().notifyDataSetChanged();
            }
        });
        picker.show(activity.getSupportFragmentManager(), AlarmView.class.getSimpleName());

    }

    private class AlarmPlaylistSpinnerAdapter extends ArrayAdapter<AlarmPlaylistAdapterWrapper> {
        public AlarmPlaylistSpinnerAdapter(List<AlarmPlaylist> alarmPlaylists) {
            super(getActivity(), R.layout.alarm_playlist_item, R.id.label);
            setDropDownViewResource(R.layout.alarm_playlist_dropdown_item);
            for (AlarmPlaylist p : alarmPlaylists) {
                add(new AlarmPlaylistAdapterWrapper(p));
            }
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public boolean isEnabled(int position) {
            return getItem(position).playlist.getId() != null;
        }

        @Override
        public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            boolean isSelectable = isEnabled(position);
            View view = super.getDropDownView(position, convertView, parent);
            view.findViewById(R.id.indent).setVisibility(isSelectable ? View.VISIBLE : View.GONE);
            final TextView label = view.findViewById(R.id.label);
            if (!isSelectable) {
                SpannableStringBuilder spannable = new SpannableStringBuilder(label.getText());
                spannable.setSpan(new StyleSpan(Typeface.BOLD), 0, spannable.length(), 0);
                label.setText(spannable);
            }
            return view;
        }
    }

    private static class AlarmPlaylistAdapterWrapper {
        final AlarmPlaylist playlist;
        AlarmPlaylistAdapterWrapper(AlarmPlaylist p) {
            playlist = p;
        }
        @Override
        public String toString() {
            return playlist.getId() != null ? playlist.getName() : playlist.getCategory();
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
