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

package uk.org.ngo.squeezer.widget;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import android.os.Looper;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;

import android.widget.LinearLayout;
import android.widget.TextView;

import uk.org.ngo.squeezer.R;

/**
 *
 * Controls a view which is a toast with an undo button.
 * <p>
 * Use this for actions which which shall be undoable. The undo bar is hosted by an activity. When
 * the undo bar is first requested (by calling
 * {@link #show(Activity, int, UndoBarController.UndoListener)}) it inflates its view, and
 * add this view to the activity. The visibility of this view is maintained by this class. You
 * supply an {@link UndoBarController.UndoListener} when you request the undo bar, which is called
 * when the undo button is pressed, or the undo bar goes away. The undo bar instance is shared
 * between requests, when using the same activity. When a new request comes in, any existing
 * listener is called, and the listener is replaced with the one in the new request.
 * <p>
 * Activities which uses the undo bar, should call {@link #hide(Activity)} in onPause.
 */
public class UndoBarController extends LinearLayout {
    public static final int FADE_DURATION = 300;
    public static final int UNDO_DURATION = 5000;

    private final View mUndoBar;
    private final TextView mMessageView;
    private final Handler mHideHandler = new Handler(Looper.getMainLooper());
    private UndoListener mUndoListener;

    private final Runnable mHideRunnable = () -> hideUndoBar(false, false);

    private UndoBarController(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        LayoutInflater.from(context).inflate(R.layout.undo_bar, this, true);
        mMessageView = findViewById(R.id.undobar_message);
        mUndoBar = (View) mMessageView.getParent();
        TextView button = findViewById(R.id.undobar_button);
        button.setOnClickListener(view -> hideUndoBar(false, true));

        hideUndoBar(true, false);
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (event.getX() < mUndoBar.getLeft() || event.getY() < mUndoBar.getTop()
                    || event.getX() > mUndoBar.getRight() || event.getY() < mUndoBar.getBottom()) {
                hideUndoBar(false, false);
            }
        }
        return false;
    }

    private void hideUndoBar(final boolean immediate, final boolean undoSelected) {
        mHideHandler.removeCallbacks(mHideRunnable);

        if (mUndoListener != null && getVisibility() == View.VISIBLE) {
            if (undoSelected)
                mUndoListener.onUndo();
            else
                mUndoListener.onDone();
            mUndoListener = null;
        }

        clearAnimation();
        startAnimation(outAnimation(immediate));
    }

    /**
     * Insert an ActionableToastBar onto an Activity
     *
     * @param activity Activity to hold this view
     * @param message The message will be shown in left side in toast
     * @param listener Callback
     */
    private static void showUndoBar(final Activity activity, final CharSequence message,
                                    final UndoListener listener) {
        UndoBarController undo = UndoBarController.getView(activity);
        if (undo == null) {
            undo = new UndoBarController(activity, null);
            ((ViewGroup) activity.findViewById(android.R.id.content)).addView(undo);
        } else {
            undo.hideUndoBar(true, false);
        }
        undo.mUndoListener = listener;

        undo.mMessageView.setText(message);

        undo.resetTimeout();
        undo.clearAnimation();
        undo.startAnimation(undo.inAnimation());
    }

    private static UndoBarController getView(final Activity activity) {
        final View view = activity.findViewById(R.id.undobar);
        UndoBarController undo = null;
        if (view != null) {
            undo = (UndoBarController) view.getParent();
        }
        return undo;
    }

    private Animation outAnimation(boolean immediate) {
        final Animation animation = new AlphaAnimation(1F, 0F);
        animation.setDuration(immediate ? 0 : FADE_DURATION);
        animation.setAnimationListener(new AnimationEndListener() {
            @Override
            public void onAnimationEnd(Animation animation) {
                mUndoBar.setVisibility(View.GONE);
            }
        });
        return animation;
    }

    private Animation inAnimation() {
        final Animation animation = new AlphaAnimation(0F, 1F);
        animation.setDuration(FADE_DURATION);
        animation.setAnimationListener(new AnimationEndListener() {
            @Override
            public void onAnimationEnd(Animation animation) {
                mUndoBar.setVisibility(View.VISIBLE);
            }
        });
        return animation;
    }


    /**
     * Show an undo bar
     *
     * @param activity Activity to hold this view
     * @param message The message will be shown in left side in toast
     * @param listener Callback
     */
    public static void show(final Activity activity, final CharSequence message,
                            final UndoListener listener) {
        showUndoBar(activity, message, listener);
    }

    /**
     * Show an undo bar
     *
     * @param activity Activity to hold this view
     * @param message The message will be shown in left side in toast
     * @param listener Callback
     */
    public static void show(final Activity activity, final @StringRes int message,
                            final UndoListener listener) {
        showUndoBar(activity, activity.getString(message), listener);
    }

    /**
     * Hide the {@link UndoBarController}
     *
     * @param activity Activity which hosts the {@link UndoBarController}
     */
    public static void hide(final Activity activity) {
        final UndoBarController v = UndoBarController.getView(activity);
        if (v != null && v.getVisibility() == View.VISIBLE) v.hideUndoBar(false, false);
    }

    /** Make the undo bar stay for the full duration from now */
    public void resetTimeout() {
        mHideHandler.removeCallbacks(mHideRunnable);
        mHideHandler.postDelayed(mHideRunnable, UNDO_DURATION);
    }

    /** Callback interface for the undo bar */
    public interface UndoListener {
        /** Called when the undo button is pressed */
        void onUndo();

        /** Called when the undo bar goes away and undo has not been pressed */
        void onDone();
    }

}