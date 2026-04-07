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

package uk.org.ngo.squeezer.service;

import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.Squeezer;
import uk.org.ngo.squeezer.SqueezerRepository;
import uk.org.ngo.squeezer.Util;
import uk.org.ngo.squeezer.model.MenuStatusMessage;
import uk.org.ngo.squeezer.model.LyrionPlayer;
import uk.org.ngo.squeezer.service.event.ActivePlayerChanged;
import uk.org.ngo.squeezer.service.event.ConnectionChanged;
import uk.org.ngo.squeezer.service.event.HandshakeComplete;
import uk.org.ngo.squeezer.service.event.LastscanChanged;
import uk.org.ngo.squeezer.service.event.PlayersChanged;
import uk.org.ngo.squeezer.service.event.RefreshEvent;
import uk.org.ngo.squeezer.util.ImageFetcher;

public class ConnectionState {

    private static final String TAG = "ConnectionState";

    ConnectionState(SqueezerRepository repository) {
        this.repository = repository;
        mHomeMenuHandling = new HomeMenuHandling(repository);
    }

    private final HomeMenuHandling mHomeMenuHandling;
    private final SqueezerRepository repository;
    private final Map<LyrionPlayer, RandomPlay> mRandomPlay = new HashMap<>();


    public final static String MEDIA_DIRS = "mediadirs";

    public enum State {
        /** User disconnected */
        MANUAL_DISCONNECT,
        /** Ordinarily disconnected from the server. */
        DISCONNECTED,
        /** A connection has been started. */
        CONNECTION_STARTED,
        /** The connection to the server did not complete. */
        CONNECTION_FAILED,
        /** The connection to the server completed, the handshake can start. */
        CONNECTION_COMPLETED,
        /** Currently trying to reestablish a previously working connection. */
        REHANDSHAKING;

        boolean isManualDisconnect() {
            return (this == MANUAL_DISCONNECT);
        }

        boolean isConnected() {
            return (this == CONNECTION_COMPLETED);
        }

        boolean isConnectInProgress() {
            return (this == CONNECTION_STARTED);
        }

        boolean isRehandshaking() {
            return (this == REHANDSHAKING);
        }
    }

    private volatile State state = State.DISCONNECTED;

    /** Milliseconds since boot of latest auto connect */
    private volatile long autoConnect;

    /** Minimum milliseconds between automatic connection */
    private static final long AUTO_CONNECT_INTERVAL = 60_000;

    /** Milliseconds since boot of latest start of rehandshake */
    private volatile long rehandshake;

    /** Duration before we give up rehandshake */
    private static final long REHANDSHAKE_TIMEOUT = 15 * 60_000;

    /** Map Player IDs to the {@link LyrionPlayer} with that ID. */
    private final Map<String, LyrionPlayer> mPlayers = new ConcurrentHashMap<>();

    /** The active player (the player to which commands are sent by default). */
    private final AtomicReference<LyrionPlayer> mActivePlayer = new AtomicReference<>();

    private final AtomicReference<String> serverVersion = new AtomicReference<>();

    private final AtomicReference<String[]> mediaDirs = new AtomicReference<>();

    public boolean canAutoConnect() {
        return (state == State.DISCONNECTED || state == State.CONNECTION_FAILED || state == State.REHANDSHAKING)
                && ((SystemClock.elapsedRealtime() - autoConnect) > AUTO_CONNECT_INTERVAL);
    }

    public void setAutoConnect() {
        this.autoConnect = SystemClock.elapsedRealtime();
    }

    State getState() {
        return state;
    }

    /**
     * Sets a new connection state, and posts a sticky
     * {@link uk.org.ngo.squeezer.service.event.ConnectionChanged} event with the new state.
     *
     * @param connectionState The new connection state.
     */
    void setState(State connectionState) {
        Log.i(TAG, "setConnectionState(" + state + " => " + connectionState + ")");
        updateState(connectionState);
        repository.post(new ConnectionChanged(connectionState));
    }

    void setConnectionError(ConnectionError connectionError) {
        Log.i(TAG, "setConnectionError(" + state + " => " + connectionError + ")");
        updateState(State.CONNECTION_FAILED);
        repository.post(new ConnectionChanged(connectionError));
    }

    private void updateState(State newState) {
        // Clear data if we were previously connected
        if (state.isConnected() && !newState.isConnected()) {
            repository.removeEvents();
            setServerVersion(null);
            mPlayers.clear();
            setActivePlayer(null);
        }

        // Start timer for rehandshake
        if (newState == State.REHANDSHAKING) {
            rehandshake = SystemClock.elapsedRealtime();
        }

        state = newState;
    }

    public void setPlayers(Map<String, LyrionPlayer> players) {
        mPlayers.clear();
        mPlayers.putAll(players);
        repository.post(new PlayersChanged());
    }

    LyrionPlayer getPlayer(String playerId) {
        if (playerId == null) return null;
        return mPlayers.get(playerId);
    }

    public Map<String, LyrionPlayer> getPlayers() {
        return mPlayers;
    }

    public LyrionPlayer getActivePlayer() {
        return mActivePlayer.get();
    }

    @NonNull Set<LyrionPlayer> getSyncGroup() {
        Set<LyrionPlayer> out = new HashSet<>();

        LyrionPlayer player = getActivePlayer();
        if (player != null) {
            out.add(player);

            LyrionPlayer master = getPlayer(player.getPlayerState().getSyncMaster());
            if (master != null) out.add(master);

            for (String slave : player.getPlayerState().getSyncSlaves()) {
                LyrionPlayer syncSlave = getPlayer(slave);
                if (syncSlave != null) out.add(syncSlave);
            }
        }

        return out;
    }

