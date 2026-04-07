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
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import uk.org.ngo.squeezer.dialog.CuePanelSettings;
import uk.org.ngo.squeezer.service.LyrionController;


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

    public CuePanel(FragmentActivity activity, @NonNull View parent, @NonNull LyrionController lyrionController) {
        super(Looper.getMainLooper());
        this.parent = parent;
        this.activity = activity;
        Preferences preferences = Squeezer.instance().preferences();
        int backwardSeconds = preferences.getBackwardSeconds();
        int forwardSeconds = preferences.getForwardSeconds();

        final View view = View.inflate(parent.getContext(), R.layout.cue_panel, null);
        ((Button)view.findViewById(R.id.backward)).setText(activity.getString(R.string.backward, backwardSeconds));
        view.findViewById(R.id.backward).setOnClickListener(view1 -> adjustSecondsElapsed(lyrionController, -backwardSeconds));
        ((Button)view.findViewById(R.id.forward)).setText(activity.getString(R.string.forward, forwardSeconds));
        view.findViewById(R.id.forward).setOnClickListener(view1 -> adjustSecondsElapsed(lyrionController, forwardSeconds));
        view.findViewById(R.id.settings).setOnClickListener(view1 -> new CuePanelSettings().show(activity.getSupportFragmentManager(), CuePanelSettings.class.getName()));
        view.findViewById(R.id.volume).setVisibility(preferences.isLargeArtwork() ? View.VISIBLE : View.INVISIBLE);
        view.findViewById(R.id.volume).setOnClickListener(v -> {
            dismiss();
            preferences.setLargeArtwork(false);
            activity.recreate();
        });
        view.setOnClickListener(v -> dismiss());

        dialog = new Dialog(view.getContext(), R.style.VolumePanel);
        dialog.setContentView(view);
        dialog.setCanceledOnTouchOutside(true);
        dialog.setOnDismissListener(d -> fadeParent(0.4, 1.0));

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
        dialog.show();

        fadeParent(1.0, 0.4);

        resetTimeout();
    }

    private void adjustSecondsElapsed(@NonNull LyrionController lyrionController, int seconds) {
        lyrionController.adjustSecondsElapsed(seconds);
        resetTimeout();
    }

    public void dismiss() {
        removeMessages(MSG_TIMEOUT);
        if (!activity.isDestroyed() && dialog.isShowing()) dialog.dismiss();
    }

    private void fadeParent(double from, double to) {
        if (activity.isDestroyed()) return;
        ObjectAnimator parentAnimator = ObjectAnimator.ofPropertyValuesHolder(parent, PropertyValuesHolder.ofFloat("alpha", (float) from, (float)to));
        parentAnimator.setTarget(parent);
        parentAnimator.setDuration(FADE_IN_TIME);
        parentAnimator.start();
    }

    private void resetTimeout() {
        removeMessages(MSG_TIMEOUT);
        sendMessageDelayed(obtainMessage(MSG_TIMEOUT), TIMEOUT_DELAY);
    }

    @Override
    public void handleMessage(Message msg) {
        if (msg.what == MSG_TIMEOUT) {
            dismiss();
        }
    }
}

