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
import android.view.Menu;
import android.view.MenuItem;

import androidx.appcompat.app.ActionBar;
import androidx.core.view.MenuCompat;


import uk.org.ngo.squeezer.framework.BaseActivity;

public class NowPlayingActivity extends BaseActivity {

    /**
     * Called when the activity is first created.
     */

    private MenuItem menuItemComposerLine;
    private MenuItem menuItemConductorLine;
    private MenuItem menuItemClassicalMusicTags;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.now_playing);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setHomeAsUpIndicator(R.drawable.ic_action_down);
        }
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
    public boolean onCreateOptionsMenu(Menu menu) {
        Menu trackInfoMenu;

        getMenuInflater().inflate(R.menu.nowplaying_menu, menu);
        trackInfoMenu = menu.findItem(R.id.menu_nowplaying_trackinfo).getSubMenu();
        MenuCompat.setGroupDividerEnabled(trackInfoMenu, true);

        menuItemComposerLine = trackInfoMenu.findItem(R.id.menu_item_composer_line);
        menuItemConductorLine = trackInfoMenu.findItem(R.id.menu_item_conductor_line);
        menuItemClassicalMusicTags = trackInfoMenu.findItem(R.id.menu_item_classical_music_tags);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        updateTrackInfoMenuItems();
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            finish();
            return true;
        } else if (itemId == R.id.menu_item_composer_line) {
            Squeezer.getPreferences().addComposerLine(!menuItemComposerLine.isChecked());
            updateTrackInfoMenuItems();
            // Make sure view is updated to reflect changes
            NowPlayingActivity.show(this);
            return true;
        } else if (itemId == R.id.menu_item_conductor_line) {
            Squeezer.getPreferences().addConductorLine(!menuItemConductorLine.isChecked());
            updateTrackInfoMenuItems();
            // Make sure view is updated to reflect changes
            NowPlayingActivity.show(this);
            return true;
        } else if (itemId == R.id.menu_item_classical_music_tags) {
            Squeezer.getPreferences().displayClassicalMusicTags(!menuItemClassicalMusicTags.isChecked());
            updateTrackInfoMenuItems();
            // Make sure view is updated to reflect changes
            NowPlayingActivity.show(this);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void updateTrackInfoMenuItems() {
        if (menuItemComposerLine != null) {
            Preferences preferences = Squeezer.getPreferences();

            menuItemComposerLine.setChecked(preferences.addComposerLine());
            menuItemConductorLine.setChecked(preferences.addConductorLine());
            menuItemClassicalMusicTags.setChecked(preferences.displayClassicalMusicTags());
        }
    }

    @Override
    public void onPause() {
        if (isFinishing()) {
            overridePendingTransition(android.R.anim.fade_in, R.anim.slide_out_down);
        }
        super.onPause();
    }

}
