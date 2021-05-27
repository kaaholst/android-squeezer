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
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import uk.org.ngo.squeezer.framework.ItemListActivity;
import uk.org.ngo.squeezer.itemlist.dialog.DefeatDestructiveTouchToPlayDialog;
import uk.org.ngo.squeezer.itemlist.dialog.PlayTrackAlbumDialog;
import uk.org.ngo.squeezer.itemlist.dialog.PlayerSyncDialog;
import uk.org.ngo.squeezer.itemlist.dialog.SyncPowerDialog;
import uk.org.ngo.squeezer.itemlist.dialog.SyncVolumeDialog;
import uk.org.ngo.squeezer.model.Item;
import uk.org.ngo.squeezer.model.Player;
import uk.org.ngo.squeezer.model.PlayerState;
import uk.org.ngo.squeezer.service.ISqueezeService;
import uk.org.ngo.squeezer.service.event.HandshakeComplete;
import uk.org.ngo.squeezer.service.event.PlayerStateChanged;
import uk.org.ngo.squeezer.service.event.PlayerVolume;


public class PlayerListActivity extends ItemListActivity implements
        PlayerSyncDialog.PlayerSyncDialogHost,
        PlayTrackAlbumDialog.PlayTrackAlbumDialogHost,
        DefeatDestructiveTouchToPlayDialog.DefeatDestructiveTouchToPlayDialogHost,
        SyncVolumeDialog.SyncVolumeDialogHost,
        SyncPowerDialog.SyncPowerDialogHost {
    private static final String CURRENT_PLAYER = "currentPlayer";
    private static final String CURRENT_SYNC_GROUP = "currentSyncGroup";
    private static final String TAG = PlayerListActivity.class.getName();
    /**
     * Map from player IDs to Players synced to that player ID.
     */
    private final Map<String, Collection<Player>> mPlayerSyncGroups = new HashMap<>();
    protected Player mTrackingTouch = null;
    /**
     * An update arrived while tracking touches. UI should be re-synced.
     */
    protected boolean mUpdateWhileTracking = false;
    PlayerListAdapter adapter;

    private Player currentPlayer;
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
    protected boolean needPlayer() {
        return false;
    }



    public void onEventMainThread(PlayerVolume event) {
        if (mTrackingTouch != event.player) {
            adapter.notifyItemChanged(event.player);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        adapter = new PlayerListAdapter(this);
        getListView().setAdapter(adapter);

        setIgnoreVolumeChange(true);

        if (savedInstanceState != null) {
            currentPlayer = savedInstanceState.getParcelable(PlayerListActivity.CURRENT_PLAYER);
        }
       currentSyncGroup = getRetainedValue(CURRENT_SYNC_GROUP);
    }

    @Override
    public Player getCurrentPlayer() {
        return currentPlayer;
    }

    public void setCurrentPlayer(Player currentPlayer) {
        this.currentPlayer = currentPlayer;
    }

    public void setCurrentSyncGroup(PlayerListAdapter.SyncGroup currentSyncGroup) {
        this.currentSyncGroup = currentSyncGroup;
    }

    public void playerRename(String newName) {
        ISqueezeService service = getService();
        if (service == null) {
            return;
        }

        service.playerRename(currentPlayer, newName);
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
    public void syncPlayerToPlayer(@NonNull Player slave, @NonNull String masterId) {
        getService().syncPlayerToPlayer(slave, masterId);
    }

    /**
     * Removes the player from any sync groups.
     *
     * @param player the player to be removed from sync groups.
     */
    @Override
    public void unsyncPlayer(@NonNull Player player) {
        getService().unsyncPlayer(player);
    }

    @Override
    public String getPlayTrackAlbum() {
        return currentPlayer.getPlayerState().prefs.get(Player.Pref.PLAY_TRACK_ALBUM);
    }

    @Override
    public void setPlayTrackAlbum(@NonNull String option) {
        getService().playerPref(currentPlayer, Player.Pref.PLAY_TRACK_ALBUM, option);
    }

    @Override
    public String getDefeatDestructiveTTP() {
        return currentPlayer.getPlayerState().prefs.get(Player.Pref.DEFEAT_DESTRUCTIVE_TTP);
    }

    @Override
    public void setDefeatDestructiveTTP(@NonNull String option) {
        getService().playerPref(currentPlayer, Player.Pref.DEFEAT_DESTRUCTIVE_TTP, option);
    }

    @Override
    public String getSyncVolume() {
        return currentSyncGroup.getItem(0).getPlayerState().prefs.get(Player.Pref.SYNC_VOLUME);
    }

    @Override
    public void setSyncVolume(@NonNull String option) {
        for (int i = 0; i < currentSyncGroup.getItemCount(); i++) {
            getService().playerPref(currentSyncGroup.getItem(i), Player.Pref.SYNC_VOLUME, option);
        }
    }

    @Override
    public String getSyncPower() {
        return currentSyncGroup.getItem(0).getPlayerState().prefs.get(Player.Pref.SYNC_POWER);
    }

    @Override
    public void setSyncPower(@NonNull String option) {
        for (int i = 0; i < currentSyncGroup.getItemCount(); i++) {
            getService().playerPref(currentSyncGroup.getItem(i), Player.Pref.SYNC_POWER, option);
        }
    }

    @Override
    protected <T extends Item> void updateAdapter(int count, int start, List<T> items, Class<T> dataType) {
    }

    /**
     * Updates the adapter with the current players, and ensures that the list view is
     * expanded.
     */
    protected void updateAndExpandPlayerList() {
        updateSyncGroups(getService().getPlayers());
        adapter.setSyncGroups(mPlayerSyncGroups);
    }

    @Override
    protected void orderPage(@NonNull ISqueezeService service, int start) {
        // Do nothing -- the service has been tracking players from the time it
        // initially connected to the server.
    }

    public void onEventMainThread(HandshakeComplete event) {
        super.onEventMainThread(event);
        updateAndExpandPlayerList();
    }

    public void onEventMainThread(PlayerStateChanged event) {
        if (mTrackingTouch == null) {
            updateAndExpandPlayerList();
        } else {
            mUpdateWhileTracking = true;
        }
    }

    public void setTrackingTouch(Player trackingTouch) {
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
    public void updateSyncGroups(Collection<Player> players) {
        Map<String, Player> connectedPlayers = new HashMap<>();

        // Make a copy of the players we know about, ignoring unconnected ones.
        for (Player player : players) {
            if (!player.getConnected())
                continue;

            connectedPlayers.put(player.getId(), player);
        }

        mPlayerSyncGroups.clear();

        // Iterate over all the connected players to build the list of master players.
        for (Player player : connectedPlayers.values()) {
            String playerId = player.getId();
            String name = player.getName();
            PlayerState playerState = player.getPlayerState();
            String syncMaster = playerState.getSyncMaster();

            Log.d(TAG, "player discovered: id=" + playerId + ", syncMaster=" + syncMaster + ", name=" + name);
            if (syncMaster == null || playerId.equals(syncMaster)) {
                // If a player doesn't have a sync master or the master is this player then add it as a
                // slave with itself as master.
                addSyncSlave(playerId, player);
            } else {
                // Must be a slave. Add it under the master. This might have already
                // happened (in the block above), but might not. For example, it's possible
                // to have a player that's a syncslave of an player that is not connected.
                addSyncSlave(syncMaster, player);
            }
        }
    }

    private void addSyncSlave(String masterId, Player player) {
        Collection<Player> slaves = mPlayerSyncGroups.get(masterId);
        if (slaves == null) {
            mPlayerSyncGroups.put(masterId, slaves = new HashSet<>());
        }
        slaves.add(player);
    }

    @NonNull
    public Map<String, Collection<Player>> getPlayerSyncGroups() {
        return mPlayerSyncGroups;
    }

    @Override
    protected void clearItemAdapter() {
        adapter.clear();
    }
}
