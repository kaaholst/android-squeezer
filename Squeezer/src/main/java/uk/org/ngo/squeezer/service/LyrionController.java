/*
 * Copyright (c) 2017 Kurt Aaholst <kaaholst@gmail.com>
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

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import uk.org.ngo.squeezer.Preferences;
import uk.org.ngo.squeezer.Squeezer;
import uk.org.ngo.squeezer.SqueezerRepository;
import uk.org.ngo.squeezer.Util;
import uk.org.ngo.squeezer.itemlist.ItemListCallback;
import uk.org.ngo.squeezer.model.Action;
import uk.org.ngo.squeezer.model.Alarm;
import uk.org.ngo.squeezer.model.AlarmPlaylist;
import uk.org.ngo.squeezer.model.CustomJiveItemHandling;
import uk.org.ngo.squeezer.model.JiveItem;
import uk.org.ngo.squeezer.model.LyrionPlayer;
import uk.org.ngo.squeezer.model.PlayerState;
import uk.org.ngo.squeezer.model.SlimCommand;
import uk.org.ngo.squeezer.service.event.ActivePlayerChanged;
import uk.org.ngo.squeezer.service.event.ConnectionChanged;
import uk.org.ngo.squeezer.service.event.LastscanChanged;
import uk.org.ngo.squeezer.service.event.MusicChanged;
import uk.org.ngo.squeezer.service.event.PlayStatusChanged;
import uk.org.ngo.squeezer.service.event.PlayerStateChanged;
import uk.org.ngo.squeezer.service.event.PlayerVolume;
import uk.org.ngo.squeezer.service.event.PlayersChanged;
import uk.org.ngo.squeezer.util.Scrobble;

public class LyrionController {
    private static final String TAG = LyrionController.class.getSimpleName();

    private final Squeezer appContext;
    private final SlimClient slimClient;
    private final SqueezerRepository repository;
    private final CallStateHelper callStateHelper;
    private final DownloadHelper downloadHelper;
    private final RandomPlayDelegate randomPlayDelegate;

    private boolean scrobblingEnabled;
    private boolean scrobblingPreviouslyEnabled;
    private int fadeInSecs;
    private boolean groupVolume;

    public LyrionController(Squeezer appContext, Preferences preferences) {
        this.appContext = appContext;
        repository = appContext.repository();
        slimClient = new CometClient(repository);
        callStateHelper = new CallStateHelper(this);
        downloadHelper = new DownloadHelper(Squeezer.instance(), this);
        randomPlayDelegate = new RandomPlayDelegate(this);

        cachePreferences(preferences);
        homeMenuHandling().setCustomShortcuts(preferences.homeGroups(), preferences.getCustomShortcuts());

        appContext.postToMainThread(() -> {
            repository.observeForever(this::onConnectionChanged);
            repository.observeForever(this::onMusicChanged);
            repository.observeForever(this::onPlayStatusChanged);
            repository.observeForever(this::onPlayerStateChanged);
            repository.observeForever(this::onActivePlayerChanged);
            repository.observeForever(this::onPlayersChanged);
            repository.observeForever(this::onLastscanChanged);
        });
    }

    private void onConnectionChanged(ConnectionChanged event) {
        boolean active = event.connectionState.isConnected() || event.connectionState.isConnectInProgress() || event.connectionState.isRehandshaking();
        Consumer<Context> registrator = active ? callStateHelper::registerCallStateListener : callStateHelper::unregisterCallStateListener;
        registrator.accept(appContext);
    }

    private void onActivePlayerChanged(ActivePlayerChanged event) {
        updateScrobbling();
    }

    private void onMusicChanged(MusicChanged event) {
        if (event.player.equals(getActivePlayer())) {
            updateScrobbling();
        }
        if (event.player.getPlayerState().isRandomPlaying()) {
            randomPlayDelegate.handleRandomOnEvent(event.player);
        }
    }

    private void onPlayStatusChanged(PlayStatusChanged event) {
        callStateHelper.mutedPlayers.remove(event.player.getId());
        if (event.player.equals(getActivePlayer())) {
            updateScrobbling();
        }
    }

    private void onPlayerStateChanged(PlayerStateChanged event) {
        if (event.player.equals(getActivePlayer())) {
            updateScrobbling();
        }
    }

    private void onPlayersChanged(PlayersChanged event) {
        LyrionPlayer activePlayer = getActivePlayer();
        if (activePlayer == null) {
            // Figure out the new active player, let everyone know.
            changeActivePlayer(getPreferredPlayer(getPlayers()), false);
        } else {
            activePlayer = getPlayer(activePlayer.getId());
            setActivePlayer(activePlayer);
            updateAllPlayerSubscriptionStates();
            requestPlayerData();
        }
    }

    private void onLastscanChanged(LastscanChanged event) {
        CustomJiveItemHandling.recoverShortcuts(this, homeMenuHandling().getCustomShortcuts());
    }

    /**
     * @return The player that should be chosen as the (new) active player. This is either the
     *     last active player (if known), the first player the server knows about if there are
     *     connected players, or null if there are no connected players.
     */
    private @Nullable LyrionPlayer getPreferredPlayer(Collection<LyrionPlayer> players) {
        final String lastConnectedPlayer = Squeezer.instance().preferences().getLastPlayer();
        Log.i(TAG, "lastConnectedPlayer was: " + lastConnectedPlayer);

        Log.i(TAG, "players empty?: " + players.isEmpty());
        for (LyrionPlayer player : players) {
            if (player.getId().equals(lastConnectedPlayer)) {
                return player;
            }
        }
        return !players.isEmpty() ? players.iterator().next() : null;
    }

    private void updateScrobbling() {
        LyrionPlayer player = getActivePlayer();
        if (player == null) return;

        // Update scrobble state, if either we're currently scrobbling, or we
        // were (to catch the case where we started scrobbling a song, and the
        // user went in to settings to disable scrobbling).
        if (scrobblingEnabled || scrobblingPreviouslyEnabled) {
            scrobblingPreviouslyEnabled = scrobblingEnabled;
            Scrobble.scrobbleFromPlayerState(appContext, player.getPlayerState());
        }

    }

    private String fadeInSecs() {
        return fadeInSecs > 0 ? " " + fadeInSecs : "";
    }

    public void startConnect(boolean autoConnect) {
        slimClient.startConnect(autoConnect);
    }

    public void disconnect(boolean fromUser) {
        slimClient.disconnect(fromUser);
    }

    public void stopServer() {
        if (!isConnected()) return;
        command().cmd("stopserver").exec();
    }

    public void restartServer() {
        if (!isConnected()) return;
        command().cmd("restartserver").exec();
    }

    public void preferenceChanged(Preferences preferences, String key) {
        Log.i(TAG, "Preference changed: " + key);
        if (Preferences.KEY_CUSTOMIZE_HOME_MENU_MODE.equals(key)) {
            boolean useArchive = preferences.getCustomizeHomeMenuMode() != Preferences.CustomizeHomeMenuMode.DISABLED;
            Set<String> archivedMenuItems = Collections.emptySet();
            if ((useArchive) && (getActivePlayer() != null)) {
                archivedMenuItems = preferences.getArchivedMenuItems(getActivePlayer());
            }
            homeMenuHandling().updateArchivedItems(archivedMenuItems);
        } else if (Preferences.KEY_CUSTOMIZE_SHORTCUT_MODE.equals(key)) {
            if (preferences.getCustomizeShortcutsMode() == Preferences.CustomizeShortcutsMode.DISABLED) {
                homeMenuHandling().removeAllShortcuts();
                preferences.saveShortcuts(homeMenuHandling().getCustomShortcuts());
            }
        } else if (Preferences.KEY_ACTION_ON_INCOMING_CALL.equals(key)) {
            if (preferences.getActionOnIncomingCall() != Preferences.IncomingCallAction.NONE) {
                callStateHelper.registerCallStateListener(appContext);
            }
        } else {
            cachePreferences(preferences);
        }
    }

    /**
     * Cache the value of various preferences.
     */
    private void cachePreferences(Preferences preferences) {
        scrobblingEnabled = preferences.isScrobbleEnabled();
        fadeInSecs = preferences.getFadeInSecs();
        groupVolume = preferences.isGroupVolume();
    }

    public void cancelClientRequests(Object client) {
        slimClient.cancelClientRequests(client);
    }


    public void requestServerStatus() {
        slimClient.requestServerStatus();
    }

    public void togglePower(LyrionPlayer player) {
        command(player).cmd("power").exec();
    }

    public void playerRename(LyrionPlayer player, String newName) {
        command(player).cmd("name", newName).exec();
    }

    public void sleep(LyrionPlayer player, int duration) {
        command(player).cmd("sleep", String.valueOf(duration)).exec();
    }

    public void syncPlayerToPlayer(@NonNull LyrionPlayer slave, @NonNull String masterId) {
        LyrionPlayer master = getPlayer(masterId);
        command(master).cmd("sync", slave.getId()).exec();
    }

    public void unsyncPlayer(@NonNull LyrionPlayer player) {
        command(player).cmd("sync", "-").exec();
    }

    public void togglePausePlay() {
        togglePausePlay(getActivePlayer());
    }

    public void togglePausePlay(LyrionPlayer player) {
        if (!isConnected()) return;


        // May be null (e.g., connected to a server with no connected
        // players. TODO: Handle this better, since it's not obvious in the
        // UI.
        if (player == null) return;

        PlayerState activePlayerState = player.getPlayerState();
        @PlayerState.PlayState String playStatus = activePlayerState.getPlayStatus();

        // May be null -- race condition when connecting to a server that
        // has a player. Squeezer knows the player exists, but has not yet
        // determined its state.
        if (playStatus == null) return;

        switch (playStatus) {
            case PlayerState.PLAY_STATE_PLAY ->
                // NOTE: we never send ambiguous "pause" toggle commands (without the '1')
                // because then we'd get confused when they came back in to us, not being
                // able to differentiate ours coming back on the listen channel vs. those
                // of those idiots at the dinner party messing around.
                    command(player).cmd("pause", "1").exec();
            case PlayerState.PLAY_STATE_STOP ->
                    command(player).cmd("play", fadeInSecs()).exec();
            case PlayerState.PLAY_STATE_PAUSE ->
                    command(player).cmd("pause", "0", fadeInSecs()).exec();
        }
    }

    public boolean play() {
        if (!isConnected()) return false;

        LyrionPlayer player = getActivePlayer();
        if (player != null) {
            String playStatus = player.getPlayerState().getPlayStatus();
            command(player)
                    .cmd(PlayerState.PLAY_STATE_PAUSE.equals(playStatus) ? List.of("pause", "0") : List.of("play"))
                    .cmd(fadeInSecs()).exec();
        }

        return true;
    }

    public boolean pause() {
        if(!isConnected()) return false;
        pause(getActivePlayer(), true);
        return true;
    }

    public void pause(LyrionPlayer player, boolean pause) {
        command(player).cmd("pause", pause ? "1" : "0", fadeInSecs()).exec();
    }

    public void mute(LyrionPlayer player, boolean mute) {
        if (player != null) {
            command(player).cmd("mixer", "muting", mute ? "1" : "0").exec();
        }
    }

    public void toggleMute() {
        toggleMute(getActivePlayer());
    }

    public void toggleMute(LyrionPlayer player) {
        if (player != null) {
            mute(player, !player.getPlayerState().isMuted());
        }
    }

    public boolean canAdjustVolumeForSyncGroup() {
        if (getActivePlayer() != null && getActivePlayer().isSyncVolume()) return false;
        return (getSyncGroup().size() > 1);
    }

    public void setVolumeTo(int percentage) {
        Set<LyrionPlayer> syncGroup = getVolumeSyncGroup();

        int lowestVolume = 100;
        int higestVolume = 0;
        for (LyrionPlayer player : syncGroup) {
            int currentVolume = player.getPlayerState().getCurrentVolume();
            if (currentVolume < lowestVolume) lowestVolume = currentVolume;
            if (currentVolume > higestVolume) higestVolume = currentVolume;
        }
        int volumeInRange = (int) Math.round(percentage / 100.0 * (100 - (higestVolume - lowestVolume)));
        for (LyrionPlayer player : syncGroup) {
            int currentVolume = player.getPlayerState().getCurrentVolume();
            int volumeOffset = currentVolume - lowestVolume;
            setPlayerVolume(player, volumeOffset + volumeInRange);
        }
    }

    public void setPlayerVolume(LyrionPlayer player, int percentage) {
        int volume = Math.min(100, Math.max(0, percentage));
        command(player).cmd("mixer", "volume", String.valueOf(volume)).exec();
        player.getPlayerState().setCurrentVolume(volume);
        repository.post(new PlayerVolume(player));
    }

    public void adjustVolume(int adjust) {
        adjust = adjust * Squeezer.instance().preferences().getVolumeIncrements();
        Set<LyrionPlayer> syncGroup = getVolumeSyncGroup();
        for (LyrionPlayer player : syncGroup) {
            int currentVolume = player.getPlayerState().getCurrentVolume();
            if (currentVolume + adjust < 0) adjust = -currentVolume;
            if (currentVolume + adjust > 100) adjust = 100 - currentVolume;
        }
        if (adjust != 0) {
            for (LyrionPlayer player : syncGroup) {
                if (player.getPlayerState().isMuted()) {
                    command(player).cmd("mixer", "muting", "0").exec();
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Log.i(TAG, "Interupted while pausing between commands");
                    }
                }
                adjustPlayerVolume(player, adjust);
            }
        }
    }

    private void adjustPlayerVolume(LyrionPlayer player, int adjust) {
        command(player).cmd("mixer", "volume", (adjust > 0 ? "+" : "") + adjust).exec();
        int currentVolume = player.getPlayerState().getCurrentVolume();
        player.getPlayerState().setCurrentVolume(currentVolume + adjust);
        repository.post(new PlayerVolume(player));
    }

    public void nextTrack() {
        nextTrack(getActivePlayer());
    }

    public void nextTrack(LyrionPlayer player) {
        if (!isConnected() || !isPlaying()) return;
        command(player).cmd("button", "jump_fwd").exec();
    }

    public void previousTrack() {
        previousTrack(getActivePlayer());
    }

    public void previousTrack(LyrionPlayer player) {
        if (!isConnected() || !isPlaying()) return;
        command(player).cmd("button", "jump_rew").exec();
    }

    public void toggleShuffle() {
        if (!isConnected()) return;
        activePlayerCommand().cmd("button", "shuffle").exec();
    }

    public void toggleRepeat() {
        if (!isConnected()) return;
        activePlayerCommand().cmd("button", "repeat").exec();
    }

    public void setSecondsElapsed(int seconds) {
        if (isConnected() && seconds >= 0) {
            activePlayerCommand().cmd("time", String.valueOf(seconds)).exec();
        }
    }

    public void adjustSecondsElapsed(int seconds) {
        if (isConnected()) {
            activePlayerCommand().cmd("time", (seconds > 0 ? "+" : "") + seconds).exec();
        }
    }

    public String getCurrentPlaylist() {
        PlayerState playerState = getActivePlayerState();

        if (playerState == null)
            return null;

        return playerState.getCurrentPlaylist();
    }

    /**
     * Start playing the song in the current playlist at the given index.
     *
     * @param index the index to jump to
     */
    public void playlistIndex(int index) {
        if (!isConnected()) return;
        activePlayerCommand().cmd("playlist", "index", String.valueOf(index), fadeInSecs()).exec();
    }

    public void playlistRemove(int index) {
        if (!isConnected()) return;
        activePlayerCommand().cmd("playlist", "delete", String.valueOf(index)).exec();
    }

    public void playlistMove(int fromIndex, int toIndex) {
        if (!isConnected()) return;
        activePlayerCommand().cmd("playlist", "move", String.valueOf(fromIndex), String.valueOf(toIndex)).exec();
    }

    public void playlistClear() {
        if (!isConnected()) return;
        activePlayerCommand().cmd("playlist", "clear").exec();
    }

    public void playlistSave(String name) {
        if (!isConnected()) return;
        activePlayerCommand().cmd("playlist", "save", name).exec();
    }

    public boolean button(LyrionPlayer player, IRButton button) {
        if (!isConnected()) {
            return false;
        }
        command(player).cmd("button", button.getFunction()).exec();
        return true;
    }


    public boolean isManualDisconnect() {
        return getConnectionState().isManualDisconnect();
    }

    public boolean isConnected() {
        return getConnectionState().isConnected();
    }

    public boolean isConnectInProgress() {
        return getConnectionState().isConnectInProgress();
    }

    public boolean isPlaying() {
        PlayerState playerState = getActivePlayerState();
        return playerState != null && playerState.isPlaying();
    }

    ConnectionState.State getConnectionState() {
        return slimClient.getConnectionState().getState();
    }

    public boolean canAutoConnect() {
        return slimClient.getConnectionState().canAutoConnect();
    }

    Command command(LyrionPlayer player) {
        return new Command(slimClient, player);
    }

    Command command() {
        return new Command(slimClient);
    }

    /** If there is an active player call {@link #command(LyrionPlayer)} with the active player */
    Command activePlayerCommand() {
        return new PlayerCommand(slimClient, slimClient.getConnectionState().getActivePlayer());
    }

    <T> Request<T> requestItems(LyrionPlayer player, int start, ItemListCallback<T> callback) {
        return new Request<>(slimClient, player, start, CometClient.mPageSize, callback);
    }

    private <T> Request<T> requestItems(LyrionPlayer player, ItemListCallback<T> callback) {
        return new Request<>(slimClient, player, 0, CometClient.mPageSize, callback);
    }

    <T> Request<T> requestAllItems(ItemListCallback<T> callback) {
        return new Request<>(slimClient, null, CometClient.ALL_ITEMS, CometClient.mPageSize, callback);
    }

    private <T> Request<T> requestItems(ItemListCallback<T> callback) {
        return new Request<>(slimClient, null, 0, CometClient.mPageSize, callback);
    }

    public <T> void requestItems(SlimCommand command, ItemListCallback<T> callback) {
        requestAllItems(callback).params(command.params).cmd(command.cmd()).exec();
    }

    /* Start an asynchronous fetch of the slimserver generic menu items */
    public void pluginItems(int start, String cmd, ItemListCallback<JiveItem> callback) {
        requestItems(getActivePlayer(), start, callback).cmd(cmd).param("menu", "menu").exec();
    }

    /* Start an asynchronous fetch of the slimserver generic menu items */
    public void pluginItems(int start, JiveItem item, Action action, ItemListCallback<JiveItem> callback) {
        requestItems(getActivePlayer(), start, callback).cmd(action.action.cmd).params(action.action.params(item.inputValue)).exec();
    }

    public void pluginItems(Action action, ItemListCallback<JiveItem> callback) {
        // We cant use paging for context menu items as LMS does some "magic"
        // See XMLBrowser.pm ("xmlBrowseInterimCM" and  "# Cannot do this if we might screw up paging")
        requestItems(getActivePlayer(), callback).cmd(action.action.cmd).params(action.action.params).exec();
    }

    public void action(JiveItem item, Action action) {
        if (!isConnected()) {
            return;
        }
        command(getActivePlayer()).cmd(action.action.cmd).params(action.action.params(item.inputValue)).exec();
    }

    public void action(Action.JsonAction action) {
        if (!isConnected()) {
            return;
        }
        command(getActivePlayer()).cmd(action.cmd).params(action.params).exec();
    }

    public void alarms(int start, ItemListCallback<Alarm> callback) {
        if (!isConnected()) {
            return;
        }
        requestItems(getActivePlayer(), start, callback).cmd("alarms").param("filter", "all").exec();
    }

    public void alarmPlaylists(ItemListCallback<AlarmPlaylist> callback) {
        if (!isConnected()) {
            return;
        }
        // The LMS documentation states that
        // The "alarm playlists" returns all the playlists, sounds, favorites etc. available to alarms.
        // This will however return only one playlist: the current playlist.
        // Inspection of the LMS code reveals that the "alarm playlists" command takes the
        // customary <start> and <itemsPerResponse> parameters, but these are interpreted as
        // categories (eg. Favorites, Natural Sounds etc.), but the returned list is flattened,
        // i.e. contains all items of the requested categories.
        // So we order all playlists without paging.
        requestItems(callback).cmd("alarm", "playlists").exec();
    }

    public void alarmAdd(int time) {
        if (!isConnected()) {
            return;
        }
        activePlayerCommand().cmd("alarm", "add").param("time", time).exec();
    }

    public void alarmDelete(String id) {
        if (!isConnected()) {
            return;
        }
        activePlayerCommand().cmd("alarm", "delete").param("id", id).exec();
    }

    public void alarmSetTime(String id, int time) {
        if (!isConnected()) {
            return;
        }
        activePlayerCommand().cmd("alarm", "update").param("id", id).param("time", time).exec();
    }

    public void alarmAddDay(String id, int day) {
        activePlayerCommand().cmd("alarm", "update").param("id", id).param("dowAdd", day).exec();
    }

    public void alarmRemoveDay(String id, int day) {
        activePlayerCommand().cmd("alarm", "update").param("id", id).param("dowDel", day).exec();
    }

    public void alarmEnable(String id, boolean enabled) {
        activePlayerCommand().cmd("alarm", "update").param("id", id).param("enabled", enabled ? "1" : "0").exec();
    }

    public void alarmRepeat(String id, boolean repeat) {
        activePlayerCommand().cmd("alarm", "update").param("id", id).param("repeat", repeat ? "1" : "0").exec();
    }

    public void alarmSetPlaylist(String id, AlarmPlaylist playlist) {
        activePlayerCommand().cmd("alarm", "update").param("id", id)
                .param("url", "".equals(playlist.getId()) ? "0" : playlist.getId()).exec();
    }

    public void downloadItem(JiveItem item) {
        downloadHelper.downloadItem(item);
    }

    public Boolean randomPlayFolder(JiveItem item) {
        SlimCommand command = item.randomPlayFolderCommand();
        String folderID = Util.getString(command.params, "folder_id");
        if (folderID == null) {
            Log.e(TAG, "randomPlayFolder: No folder_id");
            return false;
        }
        Set<String> played = Squeezer.instance().preferences().loadRandomPlayed(folderID);
        LyrionPlayer player = getActivePlayer();
        RandomPlay randomPlay = getRandomPlay(player);
        randomPlay.reset(player);
        RandomPlay.RandomPlayCallback randomPlayCallback
                = randomPlay.new RandomPlayCallback(randomPlayDelegate, folderID, played);
        requestAllItems(randomPlayCallback)
                .params(command.params)
                .cmd(command.cmd())
                .exec();
        return true;
    }

    public void toggleArchiveItem(JiveItem item) {
        Set<String> archive = homeMenuHandling().toggleArchiveItem(item);
        Squeezer.instance().preferences().setArchivedMenuItems(archive, getActivePlayer());
    }

    public boolean isInArchive(JiveItem item) {
        return homeMenuHandling().isInArchive(item);
    }

    public void setCustomShortcuts() {
        Preferences preferences = Squeezer.instance().preferences();
        homeMenuHandling().updateShortcuts(preferences.homeGroups(), preferences.getCustomShortcuts());
    }

    public void removeCustomShortcut(JiveItem item) {
        homeMenuHandling().removeShortcut(item);
        Squeezer.instance().preferences().saveShortcuts(homeMenuHandling().getCustomShortcuts());
    }

    public boolean addCustomShortcut(JiveItem item, JiveItem parent, int shortcutWeight) {
        Preferences preferences = Squeezer.instance().preferences();
        if (homeMenuHandling().addShortcut(item, parent, shortcutWeight)) {
            preferences.saveShortcuts(homeMenuHandling().getCustomShortcuts());
            return true;
        }
        return false;
    }

    public boolean isCustomShortcut(JiveItem item) {
        return homeMenuHandling().isCustomShortcut(item);
    }

    public void updateShortCut(JiveItem item, Map<String, Object> record) {
        Preferences preferences = Squeezer.instance().preferences();
        List<JiveItem> shortcuts = homeMenuHandling().updateShortcut(item, record);
        preferences.saveShortcuts(shortcuts);
    }

    public void playerPref(LyrionPlayer.Pref playerPref, String value) {
        activePlayerCommand().cmd("playerpref", playerPref.prefName(), value).exec();
    }

    public void playerPref(LyrionPlayer player, LyrionPlayer.Pref playerPref, String value) {
        command(player).cmd("playerpref", playerPref.prefName(), value).exec();
    }

    public PlayerState getActivePlayerState() {
        LyrionPlayer activePlayer = getActivePlayer();
        return activePlayer == null ? null : activePlayer.getPlayerState();
    }

    public LyrionPlayer getActivePlayer() {
        return slimClient.getConnectionState().getActivePlayer();
    }

    private void setActivePlayer(LyrionPlayer player) {
        slimClient.getConnectionState().setActivePlayer(player);
    }

    public void setActivePlayer(@Nullable final LyrionPlayer newActivePlayer, boolean continuePlaying) {
        changeActivePlayer(newActivePlayer, continuePlaying);
    }

    /**
     * Change the player that is controlled by Squeezer (the "active" player).
     *
     * @param newActivePlayer The new active player. May be null, in which case no players are controlled.
     * @param continuePlaying Continue playback on the supplied player
     */
    private void changeActivePlayer(@Nullable final LyrionPlayer newActivePlayer, boolean continuePlaying) {
        LyrionPlayer prevActivePlayer = getActivePlayer();

        // Do nothing if the player hasn't actually changed.
        if (prevActivePlayer == newActivePlayer) {
            return;
        }

        Log.i(TAG, "Active player now: " + newActivePlayer);
        setActivePlayer(newActivePlayer);

        if (prevActivePlayer != null) {
            slimClient.subscribeDisplayStatus(prevActivePlayer, false);
            slimClient.subscribeMenuStatus(prevActivePlayer, false);
        }

        updateAllPlayerSubscriptionStates();
        requestPlayerData();
        if (continuePlaying && prevActivePlayer != null) moveCurrentPlaylist(prevActivePlayer, newActivePlayer);
        Squeezer.instance().preferences().setLastPlayer(newActivePlayer);
    }

    private void requestPlayerData() {
        LyrionPlayer activePlayer = getActivePlayer();

        if (activePlayer != null) {
            slimClient.subscribeDisplayStatus(activePlayer, true);
            slimClient.subscribeMenuStatus(activePlayer, true);
            slimClient.requestPlayerStatus(activePlayer);
            // Start an asynchronous fetch of the slimserver "home menu" items
            // See http://wiki.slimdevices.com/index.php/SqueezePlayAndSqueezeCenterPlugins
            new HomeMenuReceiver(activePlayer, this, homeMenuHandling());
        }
    }

    /**
     * Adjusts the subscription to players' status updates.
     */
    private void updateAllPlayerSubscriptionStates() {
        for (LyrionPlayer player : getPlayers()) {
            updatePlayerSubscription(player);
        }
    }

    /**
     * Manage subscription to a player's status updates.
     *
     * @param player player to manage.
     */
    private void updatePlayerSubscription(LyrionPlayer player) {
        // Do nothing if the player subscription type hasn't changed.
        if (player.getPlayerState().getSubscriptionType().equals(PlayerState.PlayerSubscriptionType.NOTIFY_ON_CHANGE)) {
            return;
        }

        slimClient.subscribePlayerStatus(player, PlayerState.PlayerSubscriptionType.NOTIFY_ON_CHANGE);
    }

    private void moveCurrentPlaylist(LyrionPlayer from, LyrionPlayer to) {
        syncPlayerToPlayer(to, from.getId());
        unsyncPlayer(from);
    }

    public LyrionPlayer getPlayer(String playerId) {
        return slimClient.getConnectionState().getPlayer(playerId);
    }

    public Collection<LyrionPlayer> getPlayers() {
        return slimClient.getConnectionState().getPlayers().values();
    }

    public Set<LyrionPlayer> getSyncGroup() {
        return slimClient.getConnectionState().getSyncGroup();
    }

    private Set<LyrionPlayer> getVolumeSyncGroup() {
        return slimClient.getConnectionState().getVolumeSyncGroup(groupVolume);
    }

    public @NonNull VolumeInfo getVolume() {
        return slimClient.getConnectionState().getVolume(groupVolume);
    }

    public String getUsername() {
        return slimClient.getUsername();
    }

    public String getPassword() {
        return slimClient.getPassword();
    }

    String getUrlPrefix() {
        return slimClient.getUrlPrefix();
    }

    String[] getMediaDirs() {
        return slimClient.getConnectionState().getMediaDirs();
    }

    private HomeMenuHandling homeMenuHandling() {
        return slimClient.getConnectionState().getHomeMenuHandling();
    }

    public void addItems(String folderID, Set<String> set) {
        LyrionPlayer player = slimClient.getConnectionState().getActivePlayer();
        slimClient.getConnectionState().getRandomPlay(player).addItems(folderID, set);
    }

    public Set<String> getTracks(String folderID) {
        LyrionPlayer player = slimClient.getConnectionState().getActivePlayer();
        return slimClient.getConnectionState().getRandomPlay(player).getTracks(folderID);
    }

    public RandomPlay getRandomPlay(LyrionPlayer player) {
        return slimClient.getConnectionState().getRandomPlay(player);
    }

    public void setNextTrack(LyrionPlayer player, String nextTrack) {
        slimClient.getConnectionState().getRandomPlay(player).setNextTrack(nextTrack);
    }

    public void setActiveFolderID(String folderID) {
        LyrionPlayer player = slimClient.getConnectionState().getActivePlayer();
        slimClient.getConnectionState().getRandomPlay(player).setActiveFolderID(folderID);
    }


    static class Command extends SlimCommand {
        final SlimClient slimClient;
        final protected LyrionPlayer player;

        private Command(SlimClient slimClient, LyrionPlayer player) {
            this.slimClient = slimClient;
            this.player = player;
        }

        private Command(SlimClient slimClient) {
            this(slimClient, null);
        }

        @Override
        public Command cmd(String... commandTerms) {
            super.cmd(commandTerms);
            return this;
        }

        @Override
        public Command cmd(List<String> commandTerms) {
            super.cmd(commandTerms);
            return this;
        }

        @Override
        public Command params(Map<String, Object> params) {
            super.params(params);
            return this;
        }

        @Override
        public Command param(String tag, Object value) {
            super.param(tag, value);
            return this;
        }

        protected void exec() {
            slimClient.command(player, cmd(), params);
        }
    }

    static class PlayerCommand extends Command {
        private PlayerCommand(SlimClient slimClient, LyrionPlayer player) {
            super(slimClient, player);
        }

        @Override
        protected void exec() {
            if (player != null) super.exec();
        }
    }

    static class Request<T> extends Command {
        private final ItemListCallback<T> callback;
        private final int start;
        private final int pageSize;

        private Request(SlimClient slimClient, LyrionPlayer player, int start, int pageSize, ItemListCallback<T> callback) {
            super(slimClient, player);
            this.callback = callback;
            this.start = start;
            this.pageSize = pageSize;
        }

        @Override
        protected void exec() {
            slimClient.requestItems(player, cmd.toArray(new String[0]), params, start, pageSize, callback);
        }
    }
}
