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

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.text.TextUtils;
import android.util.Log;

import org.cometd.bayeux.Channel;
import org.cometd.bayeux.Message;
import org.cometd.bayeux.client.ClientSessionChannel;
import org.cometd.client.transport.ClientTransport;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.util.B64Code;

import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import de.greenrobot.event.EventBus;
import uk.org.ngo.squeezer.Preferences;
import uk.org.ngo.squeezer.Util;
import uk.org.ngo.squeezer.model.AlertWindow;
import uk.org.ngo.squeezer.model.DisplayMessage;
import uk.org.ngo.squeezer.itemlist.IServiceItemListCallback;
import uk.org.ngo.squeezer.model.Alarm;
import uk.org.ngo.squeezer.model.AlarmPlaylist;
import uk.org.ngo.squeezer.model.CurrentPlaylistItem;
import uk.org.ngo.squeezer.model.JiveItem;
import uk.org.ngo.squeezer.model.MusicFolderItem;
import uk.org.ngo.squeezer.model.Player;
import uk.org.ngo.squeezer.model.PlayerState;
import uk.org.ngo.squeezer.model.SlimCommand;
import uk.org.ngo.squeezer.model.Song;
import uk.org.ngo.squeezer.service.event.AlertEvent;
import uk.org.ngo.squeezer.service.event.DisplayEvent;
import uk.org.ngo.squeezer.service.event.HandshakeComplete;
import uk.org.ngo.squeezer.model.MenuStatusMessage;
import uk.org.ngo.squeezer.service.event.PlayerVolume;
import uk.org.ngo.squeezer.service.event.RegisterSqueezeNetwork;
import uk.org.ngo.squeezer.util.FluentHashMap;
import uk.org.ngo.squeezer.util.Reflection;
import uk.org.ngo.squeezer.util.SendWakeOnLan;

class CometClient extends BaseClient {
    private static final String TAG = CometClient.class.getSimpleName();

    /** {@link java.util.regex.Pattern} that splits strings on forward slash. */
    private static final Pattern mSlashSplitPattern = Pattern.compile("/");

    /** The channel to publish one-shot requests to. */
    private static final String CHANNEL_SLIM_REQUEST = "/slim/request";

    /** The format string for the channel to listen to for responses to one-shot requests. */
    private static final String CHANNEL_SLIM_REQUEST_RESPONSE_FORMAT = "/%s/slim/request/%s";

    /** The channel to publish subscription requests to. */
    private static final String CHANNEL_SLIM_SUBSCRIBE = "/slim/subscribe";

    /** The channel to publish unsubscribe requests to. */
    private static final String CHANNEL_SLIM_UNSUBSCRIBE = "/slim/unsubscribe";

    /** The format string for the channel to listen to for server status events. */
    private static final String CHANNEL_SERVER_STATUS_FORMAT = "/%s/slim/serverstatus";

    /** The format string for the channel to listen to for player status events. */
    private static final String CHANNEL_PLAYER_STATUS_FORMAT = "/%s/slim/playerstatus/%s";

    /** The format string for the channel to listen to for display status events. */
    private static final String CHANNEL_DISPLAY_STATUS_FORMAT = "/%s/slim/displaystatus/%s";

    /** The format string for the channel to listen to for menu status events. */
    private static final String CHANNEL_MENU_STATUS_FORMAT = "/%s/slim/menustatus/%s";

    // Maximum time to wait for replies for server capabilities
    private static final long HANDSHAKE_TIMEOUT = 4_000;

    // The time interval in seconds between server status messages in case nothing happened to the server info in the interval.
    public static long SERVER_STATUS_INTERVAL = 60;
    public static final long SERVER_STATUS_TIMEOUT = SERVER_STATUS_INTERVAL * 1_000 + 10_000;


    /** Handler for off-main-thread work. */
    @NonNull
    private final Handler mBackgroundHandler;

    /** Map from an item request command ("players") to the listener class for responses. */
    private final Map<Class<?>, ItemListener<?>> mItemRequestMap;

    /** Map from a request to the listener class for responses. */
    private  final Map<String, ResponseHandler> mRequestMap;

    /** Client to the comet server. */
    @Nullable
    private SqueezerBayeuxClient mBayeuxClient;

    private final Map<String, Request> mPendingRequests
            = new ConcurrentHashMap<>();

