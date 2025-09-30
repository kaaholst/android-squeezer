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
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

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
        view.<TextView>findViewById(R.id.website).setMovementMethod(LinkMovementMethod.getInstance());
        view.<TextView>findViewById(R.id.issues).setMovementMethod(LinkMovementMethod.getInstance());
        view.<TextView>findViewById(R.id.privacy).setMovementMethod(LinkMovementMethod.getInstance());

        PackageManager pm = getActivity().getPackageManager();
        PackageInfo info;
        try {
            info = pm.getPackageInfo(getActivity().getPackageName(), 0);
            versionText.setText(info.versionName);
        } catch (NameNotFoundException e) {
            titleText.setText(getString(R.string.app_name));
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getActivity());
        builder.setView(view);
        builder.setPositiveButton(android.R.string.ok, null);
        builder.setNeutralButton(R.string.changelog_full_title, (dialog, which) -> {
            ChangeLogDialog changeLog = new ChangeLogDialog(getActivity(), Squeezer.getPreferences().getSharedPreferences());
            changeLog.getThemedFullLogDialog().show();
        });
        builder.setNegativeButton(R.string.dialog_license, (dialog, which) -> requireActivity().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.license)))));
        return builder.create();
    }
}
