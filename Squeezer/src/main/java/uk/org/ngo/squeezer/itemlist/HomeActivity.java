/*
 * Copyright (c) 2009 Google Inc.  All Rights Reserved.
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


import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.MainThread;
import androidx.preference.PreferenceManager;

import uk.org.ngo.squeezer.Preferences;
import uk.org.ngo.squeezer.Squeezer;
import uk.org.ngo.squeezer.dialog.ChangeLogDialog;
import uk.org.ngo.squeezer.dialog.TipsDialog;
import uk.org.ngo.squeezer.model.JiveItem;
import uk.org.ngo.squeezer.service.event.HandshakeComplete;

public class HomeActivity extends HomeMenuActivity {
    public static final String TAG = "HomeActivity";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        getIntent().putExtra(JiveItem.class.getName(), JiveItem.HOME);
        super.onCreate(savedInstanceState);

        // Show the change log if necessary.
        Squeezer.getInstance().doInBackground(() -> {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(HomeActivity.this);
            runOnUiThread(() -> {
                ChangeLogDialog changeLog = new ChangeLogDialog(this, preferences);
                if (changeLog.isFirstRun()) {
                    if (changeLog.isFirstRunEver()) {
                        changeLog.skipLogDialog();
                    } else {
                        changeLog.getThemedLogDialog().show();
                    }
                }
            });
        });
    }

    @MainThread
    public void onEventMainThread(HandshakeComplete event) {
        Log.d(TAG, "Handshake complete");
        super.onEventMainThread(event);

        // Show a tip about volume controls, if this is the first time this app
        // has run. TODO: Add more robust and general 'tips' functionality.
        PackageInfo pInfo;
        try {
            final Preferences preferences = Squeezer.getPreferences();

            pInfo = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_META_DATA);
            if (preferences.getLastRunVersionCode() == 0) {
                new TipsDialog().show(getSupportFragmentManager(), "TipsDialog");
                preferences.setLastRunVersionCode(pInfo.versionCode);
            }
        } catch (PackageManager.NameNotFoundException e) {
            // Nothing to do, don't crash.
        }
    }

    public static void show(Context context) {
        Intent intent = new Intent(context, HomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (!(context instanceof Activity))
            intent = intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        context.startActivity(intent);
        if (context instanceof Activity) {
            ((Activity) context).overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }
    }

}
