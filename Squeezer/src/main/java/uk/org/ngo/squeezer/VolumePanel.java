/*
 * Copyright (c) 2011 Google Inc.  All Rights Reserved.
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


package uk.org.ngo.squeezer;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import uk.org.ngo.squeezer.framework.BaseActivity;
import uk.org.ngo.squeezer.widget.VolumeController;


/**
 * Implement a custom volume toast view
 */
public class VolumePanel extends Handler {

    private static final int TIMEOUT_DELAY = 3000;

    private static final int MSG_VOLUME_CHANGED = 0;

    private static final int MSG_TIMEOUT = 2;

    private final BaseActivity activity;

    /**
     * Dialog displaying the volume panel.
     */
    private final Dialog dialog;

    private final ImageView startIcon;
    private final ProgressBar seekbar;
    private final TextView label;

    @SuppressLint({"InflateParams"}) // OK, as view is passed to Dialog.setView()
    public VolumePanel(BaseActivity activity) {
        super(Looper.getMainLooper());
        this.activity = activity;

        LayoutInflater inflater = LayoutInflater.from(activity);
        View view = inflater.inflate(R.layout.volume_panel, null);

        view.setOnClickListener(v -> {
            dismiss();
            VolumeController.show(activity);
        });
        startIcon = view.findViewById(R.id.slider_down_icon);
        seekbar = view.findViewById(R.id.slider);
        label = view.findViewById(R.id.label);


        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity);
        builder.setView(view);
        builder.setOnDismissListener(dialogInterface -> removeMessages(MSG_TIMEOUT));
        builder.setOnKeyListener((dialogInterface, keyCode, event) -> {
            switch (event.getAction()) {
                case KeyEvent.ACTION_DOWN:
                    return activity.onKeyDown(keyCode, event);
                case KeyEvent.ACTION_UP:
                    return activity.onKeyUp(keyCode, event);
                default:
                    return false;
            }
        });
        dialog = builder.create();
    }

    public void dismiss() {
        if (dialog.isShowing()) {
            dialog.dismiss();
        }
    }

    private void resetTimeout() {
        removeMessages(MSG_TIMEOUT);
        sendMessageDelayed(obtainMessage(MSG_TIMEOUT), TIMEOUT_DELAY);
    }

    public void postVolumeChanged(boolean muted, int newVolume, String additionalMessage) {
        if (hasMessages(MSG_VOLUME_CHANGED)) {
            return;
        }
        obtainMessage(MSG_VOLUME_CHANGED, muted ? 1 : 0, newVolume, additionalMessage).sendToTarget();
    }

    private void onShowVolumeChanged(boolean muted, int newVolume, String additionalMessage) {
       // TODO  mute.setChecked(muted);
        startIcon.setImageResource(muted ? R.drawable.ic_volume_off : R.drawable.ic_volume_down);
        seekbar.setProgress(newVolume);
        label.setText(additionalMessage);

        if (!dialog.isShowing() && !activity.isFinishing()) {
            dialog.show();
        }

        resetTimeout();
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case MSG_VOLUME_CHANGED: {
                onShowVolumeChanged(msg.arg1 != 0, msg.arg2, (String) msg.obj);
                break;
            }

            case MSG_TIMEOUT: {
                dismiss();
                break;
            }
        }
    }
}

