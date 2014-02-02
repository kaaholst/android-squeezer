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

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicReference;

import uk.org.ngo.squeezer.IServiceCallback;
import uk.org.ngo.squeezer.IServiceHandshakeCallback;
import uk.org.ngo.squeezer.IServiceMusicChangedCallback;
import uk.org.ngo.squeezer.IServiceVolumeCallback;
import uk.org.ngo.squeezer.NowPlayingActivity;
import uk.org.ngo.squeezer.Preferences;
import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.Util;
import uk.org.ngo.squeezer.itemlist.IServiceAlbumListCallback;
import uk.org.ngo.squeezer.itemlist.IServiceArtistListCallback;
import uk.org.ngo.squeezer.itemlist.IServiceCurrentPlaylistCallback;
import uk.org.ngo.squeezer.itemlist.IServiceGenreListCallback;
import uk.org.ngo.squeezer.itemlist.IServiceMusicFolderListCallback;
import uk.org.ngo.squeezer.itemlist.IServicePlayerListCallback;
import uk.org.ngo.squeezer.itemlist.IServicePlaylistMaintenanceCallback;
import uk.org.ngo.squeezer.itemlist.IServicePlaylistsCallback;
import uk.org.ngo.squeezer.itemlist.IServicePluginItemListCallback;
import uk.org.ngo.squeezer.itemlist.IServicePluginListCallback;
import uk.org.ngo.squeezer.itemlist.IServiceSongListCallback;
import uk.org.ngo.squeezer.itemlist.IServiceYearListCallback;
import uk.org.ngo.squeezer.itemlist.dialog.AlbumViewDialog;
import uk.org.ngo.squeezer.itemlist.dialog.SongViewDialog;
import uk.org.ngo.squeezer.model.Album;
import uk.org.ngo.squeezer.model.Artist;
import uk.org.ngo.squeezer.model.Genre;
import uk.org.ngo.squeezer.model.MusicFolderItem;
import uk.org.ngo.squeezer.model.Player;
import uk.org.ngo.squeezer.model.PlayerState;
import uk.org.ngo.squeezer.model.PlayerState.PlayStatus;
import uk.org.ngo.squeezer.model.PlayerState.RepeatStatus;
import uk.org.ngo.squeezer.model.PlayerState.ShuffleStatus;
import uk.org.ngo.squeezer.model.Playlist;
import uk.org.ngo.squeezer.model.Plugin;
import uk.org.ngo.squeezer.model.PluginItem;
import uk.org.ngo.squeezer.model.Song;
import uk.org.ngo.squeezer.model.Year;
import uk.org.ngo.squeezer.util.Scrobble;


public class SqueezeService extends Service {

    private static final String TAG = "SqueezeService";

    private static final int PLAYBACKSERVICE_STATUS = 1;

    private static final String ALBUMTAGS = "alyj";

    /**
     * Information that will be requested about songs.
     * <p/>
     * a: artist name<br/>
     * C: compilation (1 if true, missing otherwise)<br/>
     * d: duration, in seconds<br/>
     * e: album ID<br/>
     * j: coverart (1 if available, missing otherwise)<br/>
     * J: artwork_track_id (if available, missing otherwise)<br/>
     * K: URL to remote artwork<br/>
     * l: album name<br/>
     * s: artist id<br/>
     * t: tracknum, if known<br/>
     * x: 1, if this is a remote track<br/>
     * y: song year
     */
    // This should probably be a field in Song.
    private static final String SONGTAGS = "aCdejJKlstxy";

