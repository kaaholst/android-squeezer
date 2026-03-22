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

import android.util.Log;

import androidx.annotation.NonNull;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import uk.org.ngo.squeezer.SqueezerRepository;
import uk.org.ngo.squeezer.itemlist.IServiceItemListCallback;
import uk.org.ngo.squeezer.model.LyrionPlayer;
import uk.org.ngo.squeezer.model.PlayerState;
import uk.org.ngo.squeezer.model.SlimCommand;
import uk.org.ngo.squeezer.service.event.PlayerVolume;

class LyrionController {
    private static final String TAG = LyrionController.class.getSimpleName();

    @NonNull private final SlimClient mClient;
    private final SqueezerRepository repository;
    private int fadeInSecs;
    private boolean groupVolume;

    LyrionController(SqueezerRepository repository) {
        mClient = new CometClient(repository);
        this.repository = repository;
    }

    public void setFadeInSecs(int fadeInSecs) {
        this.fadeInSecs = fadeInSecs;
    }

    public String fadeInSecs() {
        return fadeInSecs > 0 ? " " + fadeInSecs : "";
    }

    public void setGroupVolume(boolean groupVolume) {
        this.groupVolume = groupVolume;
    }

    void startConnect(boolean autoConnect) {
        mClient.startConnect(autoConnect);
    }

    void disconnect(boolean fromUser) {
        mClient.disconnect(fromUser);
    }

    void cancelClientRequests(Object client) {
        mClient.cancelClientRequests(client);
    }


    void requestServerStatus() {
        mClient.requestServerStatus();
    }

    void requestPlayerStatus(LyrionPlayer player) {
        mClient.requestPlayerStatus(player);
    }

    void subscribePlayerStatus(LyrionPlayer player, PlayerState.PlayerSubscriptionType subscriptionType) {
        mClient.subscribePlayerStatus(player, subscriptionType);
    }

    void subscribeDisplayStatus(LyrionPlayer player, boolean subscribe) {
        mClient.subscribeDisplayStatus(player, subscribe);
    }

    void subscribeMenuStatus(LyrionPlayer player, boolean subscribe) {
        mClient.subscribeMenuStatus(player, subscribe);
    }

    public void togglePower(LyrionPlayer player) {
        command(player).cmd("power").exec();
    }

    public boolean play() {
        if (!isConnected()) {
            return false;
        }

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
        if(!isConnected()) {
            return false;
        }
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

    public boolean nextTrack(LyrionPlayer player) {
        if (!isConnected() || !isPlaying()) {
            return false;
        }
        command(player).cmd("button", "jump_fwd").exec();
        return true;
    }

    public boolean previousTrack(LyrionPlayer player) {
        if (!isConnected() || !isPlaying()) {
            return false;
        }
        command(player).cmd("button", "jump_rew").exec();
        return true;
    }

    public void setSecondsElapsed(int seconds) {
        if (isConnected() && seconds >= 0) {
            activePlayerCommand().cmd("time", String.valueOf(seconds)).exec();
        }
    }


    public boolean isConnected() {
        return getConnectionState().isConnected();
    }

    public boolean isPlaying() {
        PlayerState playerState = getActivePlayerState();
        return playerState != null && playerState.isPlaying();
    }

    ConnectionState.State getConnectionState() {
        return mClient.getConnectionState().getState();
    }

    boolean canAutoConnect() {
        return mClient.getConnectionState().canAutoConnect();
    }

    String getServerVersion() {
        return mClient.getConnectionState().getServerVersion();
    }

    Command command(LyrionPlayer player) {
        return new Command(mClient, player);
    }

    Command command() {
        return new Command(mClient);
    }

    /** If there is an active player call {@link #command(LyrionPlayer)} with the active player */
    Command activePlayerCommand() {
        return new PlayerCommand(mClient, mClient.getConnectionState().getActivePlayer());
    }

    <T> Request<T> requestItems(LyrionPlayer player, int start, IServiceItemListCallback<T> callback) {
        return new Request<>(mClient, player, start, CometClient.mPageSize, callback);
    }

    <T> Request<T> requestItems(LyrionPlayer player, IServiceItemListCallback<T> callback) {
        return new Request<>(mClient, player, 0, CometClient.mPageSize, callback);
    }

    <T> Request<T> requestAllItems(IServiceItemListCallback<T> callback) {
        return new Request<>(mClient, null, CometClient.ALL_ITEMS, CometClient.mPageSize, callback);
    }

    <T> Request<T> requestItems(IServiceItemListCallback<T> callback) {
        return new Request<>(mClient, null, 0, CometClient.mPageSize, callback);
    }

    public PlayerState getActivePlayerState() {
        LyrionPlayer activePlayer = getActivePlayer();
        return activePlayer == null ? null : activePlayer.getPlayerState();
    }

    public LyrionPlayer getActivePlayer() {
        return mClient.getConnectionState().getActivePlayer();
    }

    void setActivePlayer(LyrionPlayer player) {
        mClient.getConnectionState().setActivePlayer(player);
    }

    LyrionPlayer getPlayer(String playerId) {
        return mClient.getConnectionState().getPlayer(playerId);
    }

    public Collection<LyrionPlayer> getPlayers() {
        return mClient.getConnectionState().getPlayers().values();
    }

    public Set<LyrionPlayer> getSyncGroup() {
        return mClient.getConnectionState().getSyncGroup();
    }

    private Set<LyrionPlayer> getVolumeSyncGroup() {
        return mClient.getConnectionState().getVolumeSyncGroup(groupVolume);
    }

    public @NonNull ISqueezeService.VolumeInfo getVolume() {
        return mClient.getConnectionState().getVolume(groupVolume);
    }

    public String getUsername() {
        return mClient.getUsername();
    }

    public String getPassword() {
        return mClient.getPassword();
    }

    String getUrlPrefix() {
        return mClient.getUrlPrefix();
    }

    String[] getMediaDirs() {
        return mClient.getConnectionState().getMediaDirs();
    }

    public HomeMenuHandling getHomeMenuHandling() {
        return mClient.getConnectionState().getHomeMenuHandling();
    }

    public void addItems(String folderID, Set<String> set) {
        LyrionPlayer player = mClient.getConnectionState().getActivePlayer();
        mClient.getConnectionState().getRandomPlay(player).addItems(folderID, set);
    }

    public Set<String> getTracks(String folderID) {
        LyrionPlayer player = mClient.getConnectionState().getActivePlayer();
        return mClient.getConnectionState().getRandomPlay(player).getTracks(folderID);
    }

    public RandomPlay getRandomPlay(LyrionPlayer player) {
        return mClient.getConnectionState().getRandomPlay(player);
    }

    public void setNextTrack(LyrionPlayer player, String nextTrack) {
        mClient.getConnectionState().getRandomPlay(player).setNextTrack(nextTrack);
    }

    public void setActiveFolderID(String folderID) {
        LyrionPlayer player = mClient.getConnectionState().getActivePlayer();
        mClient.getConnectionState().getRandomPlay(player).setActiveFolderID(folderID);
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
        private final IServiceItemListCallback<T> callback;
        private final int start;
        private final int pageSize;

        private Request(SlimClient slimClient, LyrionPlayer player, int start, int pageSize, IServiceItemListCallback<T> callback) {
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
