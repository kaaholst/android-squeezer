/*
 * Copyright (c) 2014 Google Inc.  All Rights Reserved.
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

package uk.org.ngo.squeezer.util;


import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.StringRes;
import androidx.annotation.StyleRes;

import uk.org.ngo.squeezer.Preferences;
import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.Squeezer;
import uk.org.ngo.squeezer.framework.EnumWithText;

/**
 * Manage the user's choice of theme and ensure that activities respect it.
 * <p>
 * Call {@link #onCreate(Activity)} from the activity's own {@code onCreate()} method to
 * apply the theme when the activity starts.
 * <p>
 * Call {@link #onResume(Activity)} from the activity's own {@code onResume()} method to
 * ensure that any changes to the user's theme preference are handled.
 */
public class ThemeManager {
    /** The current theme applied to the app. */
    @StyleRes
    private int mCurrentTheme;

    /** Available themes. */
    public enum Theme implements EnumWithText {
        LIGHT_DARKACTIONBAR(R.string.settings_theme_light_dark, R.style.AppTheme_Light_DarkActionBar),
        DARK(R.string.settings_theme_dark, R.style.AppTheme);

        @StringRes private final int mLabelId;
        @StyleRes public final int mThemeId;

        Theme(@StringRes int labelId, @StyleRes int themeId) {
            mLabelId = labelId;
            mThemeId = themeId;
        }

        @Override
        public String getText(Context context) {
            return context.getString(mLabelId);
        }
    }

    /**
     * Call this from each activity's onCreate() method before setContentView() or similar
     * is called.
     * <p>
     * Generally, this means immediately after calling {@code super.onCreate()}.
     *
     * @param activity The activity to be themed.
     */
    public void onCreate(Activity activity) {
        // Ensure the activity uses the correct theme.
        mCurrentTheme = getThemePreference(activity);
        activity.setTheme(mCurrentTheme);
    }

    /**
     * Call this from each activity's onResume() method before doing any other work to
     * resume the activity. If the theme has changed since the activity was paused this
     * method will restart the activity.
     *
     * @param activity The activity being themed.
     */
    public void onResume(Activity activity) {
        // Themes can only be applied before views are instantiated.  If the current theme
        // changed while this activity was paused (e.g., because the user went to the
        // SettingsActivity and changed it) then restart this activity with the new theme.
        if (mCurrentTheme != getThemePreference(activity)) {
            Intent intent = activity.getIntent();
            activity.finish();
            activity.overridePendingTransition(0, 0);
            activity.startActivity(intent);
            activity.overridePendingTransition(0, 0);
        }
    }

    /**
     * @return The application's default theme if the user did not choose one.
     */
    public static Theme getDefaultTheme() {
        return Theme.DARK;
    }

    /**
     * Retrieve the user's theme preference.
     *
     * @return A resource identifier for the user's chosen theme.
     */
    private int getThemePreference(Activity activity) {
        try {
            Theme theme = Theme.valueOf(Squeezer.getPreferences().getTheme());
            return theme.mThemeId;
        } catch (Exception e) {
            return getDefaultTheme().mThemeId;
        }
    }
}
