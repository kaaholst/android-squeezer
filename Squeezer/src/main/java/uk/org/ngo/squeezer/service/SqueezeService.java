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
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.ServiceCompat;
import androidx.media.app.NotificationCompat.MediaStyle;

import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import uk.org.ngo.squeezer.NowPlayingActivity;
import uk.org.ngo.squeezer.Preferences;
import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.Squeezer;
import uk.org.ngo.squeezer.SqueezerRepository;
import uk.org.ngo.squeezer.Util;
import uk.org.ngo.squeezer.model.Action;
import uk.org.ngo.squeezer.model.CustomJiveItemHandling;
import uk.org.ngo.squeezer.model.JiveItem;
import uk.org.ngo.squeezer.model.SlimCommand;
import uk.org.ngo.squeezer.itemlist.IServiceItemListCallback;
import uk.org.ngo.squeezer.model.Alarm;
import uk.org.ngo.squeezer.model.AlarmPlaylist;
import uk.org.ngo.squeezer.model.CurrentTrack;
import uk.org.ngo.squeezer.model.LyrionPlayer;
import uk.org.ngo.squeezer.model.PlayerState;
import uk.org.ngo.squeezer.service.event.ActivePlayerChanged;
import uk.org.ngo.squeezer.service.event.ConnectionChanged;
import uk.org.ngo.squeezer.service.event.LastscanChanged;
import uk.org.ngo.squeezer.service.event.MusicChanged;
import uk.org.ngo.squeezer.service.event.PlayStatusChanged;
import uk.org.ngo.squeezer.service.event.PlayerStateChanged;
import uk.org.ngo.squeezer.service.event.PlayerVolume;
import uk.org.ngo.squeezer.service.event.PlayersChanged;
import uk.org.ngo.squeezer.util.ImageFetcher;
import uk.org.ngo.squeezer.util.Intents;
import uk.org.ngo.squeezer.util.NotificationUtil;
import uk.org.ngo.squeezer.util.Scrobble;

/**
 * Persistent service which acts as an interface to for activities to communicate with LMS.
 * <p>
 * The interface is documented here {@link ISqueezeService}
 * <p>
 * The service lifecycle is managed as both a bound and a started service. as follows.
 * <ul>
 *     <li>On connect to LMS call Context.start[Foreground]Service and Service.startForeground</li>
 *     <li>On disconnect from LMS call Service.stopForeground and Service.stopSelf</li>
 *     <li>bind to the SqueezeService in activities in onCreate</li>
 *     <li>unbind the SqueezeService in activities  onDestroy</li>
 * </ul>
 * This means the service will as long as there is a Squeezer or we are connected to LMS activity.
 * When we are connected to LMS it runs as a foreground service and a notification is displayed.
 */
public class SqueezeService extends Service {
    private static final String TAG = "SqueezeService";

    public static final String NOTIFICATION_CHANNEL_ID = "channel_squeezer_1";
    private static final int PLAYBACKSERVICE_STATUS = 1;
    public static final int DOWNLOAD_ERROR = 2;

    /** Media session to associate with ongoing notifications. */
    private MediaSessionCompat mediaSession;

    /** Are the service currently in the foregrund */
    private volatile boolean foreGround;

    private WifiManager.WifiLock wifiLock;

    private SqueezerRepository repository;
    private LyrionController lyrionController;
    private HomeMenuHandling homeMenuHandling;
    private CallStateHelper callStateHelper;
    private RandomPlayDelegate randomPlayDelegate;
    private DownloadHelper downloadHelper;

    /**
     * Is scrobbling enabled?
     */
    private boolean scrobblingEnabled;

    /**
     * Was scrobbling enabled?
     */
    private boolean scrobblingPreviouslyEnabled;

    private static final String ACTION_NEXT_TRACK = "uk.org.ngo.squeezer.service.ACTION_NEXT_TRACK";
    private static final String ACTION_PREV_TRACK = "uk.org.ngo.squeezer.service.ACTION_PREV_TRACK";
    private static final String ACTION_PLAY = "uk.org.ngo.squeezer.service.ACTION_PLAY";
    private static final String ACTION_PAUSE = "uk.org.ngo.squeezer.service.ACTION_PAUSE";
    private static final String ACTION_CLOSE = "uk.org.ngo.squeezer.service.ACTION_CLOSE";
    static final String ACTION_POWER = "power";
    static final String ACTION_DISCONNECT = "disconnect";

    private SqueezerVolumeProvider volumeProvider;

