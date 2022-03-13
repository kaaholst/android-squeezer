/*
 * Copyright (c) 2021 Kurt Aaholst.  All Rights Reserved.
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

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.app.Dialog;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import uk.org.ngo.squeezer.dialog.CuePanelSettings;
import uk.org.ngo.squeezer.service.ISqueezeService;


/**
 * Implement a custom fast forward / rewind toast view
 */
public class CuePanel extends Handler {

    private static final int TIMEOUT_DELAY = 3000;
    private static final int FADE_IN_TIME = 200;

    private static final int MSG_TIMEOUT = 1;

    private final FragmentActivity activity;
    @NonNull
    private final View parent;
    private final Dialog dialog;

    public CuePanel(FragmentActivity activity, @NonNull View parent, @NonNull ISqueezeService service) {
        super(Looper.getMainLooper());
        this.parent = parent;
        this.activity = activity;
        Preferences preferences = new Preferences(activity);
        int backwardSeconds = preferences.getBackwardSeconds();
        int forwardSeconds = preferences.getForwardSeconds();

        final View view = View.inflate(parent.getContext(), R.layout.cue_panel, null);
        ((Button)view.findViewById(R.id.backward)).setText(activity.getString(R.string.backward, backwardSeconds));
        view.findViewById(R.id.backward).setOnClickListener(view1 -> adjustSecondsElapsed(service, -backwardSeconds));
        ((Button)view.findViewById(R.id.forward)).setText(activity.getString(R.string.forward, forwardSeconds));
        view.findViewById(R.id.forward).setOnClickListener(view1 -> adjustSecondsElapsed(service, forwardSeconds));
        view.findViewById(R.id.settings).setOnClickListener(view1 -> new CuePanelSettings().show(activity.getSupportFragmentManager(), CuePanelSettings.class.getName()));

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
        dialog.setContentView(view);

        int horizontal = (int) (parent.getWidth() * 0.1);
        int top = (int) (parent.getHeight() * 0.6);
        view.setPadding(horizontal, top, horizontal, 0);

        int[] location = new int[2];
        parent.getLocationOnScreen(location);

        Window window = dialog.getWindow();
        WindowManager.LayoutParams lp = window.getAttributes();
        lp.token = null;
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = location[0];
        lp.y = location[1];
        lp.width = parent.getWidth();
        lp.height = parent.getHeight();
        window.setAttributes(lp);
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH);
        dialog.show();

        fadeParent(1.0, 0.4);

        resetTimeout();
    }

    private void adjustSecondsElapsed(@NonNull ISqueezeService service, int seconds) {
        service.adjustSecondsElapsed(seconds);
        resetTimeout();
    }

    public void dismiss() {
        removeMessages(MSG_TIMEOUT);
        if (!activity.isDestroyed() && dialog.isShowing()) {
            dialog.dismiss();
            fadeParent(0.4, 1.0);
        }
    }

    private void fadeParent(double from, double to) {
        ObjectAnimator parentAnimator = ObjectAnimator.ofPropertyValuesHolder(parent, PropertyValuesHolder.ofFloat("alpha", (float) from, (float)to));
        parentAnimator.setTarget(parent);
        parentAnimator.setDuration(FADE_IN_TIME);
        parentAnimator.start();
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
    public void handleMessage(Message msg) {
        if (msg.what == MSG_TIMEOUT) {
            dismiss();
        }
    }
}

