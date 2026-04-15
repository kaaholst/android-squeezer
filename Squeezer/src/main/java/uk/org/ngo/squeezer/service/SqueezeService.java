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

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;

import androidx.annotation.OptIn;
import androidx.core.app.NotificationManagerCompat;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.CommandButton;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;
import androidx.media3.session.SessionCommand;
import androidx.media3.session.SessionResult;

import android.util.Log;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.jetbrains.annotations.NotNull;

import java.util.List;

import uk.org.ngo.squeezer.NowPlayingActivity;
import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.Squeezer;
import uk.org.ngo.squeezer.SqueezerRepository;
import uk.org.ngo.squeezer.model.LyrionPlayer;
import uk.org.ngo.squeezer.service.event.ActivePlayerChanged;
import uk.org.ngo.squeezer.service.event.MusicChanged;
import uk.org.ngo.squeezer.service.event.PlayStatusChanged;
import uk.org.ngo.squeezer.service.event.PlayerStateChanged;
import uk.org.ngo.squeezer.util.Intents;

public class SqueezeService extends MediaSessionService implements MediaSession.Callback {
    private static final String TAG = "SqueezeService";

    private static final int PLAYBACKSERVICE_STATUS = 1;

    @OptIn(markerClass = {UnstableApi.class})
    private SqueezeboxPlayer mediaPlayer;
    private MediaSession mediaSession;

    private LyrionController lyrionController;
    private SqueezerRepository repository;

    static final String ACTION_POWER = "power";
    static final String ACTION_DISCONNECT = "disconnect";

    @Override
    @OptIn(markerClass = {UnstableApi.class})
    public void onCreate() {
        Log.i(TAG, "::onCreate()");
        super.onCreate();

        // Clear leftover notification in case this service previously got killed while playing
        NotificationManagerCompat nm = NotificationManagerCompat.from(this);
        nm.cancel(PLAYBACKSERVICE_STATUS);

        Squeezer squeezer = (Squeezer) getApplication();
        repository = squeezer.repository();
        lyrionController = squeezer.lyrionController();
        mediaPlayer = new SqueezeboxPlayer(lyrionController);

        var powerButton = new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                .setDisplayName(getString(R.string.menu_item_poweron))
                .setCustomIconResId(R.drawable.power)
                .setSessionCommand(new SessionCommand(ACTION_POWER, new Bundle()))
                .build();
        var disconnectButton = new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                .setDisplayName(getString(R.string.menu_item_disconnect))
                .setCustomIconResId(R.drawable.ic_action_disconnect)
                .setSessionCommand(new SessionCommand(ACTION_DISCONNECT, new Bundle()))
                .build();

        var showNowPlaying = new Intent(this, NowPlayingActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        var activityIntent = PendingIntent.getActivity(this, 0, showNowPlaying, Intents.immutablePendingIntent());

        mediaSession = new MediaSession.Builder(this, mediaPlayer)
                .setCallback(this)
                .setCustomLayout(List.of(powerButton, disconnectButton))
                .setSessionActivity(activityIntent)
                // TODO .setBitmapLoader(CacheBitmapLoader(bitmapLoader))
                .build();
        addSession(mediaSession);

        repository.observeForever(this::onMusicChanged);
        repository.observeForever(this::onPlayStatusChanged);
        repository.observeForever(this::onPlayerStateChanged);
        repository.observeForever(this::onActivePlayerChanged);
        // TODO clean up observers in CometClient (also look for observeForever)
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "::onStartCommand(" + intent + ")");
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "::onBind(" + intent + ")");
        return super.onBind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(TAG, "::onUnbind(" + intent + ")");
        return super.onUnbind(intent);
    }

    @Override
    @OptIn(markerClass = {UnstableApi.class})
    public void onDestroy() {
        repository.removeObserver(this::onMusicChanged);
        repository.removeObserver(this::onPlayStatusChanged);
        repository.removeObserver(this::onPlayerStateChanged);
        repository.removeObserver(this::onActivePlayerChanged);
        mediaPlayer.release();
        mediaSession.release();
        super.onDestroy();
    }

    @Override
    public MediaSession onGetSession(MediaSession.@NotNull ControllerInfo controllerInfo) {
        Log.i(TAG, "::onGetSession(" + controllerInfo + ")");
        return mediaSession;
    }

    @Override
    @OptIn(markerClass = {UnstableApi.class})
    public MediaSession.@NotNull ConnectionResult onConnect(@NotNull MediaSession session, MediaSession.@NotNull ControllerInfo controller) {
        Log.i(TAG, "::onConnect(" + controller + ")");
        var sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(new SessionCommand(ACTION_POWER, Bundle.EMPTY))
                .add(new SessionCommand(ACTION_DISCONNECT, Bundle.EMPTY))
                .build();
        return new MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .build();
    }

    @Override
    public @NotNull ListenableFuture<SessionResult> onCustomCommand(
            @NotNull MediaSession session,
            @NotNull MediaSession.ControllerInfo controller,
            @NotNull SessionCommand customCommand,
            @NotNull Bundle args
    ) {
        Log.i(TAG, "::onCustomCommand(" + customCommand.customAction + ")");
        var sessionResult = switch (customCommand.customAction) {
            case ACTION_DISCONNECT -> {
                lyrionController.disconnect(true);
                yield SessionResult.RESULT_SUCCESS;
            }
            case ACTION_POWER -> {
                lyrionController.togglePower(lyrionController.getActivePlayer());
                yield SessionResult.RESULT_SUCCESS;
            }
            default -> SessionResult.RESULT_ERROR_NOT_SUPPORTED;
        };
        return Futures.immediateFuture(new SessionResult(sessionResult));
    }

    @OptIn(markerClass = {UnstableApi.class})
    private void updateMediaSession(LyrionPlayer player) {
        if (player == null || player.equals(lyrionController.getActivePlayer())) {
            mediaPlayer.updateMediaSession();
        }
    }

    private void onActivePlayerChanged(ActivePlayerChanged event) {
        updateMediaSession(null);
    }

    private void onMusicChanged(MusicChanged event) {
        updateMediaSession(event.player);
    }

    private void onPlayStatusChanged(PlayStatusChanged event) {
        updateMediaSession(event.player);
    }

    private void onPlayerStateChanged(PlayerStateChanged event) {
        updateMediaSession(event.player);
    }

}
