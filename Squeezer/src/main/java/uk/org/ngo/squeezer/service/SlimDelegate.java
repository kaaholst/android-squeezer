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

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.greenrobot.eventbus.EventBus;
import uk.org.ngo.squeezer.itemlist.IServiceItemListCallback;
import uk.org.ngo.squeezer.model.JiveItem;
import uk.org.ngo.squeezer.model.Player;
import uk.org.ngo.squeezer.model.PlayerState;
import uk.org.ngo.squeezer.model.SlimCommand;

public class SlimDelegate {

    @NonNull private final SlimClient mClient;

    SlimDelegate(@NonNull EventBus eventBus) {
        mClient = new CometClient(eventBus);
    }

    void startConnect(SqueezeService service, boolean autoConnect) {
        mClient.startConnect(service, autoConnect);
    }

    void disconnect(boolean fromUser) {
        mClient.disconnect(fromUser);
    }

    void cancelClientRequests(Object client) {
        mClient.cancelClientRequests(client);
    }


    void requestPlayerStatus(Player player) {
        mClient.requestPlayerStatus(player);
    }

    void subscribePlayerStatus(Player player, PlayerState.PlayerSubscriptionType subscriptionType) {
        mClient.subscribePlayerStatus(player, subscriptionType);
    }

    void subscribeDisplayStatus(Player player, boolean subscribe) {
        mClient.subscribeDisplayStatus(player, subscribe);
    }

    void subscribeMenuStatus(Player player, boolean subscribe) {
        mClient.subscribeMenuStatus(player, subscribe);
    }


    boolean isConnected() {
        return mClient.getConnectionState().isConnected();
    }

    boolean isConnectInProgress() {
        return mClient.getConnectionState().isConnectInProgress();
    }

    boolean canAutoConnect() {
        return mClient.getConnectionState().canAutoConnect();
    }

    String getServerVersion() {
        return mClient.getConnectionState().getServerVersion();
    }

    Command command(Player player) {
        return new Command(mClient, player);
    }

    Command command() {
        return new Command(mClient);
    }

    /** If there is an active player call {@link #command(Player)} with the active player */
    Command activePlayerCommand() {
        return new PlayerCommand(mClient, mClient.getConnectionState().getActivePlayer());
    }

    <T> Request<T> requestItems(Player player, int start, IServiceItemListCallback<T> callback) {
        return new Request<>(mClient, player, start, BaseClient.mPageSize, callback);
    }

    <T> Request<T> requestItems(Player player, IServiceItemListCallback<T> callback) {
        return new Request<>(mClient, player, 0, BaseClient.mPageSize, callback);
    }

    <T> Request<T> requestAllItems(IServiceItemListCallback<T> callback) {
        return new Request<>(mClient, null, -1, BaseClient.mPageSize, callback);
    }

    <T> Request<T> requestItems(IServiceItemListCallback<T> callback) {
        return new Request<>(mClient, null, 0, BaseClient.mPageSize, callback);
    }

    public Player getActivePlayer() {
        return mClient.getConnectionState().getActivePlayer();
    }

    void setActivePlayer(Player player) {
        mClient.getConnectionState().setActivePlayer(player);
    }

    Player getPlayer(String playerId) {
        return mClient.getConnectionState().getPlayer(playerId);
    }

    public Map<String, Player> getPlayers() {
        return mClient.getConnectionState().getPlayers();
    }

    public Set<Player> getSyncGroup() {
        return mClient.getConnectionState().getSyncGroup();
    }

    public Set<Player> getVolumeSyncGroup(boolean groupVolume) {
        return mClient.getConnectionState().getVolumeSyncGroup(groupVolume);
    }

    public @NonNull ISqueezeService.VolumeInfo getVolume(boolean groupVolume) {
        return mClient.getConnectionState().getVolume(groupVolume);
    }

    void setHomeMenu(List<String> archivedItems,  Map<String, Map<String, Object>> customShortcuts) {
        mClient.getConnectionState().getHomeMenuHandling().setHomeMenu(archivedItems, customShortcuts);
    }

    void setHomeMenu(List<JiveItem> items, List<String> archivedItems, Map<String, Map<String, Object>> customShortcuts) {
        mClient.getConnectionState().getHomeMenuHandling().setHomeMenu(items, archivedItems, customShortcuts);
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

    List<String> toggleArchiveItem(JiveItem item) {
        return mClient.getConnectionState().getHomeMenuHandling().toggleArchiveItem(item);
    }

    public boolean isInArchive(JiveItem item) {
        return mClient.getConnectionState().getHomeMenuHandling().isInArchive(item);
    }

    public void triggerHomeMenuEvent() {
        mClient.getConnectionState().getHomeMenuHandling().triggerHomeMenuEvent();
    }

    public HomeMenuHandling getHomeMenuHandling() {
        return mClient.getConnectionState().getHomeMenuHandling();
    }

    public void removeCustomShortcut(JiveItem item) {
        mClient.getConnectionState().getHomeMenuHandling().removeCustomShortcut(item);
    }

    public int addItems(String folderID, Set<String> set) {
        Player player = mClient.getConnectionState().getActivePlayer();
        return mClient.getConnectionState().getRandomPlay(player).addItems(folderID, set);
    }

    public Set<String> getTracks(String folderID) {
        Player player = mClient.getConnectionState().getActivePlayer();
        return mClient.getConnectionState().getRandomPlay(player).getTracks(folderID);
    }

    public RandomPlay getRandomPlay(Player player) {
        return mClient.getConnectionState().getRandomPlay(player);
    }

    public void setNextTrack(Player player, String nextTrack) {
        mClient.getConnectionState().getRandomPlay(player).setNextTrack(nextTrack);
    }

    public void setActiveFolderID(String folderID) {
        Player player = mClient.getConnectionState().getActivePlayer();
        mClient.getConnectionState().getRandomPlay(player).setActiveFolderID(folderID);
    }


    static class Command extends SlimCommand {
        final SlimClient slimClient;
        final protected Player player;

        private Command(SlimClient slimClient, Player player) {
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

        private PlayerCommand(SlimClient slimClient, Player player) {
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

        private Request(SlimClient slimClient, Player player, int start, int pageSize, IServiceItemListCallback<T> callback) {
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
