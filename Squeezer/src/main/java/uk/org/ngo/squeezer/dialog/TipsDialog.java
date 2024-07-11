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
import android.content.DialogInterface.OnKeyListener;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import android.view.KeyEvent;
import android.view.View;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import uk.org.ngo.squeezer.R;

public class TipsDialog extends DialogFragment implements OnKeyListener {

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        @SuppressLint({"InflateParams"})
        final View view = getActivity().getLayoutInflater().inflate(R.layout.tips_dialog, null);

        return new MaterialAlertDialogBuilder(getActivity())
            .setView(view)
            .setPositiveButton(android.R.string.ok, null)
            .setOnKeyListener(this)
            .create();
    }

    /*
     * Intercept hardware volume control keys to control slimserver volume.
     * 
     * Change the volume when the key is depressed. Suppress the keyUp event,
     * otherwise you get a notification beep as well as the volume changing.
     * 
     * TODO: Do this for all the dialog.
     */
    @Override
    public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                return getActivity().onKeyDown(keyCode, event);
        }

        return false;
    }
}