    @Override
    public void onCreate() {
        super.onCreate();

        // Clear leftover notification in case this service previously got killed while playing
        NotificationManagerCompat nm = NotificationManagerCompat.from(this);
        nm.cancel(PLAYBACKSERVICE_STATUS);

        repository = ((Squeezer) getApplicationContext()).repository();
        lyrionController = new LyrionController(repository);
        homeMenuHandling = lyrionController.getHomeMenuHandling();
        callStateHelper = new CallStateHelper(lyrionController);
        randomPlayDelegate = new RandomPlayDelegate(lyrionController);
        downloadHelper = new DownloadHelper(getApplicationContext(), lyrionController);

        Squeezer.getPreferences(preferences -> {
            cachePreferences(preferences);
            homeMenuHandling.setCustomShortcuts(preferences.homeGroups(), preferences.getCustomShortcuts());
        });

        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL, "Squeezer_WifiLock");

        mediaSession = new MediaSessionCompat(getApplicationContext(), "squeezer");

        repository.observeForever(this::onConnectionChanged);
        repository.observeForever(this::onPlayerVolume);
        repository.observeForever(this::onMusicChanged);
        repository.observeForever(this::onPlayStatusChanged);
        repository.observeForever(this::onPlayerStateChanged);
        repository.observeForever(this::onActivePlayerChanged);
        repository.observeForever(this::onPlayersChanged);
        repository.observeForever(this::onLastscanChanged);
        // TODO clean up observers in CometClient (also look for observeForever)
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try{
            if(intent != null && intent.getAction()!= null ) {
                switch (intent.getAction()) {
                    case ACTION_NEXT_TRACK -> squeezeService.nextTrack();
                    case ACTION_PREV_TRACK -> squeezeService.previousTrack();
                    case ACTION_PLAY -> lyrionController.play();
                    case ACTION_PAUSE -> lyrionController.pause();
                    case ACTION_CLOSE -> disconnect(true);
                }
            }
        } catch(Exception e) {
            Log.w(TAG, "Error executing intent: ", e);
        }
        return START_STICKY;
    }

    /**
     * Cache the value of various preferences.
     */
    private void cachePreferences(Preferences preferences) {
        scrobblingEnabled = preferences.isScrobbleEnabled();
        lyrionController.setFadeInSecs(preferences.getFadeInSecs());
        lyrionController.setGroupVolume(preferences.isGroupVolume());
        volumeProvider = new SqueezerVolumeProvider(lyrionController, preferences.getVolumeIncrements());
        if (squeezeService.isConnected()) {
            if (preferences.isBackgroundVolume()) {
                mediaSession.setPlaybackToRemote(volumeProvider);
            } else {
                mediaSession.setPlaybackToLocal(AudioManager.STREAM_MUSIC);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return (IBinder) squeezeService;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        disconnect(false);
        repository.removeObserver(this::onConnectionChanged);
        repository.removeObserver(this::onPlayerVolume);
        repository.removeObserver(this::onMusicChanged);
        repository.removeObserver(this::onPlayStatusChanged);
        repository.removeObserver(this::onPlayerStateChanged);
        repository.removeObserver(this::onActivePlayerChanged);
        repository.removeObserver(this::onPlayersChanged);
        repository.removeObserver(this::onLastscanChanged);
        mediaSession.release();
        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        disconnect(false);
        super.onTaskRemoved(rootIntent);
    }

    private void disconnect(boolean fromUser) {
        lyrionController.disconnect(fromUser);
    }

    /**
     * Change the player that is controlled by Squeezer (the "active" player).
     *
     * @param newActivePlayer The new active player. May be null, in which case no players are controlled.
     * @param continuePlaying Continue playback on the supplied player
     */
    private void changeActivePlayer(@Nullable final LyrionPlayer newActivePlayer, boolean continuePlaying) {
        LyrionPlayer prevActivePlayer = lyrionController.getActivePlayer();

        // Do nothing if the player hasn't actually changed.
        if (prevActivePlayer == newActivePlayer) {
            return;
        }

        Log.i(TAG, "Active player now: " + newActivePlayer);
        lyrionController.setActivePlayer(newActivePlayer);

        if (prevActivePlayer != null) {
            lyrionController.subscribeDisplayStatus(prevActivePlayer, false);
            lyrionController.subscribeMenuStatus(prevActivePlayer, false);
        }

        updateAllPlayerSubscriptionStates();
        requestPlayerData();
        if (continuePlaying && prevActivePlayer != null) moveCurrentPlaylist(prevActivePlayer, newActivePlayer);
        Squeezer.getPreferences().setLastPlayer(newActivePlayer);
    }

    private void moveCurrentPlaylist(LyrionPlayer from, LyrionPlayer to) {
        squeezeService.syncPlayerToPlayer(to, from.getId());
        squeezeService.unsyncPlayer(from);
    }

    public <T> void requestItems(SlimCommand command, IServiceItemListCallback<T> callback) {
        lyrionController.requestAllItems(callback).params(command.params).cmd(command.cmd()).exec();
    }

    public void updateShortCut(JiveItem item, Map<String, Object> record) {
        Preferences preferences = Squeezer.getPreferences();
        List<JiveItem> shortcuts = homeMenuHandling.updateShortcut(item, record);
        preferences.saveShortcuts(shortcuts);
    }

    private void requestPlayerData() {
        LyrionPlayer activePlayer = lyrionController.getActivePlayer();

        if (activePlayer != null) {
            lyrionController.subscribeDisplayStatus(activePlayer, true);
            lyrionController.subscribeMenuStatus(activePlayer, true);
            lyrionController.requestPlayerStatus(activePlayer);
            // Start an asynchronous fetch of the slimserver "home menu" items
            // See http://wiki.slimdevices.com/index.php/SqueezePlayAndSqueezeCenterPlugins
            lyrionController.requestItems(activePlayer, 0, new HomeMenuReceiver(lyrionController, homeMenuHandling))
                    .cmd("menu").param("direct", "1").exec();
        }
    }

    /**
     * Adjusts the subscription to players' status updates.
     */
    private void updateAllPlayerSubscriptionStates() {
        for (LyrionPlayer player : lyrionController.getPlayers()) {
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

        lyrionController.subscribePlayerStatus(player, PlayerState.PlayerSubscriptionType.NOTIFY_ON_CHANGE);
    }

    /**
     * Manages the state of any ongoing notification based on the player and connection state.
     */
    private void updateMediaSession() {
        LyrionPlayer player = lyrionController.getActivePlayer();
        if (player == null) {
            mediaSession.setMetadata(null);
            mediaSession.setPlaybackState(null);
            notify(null);
            return;
        }

        // Update scrobble state, if either we're currently scrobbling, or we
        // were (to catch the case where we started scrobbling a song, and the
        // user went in to settings to disable scrobbling).
        if (scrobblingEnabled || scrobblingPreviouslyEnabled) {
            scrobblingPreviouslyEnabled = scrobblingEnabled;
            Scrobble.scrobbleFromPlayerState(this, player.getPlayerState());
        }

        final MediaMetadataCompat.Builder metaBuilder = new MediaMetadataCompat.Builder();
        CurrentTrack song = player.getPlayerState().getCurrentTrack();
        if (song != null) {
            metaBuilder.putString(MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION, notificationSubtext(player));
            metaBuilder.putString(MediaMetadata.METADATA_KEY_ARTIST, song.songInfo.getArtist());
            metaBuilder.putString(MediaMetadata.METADATA_KEY_ALBUM, song.text2());
            metaBuilder.putString(MediaMetadata.METADATA_KEY_TITLE, song.songInfo.title);
            metaBuilder.putLong(MediaMetadata.METADATA_KEY_DURATION, player.getPlayerState().getCurrentTrackDuration()*1000L);
            metaBuilder.putLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER, player.getPlayerState().getCurrentPlaylistIndex() + 1);
            metaBuilder.putLong(MediaMetadata.METADATA_KEY_NUM_TRACKS, player.getPlayerState().getCurrentPlaylistTracksNum());
            mediaSession.setMetadata(metaBuilder.build());
        }

        int playState = lyrionController.isPlaying() ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_STOPPED;
        PlaybackStateCompat playbackState = new PlaybackStateCompat.Builder()
                .setState(playState, player.getPlayerState().getPosition(), lyrionController.isPlaying() ? 1.0f : 0)
                .setActions(
                        PlaybackStateCompat.ACTION_PLAY |
                                PlaybackStateCompat.ACTION_PAUSE |
                                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                                PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                                PlaybackStateCompat.ACTION_SEEK_TO
                )
                .addCustomAction(ACTION_POWER, getString(player.getPlayerState().isPoweredOn() ? R.string.menu_item_power_off :  R.string.menu_item_power_on), R.drawable.power)
                .addCustomAction(ACTION_DISCONNECT, getString(R.string.menu_item_disconnect), R.drawable.ic_action_disconnect)
                .build();
        mediaSession.setPlaybackState(playbackState);

        ImageFetcher.getInstance(this).loadImage(song != null ? song.getIcon() : null,
                getResources().getDimensionPixelSize(android.R.dimen.notification_large_icon_width),
                getResources().getDimensionPixelSize(android.R.dimen.notification_large_icon_height),
                (data, bitmap) -> {
                    if (bitmap != null) {
                        metaBuilder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, bitmap);
                        metaBuilder.putBitmap(MediaMetadata.METADATA_KEY_ART, bitmap);
                        mediaSession.setMetadata(metaBuilder.build());
                    }
                    notify(bitmap);
                });
    }

    private void notify(Bitmap bitmap) {
        final NotificationCompat.Builder notificationData = notificationData();
        notificationData.setLargeIcon(bitmap);
        final NotificationManagerCompat nm = NotificationManagerCompat.from(this);
        try {
            nm.notify(PLAYBACKSERVICE_STATUS, notificationData.build());
        } catch (SecurityException e) {
            Log.w(TAG, "Can't update notification:", e);
        }
    }

    /**
     * Prepare a notification builder from the supplied notification state.
     */
    private NotificationCompat.Builder notificationData() {
        Intent showNowPlaying = new Intent(SqueezeService.this, NowPlayingActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        PendingIntent showNowPlayingIntent = PendingIntent.getActivity(SqueezeService.this, 0, showNowPlaying, Intents.immutablePendingIntent());

        NotificationUtil.createNotificationChannel(SqueezeService.this, NOTIFICATION_CHANNEL_ID,
                "Squeezer ongoing notification",
                "Notifications of player and connection state",
                NotificationManagerCompat.IMPORTANCE_LOW, false, NotificationCompat.VISIBILITY_PUBLIC);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(SqueezeService.this, NOTIFICATION_CHANNEL_ID);
        builder.setStyle(getMediaStyle());
        builder.setContentIntent(showNowPlayingIntent);
        builder.setSmallIcon(R.drawable.squeezer_notification);
        builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        builder.setShowWhen(false);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            LyrionPlayer player = lyrionController.getActivePlayer();
            if (player != null) {
                CurrentTrack song = player.getPlayerState().getCurrentTrack();
                if (song != null) {
                    builder.setContentTitle(song.getName());
                    builder.setContentText(song.text2());
                }
                builder.setSubText(notificationSubtext(player));
            }

            PendingIntent nextPendingIntent = getPendingIntent(ACTION_NEXT_TRACK);
            PendingIntent prevPendingIntent = getPendingIntent(ACTION_PREV_TRACK);
            PendingIntent playPendingIntent = getPendingIntent(ACTION_PLAY);
            PendingIntent pausePendingIntent = getPendingIntent(ACTION_PAUSE);
            PendingIntent closePendingIntent = getPendingIntent(ACTION_CLOSE);
            builder.setDeleteIntent(closePendingIntent);
            builder.addAction(new NotificationCompat.Action(R.drawable.ic_action_disconnect, "Disconnect", closePendingIntent));
            builder.addAction(new NotificationCompat.Action(R.drawable.ic_action_previous, "Previous", prevPendingIntent));
            if (lyrionController.isPlaying()) {
                builder.addAction(new NotificationCompat.Action(R.drawable.ic_action_pause, "Pause", pausePendingIntent));
            } else {
                builder.addAction(new NotificationCompat.Action(R.drawable.ic_action_play, "Play", playPendingIntent));
            }
            builder.addAction(new NotificationCompat.Action(R.drawable.ic_action_next, "Next", nextPendingIntent));
        }

        return builder;
    }

    private MediaStyle getMediaStyle() {
        MediaStyle mediaStyle = new MediaStyle();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) mediaStyle.setShowActionsInCompactView(2, 3);
        mediaStyle.setMediaSession(mediaSession.getSessionToken());
        return mediaStyle;
    }

    public String notificationSubtext(LyrionPlayer player) {
        PlayerState playerState = player.getPlayerState();
        return player.getName() + " " + (playerState.getCurrentPlaylistIndex()+1) + "/" + playerState.getCurrentPlaylistTracksNum();
    }

    /**
     * @param action The action to be performed.
     * @return A new {@link PendingIntent} for {@literal action} that will update any existing
     *     intents that use the same action.
     */
    @NonNull
    private PendingIntent getPendingIntent(@NonNull String action){
        Intent intent = new Intent(this, SqueezeService.class);
        intent.setAction(action);

        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | Intents.immutablePendingIntent());
    }

    private void startForeground() {
        if (!foreGround) {
            Log.i(TAG, "startForeground");
            foreGround = true;

            if (!wifiLock.isHeld()) {
                wifiLock.acquire();
            }

            mediaSession.setCallback(new SqueezerMediaSessionCallback(lyrionController));
            if (Squeezer.getPreferences().isBackgroundVolume()) {
                mediaSession.setPlaybackToRemote(volumeProvider);
            }
            mediaSession.setActive(true);

            Notification notification = notificationData().build();

            // Start it and have it run forever (until it shuts itself down).
            // This is required so swapping out the activity (and unbinding the
            // service connection in onDestroy) doesn't cause the service to be
            // killed due to zero refcount.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(new Intent(this, SqueezeService.class));
            } else {
                startService(new Intent(this, SqueezeService.class));
            }

            // Call startForeground immediately after startForegroundService
            ServiceCompat.startForeground(this, PLAYBACKSERVICE_STATUS, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        }
    }

    private void stopForeground() {
        Log.i(TAG, "stopForeground");
        foreGround = false;

        if (wifiLock.isHeld()) {
            wifiLock.release();
        }

        mediaSession.setPlaybackToLocal(AudioManager.STREAM_MUSIC);
        mediaSession.setActive(false);

        stopForeground(true);
        stopSelf();
    }

    private void onConnectionChanged(ConnectionChanged event) {
        if (event.connectionState.isConnected() ||
            event.connectionState.isConnectInProgress() ||
            event.connectionState.isRehandshaking()
        ) {
            startForeground();
            callStateHelper.registerCallStateListener(getApplicationContext());
        } else {
            callStateHelper.unregisterCallStateListener(getApplicationContext());
            stopForeground();
        }
    }

    private void onPlayerVolume(PlayerVolume event) {
        if (event.player == lyrionController.getActivePlayer()) {
            volumeProvider.setCurrentVolume(lyrionController.getVolume().volume / volumeProvider.step);
        }
    }

    private void onActivePlayerChanged(ActivePlayerChanged event) {
        updateMediaSession();
    }

    private void onMusicChanged(MusicChanged event) {
        if (event.player.equals(lyrionController.getActivePlayer())) {
            updateMediaSession();
        }
        if (event.player.getPlayerState().isRandomPlaying()) {
            randomPlayDelegate.handleRandomOnEvent(event.player);
        }
    }

    private void onPlayStatusChanged(PlayStatusChanged event) {
        callStateHelper.mutedPlayers.remove(event.player.getId());
        if (event.player.equals(lyrionController.getActivePlayer())) {
            updateMediaSession();
        }
    }

    private void onPlayerStateChanged(PlayerStateChanged event) {
        if (event.player.equals(lyrionController.getActivePlayer())) {
            updateMediaSession();
        }
    }

    private void onPlayersChanged(PlayersChanged event) {
        LyrionPlayer activePlayer = lyrionController.getActivePlayer();
        if (activePlayer == null) {
            // Figure out the new active player, let everyone know.
            changeActivePlayer(getPreferredPlayer(lyrionController.getPlayers()), false);
        } else {
            activePlayer = lyrionController.getPlayer(activePlayer.getId());
            lyrionController.setActivePlayer(activePlayer);
            updateAllPlayerSubscriptionStates();
            requestPlayerData();
        }
    }

    private void onLastscanChanged(LastscanChanged event) {
        CustomJiveItemHandling.recoverShortcuts(this, homeMenuHandling.getCustomShortcuts());
    }

    /**
     * @return The player that should be chosen as the (new) active player. This is either the
     *     last active player (if known), the first player the server knows about if there are
     *     connected players, or null if there are no connected players.
     */
    private @Nullable LyrionPlayer getPreferredPlayer(Collection<LyrionPlayer> players) {
        final String lastConnectedPlayer = Squeezer.getPreferences().getLastPlayer();
        Log.i(TAG, "lastConnectedPlayer was: " + lastConnectedPlayer);

        Log.i(TAG, "players empty?: " + players.isEmpty());
        for (LyrionPlayer player : players) {
            if (player.getId().equals(lastConnectedPlayer)) {
                return player;
            }
        }
        return !players.isEmpty() ? players.iterator().next() : null;
    }


    private final ISqueezeService squeezeService = new SqueezeServiceBinder();
    private class SqueezeServiceBinder extends Binder implements ISqueezeService {

        @Override
        public void toggleMute() {
            toggleMute(getActivePlayer());
        }

        @Override
        public void toggleMute(LyrionPlayer player) {
            if (player != null) {
                mute(player, !player.getPlayerState().isMuted());
            }
        }

        @Override
        public void mute(LyrionPlayer player, boolean mute) {
            lyrionController.mute(player, mute);
        }

        @Override
        public void setVolumeTo(LyrionPlayer player, int newVolume) {
            lyrionController.setPlayerVolume(player, newVolume);
        }

        @Override
        public boolean canAdjustVolumeForSyncGroup() {
            if (getActivePlayer() != null && getActivePlayer().isSyncVolume()) return false;
            return (lyrionController.getSyncGroup().size() > 1);
        }

        @Override
        public void setVolumeTo(int percentage) {
            lyrionController.setVolumeTo(percentage);
        }

        @Override
        public void adjustVolume(int direction) {
            lyrionController.adjustVolume(direction * volumeProvider.step);
        }

        @Override
        public boolean isManualDisconnect() {
            return lyrionController.getConnectionState().isManualDisconnect();
        }

        @Override
        public boolean isConnected() {
            return lyrionController.isConnected();
        }

        @Override
        public boolean isConnectInProgress() {
            return lyrionController.getConnectionState().isConnectInProgress();
        }

        @Override
        public boolean canAutoConnect() {
            return lyrionController.canAutoConnect();
        }

        @Override
        public void startConnect(boolean autoConnect) {
            lyrionController.startConnect(autoConnect);
        }

        @Override
        public void disconnect(boolean fromUser) {
            if (!isConnected()) return;
            SqueezeService.this.disconnect(fromUser);
        }

        @Override
        public void stopServer() {
            if (!isConnected()) return;
            lyrionController.command().cmd("stopserver").exec();
        }

        @Override
        public void restartServer() {
            if (!isConnected()) return;
            lyrionController.command().cmd("restartserver").exec();
        }

        @Override
        public void requestServerStatus() {
            lyrionController.requestServerStatus();
        }

        @Override
        public void togglePower(LyrionPlayer player) {
            lyrionController.togglePower(player);
        }

        @Override
        public void playerRename(LyrionPlayer player, String newName) {
            lyrionController.command(player).cmd("name", newName).exec();
        }

        @Override
        public void sleep(LyrionPlayer player, int duration) {
            lyrionController.command(player).cmd("sleep", String.valueOf(duration)).exec();
        }

        @Override
        public void syncPlayerToPlayer(@NonNull LyrionPlayer slave, @NonNull String masterId) {
            LyrionPlayer master = lyrionController.getPlayer(masterId);
            lyrionController.command(master).cmd("sync", slave.getId()).exec();
        }

        @Override
        public void unsyncPlayer(@NonNull LyrionPlayer player) {
            lyrionController.command(player).cmd("sync", "-").exec();
        }


        @Override
        @Nullable
        public PlayerState getActivePlayerState() {
            return lyrionController.getActivePlayerState();
        }

        @Override
        public void playerPref(LyrionPlayer.Pref playerPref, String value) {
            lyrionController.activePlayerCommand().cmd("playerpref", playerPref.prefName(), value).exec();
        }

        @Override
        public void playerPref(LyrionPlayer player, LyrionPlayer.Pref playerPref, String value) {
            lyrionController.command(player).cmd("playerpref", playerPref.prefName(), value).exec();
        }

        @Override
        public String getServerVersion() {
            return lyrionController.getServerVersion();
        }

        @Override
        public boolean togglePausePlay() {
            return togglePausePlay(getActivePlayer());
        }
        @Override
        public boolean togglePausePlay(LyrionPlayer player) {
            if (!isConnected()) {
                return false;
            }


            // May be null (e.g., connected to a server with no connected
            // players. TODO: Handle this better, since it's not obvious in the
            // UI.
            if (player == null)
                return false;

            PlayerState activePlayerState = player.getPlayerState();
            @PlayerState.PlayState String playStatus = activePlayerState.getPlayStatus();

            // May be null -- race condition when connecting to a server that
            // has a player. Squeezer knows the player exists, but has not yet
            // determined its state.
            if (playStatus == null)
                return false;

            switch (playStatus) {
                case PlayerState.PLAY_STATE_PLAY ->
                    // NOTE: we never send ambiguous "pause" toggle commands (without the '1')
                    // because then we'd get confused when they came back in to us, not being
                    // able to differentiate ours coming back on the listen channel vs. those
                    // of those idiots at the dinner party messing around.
                        lyrionController.command(player).cmd("pause", "1").exec();
                case PlayerState.PLAY_STATE_STOP ->
                        lyrionController.command(player).cmd("play", lyrionController.fadeInSecs()).exec();
                case PlayerState.PLAY_STATE_PAUSE ->
                        lyrionController.command(player).cmd("pause", "0", lyrionController.fadeInSecs()).exec();
            }

            return true;
        }

        @Override
        public void pause(LyrionPlayer player, boolean pause) {
            lyrionController.pause(player, pause);
        }

        @Override
        public boolean nextTrack() {
            return nextTrack(getActivePlayer());
        }
        @Override
        public boolean nextTrack(LyrionPlayer player) {
            return lyrionController.nextTrack(player);
        }

        @Override
        public boolean previousTrack() {
            return previousTrack(getActivePlayer());
        }

        @Override
        public boolean previousTrack(LyrionPlayer player) {
            return lyrionController.previousTrack(player);
        }

        @Override
        public boolean toggleShuffle() {
            if (!isConnected()) {
                return false;
            }
            lyrionController.activePlayerCommand().cmd("button", "shuffle").exec();
            return true;
        }

        @Override
        public boolean toggleRepeat() {
            if (!isConnected()) {
                return false;
            }
            lyrionController.activePlayerCommand().cmd("button", "repeat").exec();
            return true;
        }

        /**
         * Start playing the song in the current playlist at the given index.
         *
         * @param index the index to jump to
         */
        @Override
        public boolean playlistIndex(int index) {
            if (!isConnected()) {
                return false;
            }
            lyrionController.activePlayerCommand().cmd("playlist", "index", String.valueOf(index), lyrionController.fadeInSecs()).exec();
            return true;
        }

        @Override
        public boolean playlistRemove(int index) {
            if (!isConnected()) {
                return false;
            }
            lyrionController.activePlayerCommand().cmd("playlist" ,"delete", String.valueOf(index)).exec();
            return true;
        }

        @Override
        public boolean playlistMove(int fromIndex, int toIndex) {
            if (!isConnected()) {
                return false;
            }
            lyrionController.activePlayerCommand().cmd("playlist", "move", String.valueOf(fromIndex), String.valueOf(toIndex)).exec();
            return true;
        }

        @Override
        public boolean playlistClear() {
            if (!isConnected()) {
                return false;
            }
            lyrionController.activePlayerCommand().cmd("playlist", "clear").exec();
            return true;
        }

        @Override
        public boolean playlistSave(String name) {
            if (!isConnected()) {
                return false;
            }
            lyrionController.activePlayerCommand().cmd("playlist", "save", name).exec();
            return true;
        }

        @Override
        public boolean button(LyrionPlayer player, IRButton button) {
            if (!isConnected()) {
                return false;
            }
            lyrionController.command(player).cmd("button", button.getFunction()).exec();
            return true;
        }

        @Override
        public void setActivePlayer(@Nullable final LyrionPlayer newActivePlayer, boolean continuePlaying) {
            changeActivePlayer(newActivePlayer, continuePlaying);
        }

        @Override
        @Nullable
        public LyrionPlayer getActivePlayer() {
            return lyrionController.getActivePlayer();
        }

        @Override
        public List<LyrionPlayer> getPlayers() {
            return lyrionController.getPlayers().stream().filter(LyrionPlayer::getConnected).sorted().collect(Collectors.toList());
        }

        @Override
        public LyrionPlayer getPlayer(String playerId) throws PlayerNotFoundException {
            LyrionPlayer player = lyrionController.getPlayer(playerId);
            if (player == null) {
                throw new PlayerNotFoundException(SqueezeService.this);
            }
            return player;
        }

        @Override
        public @NonNull VolumeInfo getVolume() {
            return lyrionController.getVolume();
        }

        /**
         * @return null if there is no active player, otherwise the name of the current playlist,
         *     which may be the empty string.
         */
        @Override
        @Nullable
        public String getCurrentPlaylist() {
            PlayerState playerState = getActivePlayerState();

            if (playerState == null)
                return null;

            return playerState.getCurrentPlaylist();
        }

        @Override
        public void setSecondsElapsed(int seconds) {
            lyrionController.setSecondsElapsed(seconds);
        }

        @Override
        public void adjustSecondsElapsed(int seconds) {
            if (isConnected()) {
                lyrionController.activePlayerCommand().cmd("time", (seconds > 0 ? "+" : "") + seconds).exec();
            }
        }

        @Override
        public void preferenceChanged(Preferences preferences, String key) {
            Log.i(TAG, "Preference changed: " + key);
            if (Preferences.KEY_CUSTOMIZE_HOME_MENU_MODE.equals(key)) {
                boolean useArchive = preferences.getCustomizeHomeMenuMode() != Preferences.CustomizeHomeMenuMode.DISABLED;
                Set<String> archivedMenuItems = Collections.emptySet();
                if ((useArchive) && (getActivePlayer() != null)) {
                    archivedMenuItems = preferences.getArchivedMenuItems(getActivePlayer());
                }
                homeMenuHandling.updateArchivedItems(archivedMenuItems);
            } else if (Preferences.KEY_CUSTOMIZE_SHORTCUT_MODE.equals(key)) {
                if (preferences.getCustomizeShortcutsMode() == Preferences.CustomizeShortcutsMode.DISABLED) {
                    homeMenuHandling.removeAllShortcuts();
                    preferences.saveShortcuts(homeMenuHandling.getCustomShortcuts());
                }
            } else if (Preferences.KEY_ACTION_ON_INCOMING_CALL.equals(key)) {
                if (preferences.getActionOnIncomingCall() != Preferences.IncomingCallAction.NONE) {
                    callStateHelper.registerCallStateListener(getApplicationContext());
                }
            } else {
                cachePreferences(preferences);
            }
        }


        @Override
        public void cancelItemListRequests(Object client) {
            lyrionController.cancelClientRequests(client);
        }

        @Override
        public void alarms(int start, IServiceItemListCallback<Alarm> callback) {
            if (!isConnected()) {
                return;
            }
            lyrionController.requestItems(getActivePlayer(), start, callback).cmd("alarms").param("filter", "all").exec();
        }

        @Override
        public void alarmPlaylists(IServiceItemListCallback<AlarmPlaylist> callback) {
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
            lyrionController.requestItems(callback).cmd("alarm", "playlists").exec();
        }

        @Override
        public void alarmAdd(int time) {
            if (!isConnected()) {
                return;
            }
            lyrionController.activePlayerCommand().cmd("alarm", "add").param("time", time).exec();
        }

        @Override
        public void alarmDelete(String id) {
            if (!isConnected()) {
                return;
            }
            lyrionController.activePlayerCommand().cmd("alarm", "delete").param("id", id).exec();
        }

        @Override
        public void alarmSetTime(String id, int time) {
            if (!isConnected()) {
                return;
            }
            lyrionController.activePlayerCommand().cmd("alarm", "update").param("id", id).param("time", time).exec();
        }

        @Override
        public void alarmAddDay(String id, int day) {
            lyrionController.activePlayerCommand().cmd("alarm", "update").param("id", id).param("dowAdd", day).exec();
        }

        @Override
        public void alarmRemoveDay(String id, int day) {
            lyrionController.activePlayerCommand().cmd("alarm", "update").param("id", id).param("dowDel", day).exec();
        }

        @Override
        public void alarmEnable(String id, boolean enabled) {
            lyrionController.activePlayerCommand().cmd("alarm", "update").param("id", id).param("enabled", enabled ? "1" : "0").exec();
        }

        @Override
        public void alarmRepeat(String id, boolean repeat) {
            lyrionController.activePlayerCommand().cmd("alarm", "update").param("id", id).param("repeat", repeat ? "1" : "0").exec();
        }

        @Override
        public void alarmSetPlaylist(String id, AlarmPlaylist playlist) {
            lyrionController.activePlayerCommand().cmd("alarm", "update").param("id", id)
                    .param("url", "".equals(playlist.getId()) ? "0" : playlist.getId()).exec();
        }

        /* Start an asynchronous fetch of the slimserver generic menu items */
        @Override
        public void pluginItems(int start, String cmd, IServiceItemListCallback<JiveItem>  callback) {
            lyrionController.requestItems(getActivePlayer(), start, callback).cmd(cmd).param("menu", "menu").exec();
        }

        /* Start an asynchronous fetch of the slimserver generic menu items */
        @Override
        public void pluginItems(int start, JiveItem item, Action action, IServiceItemListCallback<JiveItem>  callback) {
            lyrionController.requestItems(getActivePlayer(), start, callback).cmd(action.action.cmd).params(action.action.params(item.inputValue)).exec();
        }

        @Override
        public void pluginItems(Action action, IServiceItemListCallback<JiveItem> callback) {
            // We cant use paging for context menu items as LMS does some "magic"
            // See XMLBrowser.pm ("xmlBrowseInterimCM" and  "# Cannot do this if we might screw up paging")
            lyrionController.requestItems(getActivePlayer(), callback).cmd(action.action.cmd).params(action.action.params).exec();
        }

        @Override
        public void action(JiveItem item, Action action) {
            if (!isConnected()) {
                return;
            }
            lyrionController.command(getActivePlayer()).cmd(action.action.cmd).params(action.action.params(item.inputValue)).exec();
        }

        @Override
        public void action(Action.JsonAction action) {
            if (!isConnected()) {
                return;
            }
            lyrionController.command(getActivePlayer()).cmd(action.cmd).params(action.params).exec();
        }

        @Override
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
            Set<String> played = Squeezer.getPreferences().loadRandomPlayed(folderID);
            LyrionPlayer player = lyrionController.getActivePlayer();
            RandomPlay randomPlay = lyrionController.getRandomPlay(player);
            randomPlay.reset(player);
            RandomPlay.RandomPlayCallback randomPlayCallback
                    = randomPlay.new RandomPlayCallback(randomPlayDelegate, folderID, played);
            lyrionController.requestAllItems(randomPlayCallback)
                    .params(command.params)
                    .cmd(command.cmd())
                    .exec();
            return true;
        }

        public void toggleArchiveItem(JiveItem item) {
            Set<String> archive = homeMenuHandling.toggleArchiveItem(item);
            Squeezer.getPreferences().setArchivedMenuItems(archive, getActivePlayer());
        }

        @Override
        public boolean isInArchive(JiveItem item) {
           return homeMenuHandling.isInArchive(item);
        }

        @Override
        public HomeMenuHandling getHomeMenuHandling() {
            return homeMenuHandling;
        }

        @Override
        public void setCustomShortcuts() {
            Preferences preferences = Squeezer.getPreferences();
            homeMenuHandling.updateShortcuts(preferences.homeGroups(), preferences.getCustomShortcuts());
        }

        @Override
        public void removeCustomShortcut(JiveItem item) {
            homeMenuHandling.removeShortcut(item);
            Squeezer.getPreferences().saveShortcuts(homeMenuHandling.getCustomShortcuts());
        }

        @Override
        public boolean addCustomShortcut(JiveItem item, JiveItem parent, int shortcutWeight) {
            Preferences preferences = Squeezer.getPreferences();
            if (homeMenuHandling.addShortcut(item, parent, shortcutWeight)) {
                preferences.saveShortcuts(homeMenuHandling.getCustomShortcuts());
                return true;
            }
            return false;
        }
    }
}
