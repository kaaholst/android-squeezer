/*
 * Copyright (c) 2011 Kurt Aaholst <kaaholst@gmail.com>
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

import android.annotation.SuppressLint;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.slider.Slider;

import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.Util;
import uk.org.ngo.squeezer.itemlist.dialog.DefeatDestructiveTouchToPlayDialog;
import uk.org.ngo.squeezer.itemlist.dialog.PlayTrackAlbumDialog;
import uk.org.ngo.squeezer.itemlist.dialog.PlayerRenameDialog;
import uk.org.ngo.squeezer.itemlist.dialog.PlayerSyncDialog;
import uk.org.ngo.squeezer.model.Player;
import uk.org.ngo.squeezer.model.PlayerState;
import uk.org.ngo.squeezer.service.ISqueezeService;

public class PlayerView extends PlayerBaseView {
    private final PlayerListActivity activity;
    private MaterialButton mute;
    private final Slider volumeBar;

    public PlayerView(PlayerListActivity activity, View view) {
        super(activity, view);
        this.activity = activity;

        setItemViewParams(VIEW_PARAM_ICON | VIEW_PARAM_TWO_LINE | VIEW_PARAM_CONTEXT_BUTTON);

        mute = view.findViewById(R.id.mute);
        volumeBar = view.findViewById(R.id.volume_slider);
    }

    @Override
    public void bindView(Player player) {
        super.bindView(player);

        PlayerState playerState = player.getPlayerState();

        mute.setIconResource(playerState.isMuted() ? R.drawable.ic_volume_off : R.drawable.ic_volume_down);
        volumeBar.setEnabled(!playerState.isMuted());

        mute.setOnClickListener(view -> {
            ISqueezeService service = activity.getService();
            if (service == null) {
                return;
            }
            service.toggleMute(player);
        });
        volumeBar.clearOnSliderTouchListeners();
        volumeBar.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            @SuppressLint("RestrictedApi")
            public void onStartTrackingTouch(@NonNull Slider slider) {
                activity.setTrackingTouch(player);
            }

            @Override
            @SuppressLint("RestrictedApi")
            public void onStopTrackingTouch(@NonNull Slider slider) {
                activity.setTrackingTouch(null);
                activity.adapter.notifyGroupChanged(player);
            }
        });
        volumeBar.clearOnChangeListeners();
        volumeBar.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                ISqueezeService service = activity.getService();
                if (service == null) {
                    return;
                }
                service.setVolumeTo(player, (int)value);
            }
        });
        volumeBar.setValue(playerState.getCurrentVolume());

        text2.setVisibility(playerState.getSleepDuration() > 0 ? View.VISIBLE : View.INVISIBLE);
        if (playerState.getSleepDuration() > 0) {
            text2.setText(activity.getString(R.string.SLEEPING_IN)
                    + " " + Util.formatElapsedTime(player.getSleepingIn()));
        }
    }

    @Override
    public void showContextMenu() {
        PopupMenu popup = new PopupMenu(getActivity(), contextMenuButtonHolder);
        popup.inflate(R.menu.player_context_menu);

        Menu menu = popup.getMenu();
        PlayerViewLogic.inflatePlayerActions(activity, popup.getMenuInflater(), menu);

        PlayerState playerState = item.getPlayerState();
        menu.findItem(R.id.cancel_sleep).setVisible(playerState.getSleepDuration() != 0);

        menu.findItem(R.id.end_of_song).setVisible(playerState.isPlaying());

        menu.findItem(R.id.toggle_power).setTitle(playerState.isPoweredOn() ? R.string.menu_item_power_off : R.string.menu_item_power_on);

        // Enable player sync menu options if there's more than one player.
        menu.findItem(R.id.player_sync).setVisible(activity.adapter.mPlayerCount > 1);

        menu.findItem(R.id.play_track_album).setVisible(playerState.prefs.containsKey(Player.Pref.PLAY_TRACK_ALBUM));

        menu.findItem(R.id.defeat_destructive_ttp).setVisible(playerState.prefs.containsKey(Player.Pref.DEFEAT_DESTRUCTIVE_TTP));

        popup.setOnMenuItemClickListener(menuItem -> doItemContext(menuItem, item));

        activity.adapter.mPlayersChanged = false;
        popup.show();
    }

    private boolean doItemContext(MenuItem menuItem, Player selectedItem) {
        if (activity.adapter.mPlayersChanged) {
            Toast.makeText(activity, activity.getText(R.string.player_list_changed),
                    Toast.LENGTH_LONG).show();
            return true;
        }

        activity.setCurrentPlayer(selectedItem);
        ISqueezeService service = activity.getService();
        if (service == null) {
            return true;
        }

        if (PlayerViewLogic.doPlayerAction(activity.getSupportFragmentManager(), service, menuItem, selectedItem)) {
            return  true;
        }

        if (menuItem.getItemId() == R.id.rename) {
            new PlayerRenameDialog().show(activity.getSupportFragmentManager(),
                    PlayerRenameDialog.class.getName());
            return true;
        } else if (menuItem.getItemId() == R.id.player_sync) {
            new PlayerSyncDialog().show(activity.getSupportFragmentManager(),
                    PlayerSyncDialog.class.getName());
            return true;
        } else if (menuItem.getItemId() == R.id.play_track_album) {
            PlayTrackAlbumDialog.show(activity);
            return true;
        } else if (menuItem.getItemId() == R.id.defeat_destructive_ttp) {
            DefeatDestructiveTouchToPlayDialog.show(activity);
            return true;
        }

        return false;
    }

}
