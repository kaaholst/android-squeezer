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
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Message;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.sdsmdg.harjot.crollerTest.Croller;
import com.sdsmdg.harjot.crollerTest.OnCrollerChangeListener;

import uk.org.ngo.squeezer.framework.BaseActivity;
import uk.org.ngo.squeezer.service.ISqueezeService;


/**
 * Implement a custom volume toast view
 */
public class VolumePanel extends Handler implements OnCrollerChangeListener {

    private static final int TIMEOUT_DELAY = 3000;

    private static final int MSG_VOLUME_CHANGED = 0;

    private static final int MSG_TIMEOUT = 2;

    private final BaseActivity activity;

    /**
     * Dialog displaying the volume panel.
     */
    private final Dialog dialog;

    /**
     * View displaying volume sliders.
     */
    private final View view;

    private final TextView label;

    private final CheckBox mute;
    private final Croller seekbar;
    private int currentProgress = 0;
    private boolean trackingTouch = false;

    @SuppressLint({"InflateParams"}) // OK, as view is passed to Dialog.setView()
    public VolumePanel(BaseActivity activity) {
        this.activity = activity;

        LayoutInflater inflater = (LayoutInflater) activity
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        view = inflater.inflate(R.layout.volume_adjust, null);
        GradientDrawable background = (GradientDrawable) ContextCompat.getDrawable(view.getContext(), R.drawable.panel_background);
        background.setColor(view.getResources().getColor(activity.getAttributeValue(R.attr.colorSurface)));
        background.setStroke(
                activity.getResources().getDimensionPixelSize(R.dimen.volume_panel_border_Width),
                view.getResources().getColor(activity.getAttributeValue(R.attr.colorPrimary))
        );
        view.setBackground(background);
        view.setOnTouchListener((v, event) -> {
            resetTimeout();
            if (event.getAction() == MotionEvent.ACTION_UP) {
                v.performClick();
            }
            return false;
        });

        label = view.findViewById(R.id.label);

        seekbar = view.findViewById(R.id.level);
        seekbar.setOnCrollerChangeListener(this);

        mute = view.findViewById(R.id.mute);
        mute.setOnClickListener(v -> {
            resetTimeout();
            activity.getService().toggleMute();
        });

        dialog = new Dialog(view.getContext(), R.style.VolumePanel) { //android.R.style.Theme_Panel) {
            @Override
            public boolean onTouchEvent(@NonNull MotionEvent event) {
                if (isShowing() && event.getAction() == MotionEvent.ACTION_OUTSIDE) {
                    forceTimeout();
                    return true;
                }
                return false;
            }
        };
        dialog.setTitle("Volume Control");
        dialog.setContentView(view);

        // Set window properties to match other toasts/dialogs.
        Window window = dialog.getWindow();
        window.setGravity(Gravity.TOP);
        WindowManager.LayoutParams lp = window.getAttributes();
        lp.token = null;
        lp.y = activity.getResources().getDimensionPixelSize(R.dimen.volume_panel_top_margin);
        lp.width = WindowManager.LayoutParams.WRAP_CONTENT;
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
        window.setAttributes(lp);
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH);
    }

    public void dismiss() {
        removeMessages(MSG_TIMEOUT);
        if (dialog.isShowing()) {
            dialog.dismiss();
        }
    }

    private void resetTimeout() {
        removeMessages(MSG_TIMEOUT);
        sendMessageDelayed(obtainMessage(MSG_TIMEOUT), TIMEOUT_DELAY);
    }

    private void forceTimeout() {
        removeMessages(MSG_TIMEOUT);
        sendMessage(obtainMessage(MSG_TIMEOUT));
    }

    @Override
    public void onProgressChanged(Croller croller, int progress) {
        if (currentProgress != progress) {
            currentProgress = progress;
            ISqueezeService service = activity.getService();
            if (service != null) {
                service.adjustVolumeTo(progress);
            }
        }
    }

    @Override
    public void onStartTrackingTouch(Croller croller) {
        trackingTouch = true;
        removeMessages(MSG_TIMEOUT);
    }

    @Override
    public void onStopTrackingTouch(Croller croller) {
        trackingTouch = false;
        resetTimeout();
    }

    public void postVolumeChanged(boolean muted, int newVolume, String additionalMessage) {
        if (hasMessages(MSG_VOLUME_CHANGED)) {
            return;
        }
        obtainMessage(MSG_VOLUME_CHANGED, muted ? 1 : 0, newVolume, additionalMessage).sendToTarget();
    }

    private void onShowVolumeChanged(boolean muted, int newVolume, String additionalMessage) {
        if (trackingTouch) {
            return;
        }

        mute.setChecked(muted);
        currentProgress = newVolume;
        seekbar.setProgress(newVolume);
        label.setText(additionalMessage);

        seekbar.setIndicatorColor(ColorUtils.setAlphaComponent(seekbar.getIndicatorColor(), muted ? 63 : 255));
        seekbar.setProgressPrimaryColor(ColorUtils.setAlphaComponent(seekbar.getProgressPrimaryColor(), muted ? 63 : 255));
        seekbar.setProgressSecondaryColor(ColorUtils.setAlphaComponent(seekbar.getProgressSecondaryColor(), muted ? 63 : 255));
        seekbar.setOnCrollerChangeListener(muted ? null : this);
        seekbar.setOnTouchListener(muted ? (view, motionEvent) -> true : null);

        if (!dialog.isShowing() && !activity.isFinishing()) {
            dialog.setContentView(view);
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