    private final Map<String, BrowseRequest<?>> mPendingBrowseRequests = new ConcurrentHashMap<>();

    private final PublishListener mPublishListener = new PublishListener();

    // All requests are tagged with a correlation id, which can be used when
    // asynchronous responses are received.
    private volatile int mCorrelationId = 0;

    CometClient(@NonNull EventBus eventBus) {
        super(eventBus);

        HandlerThread handlerThread = new HandlerThread(SqueezeService.class.getSimpleName());
        handlerThread.start();
        mBackgroundHandler = new CliHandler(handlerThread.getLooper());

        List<ItemListener<?>> itemListeners = Arrays.asList(
                new AlarmsListener(),
                new AlarmPlaylistsListener(),
                new SongListener(),
                new MusicFolderListener(),
                new JiveItemListener()
        );
        mItemRequestMap = new HashMap<>();
        for (ItemListener<?> itemListener : itemListeners) {
            mItemRequestMap.put(itemListener.getDataType(), itemListener);
        }

        mRequestMap = new FluentHashMap<String, ResponseHandler>()
                .with("sync", (player, request, message) -> {
                    // LMS does not send new player status for the affected players, even if status
                    // changes are subscribed, so we order them  here
                    for (Player value : getConnectionState().getPlayers().values()) {
                        requestPlayerStatus(value);
                    }
                })
                .with("mixer", (player, request, message) -> {
                    if (request.cmd.get(1).equals("volume")) {
                        String volume = (String) message.getDataAsMap().get("_volume");
                        if (volume != null) {
                            int newVolume = Integer.parseInt(volume);
                            PlayerState playerState = player.getPlayerState();
                            playerState.setCurrentVolume(newVolume);
                            mEventBus.post(new PlayerVolume(playerState.isMuted(), playerState.getCurrentVolume(), player));
                        } else {
                            // LMS delays player status for volume changes, so order it immediately to respond faster to user input
                            command(player, new String[]{"mixer", "volume", "?"}, Collections.emptyMap());

                            // Since LMS doesn't send player status when volume is updated via a synced player we order them explicitly
                            if ("1".equals(player.getPlayerState().prefs.get(Player.Pref.SYNC_VOLUME))) {
                                List<String> slaves = player.getPlayerState().getSyncSlaves();
                                Player master = getConnectionState().getPlayer(player.getPlayerState().getSyncMaster());
                                if (master != null && master != player) {
                                    command(master, new String[]{"mixer", "volume", "?"}, Collections.emptyMap());
                                }
                                for (String slave : slaves) {
                                    Player syncSlave = getConnectionState().getPlayer(slave);
                                    if (syncSlave != null && syncSlave != player) {
                                        command(syncSlave, new String[]{"mixer", "volume", "?"}, Collections.emptyMap());
                                    }
                                }
                            }
                        }
                    }
                });
    }

