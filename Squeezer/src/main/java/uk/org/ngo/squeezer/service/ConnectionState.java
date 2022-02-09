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

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import de.greenrobot.event.EventBus;
import uk.org.ngo.squeezer.Util;
import uk.org.ngo.squeezer.model.CustomJiveItemHandling;
import uk.org.ngo.squeezer.model.MenuStatusMessage;
import uk.org.ngo.squeezer.model.Player;
import uk.org.ngo.squeezer.model.PlayerState;
import uk.org.ngo.squeezer.service.event.ActivePlayerChanged;
import uk.org.ngo.squeezer.service.event.ConnectionChanged;
import uk.org.ngo.squeezer.service.event.HandshakeComplete;
import uk.org.ngo.squeezer.service.event.PlayersChanged;

public class ConnectionState {

    private static final String TAG = "ConnectionState";

    ConnectionState(@NonNull EventBus eventBus) {
        mEventBus = eventBus;
        mHomeMenuHandling = new HomeMenuHandling(eventBus);
    }

    private final EventBus mEventBus;
    private final HomeMenuHandling mHomeMenuHandling;
    private final Map<Player, RandomPlay> mRandomPlay = new HashMap<>();


    public final static String MEDIA_DIRS = "mediadirs";

    // Connection state machine
    @IntDef({MANUAL_DISCONNECT, DISCONNECTED, CONNECTION_STARTED, CONNECTION_FAILED, CONNECTION_COMPLETED})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ConnectionStates {}
    /** User disconnected */
    public static final int MANUAL_DISCONNECT = 0;
    /** Ordinarily disconnected from the server. */
    public static final int DISCONNECTED = 1;
    /** A connection has been started. */
    public static final int CONNECTION_STARTED = 2;
    /** The connection to the server did not complete. */
    public static final int CONNECTION_FAILED = 3;
    /** The connection to the server completed, the handshake can start. */
    public static final int CONNECTION_COMPLETED = 4;

    @ConnectionStates
    private volatile int mConnectionState = DISCONNECTED;

    /** Milliseconds since boot of latest auto connect */
    private volatile long autoConnect;

    /** Minimum milliseconds between automatic connection */
    private static final long AUTO_CONNECT_INTERVAL = 60_000;

    /** Map Player IDs to the {@link uk.org.ngo.squeezer.model.Player} with that ID. */
    private final Map<String, Player> mPlayers = new ConcurrentHashMap<>();

    /** The active player (the player to which commands are sent by default). */
    private final AtomicReference<Player> mActivePlayer = new AtomicReference<>();

    private final AtomicReference<String> serverVersion = new AtomicReference<>();

    private final AtomicReference<String[]> mediaDirs = new AtomicReference<>();

    public boolean canAutoConnect() {
        return (mConnectionState == DISCONNECTED || mConnectionState == CONNECTION_FAILED)
                && ((SystemClock.elapsedRealtime() - autoConnect) > AUTO_CONNECT_INTERVAL);
    }

    public void setAutoConnect() {
        this.autoConnect = SystemClock.elapsedRealtime();
    }

    /**
     * Sets a new connection state, and posts a sticky
     * {@link uk.org.ngo.squeezer.service.event.ConnectionChanged} event with the new state.
     *
     * @param connectionState The new connection state.
     */
    void setConnectionState(@ConnectionStates int connectionState) {
        Log.i(TAG, "setConnectionState(" + mConnectionState + " => " + connectionState + ")");
        updateConnectionState(connectionState);
        mEventBus.postSticky(new ConnectionChanged(connectionState));
    }

    void setConnectionError(ConnectionError connectionError) {
        Log.i(TAG, "setConnectionError(" + mConnectionState + " => " + connectionError.name() + ")");
        updateConnectionState(CONNECTION_FAILED);
        mEventBus.postSticky(new ConnectionChanged(connectionError));
    }

    private void updateConnectionState(@ConnectionStates int connectionState) {
        // Clear data if we were previously connected
        if (isConnected() && !isConnected(connectionState)) {
            mEventBus.removeAllStickyEvents();
            setServerVersion(null);
            mPlayers.clear();
            setActivePlayer(null);
        }
        mConnectionState = connectionState;
    }

