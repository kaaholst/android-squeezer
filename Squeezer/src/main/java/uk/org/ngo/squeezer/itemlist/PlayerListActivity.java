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

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.Util;
import uk.org.ngo.squeezer.framework.BaseActivity;
import uk.org.ngo.squeezer.itemlist.dialog.DefeatDestructiveTouchToPlayDialog;
import uk.org.ngo.squeezer.itemlist.dialog.PlayTrackAlbumDialog;
import uk.org.ngo.squeezer.itemlist.dialog.PlayerSyncDialog;
import uk.org.ngo.squeezer.itemlist.dialog.SyncPowerDialog;
import uk.org.ngo.squeezer.itemlist.dialog.SyncVolumeDialog;
import uk.org.ngo.squeezer.model.LyrionPlayer;
import uk.org.ngo.squeezer.model.PlayerState;
import uk.org.ngo.squeezer.service.event.HandshakeComplete;
import uk.org.ngo.squeezer.service.event.PlayerStateChanged;
import uk.org.ngo.squeezer.service.event.PlayerVolume;
import uk.org.ngo.squeezer.service.event.SleepTimeChanged;
import uk.org.ngo.squeezer.widget.ViewUtilities;


public class PlayerListActivity extends BaseActivity implements
        PlayerSyncDialog.PlayerSyncDialogHost,
        PlayTrackAlbumDialog.PlayTrackAlbumDialogHost,
        DefeatDestructiveTouchToPlayDialog.DefeatDestructiveTouchToPlayDialogHost,
        SyncVolumeDialog.SyncVolumeDialogHost,
        SyncPowerDialog.SyncPowerDialogHost {
    private static final String CURRENT_PLAYER = "currentPlayer";
    private static final String CURRENT_SYNC_GROUP = "currentSyncGroup";
    /**
     * Map from player IDs to Players synced to that player ID.
     */
    private final Map<String, Collection<LyrionPlayer>> mPlayerSyncGroups = new HashMap<>();
    protected LyrionPlayer mTrackingTouch = null;
    /**
     * An update arrived while tracking touches. UI should be re-synced.
     */
    protected boolean mUpdateWhileTracking = false;
    private RecyclerView listView;
    PlayerListAdapter adapter;

    private LyrionPlayer currentPlayer;
    private PlayerListAdapter.SyncGroup currentSyncGroup;

    public static void show(Context context) {
        final Intent intent = new Intent(context, PlayerListActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        context.startActivity(intent);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putParcelable(CURRENT_PLAYER, currentPlayer);
        putRetainedValue(CURRENT_SYNC_GROUP, currentSyncGroup);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.list_activity_layout);

        adapter = new PlayerListAdapter(this);
        listView = requireView(R.id.item_list);
        listView.setAdapter(adapter);
        listView.setLayoutManager(new LinearLayoutManager(this));

        setSupportActionBar(requireView(R.id.toolbar));
        ViewUtilities.setInsetsListener(requireView(R.id.toolbar), true, false, false);
        ViewUtilities.setInsetsListener(listView, false, true, false);

        if (savedInstanceState != null) {
            currentPlayer = savedInstanceState.getParcelable(PlayerListActivity.CURRENT_PLAYER);
        }
       currentSyncGroup = getRetainedValue(CURRENT_SYNC_GROUP);
    }

    @Override
    protected void registerObservers() {
        super.registerObservers();
        repository().observe(this, (HandshakeComplete event) -> updateAndExpandPlayerList());
        repository().observe(this, (PlayerStateChanged event) -> maybeUpdateAndExpandPlayerList());
        repository().observe(this, (PlayerVolume event) -> {
            adapter.notifyVolumeChanged(event.player);
            adapter.notifyGroupVolumeChanged(event.player);
        });
        repository().observe(this, (SleepTimeChanged event) -> maybeUpdateAndExpandPlayerList());
    }

    public RecyclerView getListView() {
        return listView;
    }

    @Override
    public LyrionPlayer getCurrentPlayer() {
        return currentPlayer;
    }

    public void setCurrentPlayer(LyrionPlayer currentPlayer) {
        this.currentPlayer = currentPlayer;
    }

    void setCurrentSyncGroup(PlayerListAdapter.SyncGroup currentSyncGroup) {
        this.currentSyncGroup = currentSyncGroup;
    }

    public void playerRename(String newName) {
        lyrionController().playerRename(currentPlayer, newName);
        this.currentPlayer.setName(newName);
        adapter.notifyItemChanged(currentPlayer);
    }

    /**
     * Synchronises the slave player to the player with masterId.
     *
     * @param slave    the player to sync.
     * @param masterId ID of the player to sync to.
     */
    @Override
    public void syncPlayerToPlayer(@NonNull LyrionPlayer slave, @NonNull String masterId) {
        lyrionController().syncPlayerToPlayer(slave, masterId);
    }

    /**
     * Removes the player from any sync groups.
     *
     * @param player the player to be removed from sync groups.
     */
    @Override
    public void unsyncPlayer(@NonNull LyrionPlayer player) {
        lyrionController().unsyncPlayer(player);
    }

    @Override
    public String getPlayTrackAlbum() {
        return currentPlayer.getPlayerState().prefs.get(LyrionPlayer.Pref.PLAY_TRACK_ALBUM);
    }

    @Override
    public void setPlayTrackAlbum(@NonNull String option) {
        lyrionController().playerPref(currentPlayer, LyrionPlayer.Pref.PLAY_TRACK_ALBUM, option);
    }

    @Override
    public String getDefeatDestructiveTTP() {
        return currentPlayer.getPlayerState().prefs.get(LyrionPlayer.Pref.DEFEAT_DESTRUCTIVE_TTP);
    }

    @Override
    public void setDefeatDestructiveTTP(@NonNull String option) {
        lyrionController().playerPref(currentPlayer, LyrionPlayer.Pref.DEFEAT_DESTRUCTIVE_TTP, option);
    }

    @Override
    public int getSyncVolume() {
        return getGroupPref(LyrionPlayer.Pref.SYNC_VOLUME);
    }

    @Override
    public void setSyncVolume(@NonNull String option) {
        for (int i = 0; i < currentSyncGroup.getItemCount(); i++) {
            lyrionController().playerPref(currentSyncGroup.getItem(i), LyrionPlayer.Pref.SYNC_VOLUME, option);
        }
    }

    @Override
    public int getSyncPower() {
        return getGroupPref(LyrionPlayer.Pref.SYNC_POWER);
    }

    @Override
    public void setSyncPower(@NonNull String option) {
        for (int i = 0; i < currentSyncGroup.getItemCount(); i++) {
            lyrionController().playerPref(currentSyncGroup.getItem(i), LyrionPlayer.Pref.SYNC_POWER, option);
        }
    }

    private int getGroupPref(LyrionPlayer.Pref pref) {
        for (int i = 0; i < currentSyncGroup.getItemCount(); i++) {
            int prefValue = Util.getInt(currentSyncGroup.getItem(i).getPlayerState().prefs.get(pref), -1);
            if (prefValue != -1) return prefValue;
        }
        return 0;
    }

    /**
     * Updates the adapter with the current players, and ensures that the list view is
     * expanded.
     */
    protected void updateAndExpandPlayerList() {
        updateSyncGroups(lyrionController().getPlayers());
        adapter.setSyncGroups(mPlayerSyncGroups);
    }

    private void maybeUpdateAndExpandPlayerList() {
        if (mTrackingTouch == null) {
            updateAndExpandPlayerList();
        } else {
            mUpdateWhileTracking = true;
        }
    }

    public void setTrackingTouch(LyrionPlayer trackingTouch) {
        mTrackingTouch = trackingTouch;
        if (mTrackingTouch == null) {
            if (mUpdateWhileTracking) {
                mUpdateWhileTracking = false;
                updateAndExpandPlayerList();
            }
        }
    }

    /**
     * Builds the list of lists that is a sync group.
     *
     * @param players List of players.
     */
    public void updateSyncGroups(Collection<LyrionPlayer> players) {
        mPlayerSyncGroups.clear();

        // Iterate over all the connected players to build the list of master players.
        for (LyrionPlayer player : players) {
            String playerId = player.getId();
            PlayerState playerState = player.getPlayerState();
            String syncMaster = playerState.getSyncMaster();

            if (syncMaster == null || playerId.equals(syncMaster)) {
                // If a player doesn't have a sync master or the master is this player then add it as a
                // slave with itself as master.
                addSyncSlave(playerId, player);
            } else {
                // Must be a slave. Add it under the master. This might have already
                // happened (in the block above), but might not. For example, it's possible
                // to have a player that's a syncslave of a player that is not connected.
                addSyncSlave(syncMaster, player);
            }
        }
    }

    private void addSyncSlave(String masterId, LyrionPlayer player) {
        Collection<LyrionPlayer> slaves = mPlayerSyncGroups.get(masterId);
        if (slaves == null) {
            mPlayerSyncGroups.put(masterId, slaves = new HashSet<>());
        }
        slaves.add(player);
    }

    @NonNull
    public Map<String, Collection<LyrionPlayer>> getPlayerSyncGroups() {
        return mPlayerSyncGroups;
    }
}