    // Shims around ConnectionState methods.
    @Override
    public void startConnect(final SqueezeService service) {
        Log.i(TAG, "startConnect()");
        // Start the background connect
        mBackgroundHandler.post(() -> {
            final Preferences preferences = new Preferences(service);
            final Preferences.ServerAddress serverAddress = preferences.getServerAddress();
            final String username = serverAddress.userName;
            final String password = serverAddress.password;
            if (serverAddress.wakeOnLan) {
                Log.i(TAG, "Send Wake-on-LAN to: " + Util.formatMac(serverAddress.mac));
                SendWakeOnLan.sendWakeOnLan(serverAddress.mac);
            }
            Log.i(TAG, "Connecting to: " + username + "@" + serverAddress.address());

            if (!mEventBus.isRegistered(CometClient.this)) {
                mEventBus.register(CometClient.this);
            }
            mConnectionState.setConnectionState(ConnectionState.CONNECTION_STARTED);
            final boolean isSqueezeNetwork = serverAddress.squeezeNetwork;

            final HttpClient httpClient = new HttpClient();
            httpClient.setUserAgentField(new HttpField(HttpHeader.USER_AGENT, "Squeezer-squeezer/" + SqueezerBayeuxExtension.getRevision()));
            try {
                httpClient.start();
            } catch (Exception e) {
                mConnectionState.setConnectionError(ConnectionError.START_CLIENT_ERROR);
                return;
            }

            CometClient.this.username.set(username);
            CometClient.this.password.set(password);

            mUrlPrefix = "http://" + serverAddress.address();
            final String url = mUrlPrefix + "/cometd";
            try {
                // Neither URLUtil.isValidUrl nor Patterns.WEB_URL works as expected
                // Not even create of URL and URI throws reliably so we add some extra checks
                URI uri = new URL(url).toURI();
                if (!(
                        TextUtils.equals(uri.getHost(), serverAddress.host())
                                && uri.getPort() == serverAddress.port()
                                && TextUtils.equals(uri.getPath(), "/cometd")
                )) {
                    throw new IllegalArgumentException("Invalid url: " + url);
                }
            } catch (Exception e) {
                mConnectionState.setConnectionError(ConnectionError.INVALID_URL);
                return;
            }

            // Set the VM-wide authentication handler (needed by image fetcher and other using
            // the standard java http API)
            Authenticator.setDefault(new Authenticator() {
                @Override
                public PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(username, password.toCharArray());
                }
            });

            ClientTransport clientTransport = new HttpStreamingTransport(url, null, httpClient) {
                @Override
                protected void customize(org.eclipse.jetty.client.api.Request request) {
                    if (!isSqueezeNetwork && username != null && password != null) {
                        String authorization = B64Code.encode(username + ":" + password);
                        request.header(HttpHeader.AUTHORIZATION, "Basic " + authorization);
                    }
                }
            };
            mBayeuxClient = new SqueezerBayeuxClient(url, clientTransport);
            mBayeuxClient.addExtension(new SqueezerBayeuxExtension());
            mBayeuxClient.getChannel(Channel.META_HANDSHAKE).addListener((ClientSessionChannel.MessageListener) (channel, message) -> {
                if (message.isSuccessful()) {
                    onConnected(isSqueezeNetwork);
                } else {
                    Map<String, Object> failure = Util.getRecord(message, "failure");
                    Message failedMessage = (failure != null) ? (Message) failure.get("message") : message;
                    if (getAdviceAction(failedMessage.getAdvice()) == null) {
                        // Advices are handled internally by the bayeux protocol, so skip these here
                        Object httpCodeValue = (failure != null) ? failure.get("httpCode") : null;
                        int httpCode = (httpCodeValue instanceof Integer) ? (int) httpCodeValue : -1;
                        disconnect((httpCode == 401) ? ConnectionError.LOGIN_FALIED : ConnectionError.CONNECTION_ERROR);
                    }
                }
            });
            mBayeuxClient.getChannel(Channel.META_CONNECT).addListener((ClientSessionChannel.MessageListener) (channel, message) -> {
                if (!message.isSuccessful() && (getAdviceAction(message.getAdvice()) == null)) {
                    // Advices are handled internally by the bayeux protocol, so skip these here
                    disconnect();
                }
            });

            mBayeuxClient.handshake();
        });
    }

    private boolean needRegister() {
        return mBayeuxClient.getId().startsWith("1X");
    }

    private void onConnected(boolean isSqueezeNetwork) {
        Log.i(TAG, "Connected, start learning server capabilities");
        mConnectionState.setConnectionState(ConnectionState.CONNECTION_COMPLETED);
        // If this is a rehandshake we may already have players.
        boolean rehandshake = !mConnectionState.getPlayers().isEmpty();

        // Set a timeout for the handshake
        if (mConnectionState.getServerVersion() == null) {
            mBackgroundHandler.removeMessages(MSG_HANDSHAKE_TIMEOUT);
            mBackgroundHandler.sendEmptyMessageDelayed(MSG_HANDSHAKE_TIMEOUT, HANDSHAKE_TIMEOUT);
        }

        String clientId = mBayeuxClient.getId();
        mBayeuxClient.getChannel(String.format(CHANNEL_SLIM_REQUEST_RESPONSE_FORMAT, clientId, "*")).subscribe(this::parseRequestResponse);
        mBayeuxClient.getChannel(String.format(CHANNEL_SERVER_STATUS_FORMAT, clientId)).subscribe(this::parseServerStatus);
        mBayeuxClient.getChannel(String.format(CHANNEL_PLAYER_STATUS_FORMAT, clientId, "*")).subscribe(this::parsePlayerStatus);
        mBayeuxClient.getChannel(String.format(CHANNEL_DISPLAY_STATUS_FORMAT, clientId, "*")).subscribe(this::parseDisplayStatus);
        mBayeuxClient.getChannel(String.format(CHANNEL_MENU_STATUS_FORMAT, clientId, "*")).subscribe(this::parseMenuStatus);

        // Request server status
        publishMessage(serverStatusRequest(), CHANNEL_SLIM_REQUEST, String.format(CHANNEL_SERVER_STATUS_FORMAT, clientId), null);

        // Subscribe to server changes
        {
            Request request = serverStatusRequest().param("subscribe", String.valueOf(SERVER_STATUS_INTERVAL));
            publishMessage(request, CHANNEL_SLIM_SUBSCRIBE, String.format(CHANNEL_SERVER_STATUS_FORMAT, clientId), null);
        }

        if (isSqueezeNetwork) {
            if (needRegister()) {
                mEventBus.post(new RegisterSqueezeNetwork());
            }
        }

        if (rehandshake) {
            // Make sure we reorder subscriptions on rehandshake
            mConnectionState.getPlayers().values().stream().forEach(player -> player.getPlayerState().setSubscriptionType(PlayerState.PlayerSubscriptionType.NOTIFY_NONE));
            mConnectionState.setServerVersion(null);
        }
    }

    private void parseRequestResponse(ClientSessionChannel channel, Message message) {
        Request request = mPendingRequests.get(message.getChannel());
        if (request != null) {
            request.callback.onResponse(request.player, request, message);
            mPendingRequests.remove(message.getChannel());
        }
    }

    private void parseServerStatus(ClientSessionChannel channel, Message message) {
        Map<String, Object> data = message.getDataAsMap();

        // We can't distinguish between no connected players and players not received
        // so we check the server version which is also set from server status
        boolean firstTimePlayersReceived = (getConnectionState().getServerVersion() == null);

        getConnectionState().setMediaDirs(Util.getStringArray(data, ConnectionState.MEDIA_DIRS));
        getConnectionState().setServerVersion((String) data.get("version"));
        Object[] item_data = (Object[]) data.get("players_loop");
        final HashMap<String, Player> players = new HashMap<>();
        if (item_data != null) {
            for (Object item_d : item_data) {
                Map<String, Object> record = (Map<String, Object>) item_d;
                if (!record.containsKey(Player.Pref.DEFEAT_DESTRUCTIVE_TTP.prefName()) &&
                        data.containsKey(Player.Pref.DEFEAT_DESTRUCTIVE_TTP.prefName())) {
                    record.put(Player.Pref.DEFEAT_DESTRUCTIVE_TTP.prefName(), data.get(Player.Pref.DEFEAT_DESTRUCTIVE_TTP.prefName()));
                }
                Player player = new Player(record);
                players.put(player.getId(), player);
            }
        }

        Map<String, Player> currentPlayers = mConnectionState.getPlayers();
        if (firstTimePlayersReceived || !players.equals(currentPlayers)) {
            mConnectionState.setPlayers(players);
        } else {
            for (Player player : players.values()) {
                PlayerState currentPlayerState = currentPlayers.get(player.getId()).getPlayerState();
                if (!player.getPlayerState().prefs.equals(currentPlayerState.prefs)) {
                    currentPlayerState.prefs = player.getPlayerState().prefs;
                    postPlayerStateChanged(player);
                }
            }
        }

        // Set a timeout for the next server status message
        mBackgroundHandler.removeMessages(MSG_SERVER_STATUS_TIMEOUT);
        mBackgroundHandler.sendEmptyMessageDelayed(MSG_SERVER_STATUS_TIMEOUT, SERVER_STATUS_TIMEOUT);
    }

    private void parsePlayerStatus(ClientSessionChannel channel, Message message) {
        String[] channelParts = mSlashSplitPattern.split(message.getChannel());
        String playerId = channelParts[channelParts.length - 1];
        Player player = mConnectionState.getPlayer(playerId);

        // XXX: Can we ever see a status for a player we don't know about?
        // XXX: Maybe the better thing to do is to add it.
        if (player == null)
            return;

        Map<String, Object> messageData = message.getDataAsMap();

        CurrentPlaylistItem currentSong = null;
        Object[] item_data = (Object[]) messageData.get("item_loop");
        if (item_data != null && item_data.length > 0) {
            Map<String, Object> record = (Map<String, Object>) item_data[0];

            patchUrlPrefix(record);
            record.put("base", messageData.get("base"));
            currentSong = new CurrentPlaylistItem(record);
            record.remove("base");
        }
        parseStatus(player, currentSong, messageData);
    }

    @Override
    protected void postSongTimeChanged(Player player) {
        super.postSongTimeChanged(player);
        if (player.getPlayerState().isPlaying()) {
            mBackgroundHandler.removeMessages(MSG_TIME_UPDATE);
            mBackgroundHandler.sendEmptyMessageDelayed(MSG_TIME_UPDATE, 1000);
        }
    }

    @Override
    protected void postPlayerStateChanged(Player player) {
        super.postPlayerStateChanged(player);
        if (player.getPlayerState().getSleepDuration() > 0) {
            android.os.Message message = mBackgroundHandler.obtainMessage(MSG_STATE_UPDATE, player);
            mBackgroundHandler.removeMessages(MSG_STATE_UPDATE);
            mBackgroundHandler.sendMessageDelayed(message, 1000);
        }
    }

    private void parseDisplayStatus(ClientSessionChannel channel, Message message) {
        Map<String, Object> display = Util.getRecord(message.getDataAsMap(), "display");
        if (display != null) {
            String type = Util.getString(display, "type");
            if ("alertWindow".equals(type)) {
                AlertWindow alertWindow = new AlertWindow(display);
                mEventBus.post(new AlertEvent(alertWindow));
            } else {
                display.put("urlPrefix", mUrlPrefix);
                DisplayMessage displayMessage = new DisplayMessage(display);
                mEventBus.post(new DisplayEvent(displayMessage));
            }
        }
    }

    private void parseMenuStatus(ClientSessionChannel channel, Message message) {
        Object[] data = (Object[]) message.getData();

        // each chunk.data[2] contains a table that needs insertion into the menu
        Object[] item_data = (Object[]) data[1];
        JiveItem[] menuItems = new JiveItem[item_data.length];
        for (int i = 0; i < item_data.length; i++) {
            Map<String, Object> record = (Map<String, Object>) item_data[i];
            patchUrlPrefix(record);
            menuItems[i] = new JiveItem(record);
        }

        // directive for these items is in chunk.data[3]
        String menuDirective = (String) data[2];

        // the player ID this notification is for is in chunk.data[4]
        String playerId = (String) data[3];

        mConnectionState.menuStatusEvent(new MenuStatusMessage(playerId, menuDirective, menuItems));
    }

    /**
     * Add endpoint to fetch further info from a slimserver item
     */
    private void patchUrlPrefix(Map<String, Object> record) {
        record.put("urlPrefix", mUrlPrefix);
        Map<String, Object> window = (Map<String, Object>) record.get("window");
        if (window != null) {
            window.put("urlPrefix", mUrlPrefix);
        }
    }

    private interface ResponseHandler {
        void onResponse(Player player, Request request, Message message);
    }

    private class PublishListener implements ClientSessionChannel.MessageListener {
        @Override
        public void onMessage(ClientSessionChannel channel, Message message) {
            if (!message.isSuccessful()) {
                if (Message.RECONNECT_HANDSHAKE_VALUE.equals(getAdviceAction(message.getAdvice()))) {
                    Log.i(TAG, "rehandshake");
                    mBayeuxClient.rehandshake();
                } else {
                    Map<String, Object> failure = (Map<String, Object>) message.get("failure");
                    Exception exception = (failure != null) ? (Exception) failure.get("exception") : null;
                    Log.w(TAG, channel + ": " + message.getJSON(), exception);
                }
            }
        }
    }

    private abstract class ItemListener<T> extends BaseListHandler<T> implements ResponseHandler {
        void parseMessage(String countName, String itemLoopName, Message message) {
            @SuppressWarnings("unchecked")
            BrowseRequest<T> browseRequest = (BrowseRequest<T>) mPendingBrowseRequests.get(message.getChannel());
            if (browseRequest == null) {
                return;
            }

            mPendingBrowseRequests.remove(message.getChannel());
            clear();
            Map<String, Object> data = message.getDataAsMap();
            int count = Util.getInt(data.get(countName));
            Map<String, Object> baseRecord = (Map<String, Object>) data.get("base");
            if (baseRecord != null) {
                patchUrlPrefix(baseRecord);
            }
            Object[] item_data = (Object[]) data.get(itemLoopName);
            if (item_data != null) {
                for (Object item_d : item_data) {
                    Map<String, Object> record = (Map<String, Object>) item_d;
                    patchUrlPrefix(record);
                    if (baseRecord != null) record.put("base", baseRecord);
                    add(record);
                    record.remove("base");
                }
            }

            // Process the lists for all the registered handlers
            final boolean fullList = browseRequest.isFullList();
            final int start = browseRequest.getStart();
            final int end = start + getItems().size();
            int max = 0;
            patchUrlPrefix(data);
            browseRequest.getCallback().onItemsReceived(count, start, data, getItems(), getDataType());
            if (count > max) {
                max = count;
            }

            // Check if we need to order more items
            if ((fullList || end % mPageSize != 0) && end < max) {
                int itemsPerResponse = (end + mPageSize > max ? max - end : fullList ? mPageSize : mPageSize - browseRequest.getItemsPerResponse());
                //XXX support prefix
                internalRequestItems(browseRequest.update(end, itemsPerResponse));
            }
        }

        void parseMessage(String itemLoopName, Message message) {
            parseMessage("count", itemLoopName, message);
        }
    }

    private class AlarmsListener extends ItemListener<Alarm> {
        @Override
        public void onResponse(Player player, Request request, Message message) {
            parseMessage("alarms_loop", message);
        }
    }

    private class AlarmPlaylistsListener extends ItemListener<AlarmPlaylist> {
        @Override
        public void onResponse(Player player, Request request, Message message) {
            parseMessage("item_loop", message);
        }
    }

    private class SongListener extends ItemListener<Song> {
        @Override
        public void onResponse(Player player, Request request, Message message) {
            switch (request.getRequest()) {
                default:
                    parseMessage("titles_loop", message);
                    break;
                case "playlists tracks":
                    parseMessage("playlisttracks_loop", message);
                    break;
                case "status":
                    parseMessage("playlist_tracks", "playlist_loop", message);
                    break;
            }
            parseMessage("titles_loop", message);
        }
    }

    private class MusicFolderListener extends ItemListener<MusicFolderItem> {
        @Override
        public void onResponse(Player player, Request request, Message message) {
            parseMessage("folder_loop", message);
        }
    }

    private class JiveItemListener extends ItemListener<JiveItem> {
        @Override
        public void onResponse(Player player, Request request, Message message) {
            parseMessage("item_loop", message);
        }
    }

    public void onEvent(@SuppressWarnings("unused") HandshakeComplete event) {
        mBackgroundHandler.removeMessages(MSG_HANDSHAKE_TIMEOUT);
    }

    @Override
    public void disconnect() {
        disconnect(ConnectionState.DISCONNECTED);
    }

    private void disconnect(@ConnectionState.ConnectionStates int connectionState) {
        if (mBayeuxClient != null) mBackgroundHandler.sendEmptyMessage(MSG_DISCONNECT);
        mConnectionState.setConnectionState(connectionState);
    }

    private void disconnect(ConnectionError connectionError) {
        if (mBayeuxClient != null) mBackgroundHandler.sendEmptyMessage(MSG_DISCONNECT);
        mConnectionState.setConnectionError(connectionError);
    }

    @Override
    public void cancelClientRequests(Object client) {
        for (Map.Entry<String, BrowseRequest<?>> entry : mPendingBrowseRequests.entrySet()) {
            if (entry.getValue().getCallback().getClient() == client) {
                mPendingBrowseRequests.remove(entry.getKey());
            }
        }
    }

    private void exec(ResponseHandler callback, String... cmd) {
        exec(request(callback, cmd));
    }

    private String exec(Request request) {
        String responseChannel = String.format(CHANNEL_SLIM_REQUEST_RESPONSE_FORMAT, mBayeuxClient.getId(), mCorrelationId++);
        if (request.callback != null) mPendingRequests.put(responseChannel, request);
        publishMessage(request, CHANNEL_SLIM_REQUEST, responseChannel, null);
        return responseChannel;
    }

    /** If request is null, this is an unsubscribe to the suplied response channel */
    private void publishMessage(final Request request, final String channel, final String responseChannel, final PublishListener publishListener) {
        // Make sure all requests are done in the handler thread
        if (mBackgroundHandler.getLooper() == Looper.myLooper()) {
            _publishMessage(request, channel, responseChannel, publishListener);
        } else {
            PublishMessage publishMessage = new PublishMessage(request, channel, responseChannel, publishListener);
            android.os.Message message = mBackgroundHandler.obtainMessage(MSG_PUBLISH, publishMessage);
            mBackgroundHandler.sendMessage(message);
        }

    }

    /** This may only be called from the handler thread */
    private void _publishMessage(Request request, String channel, String responseChannel, PublishListener publishListener) {
        Map<String, Object> data = new HashMap<>();
        if (request != null) {
            data.put("request", request.slimRequest());
            data.put("response", responseChannel);
        } else {
            data.put("unsubscribe", responseChannel);
        }
        mBayeuxClient.getChannel(channel).publish(data, publishListener != null ? publishListener : this.mPublishListener);
    }

    @Override
    protected  <T> void internalRequestItems(final BrowseRequest<T> browseRequest) {
        Class<?> callbackClass = Reflection.getGenericClass(browseRequest.getCallback().getClass(), IServiceItemListCallback.class, 0);
        ItemListener listener = mItemRequestMap.get(callbackClass);
        if (listener == null) {
            throw new RuntimeException("No handler defined for '" + browseRequest.getCallback().getClass() + "'");
        }

        Request request = request(browseRequest.getPlayer(), listener, browseRequest.cmd())
                .page(browseRequest.getStart(), browseRequest.getItemsPerResponse())
                .params(browseRequest.params);
        mPendingBrowseRequests.put(exec(request), browseRequest);
    }

    @Override
    public void command(Player player, String[] cmd, Map<String, Object> params) {
        ResponseHandler callback = mRequestMap.get(cmd[0]);
        exec(request(player, callback, cmd).params(params));
    }

    @Override
    public void requestPlayerStatus(Player player) {
        Request request = statusRequest(player);
        publishMessage(request, CHANNEL_SLIM_REQUEST, subscribeResponseChannel(player, CHANNEL_PLAYER_STATUS_FORMAT), null);
    }

    @Override
    public void subscribePlayerStatus(final Player player, final PlayerState.PlayerSubscriptionType subscriptionType) {
        Request request = statusRequest(player).param("subscribe", subscriptionType.getStatus());
        publishMessage(request, CHANNEL_SLIM_SUBSCRIBE, subscribeResponseChannel(player, CHANNEL_PLAYER_STATUS_FORMAT), new PublishListener() {
            @Override
            public void onMessage(ClientSessionChannel channel, Message message) {
                super.onMessage(channel, message);
                if (message.isSuccessful()) {
                    player.getPlayerState().setSubscriptionType(subscriptionType);
                }
            }
        });
    }

    @Override
    public void subscribeDisplayStatus(Player player, boolean subscribe) {
        Request request = request(player, "displaystatus").param("subscribe", subscribe ? "showbriefly" : "");
        publishMessage(request, CHANNEL_SLIM_SUBSCRIBE, subscribeResponseChannel(player, CHANNEL_DISPLAY_STATUS_FORMAT), mPublishListener);
    }

    @Override
    public void subscribeMenuStatus(Player player, boolean subscribe) {
        if (subscribe)
            subscribeMenuStatus(player);
        else
            unsubscribeMenuStatus(player);
    }

    private void subscribeMenuStatus(Player player) {
        Request request = request(player, "menustatus");
        publishMessage(request, CHANNEL_SLIM_SUBSCRIBE, subscribeResponseChannel(player, CHANNEL_MENU_STATUS_FORMAT), null);
    }

    private void unsubscribeMenuStatus(Player player) {
        publishMessage(null, CHANNEL_SLIM_UNSUBSCRIBE, subscribeResponseChannel(player, CHANNEL_MENU_STATUS_FORMAT), null);
    }

    private String subscribeResponseChannel(Player player, String format) {
        return String.format(format, mBayeuxClient.getId(), player.getId());
    }

    private static String getAdviceAction(Map<String, Object> advice) {
        String action = null;
        if (advice != null && advice.containsKey(Message.RECONNECT_FIELD))
            action = (String)advice.get(Message.RECONNECT_FIELD);
        return action;
    }

    private static final int MSG_PUBLISH = 1;
    private static final int MSG_DISCONNECT = 2;
    private static final int MSG_HANDSHAKE_TIMEOUT = 3;
    private static final int MSG_SERVER_STATUS_TIMEOUT = 4;
    private static final int MSG_TIME_UPDATE = 5;
    private static final int MSG_STATE_UPDATE = 6;
    private class CliHandler extends Handler {
        CliHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(android.os.Message msg) {
            switch (msg.what) {
                case MSG_PUBLISH: {
                    PublishMessage message = (PublishMessage) msg.obj;
                    _publishMessage(message.request, message.channel, message.responseChannel, message.publishListener);
                    break;
                }
                case MSG_DISCONNECT:
                    mBayeuxClient.disconnect();
                    break;
                case MSG_HANDSHAKE_TIMEOUT:
                    Log.w(TAG, "Handshake timeout: " + mConnectionState);
                    disconnect();
                    break;
                case MSG_SERVER_STATUS_TIMEOUT:
                    Log.w(TAG, "Server status timeout: initiate a new handshake");
                    mBayeuxClient.rehandshake();
                    break;
                case MSG_TIME_UPDATE: {
                    Player activePlayer = mConnectionState.getActivePlayer();
                    if (activePlayer != null) {
                        postSongTimeChanged(activePlayer);
                    }
                    break;
                }
                case MSG_STATE_UPDATE: {
                    Player player = (Player) msg.obj;
                    postPlayerStateChanged(player);
                    break;
                }
            }
        }
    }

    @NonNull
    private Request serverStatusRequest() {
        return request("serverstatus")
                .defaultPage()
                .prefs("prefs", ConnectionState.MEDIA_DIRS, Player.Pref.DEFEAT_DESTRUCTIVE_TTP.prefName())
                .prefs("playerprefs", Arrays.stream(Player.Pref.values()).map(Player.Pref::prefName).toArray(String[]::new));
    }

    @NonNull
    private Request statusRequest(Player player) {
        return request(player, "status")
                .currentSong()
                .param("menu", "menu")
                .param("useContextMenu", "1");
    }

    private Request request(Player player, ResponseHandler callback, String... cmd) {
        return new Request(player, callback, cmd);
    }

    private Request request(Player player, String... cmd) {
        return new Request(player, null, cmd);
    }

    private Request request(ResponseHandler callback, String... cmd) {
        return new Request(null, callback, cmd);
    }

    private Request request(String... cmd) {
        return new Request(null, null, cmd);
    }

    private static class Request extends SlimCommand {
        private final ResponseHandler callback;
        private final Player player;
        private PagingParams page;

        private Request(Player player, ResponseHandler callback, String... cmd) {
            this.player = player;
            this.callback = callback;
            this.cmd(cmd);
        }

        public Request param(String param, Object value) {
            super.param(param, value);
            return this;
        }

        public Request params(Map<String, Object> params) {
            super.params(params);
            return this;
        }

        public Request prefs(String param, String ... prefs) {
            param(param, TextUtils.join(",", prefs));
            return this;
        }

        private Request page(int start, int page) {
            this.page = new PagingParams(String.valueOf(start), String.valueOf(page));
            return this;
        }

        private Request defaultPage() {
            page = PagingParams._default;
            return this;
        }

        private Request currentSong() {
            page = PagingParams.status;
            return this;
        }

        public String getRequest() {
            return TextUtils.join(" ", cmd);
        }

        List<Object> slimRequest() {
            List<Object> slimRequest = new ArrayList<>();

            slimRequest.add(player == null ? "" : player.getId());
            List<String> inner = new ArrayList<>();
            slimRequest.add(inner);
            inner.addAll(cmd);
            for (Map.Entry<String, Object> parameter : params.entrySet()) {
                if (parameter.getValue() == null) inner.add(parameter.getKey());
            }
            if (page != null) {
                inner.add(page.start);
                inner.add(page.page);
            }
            for (Map.Entry<String, Object> parameter : params.entrySet()) {
                if (parameter.getValue() != null) inner.add(parameter.getKey() + ":" + parameter.getValue());
            }

            return slimRequest;
        }
    }

    private static class PagingParams {
        private static final PagingParams status = new PagingParams("-", "1");
        private static final PagingParams _default = new PagingParams("0", "255");

        private final String start;
        private final String page;

        private PagingParams(String start, String page) {
            this.start = start;
            this.page = page;
        }
    }

    private static class PublishMessage {
        final Request request;
        final String channel;
        final String responseChannel;
        final PublishListener publishListener;

        private PublishMessage(Request request, String channel, String responseChannel, PublishListener publishListener) {
            this.request = request;
            this.channel = channel;
            this.responseChannel = responseChannel;
            this.publishListener = publishListener;
        }
    }
}