    public void setPlayers(Map<String, Player> players) {
        mPlayers.clear();
        mPlayers.putAll(players);
        mEventBus.postSticky(new PlayersChanged());
    }

    Player getPlayer(String playerId) {
        if (playerId == null) return null;
        return mPlayers.get(playerId);
    }

    public Map<String, Player> getPlayers() {
        return mPlayers;
    }

    public Player getActivePlayer() {
        return mActivePlayer.get();
    }

    @NonNull Set<Player> getSyncGroup() {
        Set<Player> out = new HashSet<>();

        Player player = getActivePlayer();
        if (player != null) {
            out.add(player);

            Player master = getPlayer(player.getPlayerState().getSyncMaster());
            if (master != null) out.add(master);

            for (String slave : player.getPlayerState().getSyncSlaves()) {
                Player syncSlave = getPlayer(slave);
                if (syncSlave != null) out.add(syncSlave);
            }
        }

        return out;
    }

    @NonNull Set<Player> getVolumeSyncGroup(boolean groupVolume) {
        Player player = getActivePlayer();
        if (player != null && (player.isSyncVolume() || !groupVolume)) {
            Set<Player> players = new HashSet<>();
            players.add(player);
            return players;
        }

        return getSyncGroup();
    }

    public @NonNull ISqueezeService.VolumeInfo getVolume(boolean groupVolume) {
        Set<Player> syncGroup = getVolumeSyncGroup(groupVolume);
        int lowestVolume = 100;
        int higestVolume = 0;
        boolean muted = false;
        List<String> playerNames = new ArrayList<>();
        for (Player player : syncGroup) {
            int currentVolume = player.getPlayerState().getCurrentVolume();
            if (currentVolume < lowestVolume) lowestVolume = currentVolume;
            if (currentVolume > higestVolume) higestVolume = currentVolume;

            muted |= player.getPlayerState().isMuted();
            playerNames.add(player.getName());
        }

        long volume = Math.round(lowestVolume / (100.0 - (higestVolume - lowestVolume)) * 100);
        return new ISqueezeService.VolumeInfo(muted, (int) volume, TextUtils.join(", ", playerNames));
    }

    void setActivePlayer(Player player) {
        mActivePlayer.set(player);
        mEventBus.post(new ActivePlayerChanged(player));
    }

    void setServerVersion(String version) {
        if (Util.atomicReferenceUpdated(serverVersion, version)) {
            if (version != null && mConnectionState == CONNECTION_COMPLETED) {
                HandshakeComplete event = new HandshakeComplete(getServerVersion());
                Log.i(TAG, "Handshake complete: " + event);
                mEventBus.postSticky(event);
            }
        }
    }

    void setMediaDirs(String[] mediaDirs) {
        this.mediaDirs.set(mediaDirs);
    }

    public HomeMenuHandling getHomeMenuHandling() {
        return mHomeMenuHandling;
    }

    public RandomPlay getRandomPlay(Player player) {
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
        if (event.playerId.equals(getActivePlayer().getId())) {
            mHomeMenuHandling.handleMenuStatusEvent(event);
        }
    }

    String getServerVersion() {
        return serverVersion.get();
    }

    String[] getMediaDirs() {
        return mediaDirs.get();
    }

    /**
     * @return True if the socket connection to the server has completed.
     */
    boolean isConnected() {
        return isConnected(mConnectionState);
    }

    /**
     * @return True if the socket connection to the server has completed.
     */
    static boolean isConnected(@ConnectionStates int connectionState) {
        return connectionState == CONNECTION_COMPLETED;
    }

    /**
     * @return True if the socket connection to the server has started, but not yet
     *     completed (successfully or unsuccessfully).
     */
    boolean isConnectInProgress() {
        return isConnectInProgress(mConnectionState);
    }

    /**
     * @return True if the socket connection to the server has started, but not yet
     *     completed (successfully or unsuccessfully).
     */
    static boolean isConnectInProgress(@ConnectionStates int connectionState) {
        return connectionState == CONNECTION_STARTED;
    }

    @NonNull
    @Override
    public String toString() {
        return "ConnectionState{" +
                "mConnectionState=" + mConnectionState +
                ", serverVersion=" + serverVersion +
                '}';
    }
}
