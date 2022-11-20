/*
 * Copyright (c) 2020 Kurt Aaholst <kaaholst@gmail.com>
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

package uk.org.ngo.squeezer.itemlist;

import android.content.Context;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import androidx.fragment.app.FragmentManager;

import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.itemlist.dialog.PlayerSleepDialog;
import uk.org.ngo.squeezer.model.Player;
import uk.org.ngo.squeezer.model.PlayerState;
import uk.org.ngo.squeezer.service.ISqueezeService;
import uk.org.ngo.squeezer.service.event.SongTimeChanged;

public class PlayerViewLogic {

    /**
     * Inflate common player actions onto the supplied menu
     */
    public static void inflatePlayerActions(Context context, MenuInflater inflater, Menu menu) {
        inflater.inflate(R.menu.player_menu, menu);

        String xMinutes = context.getString(R.string.X_MINUTES);
        menu.findItem(R.id.in_15_minutes).setTitle(String.format(xMinutes, "15"));
        menu.findItem(R.id.in_30_minutes).setTitle(String.format(xMinutes, "30"));
        menu.findItem(R.id.in_45_minutes).setTitle(String.format(xMinutes, "45"));
        menu.findItem(R.id.in_60_minutes).setTitle(String.format(xMinutes, "60"));
        menu.findItem(R.id.in_90_minutes).setTitle(String.format(xMinutes, "90"));
    }

    /**
     * If menu item is a known player action, perform it and return true.
     */
    public static boolean doPlayerAction(FragmentManager fragmentManager, ISqueezeService service, MenuItem menuItem, Player selectedItem) {
        switch (menuItem.getItemId()) {
            case R.id.sleep:
                // This is the start of a context menu.
                // Just return, as we have set the current player.
                return true;
            case R.id.end_of_song: {
                PlayerState playerState = selectedItem.getPlayerState();
                if (playerState.isPlaying()) {
                    SongTimeChanged trackElapsed = selectedItem.getTrackElapsed();
                    int sleep = trackElapsed.duration - trackElapsed.currentPosition + 1;
                    if (sleep >= 0)
                        service.sleep(selectedItem, sleep);
                }
                return true;
            }
            case R.id.in_15_minutes:
                service.sleep(selectedItem, 15*60);
                return true;
            case R.id.in_30_minutes:
                service.sleep(selectedItem, 30*60);
                return true;
            case R.id.in_45_minutes:
                service.sleep(selectedItem, 45*60);
                return true;
            case R.id.in_60_minutes:
                service.sleep(selectedItem, 60*60);
                return true;
            case R.id.in_90_minutes:
                service.sleep(selectedItem, 90*60);
                return true;
            case R.id.in_x_minutes:
                new PlayerSleepDialog(selectedItem).show(fragmentManager, PlayerSleepDialog.class.getName());
                return true;
            case R.id.cancel_sleep:
                service.sleep(selectedItem, 0);
                return true;
            case R.id.toggle_power:
                service.togglePower(selectedItem);
                return true;
        }


        return false;
    }

}
