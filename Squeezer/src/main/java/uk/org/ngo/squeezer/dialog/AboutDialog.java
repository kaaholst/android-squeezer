/*
 * Copyright (c) 2012 Google Inc.  All Rights Reserved.
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

package uk.org.ngo.squeezer.dialog;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import uk.org.ngo.squeezer.BuildConfig;
import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.Squeezer;

public class AboutDialog extends DialogFragment {
    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        @SuppressLint({"InflateParams"})
        final View view = getActivity().getLayoutInflater().inflate(R.layout.about_dialog, null);
        final TextView titleText = view.findViewById(R.id.about_title);
        final TextView versionText = view.findViewById(R.id.version_text);

        PackageManager pm = getActivity().getPackageManager();
        PackageInfo info;
        try {
            info = pm.getPackageInfo(getActivity().getPackageName(), 0);
            if (BuildConfig.DEBUG) {
                versionText.setText(info.versionName + ' ' + BuildConfig.GIT_DESCRIPTION);
            } else {
                versionText.setText(info.versionName);
            }
        } catch (NameNotFoundException e) {
            titleText.setText(getString(R.string.app_name));
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getActivity());
        builder.setView(view);
        builder.setPositiveButton(android.R.string.ok, null);
        builder.setNeutralButton(R.string.changelog_full_title, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                ChangeLogDialog changeLog = new ChangeLogDialog(getActivity(), Squeezer.getPreferences().getSharedPreferences());
                changeLog.getThemedFullLogDialog().show();
            }
        });
        builder.setNegativeButton(R.string.dialog_license, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                new LicenseDialog()
                        .show(getActivity().getSupportFragmentManager(), "LicenseDialog");
            }
        });
        return builder.create();
    }
}