    final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);

    Thread mainThread;

    private boolean mHandshakeComplete = false;

    final RemoteCallbackList<IServiceCallback> mServiceCallbacks
            = new RemoteCallbackList<IServiceCallback>();

    final RemoteCallbackList<IServiceCurrentPlaylistCallback> mCurrentPlaylistCallbacks
            = new RemoteCallbackList<IServiceCurrentPlaylistCallback>();

    final RemoteCallbackList<IServiceMusicChangedCallback> mMusicChangedCallbacks
            = new RemoteCallbackList<IServiceMusicChangedCallback>();

    final RemoteCallbackList<IServiceHandshakeCallback> mHandshakeCallbacks
            = new RemoteCallbackList<IServiceHandshakeCallback>();

    final AtomicReference<IServicePlayerListCallback> playerListCallback
            = new AtomicReference<IServicePlayerListCallback>();

    final AtomicReference<IServiceAlbumListCallback> albumListCallback
            = new AtomicReference<IServiceAlbumListCallback>();

    final AtomicReference<IServiceArtistListCallback> artistListCallback
            = new AtomicReference<IServiceArtistListCallback>();

    final AtomicReference<IServiceYearListCallback> yearListCallback
            = new AtomicReference<IServiceYearListCallback>();

    final AtomicReference<IServiceGenreListCallback> genreListCallback
            = new AtomicReference<IServiceGenreListCallback>();

    final AtomicReference<IServiceSongListCallback> songListCallback
            = new AtomicReference<IServiceSongListCallback>();

    final AtomicReference<IServicePlaylistsCallback> playlistsCallback
            = new AtomicReference<IServicePlaylistsCallback>();

    final AtomicReference<IServicePlaylistMaintenanceCallback> playlistMaintenanceCallback
            = new AtomicReference<IServicePlaylistMaintenanceCallback>();

    final AtomicReference<IServicePluginListCallback> pluginListCallback
            = new AtomicReference<IServicePluginListCallback>();

    final AtomicReference<IServicePluginItemListCallback> pluginItemListCallback
            = new AtomicReference<IServicePluginItemListCallback>();

    final AtomicReference<IServiceMusicFolderListCallback> musicFolderListCallback
            = new AtomicReference<IServiceMusicFolderListCallback>();

    final RemoteCallbackList<IServiceVolumeCallback> mVolumeCallbacks
            = new RemoteCallbackList<IServiceVolumeCallback>();

    ConnectionState connectionState = new ConnectionState();

    PlayerState playerState = new PlayerState();

    CliClient cli = new CliClient(this);

    /**
     * Is scrobbling enabled?
     */
    private boolean scrobblingEnabled;

    /**
     * Was scrobbling enabled?
     */
    private boolean scrobblingPreviouslyEnabled;

    boolean debugLogging; // Enable this if you are debugging something

    boolean mUpdateOngoingNotification;

    int mFadeInSecs;

    @Override
    public void onCreate() {
        super.onCreate();

        // Get the main thread
        mainThread = Thread.currentThread();

        // Clear leftover notification in case this service previously got killed while playing
        NotificationManager nm = (NotificationManager) getSystemService(
                Context.NOTIFICATION_SERVICE);
        nm.cancel(PLAYBACKSERVICE_STATUS);
        connectionState
                .setWifiLock(((WifiManager) getSystemService(Context.WIFI_SERVICE)).createWifiLock(
                        WifiManager.WIFI_MODE_FULL, "Squeezer_WifiLock"));

        getPreferences();

        cli.initialize();
    }

    private void getPreferences() {
        final SharedPreferences preferences = getSharedPreferences(Preferences.NAME, MODE_PRIVATE);
        scrobblingEnabled = preferences.getBoolean(Preferences.KEY_SCROBBLE_ENABLED, false);
        mFadeInSecs = preferences.getInt(Preferences.KEY_FADE_IN_SECS, 0);
        mUpdateOngoingNotification = preferences
                .getBoolean(Preferences.KEY_NOTIFY_OF_CONNECTION, false);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return squeezeService;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        disconnect();
        mServiceCallbacks.kill();
    }

    void disconnect() {
        disconnect(false);
    }

    void disconnect(boolean isServerDisconnect) {
        connectionState.disconnect(this, isServerDisconnect && mHandshakeComplete == false);
        mHandshakeComplete = false;
        clearOngoingNotification();
        playerState = new PlayerState();
    }


    private interface CmdHandler {

        public void handle(List<String> tokens);
    }

    private Map<String, CmdHandler> initializeGlobalHandlers() {
        Map<String, CmdHandler> handlers = new HashMap<String, CmdHandler>();

        for (final CliClient.ExtendedQueryFormatCmd cmd : cli.extQueryFormatCmds) {
            if (!(cmd.playerSpecific || cmd.prefixed)) {
                handlers.put(cmd.cmd, new CmdHandler() {
                    @Override
                    public void handle(List<String> tokens) {
                        cli.parseSqueezerList(cmd, tokens);
                    }
                });
            }
        }
        handlers.put("playlists", new CmdHandler() {
            @Override
            public void handle(List<String> tokens) {
                if ("delete".equals(tokens.get(1))) {
                    ;
                } else if ("edit".equals(tokens.get(1))) {
                    ;
                } else if ("new".equals(tokens.get(1))) {
                    HashMap<String, String> tokenMap = parseTokens(tokens);
                    if (tokenMap.get("overwritten_playlist_id") != null) {
                        IServicePlaylistMaintenanceCallback callback = playlistMaintenanceCallback
                                .get();
                        if (callback != null) {
                            try {
                                callback.onCreateFailed(getString(R.string.PLAYLIST_EXISTS_MESSAGE,
                                        tokenMap.get("name")));
                            } catch (RemoteException e) {
                                Log.e(TAG, getString(R.string.PLAYLIST_EXISTS_MESSAGE,
                                        tokenMap.get("name")));
                            }
                        }
                    }
                } else if ("rename".equals(tokens.get(1))) {
                    HashMap<String, String> tokenMap = parseTokens(tokens);
                    if (tokenMap.get("dry_run") != null) {
                        if (tokenMap.get("overwritten_playlist_id") != null) {
                            IServicePlaylistMaintenanceCallback callback
                                    = playlistMaintenanceCallback.get();
                            if (callback != null) {
                                try {
                                    callback.onRenameFailed(
                                            getString(R.string.PLAYLIST_EXISTS_MESSAGE,
                                                    tokenMap.get("newname")));
                                } catch (RemoteException e) {
                                    Log.e(TAG, getString(R.string.PLAYLIST_EXISTS_MESSAGE,
                                            tokenMap.get("newname")));
                                }
                            }
                        } else {
                            cli.sendCommandImmediately(
                                    "playlists rename playlist_id:" + tokenMap.get("playlist_id")
                                            + " newname:" + Util.encode(tokenMap.get("newname")));
                        }
                    }
                } else if ("tracks".equals(tokens.get(1))) {
                    cli.parseSqueezerList(cli.extQueryFormatCmdMap.get("playlists tracks"), tokens);
                } else {
                    cli.parseSqueezerList(cli.extQueryFormatCmdMap.get("playlists"), tokens);
                }
            }
        });
        handlers.put("login", new CmdHandler() {
            @Override
            public void handle(List<String> tokens) {
                Log.i(TAG, "Authenticated: " + tokens);
                onAuthenticated();
            }
        });
        handlers.put("pref", new CmdHandler() {
            @Override
            public void handle(List<String> tokens) {
                Log.i(TAG, "Preference received: " + tokens);
                if ("httpport".equals(tokens.get(1)) && tokens.size() >= 3) {
                    connectionState.setHttpPort(Integer.parseInt(tokens.get(2)));
                }
                if ("jivealbumsort".equals(tokens.get(1)) && tokens.size() >= 3) {
                    connectionState.setPreferedAlbumSort(tokens.get(2));
                }
            }
        });
        handlers.put("can", new CmdHandler() {
            @Override
            public void handle(List<String> tokens) {
                Log.i(TAG, "Capability received: " + tokens);
                if ("musicfolder".equals(tokens.get(1)) && tokens.size() >= 3) {
                    connectionState
                            .setCanMusicfolder(Util.parseDecimalIntOrZero(tokens.get(2)) == 1);
                }

                if ("randomplay".equals(tokens.get(1)) && tokens.size() >= 3) {
                    connectionState
                            .setCanRandomplay(Util.parseDecimalIntOrZero(tokens.get(2)) == 1);
                }
            }
        });
        handlers.put("getstring", new CmdHandler() {
            @Override
            public void handle(List<String> tokens) {
                int maxOrdinal = 0;
                Map<String, String> tokenMap = parseTokens(tokens);
                for (Entry<String, String> entry : tokenMap.entrySet()) {
                    if (entry.getValue() != null) {
                        ServerString serverString = ServerString.valueOf(entry.getKey());
                        serverString.setLocalizedString(entry.getValue());
                        if (serverString.ordinal() > maxOrdinal) {
                            maxOrdinal = serverString.ordinal();
                        }
                    }
                }

                // Fetch the next strings until the list is completely translated
                if (maxOrdinal < ServerString.values().length - 1) {
                    cli.sendCommandImmediately(
                            "getstring " + ServerString.values()[maxOrdinal + 1].name());
                }
            }
        });
        handlers.put("version", new CmdHandler() {
            /**
             * Seeing the <code>version</code> result indicates that the
             * handshake has completed (see
             * {@link onCliPortConnectionEstablished}), call any handshake
             * callbacks that have been registered.
             */
            @Override
            public void handle(List<String> tokens) {
                Log.i(TAG, "Version received: " + tokens);
                mHandshakeComplete = true;
                strings();

                int i = mHandshakeCallbacks.beginBroadcast();
                while (i > 0) {
                    i--;
                    try {
                        mHandshakeCallbacks.getBroadcastItem(i).onHandshakeCompleted();
                    } catch (RemoteException e) {
                        // The RemoteCallbackList will take care of removing
                        // the dead object for us.
                    }
                }
                mHandshakeCallbacks.finishBroadcast();
            }
        });

        return handlers;
    }

    private Map<String, CmdHandler> initializePrefixedHandlers() {
        Map<String, CmdHandler> handlers = new HashMap<String, CmdHandler>();

        for (final CliClient.ExtendedQueryFormatCmd cmd : cli.extQueryFormatCmds) {
            if (cmd.prefixed && !cmd.playerSpecific) {
                handlers.put(cmd.cmd, new CmdHandler() {
                    @Override
                    public void handle(List<String> tokens) {
                        cli.parseSqueezerList(cmd, tokens);
                    }
                });
            }
        }

        return handlers;
    }

    private Map<String, CmdHandler> initializePlayerSpecificHandlers() {
        Map<String, CmdHandler> handlers = new HashMap<String, CmdHandler>();

        for (final CliClient.ExtendedQueryFormatCmd cmd : cli.extQueryFormatCmds) {
            if (cmd.playerSpecific && !cmd.prefixed) {
                handlers.put(cmd.cmd, new CmdHandler() {
                    @Override
                    public void handle(List<String> tokens) {
                        cli.parseSqueezerList(cmd, tokens);
                    }
                });
            }
        }
        handlers.put("prefset", new CmdHandler() {
            @Override
            public void handle(List<String> tokens) {
                Log.v(TAG, "Prefset received: " + tokens);
                if (tokens.size() > 4 && tokens.get(2).equals("server") && tokens.get(3)
                        .equals("volume")) {
                    String newVolume = tokens.get(4);
                    updatePlayerVolume(Util.parseDecimalIntOrZero(newVolume));
                }
            }
        });
        handlers.put("play", new CmdHandler() {
            @Override
            public void handle(List<String> tokens) {
                Log.v(TAG, "play registered");
                updatePlayStatus(PlayerState.PlayStatus.play);
            }
        });
        handlers.put("stop", new CmdHandler() {
            @Override
            public void handle(List<String> tokens) {
                Log.v(TAG, "stop registered");
                updatePlayStatus(PlayerState.PlayStatus.stop);
            }
        });
        handlers.put("pause", new CmdHandler() {
            @Override
            public void handle(List<String> tokens) {
                Log.v(TAG, "pause registered: " + tokens);
                parsePause(tokens.size() >= 3 ? tokens.get(2) : null);
            }
        });
        handlers.put("status", new CmdHandler() {
            @Override
            public void handle(List<String> tokens) {
                if (tokens.size() >= 3 && "-".equals(tokens.get(2))) {
                    parseStatusLine(tokens);
                } else {
                    cli.parseSqueezerList(cli.extQueryFormatCmdMap.get("status"), tokens);
                }
            }
        });
        handlers.put("playlist", new CmdHandler() {
            @Override
            public void handle(List<String> tokens) {
                parsePlaylistNotification(tokens);
            }
        });

        return handlers;
    }

    private Map<String, CmdHandler> initializePrefixedPlayerSpecificHandlers() {
        Map<String, CmdHandler> handlers = new HashMap<String, CmdHandler>();

        for (final CliClient.ExtendedQueryFormatCmd cmd : cli.extQueryFormatCmds) {
            if (cmd.playerSpecific && cmd.prefixed) {
                handlers.put(cmd.cmd, new CmdHandler() {
                    @Override
                    public void handle(List<String> tokens) {
                        cli.parseSqueezerList(cmd, tokens);
                    }
                });
            }
        }

        return handlers;
    }

    private final Map<String, CmdHandler> globalHandlers = initializeGlobalHandlers();

    private final Map<String, CmdHandler> prefixedHandlers = initializePrefixedHandlers();

    private final Map<String, CmdHandler> playerSpecificHandlers
            = initializePlayerSpecificHandlers();

    private final Map<String, CmdHandler> prefixedPlayerSpecificHandlers
            = initializePrefixedPlayerSpecificHandlers();

    void onLineReceived(String serverLine) {
        if (debugLogging) {
            Log.v(TAG, "LINE: " + serverLine);
        }
        List<String> tokens = Arrays.asList(serverLine.split(" "));
        if (tokens.size() < 2) {
            return;
        }

        CmdHandler handler;
        if ((handler = globalHandlers.get(tokens.get(0))) != null) {
            handler.handle(tokens);
            return;
        }
        if ((handler = prefixedHandlers.get(tokens.get(1))) != null) {
            handler.handle(tokens);
            return;
        }

        // Player-specific commands follow.  But we ignore all that aren't for our
        // active player.
        String activePlayerId = (connectionState.getActivePlayer() != null ? connectionState
                .getActivePlayer().getId() : null);
        if (activePlayerId == null || activePlayerId.length() == 0 ||
                !Util.decode(tokens.get(0)).equals(activePlayerId)) {
            // Different player that we're not interested in.
            // (yet? maybe later.)
            return;
        }
        if ((handler = playerSpecificHandlers.get(tokens.get(1))) != null) {
            handler.handle(tokens);
            return;
        }
        if (tokens.size() > 2
                && (handler = prefixedPlayerSpecificHandlers.get(tokens.get(2))) != null) {
            handler.handle(tokens);
        }
    }

    private void updatePlayerVolume(int newVolume) {
        playerState.setCurrentVolume(newVolume);
        Player player = connectionState.getActivePlayer();
        int i = mVolumeCallbacks.beginBroadcast();
        while (i > 0) {
            i--;
            try {
                mVolumeCallbacks.getBroadcastItem(i).onVolumeChanged(newVolume, player);
            } catch (RemoteException e) {
                // The RemoteCallbackList will take care of removing
                // the dead object for us.
            }
        }
        mVolumeCallbacks.finishBroadcast();
    }

    private void updateTimes(int secondsIn, int secondsTotal) {
        playerState.setCurrentSongDuration(secondsTotal);
        if (playerState.getCurrentTimeSecond() != secondsIn) {
            playerState.setCurrentTimeSecond(secondsIn);
            int i = mServiceCallbacks.beginBroadcast();
            while (i > 0) {
                i--;
                try {
                    mServiceCallbacks.getBroadcastItem(i)
                            .onTimeInSongChange(secondsIn, secondsTotal);
                } catch (RemoteException e) {
                    // The RemoteCallbackList will take care of removing
                    // the dead object for us.
                }
            }
            mServiceCallbacks.finishBroadcast();
        }
    }

    private void parsePlaylistNotification(List<String> tokens) {
        Log.v(TAG, "Playlist notification received: " + tokens);
        String notification = tokens.get(2);
        if ("newsong".equals(notification)) {
            // When we don't subscribe to the current players status, we rely
            // on playlist notifications and order song details here.
            // TODO keep track of subscribe status
            cli.sendPlayerCommand("status - 1 tags:" + SONGTAGS);
        } else if ("play".equals(notification)) {
            updatePlayStatus(PlayerState.PlayStatus.play);
        } else if ("stop".equals(notification)) {
            updatePlayStatus(PlayerState.PlayStatus.stop);
        } else if ("pause".equals(notification)) {
            parsePause(tokens.size() >= 4 ? tokens.get(3) : null);
        } else if ("addtracks".equals(notification)) {
            int i = mCurrentPlaylistCallbacks.beginBroadcast();
            while (i > 0) {
                i--;
                try {
                    mCurrentPlaylistCallbacks.getBroadcastItem(i).onAddTracks(playerState);
                } catch (RemoteException e) {
                    // The RemoteCallbackList will take care of removing
                    // the dead object for us.
                }
            }
            mCurrentPlaylistCallbacks.finishBroadcast();
        } else if ("delete".equals(notification)) {
            int i = mCurrentPlaylistCallbacks.beginBroadcast();
            while (i > 0) {
                i--;
                try {
                    mCurrentPlaylistCallbacks.getBroadcastItem(i)
                            .onDelete(playerState, Integer.parseInt(tokens.get(3)));
                } catch (RemoteException e) {
                    // The RemoteCallbackList will take care of removing
                    // the dead object for us.
                }
            }
            mCurrentPlaylistCallbacks.finishBroadcast();
        }
    }

    private void parsePause(String explicitPause) {
        if ("0".equals(explicitPause)) {
            updatePlayStatus(PlayerState.PlayStatus.play);
        } else if ("1".equals(explicitPause)) {
            updatePlayStatus(PlayerState.PlayStatus.pause);
        }
    }

    private HashMap<String, String> parseTokens(List<String> tokens) {
        HashMap<String, String> tokenMap = new HashMap<String, String>();
        String key, value;
        for (String token : tokens) {
            if (token == null || token.length() == 0) {
                continue;
            }
            int colonPos = token.indexOf("%3A");
            if (colonPos == -1) {
                key = Util.decode(token);
                value = null;
            } else {
                key = Util.decode(token.substring(0, colonPos));
                value = Util.decode(token.substring(colonPos + 3));
            }
            tokenMap.put(key, value);
        }
        return tokenMap;
    }

    private void parseStatusLine(List<String> tokens) {
        HashMap<String, String> tokenMap = parseTokens(tokens);

        updatePowerStatus(Util.parseDecimalIntOrZero(tokenMap.get("power")) == 1);
        updatePlayStatus(tokenMap.get("mode"));
        updateShuffleStatus(tokenMap.get("playlist shuffle"));
        updateRepeatStatus(tokenMap.get("playlist repeat"));

        playerState.setCurrentPlaylist(tokenMap.get("playlist_name"));
        playerState.setCurrentPlaylistIndex(
                Util.parseDecimalIntOrZero(tokenMap.get("playlist_cur_index")));
        updateCurrentSong(new Song(tokenMap));

        updateTimes(Util.parseDecimalIntOrZero(tokenMap.get("time")),
                Util.parseDecimalIntOrZero(tokenMap.get("duration")));
    }

    void changeActivePlayer(Player newPlayer) {
        if (newPlayer == null) {
            return;
        }

        Log.v(TAG, "Active player now: " + newPlayer);
        final String playerId = newPlayer.getId();
        String oldPlayerId = (connectionState.getActivePlayer() != null ? connectionState
                .getActivePlayer().getId() : null);
        boolean changed = false;
        if (oldPlayerId == null || !oldPlayerId.equals(playerId)) {
            if (oldPlayerId != null) {
                // Unsubscribe from the old player's status.
                cli.sendCommand(Util.encode(oldPlayerId) + " status - 1 subscribe:-");
            }

            connectionState.setActivePlayer(newPlayer);
            playerState = new PlayerState();
            changed = true;
        }

        // Start an async fetch of its status.
        cli.sendPlayerCommand("status - 1 tags:" + SONGTAGS);

        if (changed) {
            updatePlayerSubscriptionState();

            // NOTE: this involves a write and can block (sqlite lookup via binder call), so
            // should be done off-thread, so we can process service requests & send our callback
            // as quickly as possible.
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    Log.v(TAG, "Saving " + Preferences.KEY_LASTPLAYER + "=" + playerId);
                    final SharedPreferences preferences = getSharedPreferences(Preferences.NAME,
                            MODE_PRIVATE);
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putString(Preferences.KEY_LASTPLAYER, playerId);
                    editor.commit();
                }
            });
        }

        int i = mServiceCallbacks.beginBroadcast();
        while (i > 0) {
            i--;
            try {
                mServiceCallbacks.getBroadcastItem(i).onPlayerChanged(newPlayer);
            } catch (RemoteException e) {
                // The RemoteCallbackList will take care of removing
                // the dead object for us.
            }
        }
        mServiceCallbacks.finishBroadcast();
    }


    private void updatePlayerSubscriptionState() {
        // Subscribe or unsubscribe to the player's realtime status updates
        // depending on whether we have an Activity or some sort of client
        // that cares about second-to-second updates.
        //
        // Note: If scrobbling is turned on then that counts as caring
        // about second-to-second updates -- otherwise we miss events from
        // buttons on the player, the web interface, and so on
        int clients = mServiceCallbacks.beginBroadcast();
        mServiceCallbacks.finishBroadcast();
        if (clients > 0 || scrobblingEnabled) {
            cli.sendPlayerCommand("status - 1 subscribe:1 tags:" + SONGTAGS);
        } else {
            cli.sendPlayerCommand("status - 1 subscribe:-");
        }
    }

    /**
     * Authenticate on the SqueezeServer.
     * <p/>
     * The server does
     * <pre>
     * login user wrongpassword
     * login user ******
     * (Connection terminted)
     * </pre>
     * instead of as documented
     * <pre>
     * login user wrongpassword
     * (Connection terminted)
     * </pre>
     * therefore a disconnect when handshake (the next step after authentication) is not completed,
     * is considered an authentication failure.
     */
    void onCliPortConnectionEstablished(final String userName, final String password) {
        cli.sendCommandImmediately("login " + Util.encode(userName) + " " + Util.encode(password));
    }

    /**
     * Handshake with the SqueezeServer, learn some of its supported features, and start listening
     * for asynchronous updates of server state.
     */
    private void onAuthenticated() {
        cli.sendCommandImmediately("listen 1",
                "players 0 1",   // initiate an async player fetch
                "can musicfolder ?", // learn music folder browsing support
                "can randomplay ?",   // learn random play function functionality
                "pref httpport ?", // learn the HTTP port (needed for images)
                "pref jivealbumsort ?", // learn the preferred album sort order

                // Fetch the version number. This must be the last thing
                // fetched, as seeing the result triggers the
                // "handshake is complete" logic elsewhere.
                "version ?"
        );
    }

    /* Start an asynchronous fetch of the squeezeservers localized strings */
    private void strings() {
        cli.sendCommandImmediately("getstring " + ServerString.values()[0].name());
    }

    private void updatePlayStatus(String token) {
        if (token != null)
            try {
                updatePlayStatus(PlayStatus.valueOf(token));
            } catch (IllegalArgumentException e) {
                // Server sent us an unknown status, we skip the update
            }
    }

    private void updatePlayStatus(PlayStatus playStatus) {
        if (playerState.getPlayStatus() != playStatus) {
            playerState.setPlayStatus(playStatus);
            //TODO when do we want to keep the wiFi lock ?
            connectionState.updateWifiLock(playerState.isPlaying());
            updateOngoingNotification();

            int i = mServiceCallbacks.beginBroadcast();
            while (i > 0) {
                i--;
                try {
                    mServiceCallbacks.getBroadcastItem(i).onPlayStatusChanged(playStatus.name());
                } catch (RemoteException e) {
                    // The RemoteCallbackList will take care of removing
                    // the dead object for us.
                }
            }
            mServiceCallbacks.finishBroadcast();
        }
    }

    private void updateShuffleStatus(String token) {
        if (token != null) {
            ShuffleStatus shuffleStatus = ShuffleStatus.valueOf(Util.parseDecimalIntOrZero(token));
            if (shuffleStatus != playerState.getShuffleStatus()) {
                boolean wasUnknown = playerState.getShuffleStatus() == null;
                playerState.setShuffleStatus(shuffleStatus);
                onShuffleStatusChanged(wasUnknown, shuffleStatus);
            }
        }
    }

    private void onShuffleStatusChanged(boolean initial, ShuffleStatus shuffleStatus) {
        int i = mServiceCallbacks.beginBroadcast();
        while (i > 0) {
            i--;
            try {
                mServiceCallbacks.getBroadcastItem(i)
                        .onShuffleStatusChanged(initial, shuffleStatus.getId());
            } catch (RemoteException e) {
            }
        }
        mServiceCallbacks.finishBroadcast();
    }

    private void updateRepeatStatus(String token) {
        if (token != null) {
            RepeatStatus repeatStatus = RepeatStatus.valueOf(Util.parseDecimalIntOrZero(token));
            if (repeatStatus != playerState.getRepeatStatus()) {
                boolean wasUnknown = playerState.getRepeatStatus() == null;
                playerState.setRepeatStatus(repeatStatus);
                onRepeatStatusChanged(wasUnknown, repeatStatus);
            }
        }
    }

    private void onRepeatStatusChanged(boolean wasUnknown, RepeatStatus repeatStatus) {
        int i = mServiceCallbacks.beginBroadcast();
        while (i > 0) {
            i--;
            try {
                mServiceCallbacks.getBroadcastItem(i)
                        .onRepeatStatusChanged(wasUnknown, repeatStatus.getId());
            } catch (RemoteException e) {
            }
        }
        mServiceCallbacks.finishBroadcast();
    }

    private void updateOngoingNotification() {
        boolean playing = playerState.isPlaying();
        String songName = playerState.getCurrentSongName();
        String playerName = connectionState.getActivePlayer() != null ? connectionState
                .getActivePlayer().getName() : "squeezer";

        // Update scrobble state, if either we're currently scrobbling, or we
        // were (to catch the case where we started scrobbling a song, and the
        // user went in to settings to disable scrobbling).
        if (scrobblingEnabled || scrobblingPreviouslyEnabled) {
            scrobblingPreviouslyEnabled = scrobblingEnabled;
            Song s = playerState.getCurrentSong();

            if (s != null) {
                Log.v(TAG, "Scrobbling, playing is: " + playing);
                Intent i = new Intent();

                if (Scrobble.haveScrobbleDroid()) {
                    // http://code.google.com/p/scrobbledroid/wiki/DeveloperAPI
                    i.setAction("net.jjc1138.android.scrobbler.action.MUSIC_STATUS");
                    i.putExtra("playing", playing);
                    i.putExtra("track", songName);
                    i.putExtra("album", s.getAlbum());
                    i.putExtra("artist", s.getArtist());
                    i.putExtra("secs", playerState.getCurrentSongDuration());
                    i.putExtra("source", "P");
                } else if (Scrobble.haveSls()) {
                    // http://code.google.com/p/a-simple-lastfm-scrobbler/wiki/Developers
                    i.setAction("com.adam.aslfms.notify.playstatechanged");
                    i.putExtra("state", playing ? 0 : 2);
                    i.putExtra("app-name", getText(R.string.app_name));
                    i.putExtra("app-package", "uk.org.ngo.squeezer");
                    i.putExtra("track", songName);
                    i.putExtra("album", s.getAlbum());
                    i.putExtra("artist", s.getArtist());
                    i.putExtra("duration", playerState.getCurrentSongDuration());
                    i.putExtra("source", "P");
                }
                sendBroadcast(i);
            }
        }

        if (!playing) {
            if (!mUpdateOngoingNotification) {
                clearOngoingNotification();
                return;
            }
        }

        NotificationManager nm =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        Notification status = new Notification();
        //status.contentView = views;
        Intent showNowPlaying = new Intent(this, NowPlayingActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        PendingIntent pIntent = PendingIntent.getActivity(this, 0, showNowPlaying, 0);
        if (playing) {
            status.setLatestEventInfo(this,
                    getString(R.string.notification_playing_text, playerName), songName, pIntent);
            status.flags |= Notification.FLAG_ONGOING_EVENT;
            status.icon = R.drawable.stat_notify_musicplayer;
        } else {
            status.setLatestEventInfo(this,
                    getString(R.string.notification_connected_text, playerName), "-", pIntent);
            status.flags |= Notification.FLAG_ONGOING_EVENT;
            status.icon = R.drawable.ic_launcher;
        }
        nm.notify(PLAYBACKSERVICE_STATUS, status);
    }

    private void updateCurrentSong(Song song) {
        Song currentSong = playerState.getCurrentSong();
        if ((song == null ? (currentSong != null) : !song.equals(currentSong))) {
            Log.d(TAG, "updateCurrentSong: " + song);
            playerState.setCurrentSong(song);
            updateOngoingNotification();
            int i = mMusicChangedCallbacks.beginBroadcast();
            while (i > 0) {
                i--;
                try {
                    mMusicChangedCallbacks.getBroadcastItem(i).onMusicChanged(playerState);
                } catch (RemoteException e) {
                    // The RemoteCallbackList will take care of removing
                    // the dead object for us.
                }
            }
            mMusicChangedCallbacks.finishBroadcast();
        }
    }

    private void clearOngoingNotification() {
        NotificationManager nm =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(PLAYBACKSERVICE_STATUS);
    }

    private void updatePowerStatus(boolean powerStatus) {
        if (powerStatus != playerState.isPoweredOn()) {
            playerState.setPoweredOn(powerStatus);
            int i = mServiceCallbacks.beginBroadcast();
            while (i > 0) {
                i--;
                try {
                    mServiceCallbacks.getBroadcastItem(i)
                            .onPowerStatusChanged(squeezeService.canPowerOn(),
                                    squeezeService.canPowerOff());
                } catch (RemoteException e) {
                    // The RemoteCallbackList will take care of removing
                    // the dead object for us.
                }
            }
            mServiceCallbacks.finishBroadcast();
        }
    }

    private final ISqueezeService.Stub squeezeService = new ISqueezeService.Stub() {

        @Override
        public void registerCallback(IServiceCallback callback) throws RemoteException {
            mServiceCallbacks.register(callback);
            updatePlayerSubscriptionState();
        }

        @Override
        public void unregisterCallback(IServiceCallback callback) throws RemoteException {
            mServiceCallbacks.unregister(callback);
            updatePlayerSubscriptionState();
        }

        @Override
        public void registerCurrentPlaylistCallback(IServiceCurrentPlaylistCallback callback)
                throws RemoteException {
            mCurrentPlaylistCallbacks.register(callback);
        }

        @Override
        public void unregisterCurrentPlaylistCallback(IServiceCurrentPlaylistCallback callback)
                throws RemoteException {
            mCurrentPlaylistCallbacks.unregister(callback);
        }

        @Override
        public void registerMusicChangedCallback(IServiceMusicChangedCallback callback)
                throws RemoteException {
            mMusicChangedCallbacks.register(callback);
        }

        @Override
        public void unregisterMusicChangedCallback(IServiceMusicChangedCallback callback)
                throws RemoteException {
            mMusicChangedCallbacks.unregister(callback);
        }

        @Override
        public void registerHandshakeCallback(IServiceHandshakeCallback callback)
                throws RemoteException {
            mHandshakeCallbacks.register(callback);

            // Call onHandshakeCompleted() immediately if handshaking is done.
            if (mHandshakeComplete) {
                callback.onHandshakeCompleted();
            }
        }

        @Override
        public void unregisterHandshakeCallback(IServiceHandshakeCallback callback)
                throws RemoteException {
            mHandshakeCallbacks.unregister(callback);
        }

        @Override
        public void registerVolumeCallback(IServiceVolumeCallback callback) throws RemoteException {
            mVolumeCallbacks.register(callback);
        }

        @Override
        public void unregisterVolumeCallback(IServiceVolumeCallback callback)
                throws RemoteException {
            mVolumeCallbacks.unregister(callback);
        }

        @Override
        public void adjustVolumeTo(int newVolume) throws RemoteException {
            cli.sendPlayerCommand("mixer volume " + Math.min(100, Math.max(0, newVolume)));
        }

        @Override
        public void adjustVolumeBy(int delta) throws RemoteException {
            if (delta > 0) {
                cli.sendPlayerCommand("mixer volume %2B" + delta);
            } else if (delta < 0) {
                cli.sendPlayerCommand("mixer volume " + delta);
            }
        }

        @Override
        public boolean isConnected() throws RemoteException {
            return connectionState.isConnected();
        }

        @Override
        public boolean isConnectInProgress() throws RemoteException {
            return connectionState.isConnectInProgress();
        }

        @Override
        public void startConnect(String hostPort, String userName, String password)
                throws RemoteException {
            connectionState
                    .startConnect(SqueezeService.this, executor, hostPort, userName, password);
        }

        @Override
        public void disconnect() throws RemoteException {
            if (!isConnected()) {
                return;
            }
            SqueezeService.this.disconnect();
        }

        @Override
        public boolean powerOn() throws RemoteException {
            if (!isConnected()) {
                return false;
            }
            cli.sendPlayerCommand("power 1");
            return true;
        }

        @Override
        public boolean powerOff() throws RemoteException {
            if (!isConnected()) {
                return false;
            }
            cli.sendPlayerCommand("power 0");
            return true;
        }

        @Override
        public boolean canPowerOn() {
            return canPower() && !playerState.isPoweredOn();
        }

        @Override
        public boolean canPowerOff() {
            return canPower() && playerState.isPoweredOn();
        }

        private boolean canPower() {
            Player player = connectionState.getActivePlayer();
            return connectionState.isConnected() && player != null && player.isCanpoweroff();
        }

        /**
         * Determines whether the Squeezeserver supports the
         * <code>musicfolders</code> command.
         *
         * @return <code>true</code> if it does, <code>false</code> otherwise.
         */
        @Override
        public boolean canMusicfolder() {
            return connectionState.canMusicfolder();
        }

        @Override
        public boolean canRandomplay() {
            return connectionState.canRandomplay();
        }

        @Override
        public String preferredAlbumSort() {
            return connectionState.getPreferredAlbumSort();
        }

        private String fadeInSecs() {
            return mFadeInSecs > 0 ? " " + mFadeInSecs : "";
        }

        @Override
        public boolean togglePausePlay() throws RemoteException {
            if (!isConnected()) {
                return false;
            }

            PlayerState.PlayStatus playStatus = playerState.getPlayStatus();

            // May be null (e.g., connected to a server with no connected
            // players. TODO: Handle this better, since it's not obvious in the
            // UI.
            if (playStatus == null) {
                return false;
            }

            switch (playStatus) {
                case play:
                    // NOTE: we never send ambiguous "pause" toggle commands (without the '1')
                    // because then we'd get confused when they came back in to us, not being
                    // able to differentiate ours coming back on the listen channel vs. those
                    // of those idiots at the dinner party messing around.
                    cli.sendPlayerCommand("pause 1");
                    break;
                case stop:
                    cli.sendPlayerCommand("play" + fadeInSecs());
                    break;
                case pause:
                    cli.sendPlayerCommand("pause 0" + fadeInSecs());
                    break;
            }
            return true;
        }

        @Override
        public boolean play() throws RemoteException {
            if (!isConnected()) {
                return false;
            }
            cli.sendPlayerCommand("play" + fadeInSecs());
            return true;
        }

        @Override
        public boolean stop() throws RemoteException {
            if (!isConnected()) {
                return false;
            }
            cli.sendPlayerCommand("stop");
            return true;
        }

        @Override
        public boolean nextTrack() throws RemoteException {
            if (!isConnected() || !isPlaying()) {
                return false;
            }
            cli.sendPlayerCommand("button jump_fwd");
            return true;
        }

        @Override
        public boolean previousTrack() throws RemoteException {
            if (!isConnected() || !isPlaying()) {
                return false;
            }
            cli.sendPlayerCommand("button jump_rew");
            return true;
        }

        @Override
        public boolean toggleShuffle() throws RemoteException {
            if (!isConnected()) {
                return false;
            }
            cli.sendPlayerCommand("playlist shuffle");
            return true;
        }

        @Override
        public boolean toggleRepeat() throws RemoteException {
            if (!isConnected()) {
                return false;
            }
            cli.sendPlayerCommand("playlist repeat");
            return true;
        }

        @Override
        public boolean playlistControl(String cmd, String tag, String itemId)
                throws RemoteException {
            if (!isConnected()) {
                return false;
            }

            cli.sendPlayerCommand("playlistcontrol cmd:" + cmd + " " + tag + ":" + itemId);
            return true;
        }

        @Override
        public boolean randomPlay(String type) throws RemoteException {
            if (!isConnected()) {
                return false;
            }
            cli.sendPlayerCommand("randomplay " + type);
            return true;
        }

        /**
         * Start playing the song in the current playlist at the given index.
         *
         * @param index the index to jump to
         */
        @Override
        public boolean playlistIndex(int index) throws RemoteException {
            if (!isConnected()) {
                return false;
            }
            cli.sendPlayerCommand("playlist index " + index + fadeInSecs());
            return true;
        }

        @Override
        public boolean playlistRemove(int index) throws RemoteException {
            if (!isConnected()) {
                return false;
            }
            cli.sendPlayerCommand("playlist delete " + index);
            return true;
        }

        @Override
        public boolean playlistMove(int fromIndex, int toIndex) throws RemoteException {
            if (!isConnected()) {
                return false;
            }
            cli.sendPlayerCommand("playlist move " + fromIndex + " " + toIndex);
            return true;
        }

        @Override
        public boolean playlistClear() throws RemoteException {
            if (!isConnected()) {
                return false;
            }
            cli.sendPlayerCommand("playlist clear");
            return true;
        }

        @Override
        public boolean playlistSave(String name) throws RemoteException {
            if (!isConnected()) {
                return false;
            }
            cli.sendPlayerCommand("playlist save " + Util.encode(name));
            return true;
        }

        @Override
        public boolean pluginPlaylistControl(Plugin plugin, String cmd, String itemId)
                throws RemoteException {
            if (!isConnected()) {
                return false;
            }
            cli.sendPlayerCommand(plugin.getId() + " playlist " + cmd + " item_id:" + itemId);
            return true;

        }

        private boolean isPlaying() throws RemoteException {
            return playerState.isPlaying();
        }

        @Override
        public void setActivePlayer(Player player) throws RemoteException {
            changeActivePlayer(player);
        }

        @Override
        public Player getActivePlayer() throws RemoteException {
            return connectionState.getActivePlayer();
        }

        @Override
        public PlayerState getPlayerState() throws RemoteException {
            return playerState;
        }

        @Override
        public String getCurrentPlaylist() {
            return playerState.getCurrentPlaylist();
        }

        @Override
        public String getAlbumArtUrl(String artworkTrackId) throws RemoteException {
            return getAbsoluteUrl(artworkTrackIdUrl(artworkTrackId));
        }

        private String artworkTrackIdUrl(String artworkTrackId) {
            return "/music/" + artworkTrackId + "/cover.jpg";
        }

        /**
         * Returns a URL to download a song.
         *
         * @param songId the song ID
         * @return The URL (as a string)
         */
        @Override
        public String getSongDownloadUrl(String songId) throws RemoteException {
            return getAbsoluteUrl(songDownloadUrl(songId));
        }

        private String songDownloadUrl(String songId) {
            return "/music/" + songId + "/download";
        }

        @Override
        public String getIconUrl(String icon) throws RemoteException {
            return getAbsoluteUrl('/' + icon);
        }

        private String getAbsoluteUrl(String relativeUrl) {
            Integer port = connectionState.getHttpPort();
            if (port == null || port == 0) {
                return "";
            }
            return "http://" + connectionState.getCurrentHost() + ":" + port + relativeUrl;
        }

        @Override
        public boolean setSecondsElapsed(int seconds) throws RemoteException {
            if (!isConnected()) {
                return false;
            }
            if (seconds < 0) {
                return false;
            }

            cli.sendPlayerCommand("time " + seconds);

            return true;
        }

        @Override
        public void preferenceChanged(String key) throws RemoteException {
            Log.i(TAG, "Preference changed: " + key);
            if (Preferences.KEY_NOTIFY_OF_CONNECTION.equals(key)) {
                updateOngoingNotification();
                return;
            }

            // If the server address changed then disconnect.
            if (Preferences.KEY_SERVERADDR.equals(key)) {
                disconnect();
                return;
            }

            getPreferences();
        }

        /* Start an async fetch of the SqueezeboxServer's players */
        @Override
        public boolean players(int start) throws RemoteException {
            if (!isConnected()) {
                return false;
            }
            cli.requestItems("players", start);
            return true;
        }

        @Override
        public void registerPlayerListCallback(IServicePlayerListCallback callback)
                throws RemoteException {
            Log.v(TAG, "PlayerListCallback attached.");
            SqueezeService.this.playerListCallback.set(callback);
        }

        @Override
        public void unregisterPlayerListCallback(IServicePlayerListCallback callback)
                throws RemoteException {
            Log.v(TAG, "PlayerListCallback detached.");
            SqueezeService.this.playerListCallback.compareAndSet(callback, null);
            cli.cancelRequests(Player.class);
        }

        /* Start an async fetch of the SqueezeboxServer's albums, which are matching the given parameters */
        @Override
        public boolean albums(int start, String sortOrder, String searchString,
                Artist artist, Year year, Genre genre, Song song)
                throws RemoteException {
            if (!isConnected()) {
                return false;
            }
            List<String> parameters = new ArrayList<String>();
            parameters.add("tags:" + ALBUMTAGS);
            parameters.add("sort:" + sortOrder);
            if (searchString != null && searchString.length() > 0) {
                parameters.add("search:" + searchString);
            }
            if (artist != null) {
                parameters.add("artist_id:" + artist.getId());
            }
            if (year != null) {
                parameters.add("year:" + year.getId());
            }
            if (genre != null) {
                parameters.add("genre_id:" + genre.getId());
            }
            if (song != null) {
                parameters.add("track_id:" + song.getId());
            }
            cli.requestItems("albums", start, parameters);
            return true;
        }

        @Override
        public void registerAlbumListCallback(IServiceAlbumListCallback callback)
                throws RemoteException {
            Log.v(TAG, "AlbumListCallback attached.");
            SqueezeService.this.albumListCallback.set(callback);
        }

        @Override
        public void unregisterAlbumListCallback(IServiceAlbumListCallback callback)
                throws RemoteException {
            Log.v(TAG, "AlbumListCallback detached.");
            SqueezeService.this.albumListCallback.compareAndSet(callback, null);
            cli.cancelRequests(Album.class);
        }


        /* Start an async fetch of the SqueezeboxServer's artists */
        @Override
        public boolean artists(int start, String searchString, Album album,
                Genre genre) throws RemoteException {
            if (!isConnected()) {
                return false;
            }
            List<String> parameters = new ArrayList<String>();
            if (searchString != null && searchString.length() > 0) {
                parameters.add("search:" + searchString);
            }
            if (album != null) {
                parameters.add("album_id:" + album.getId());
            }
            if (genre != null) {
                parameters.add("genre_id:" + genre.getId());
            }
            cli.requestItems("artists", start, parameters);
            return true;
        }

        @Override
        public void registerArtistListCallback(IServiceArtistListCallback callback)
                throws RemoteException {
            Log.v(TAG, "ArtistListCallback attached.");
            SqueezeService.this.artistListCallback.set(callback);
        }

        @Override
        public void unregisterArtistListCallback(IServiceArtistListCallback callback)
                throws RemoteException {
            Log.v(TAG, "ArtistListCallback detached.");
            SqueezeService.this.artistListCallback.compareAndSet(callback, null);
            cli.cancelRequests(Artist.class);
        }


        /* Start an async fetch of the SqueezeboxServer's years */
        @Override
        public boolean years(int start) throws RemoteException {
            if (!isConnected()) {
                return false;
            }
            cli.requestItems("years", start);
            return true;
        }

        @Override
        public void registerYearListCallback(IServiceYearListCallback callback)
                throws RemoteException {
            Log.v(TAG, "YearListCallback attached.");
            SqueezeService.this.yearListCallback.set(callback);
        }

        @Override
        public void unregisterYearListCallback(IServiceYearListCallback callback)
                throws RemoteException {
            Log.v(TAG, "YearListCallback detached.");
            SqueezeService.this.yearListCallback.compareAndSet(callback, null);
            cli.cancelRequests(Year.class);
        }

        /* Start an async fetch of the SqueezeboxServer's genres */
        @Override
        public boolean genres(int start, String searchString) throws RemoteException {
            if (!isConnected()) {
                return false;
            }
            List<String> parameters = new ArrayList<String>();
            if (searchString != null && searchString.length() > 0) {
                parameters.add("search:" + searchString);
            }
            cli.requestItems("genres", start, parameters);
            return true;
        }

        @Override
        public void registerGenreListCallback(IServiceGenreListCallback callback)
                throws RemoteException {
            Log.v(TAG, "GenreListCallback attached.");
            SqueezeService.this.genreListCallback.set(callback);
        }

        @Override
        public void unregisterGenreListCallback(IServiceGenreListCallback callback)
                throws RemoteException {
            Log.v(TAG, "GenreListCallback detached.");
            SqueezeService.this.genreListCallback.compareAndSet(callback, null);
            cli.cancelRequests(Genre.class);
        }

        /**
         * Starts an async fetch of the contents of a SqueezerboxServer's music
         * folders in the given folderId.
         * <p>
         * folderId may be null, in which case the contents of the root music
         * folder are returned.
         * <p>
         * Results are returned through the callback registered with
         * {@link registerMusicFolderListCallback}.
         *
         * @param start Where in the list of folders to start.
         * @param folderId The folder to view.
         * @return <code>true</code> if the request was sent, <code>false</code>
         *         if the service is not connected.
         */
        @Override
        public boolean musicFolders(int start, String folderId) throws RemoteException {
            if (!isConnected()) {
                return false;
            }

            List<String> parameters = new ArrayList<String>();

            if (folderId != null) {
                parameters.add("folder_id:" + folderId);
            }

            cli.requestItems("musicfolder", start, parameters);
            return true;
        }

        @Override
        public void registerMusicFolderListCallback(IServiceMusicFolderListCallback callback)
                throws RemoteException {
            Log.v(TAG, "MusicFolderListCallback attached.");
            SqueezeService.this.musicFolderListCallback.set(callback);
        }

        @Override
        public void unregisterMusicFolderListCallback(IServiceMusicFolderListCallback callback)
                throws RemoteException {
            Log.v(TAG, "MusicFolderListCallback detached.");
            SqueezeService.this.musicFolderListCallback.compareAndSet(callback, null);
            cli.cancelRequests(MusicFolderItem.class);
        }

        /* Start an async fetch of the SqueezeboxServer's songs */
        @Override
        public boolean songs(int start, String sortOrder, String searchString, Album album,
                Artist artist, Year year, Genre genre) throws RemoteException {
            if (!isConnected()) {
                return false;
            }
            List<String> parameters = new ArrayList<String>();
            parameters.add("tags:" + SONGTAGS);
            parameters.add("sort:" + sortOrder);
            if (searchString != null && searchString.length() > 0) {
                parameters.add("search:" + searchString);
            }
            if (album != null) {
                parameters.add("album_id:" + album.getId());
            }
            if (artist != null) {
                parameters.add("artist_id:" + artist.getId());
            }
            if (year != null) {
                parameters.add("year:" + year.getId());
            }
            if (genre != null) {
                parameters.add("genre_id:" + genre.getId());
            }
            cli.requestItems("songs", start, parameters);
            return true;
        }

        /* Start an async fetch of the SqueezeboxServer's current playlist */
        @Override
        public boolean currentPlaylist(int start) throws RemoteException {
            if (!isConnected()) {
                return false;
            }
            cli.requestPlayerItems("status", start, Arrays.asList("tags:" + SONGTAGS));
            return true;
        }

        /* Start an async fetch of the songs of the supplied playlist */
        @Override
        public boolean playlistSongs(int start, Playlist playlist) throws RemoteException {
            if (!isConnected()) {
                return false;
            }
            cli.requestItems("playlists tracks", start,
                    Arrays.asList("playlist_id:" + playlist.getId(), "tags:" + SONGTAGS));
            return true;
        }

        @Override
        public void registerSongListCallback(IServiceSongListCallback callback)
                throws RemoteException {
            Log.v(TAG, "SongListCallback attached.");
            SqueezeService.this.songListCallback.set(callback);
        }

        @Override
        public void unregisterSongListCallback(IServiceSongListCallback callback)
                throws RemoteException {
            Log.v(TAG, "SongListCallback detached.");
            SqueezeService.this.songListCallback.compareAndSet(callback, null);
            cli.cancelRequests(Song.class);
        }

        /* Start an async fetch of the SqueezeboxServer's playlists */
        @Override
        public boolean playlists(int start) throws RemoteException {
            if (!isConnected()) {
                return false;
            }
            cli.requestItems("playlists", start);
            return true;
        }

        @Override
        public void registerPlaylistsCallback(IServicePlaylistsCallback callback)
                throws RemoteException {
            Log.v(TAG, "PlaylistsCallback attached.");
            SqueezeService.this.playlistsCallback.set(callback);
        }

        @Override
        public void unregisterPlaylistsCallback(IServicePlaylistsCallback callback)
                throws RemoteException {
            Log.v(TAG, "PlaylistsCallback detached.");
            SqueezeService.this.playlistsCallback.compareAndSet(callback, null);
            cli.cancelRequests(Playlist.class);
        }


        @Override
        public void registerPlaylistMaintenanceCallback(
                IServicePlaylistMaintenanceCallback callback) throws RemoteException {
            Log.v(TAG, "PlaylistMaintenanceCallback attached.");
            playlistMaintenanceCallback.set(callback);
        }

        @Override
        public void unregisterPlaylistMaintenanceCallback(
                IServicePlaylistMaintenanceCallback callback) throws RemoteException {
            Log.v(TAG, "PlaylistMaintenanceCallback detached.");
            playlistMaintenanceCallback.compareAndSet(callback, null);
        }

        @Override
        public boolean playlistsDelete(Playlist playlist) throws RemoteException {
            if (!isConnected()) {
                return false;
            }
            cli.sendCommand("playlists delete playlist_id:" + playlist.getId());
            return true;
        }

        @Override
        public boolean playlistsMove(Playlist playlist, int index, int toindex)
                throws RemoteException {
            if (!isConnected()) {
                return false;
            }
            cli.sendCommand("playlists edit cmd:move playlist_id:" + playlist.getId()
                    + " index:" + index + " toindex:" + toindex);
            return true;
        }

        @Override
        public boolean playlistsNew(String name) throws RemoteException {
            if (!isConnected()) {
                return false;
            }
            cli.sendCommand("playlists new name:" + Util.encode(name));
            return true;
        }

        @Override
        public boolean playlistsRemove(Playlist playlist, int index) throws RemoteException {
            if (!isConnected()) {
                return false;
            }
            cli.sendCommand("playlists edit cmd:delete playlist_id:" + playlist.getId() + " index:"
                    + index);
            return true;
        }

        @Override
        public boolean playlistsRename(Playlist playlist, String newname) throws RemoteException {
            if (!isConnected()) {
                return false;
            }
            cli.sendCommand(
                    "playlists rename playlist_id:" + playlist.getId() + " dry_run:1 newname:"
                            + Util.encode(newname));
            return true;
        }

        /* Start an asynchronous search of the SqueezeboxServer's library */
        @Override
        public boolean search(int start, String searchString) throws RemoteException {
            if (!isConnected()) {
                return false;
            }

            AlbumViewDialog.AlbumsSortOrder albumSortOrder = AlbumViewDialog.AlbumsSortOrder
                    .valueOf(
                            preferredAlbumSort());

            artists(start, searchString, null, null);
            albums(start, albumSortOrder.name().replace("__", ""), searchString, null, null, null,
                    null);
            genres(start, searchString);
            songs(start, SongViewDialog.SongsSortOrder.title.name(), searchString, null,
                    null, null, null);

            return true;
        }

        /* Start an asynchronous fetch of the squeezeservers radio type plugins */
        @Override
        public boolean radios(int start) throws RemoteException {
            if (!isConnected()) {
                return false;
            }
            cli.requestItems("radios", start);
            return true;
        }

        /* Start an asynchronous fetch of the squeezeservers radio application plugins */
        @Override
        public boolean apps(int start) throws RemoteException {
            if (!isConnected()) {
                return false;
            }
            cli.requestItems("apps", start);
            return true;
        }

        @Override
        public void registerPluginListCallback(IServicePluginListCallback callback)
                throws RemoteException {
            Log.v(TAG, "PluginListCallback attached.");
            SqueezeService.this.pluginListCallback.set(callback);
        }

        @Override
        public void unregisterPluginListCallback(IServicePluginListCallback callback)
                throws RemoteException {
            Log.v(TAG, "PluginListCallback detached.");
            SqueezeService.this.pluginListCallback.compareAndSet(callback, null);
            cli.cancelRequests(Plugin.class);
        }


        /* Start an asynchronous fetch of the squeezeservers items of the given type */
        @Override
        public boolean pluginItems(int start, Plugin plugin, PluginItem parent, String search)
                throws RemoteException {
            if (!isConnected()) {
                return false;
            }
            List<String> parameters = new ArrayList<String>();
            if (parent != null) {
                parameters.add("item_id:" + parent.getId());
            }
            if (search != null && search.length() > 0) {
                parameters.add("search:" + search);
            }
            cli.requestPlayerItems(plugin.getId() + " items", start, parameters);
            return true;
        }

        @Override
        public void registerPluginItemListCallback(IServicePluginItemListCallback callback)
                throws RemoteException {
            Log.v(TAG, "SongListCallback attached.");
            SqueezeService.this.pluginItemListCallback.set(callback);
        }

        @Override
        public void unregisterPluginItemListCallback(IServicePluginItemListCallback callback)
                throws RemoteException {
            Log.v(TAG, "PluginItemListCallback detached.");
            SqueezeService.this.pluginItemListCallback.compareAndSet(callback, null);
            cli.cancelRequests(PluginItem.class);
        }
    };

}