    @NonNull Set<LyrionPlayer> getVolumeSyncGroup(boolean groupVolume) {
        LyrionPlayer player = getActivePlayer();
        if (player != null && (player.isSyncVolume() || !groupVolume)) {
            Set<LyrionPlayer> players = new HashSet<>();
            players.add(player);
            return players;
        }

        return getSyncGroup();
    }

    public @NonNull VolumeInfo getVolume(boolean groupVolume) {
        Set<LyrionPlayer> syncGroup = getVolumeSyncGroup(groupVolume);
        int lowestVolume = 100;
        int higestVolume = 0;
        boolean muted = false;
        List<String> playerNames = new ArrayList<>();
        for (LyrionPlayer player : syncGroup) {
            int currentVolume = player.getPlayerState().getCurrentVolume();
            if (currentVolume < lowestVolume) lowestVolume = currentVolume;
            if (currentVolume > higestVolume) higestVolume = currentVolume;

            muted |= player.getPlayerState().isMuted();
            playerNames.add(player.getName());
        }

        long volume = Math.round(lowestVolume / (100.0 - (higestVolume - lowestVolume)) * 100);
        return new VolumeInfo(muted, (int) volume, TextUtils.join(", ", playerNames));
    }

    void setActivePlayer(LyrionPlayer player) {
        mActivePlayer.set(player);
        repository.post(new ActivePlayerChanged(player));
    }

    void setServerVersion(String version) {
        if (Util.atomicReferenceUpdated(serverVersion, version)) {
            if (version != null && state == State.CONNECTION_COMPLETED) {
                HandshakeComplete event = new HandshakeComplete(getServerVersion());
                Log.i(TAG, "Handshake complete: " + event);
                repository.post(event);
            }
        }
    }

    void setMediaDirs(String[] mediaDirs) {
        this.mediaDirs.set(mediaDirs);
    }

    public HomeMenuHandling getHomeMenuHandling() {
        return mHomeMenuHandling;
    }

    public RandomPlay getRandomPlay(LyrionPlayer player) {
        RandomPlay randomPlay = mRandomPlay.get(player);
        if (randomPlay != null) {
            return mRandomPlay.get(player);
        } else {
            RandomPlay newRandomPlay = new RandomPlay(player);
            mRandomPlay.put(player, newRandomPlay);
            return newRandomPlay;
        }
    }

//    For menu updates sent from LMS, handling of archived nodes needs testing!
    void menuStatusEvent(MenuStatusMessage event) {
        LyrionPlayer activePlayer = getActivePlayer();
        if (activePlayer != null && event.playerId.equals(activePlayer.getId())) {
            mHomeMenuHandling.handleMenuStatusEvent(event);
        }
    }

    String getServerVersion() {
        return serverVersion.get();
    }

    String[] getMediaDirs() {
        return mediaDirs.get();
    }

    boolean canRehandshake() {
        return state.isRehandshaking()
                && ((SystemClock.elapsedRealtime() - rehandshake) < REHANDSHAKE_TIMEOUT);
    }

    private volatile boolean rescan;
    private volatile boolean rescanned;
    private volatile long lastScan = 0;
    private volatile long savedScan = 0;

    public void initLastScan(long lastScan) {
        savedScan = lastScan;
        this.lastScan = 0;
        rescan = rescanned = false;
    }

    /**
     * Last scan time will change when browsing music folder
     * So we only flush cache and rebuild shortcut after a rescan or if
     * last scan time changed since last connection
     *
     * @param lastScan New last scanning time
     * @return true if the new last scanning time is different from stored value.
     */
    public boolean setLastScan(long lastScan) {
        if (lastScan != 0 && lastScan != savedScan) {
            Log.i(TAG, "setLastScan(" + lastScan + ") was " + savedScan);
            if (rescanned || (this.lastScan == 0)) {
                Log.i(TAG, "Flush/rebuild client side caches: " + lastScan);
                rescanned = false;
                ImageFetcher.getInstance(Squeezer.instance()).clearCache();
                repository.post(new LastscanChanged(lastScan));
            }
            this.lastScan = savedScan = lastScan;
            return true;
        }
        this.lastScan = lastScan;
        return false;
    }

    /**
     * When a scanning status is received we notify subscribers of the progress.
     * When the scanning is completed ask subscribers to refresh the contents from the server.
     * Because last scanning time will change unnecessarily we store whether there have been a rescan
     * in {@link #rescanned}
     */
    public void setRescan(boolean rescan, String progressName, String progressDone, String progressTotal) {
        if (rescan || rescan != this.rescan) {
            Log.i(TAG, "setRescan(" + (rescan ? formatScanningProgress(progressName, progressDone, progressTotal) : "done") + ")");
            this.rescan = rescan;
            if (!rescan) repository.post(new RefreshEvent());
            if (rescan) rescanned = true;
        }
    }

    private String formatScanningProgress(String progressName, String progressDone, String progressTotal) {
        if (progressName == null) return Squeezer.instance().getString(R.string.RESCANNING_SHORT);
        return progressName + (progressDone != null && progressTotal != null ? String.format(" %s/%s", progressDone, progressTotal) : "");
    }

    @NonNull
    @Override
    public String toString() {
        return "ConnectionState{" +
                "mConnectionState=" + state +
                ", serverVersion=" + serverVersion +
                '}';
    }
}
