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

import androidx.annotation.NonNull;

import java.util.List;
import java.util.Map;

import de.greenrobot.event.EventBus;
import uk.org.ngo.squeezer.model.JiveItem;
import uk.org.ngo.squeezer.model.SlimCommand;
import uk.org.ngo.squeezer.itemlist.IServiceItemListCallback;
import uk.org.ngo.squeezer.model.Player;
import uk.org.ngo.squeezer.model.PlayerState;

class SlimDelegate {

    @NonNull private final SlimClient mClient;

    SlimDelegate(@NonNull EventBus eventBus) {
        mClient = new CometClient(eventBus);
    }

    void startConnect(SqueezeService service) {
        mClient.startConnect(service);
    }

    void disconnect() {
        mClient.disconnect();
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

    <T> Request requestItems(Player player, int start, IServiceItemListCallback<T> callback) {
        return new Request<>(mClient, player, start, callback);
    }

    <T> Request requestItems(Player player, IServiceItemListCallback<T> callback) {
        return new Request<>(mClient, player, 0, 200, callback);
    }

    <T> Request requestItems(int start, IServiceItemListCallback<T> callback) {
        return new Request<>(mClient, start, callback);
    }

    <T> Request requestItems(IServiceItemListCallback<T> callback) {
        return new Request<>(mClient, 0, 200, callback);
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

    void setHomeMenu(List<JiveItem> items) {
        mClient.getConnectionState().setHomeMenu(items);
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

        private Request(SlimClient slimClient, Player player, int start, IServiceItemListCallback<T> callback) {
            this(slimClient, player, start, BaseClient.mPageSize, callback);
        }

        private Request(SlimClient slimClient, int start, IServiceItemListCallback<T> callback) {
            this(slimClient, null, start, BaseClient.mPageSize, callback);
        }

        private Request(SlimClient slimClient, int start, int pageSize, IServiceItemListCallback<T> callback) {
            this(slimClient, null, start, pageSize, callback);
        }

        @Override
        protected void exec() {
            slimClient.requestItems(player, cmd.toArray(new String[0]), params, start, pageSize, callback);
        }
    }
}
