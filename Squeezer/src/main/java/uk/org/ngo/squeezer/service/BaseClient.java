/*
 * Copyright (c) 2015 Google Inc.  All Rights Reserved.
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

package uk.org.ngo.squeezer.service;

import android.os.SystemClock;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.Squeezer;
import uk.org.ngo.squeezer.SqueezerRepository;
import uk.org.ngo.squeezer.Util;
import uk.org.ngo.squeezer.itemlist.IServiceItemListCallback;
import uk.org.ngo.squeezer.model.CurrentTrack;
import uk.org.ngo.squeezer.model.Player;
import uk.org.ngo.squeezer.model.PlayerState;
import uk.org.ngo.squeezer.model.SlimCommand;
import uk.org.ngo.squeezer.service.event.PlayStatusChanged;
import uk.org.ngo.squeezer.service.event.PlayerStateChanged;
import uk.org.ngo.squeezer.service.event.PlayerVolume;
import uk.org.ngo.squeezer.service.event.PlaylistChanged;
import uk.org.ngo.squeezer.service.event.PowerStatusChanged;
import uk.org.ngo.squeezer.service.event.RepeatStatusChanged;
import uk.org.ngo.squeezer.service.event.ShuffleStatusChanged;
import uk.org.ngo.squeezer.service.event.SleepTimeChanged;

abstract class BaseClient implements SlimClient {
    final static int mPageSize = Squeezer.getInstance().getResources().getInteger(R.integer.PageSize);

    final AtomicReference<String> username = new AtomicReference<>();
    final AtomicReference<String> password = new AtomicReference<>();

    final ConnectionState mConnectionState;

    /** Shared event bus for status changes. */
    @NonNull final SqueezerRepository repository;

    /** The prefix for URLs for downloads and cover art. */
    String mUrlPrefix;

    BaseClient(@NonNull SqueezerRepository repository) {
        this.repository = repository;
        mConnectionState = new ConnectionState(repository);
    }

    @Override
    public ConnectionState getConnectionState() {
        return mConnectionState;
    }

    @Override
    public <T> void requestItems(Player player, String[] cmd, Map<String, Object> params, int start, int pageSize, IServiceItemListCallback<T> callback) {
        final BaseClient.BrowseRequest<T> browseRequest = new BaseClient.BrowseRequest<>(player, cmd, params, start, pageSize, callback);
        internalRequestItems(browseRequest);
    }

    protected abstract <T> void internalRequestItems(BrowseRequest<T> browseRequest);

    @Override
    public String getUsername() {
        return username.get();
    }

    @Override
    public String getPassword() {
        return password.get();
    }

    @Override
    public String getUrlPrefix() {
        return mUrlPrefix;
    }

    void parseStatus(final Player player, CurrentTrack currentSong, Map<String, Object> tokenMap) {
        PlayerState playerState = player.getPlayerState();
        playerState.statusSeen = SystemClock.elapsedRealtime() / 1000.0;

        boolean changedPower = playerState.setPoweredOn(Util.getInt(tokenMap, "power") == 1);
        boolean changedShuffleStatus = playerState.setShuffleStatus(Util.getString(tokenMap, "playlist shuffle"));
        boolean changedRepeatStatus = playerState.setRepeatStatus(Util.getString(tokenMap, "playlist repeat"));
        boolean changedPlaylist = playerState.setCurrentPlaylistTimestamp(Util.getLong(tokenMap, "playlist_timestamp"));
        playerState.setCurrentPlaylistTracksNum(Util.getInt(tokenMap, "playlist_tracks"));
        boolean changedPlaylistIndex = playerState.setCurrentPlaylistIndex(Util.getInt(tokenMap, "playlist_cur_index"));
        playerState.setCurrentPlaylist(Util.getString(tokenMap, "playlist_name"));
        boolean changedSleep = playerState.setSleep(Util.getInt(tokenMap, "will_sleep_in"));
        boolean changedSleepDuration = playerState.setSleepDuration(Util.getInt(tokenMap, "sleep"));
        if (currentSong == null) {
            if (playerState.getCurrentPlaylistTracksNum() == 0) {
                currentSong = new CurrentTrack(tokenMap);
            } else if (!changedPlaylistIndex && playerState.getCurrentTrack() != null && !playerState.getCurrentTrack().getName().isEmpty()) {
                currentSong = playerState.getCurrentTrack();
            } else {
                currentSong = new CurrentTrack(tokenMap);
            }
        } else if (!changedPlaylistIndex && playerState.getCurrentTrack() != null && playerState.getCurrentTrack().isSameSong(currentSong)) {
            currentSong.songInfo = playerState.getCurrentTrack().songInfo;
        }
        boolean changedSong = playerState.setCurrentSong(currentSong);
        playerState.setRemote(Util.getInt(tokenMap, "remote") == 1);
        playerState.waitingToPlay = Util.getInt(tokenMap, "waitingToPlay") == 1;
        playerState.rate = Util.getDouble(tokenMap, "rate");
        boolean changedSongDuration = playerState.setCurrentSongDuration(Util.getInt(tokenMap, "duration"));
        boolean changedSongTime = playerState.setCurrentTimeSecond(Util.getDouble(tokenMap, "time"));
        boolean changedVolume = playerState.setCurrentVolume(Util.getInt(tokenMap, "mixer volume"));
        boolean changedSyncMaster = playerState.setSyncMaster(Util.getString(tokenMap, "sync_master"));
        boolean changedSyncSlaves = playerState.setSyncSlaves(Arrays.stream(Util.getStringOrEmpty(tokenMap, "sync_slaves").split(",")).filter(it -> !it.isEmpty()).collect(Collectors.toList()));
        boolean changedPlayStatus = updatePlayStatus(playerState, Util.getStringOrEmpty(tokenMap, "mode"));

        // Playing status
        if (changedPlayStatus) {
            repository.post(new PlayStatusChanged(playerState.getPlayStatus(), player));
        }

        // Current playlist
        if (changedPlaylist) {
            repository.post(new PlaylistChanged(player));
        }

        if (changedPower || changedSleep || changedSleepDuration || changedVolume
                || changedSong || changedSongDuration || changedSongTime
                || changedSyncMaster || changedSyncSlaves) {
            postPlayerStateChanged(player);
        }

        // Volume
        if (changedVolume) {
            repository.post(new PlayerVolume(player));
        }

        // Power status
        if (changedPower) {
            repository.post(new PowerStatusChanged(player));
        }

        // Current song
        if (changedSong) {
            handleChangedSong(player);
        }

        // Shuffle status.
        if (changedShuffleStatus) {
            repository.post(new ShuffleStatusChanged(player, playerState.getShuffleStatus()));
        }

        // Repeat status.
        if (changedRepeatStatus) {
            repository.post(new RepeatStatusChanged(player, playerState.getRepeatStatus()));
        }

        // Position in song
        if (changedSongDuration || changedSongTime || changedPlayStatus) {
            postSongTimeChanged(player);
        }

        // Sleep times
        if (changedSleep || changedSleepDuration) {
            postSleepTimeChanged(player);
        }
    }

    protected abstract void handleChangedSong(Player player);

    protected void postSongTimeChanged(Player player) {
        repository.post(player.getTrackElapsed());
    }

    protected void postSleepTimeChanged(Player player) {
        repository.post(new SleepTimeChanged(player));
    }

    protected void postPlayerStateChanged(Player player) {
        repository.post(new PlayerStateChanged(player));
    }

    private boolean updatePlayStatus(PlayerState playerState, String playStatus) {
        // Handle unknown states.
        if (!playStatus.equals(PlayerState.PLAY_STATE_PLAY) &&
                !playStatus.equals(PlayerState.PLAY_STATE_PAUSE) &&
                !playStatus.equals(PlayerState.PLAY_STATE_STOP)) {
            return false;
        }

        return playerState.setPlayStatus(playStatus);
    }

    protected static class BrowseRequest<T> extends SlimCommand {
        private final Player player;
        private final boolean fullList;
        private int start;
        private int itemsPerResponse;
        private final IServiceItemListCallback<T> callback;

        BrowseRequest(Player player, String[] cmd, Map<String, Object> params, int start, int itemsPerResponse, IServiceItemListCallback<T> callback) {
            this.player = player;
            this.cmd(cmd);
            this.fullList = (start == ALL_ITEMS);
            this.start = start;
            this.itemsPerResponse = itemsPerResponse;
            this.callback = callback;
            if (params != null) this.params(params);
        }

        public BrowseRequest<T> update(int start, int itemsPerResponse) {
            this.start = start;
            this.itemsPerResponse = itemsPerResponse;
            return this;
        }

        public Player getPlayer() {
            return player;
        }

        boolean isFullList() {
            return (fullList);
        }

        boolean isCurrent() {
            return (start == CURRENT);
        }

        public int getStart() {
            return (start < 0 ? 0 : start);
        }

        int getItemsPerResponse() {
            return itemsPerResponse;
        }

        public IServiceItemListCallback<T> getCallback() {
            return callback;
        }
    }
}
