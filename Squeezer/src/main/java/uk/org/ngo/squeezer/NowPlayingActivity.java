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

package uk.org.ngo.squeezer;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.MotionEvent;

import androidx.appcompat.app.ActionBar;
import androidx.core.view.GestureDetectorCompat;

import uk.org.ngo.squeezer.framework.BaseActivity;
import uk.org.ngo.squeezer.widget.OnSwipeListener;

public class NowPlayingActivity extends BaseActivity {
    private static final String TAG = "NowPlayingActivity";

    private GestureDetectorCompat mDetector;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.now_playing);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setHomeAsUpIndicator(R.drawable.ic_action_down);
        }

        mDetector = new GestureDetectorCompat(this, new OnSwipeListener() {
            @Override
            public boolean onSwipeDown() {
                finish();
                return true;
            }
        });
    }

    public static void show(Context context) {
        final Intent intent = new Intent(context, NowPlayingActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);

        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
        if (context instanceof Activity) {
            ((Activity) context).overridePendingTransition(R.anim.slide_in_up, android.R.anim.fade_out);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event){
        mDetector.onTouchEvent(event);
        return super.onTouchEvent(event);
    }

    @Override
    public void onPause() {
        if (isFinishing()) {
            overridePendingTransition(android.R.anim.fade_in, R.anim.slide_out_down);
        }
        super.onPause();
    }
}
