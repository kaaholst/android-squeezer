package uk.org.ngo.squeezer.service;

import android.os.Looper;
import android.util.Log;

import androidx.media3.common.DeviceInfo;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.SimpleBasePlayer;
import androidx.media3.common.Timeline;
import androidx.media3.common.util.UnstableApi;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

import uk.org.ngo.squeezer.itemlist.ItemListCallback;
import uk.org.ngo.squeezer.model.JiveItem;
import uk.org.ngo.squeezer.model.LyrionPlayer;
import uk.org.ngo.squeezer.model.PlayerState;
import uk.org.ngo.squeezer.service.event.PlaylistChanged;

@UnstableApi
class SqueezeboxPlayer extends SimpleBasePlayer {
    private static final String TAG = "SqueezeboxPlayer";

    private final LyrionController lyrionController;

    public SqueezeboxPlayer(LyrionController lyrionController) {
        super(Looper.getMainLooper());
        this.lyrionController = lyrionController;
        Log.i(TAG, "::new");
    }

    public LyrionPlayer currentPlayer() {
        return lyrionController.getActivePlayer();
    }

    public void updateMediaSession() {
        Log.i(TAG, "::updateMediaSession()");
        invalidateState();
    }

    public void onPlaylistChanged(PlaylistChanged event) {
        if (event.player == lyrionController.getActivePlayer()) {
            lyrionController.requestAllItems(new ItemListCallback<JiveItem>() {
                @Override
                public Object getClient() {
                    return lyrionController;
                }

                @Override
                public void onItemsReceived(int count, int start, Map<String, Object> parameters, List<JiveItem> items, Class<JiveItem> dataType) {
                    playlist = items;
                    invalidateState();
                }
            });
        }
    }

    public void onConnectionChanged() {
        invalidateState();
    }

    private List<JiveItem> playlist = null;

    @Override
    public @NotNull ListenableFuture<Void> handleSetDeviceVolume(int deviceVolume, int flags) {
        lyrionController.setVolumeTo(deviceVolume);
        return Futures.immediateVoidFuture();
    }

    @Override
    public @NotNull ListenableFuture<Void> handleIncreaseDeviceVolume(int flags) {
        lyrionController.adjustVolume(1);
        return Futures.immediateVoidFuture();
    }

    @Override
    public @NotNull ListenableFuture<Void> handleDecreaseDeviceVolume(int flags) {
        lyrionController.adjustVolume(-1);
        return Futures.immediateVoidFuture();
    }

    @Override
    public @NotNull ListenableFuture<Void> handleSetDeviceMuted(boolean muted, int flags) {
        lyrionController.mute(currentPlayer(), muted);
        return Futures.immediateVoidFuture();
    }

    @Override
    public @NotNull ListenableFuture<Void> handleSetPlayWhenReady(boolean play) {
        lyrionController.pause(currentPlayer(), !play);
        return Futures.immediateVoidFuture();
    }

    @Override
    public @NotNull ListenableFuture<Void> handleStop() {
        lyrionController.pause(currentPlayer(), true);
        return Futures.immediateVoidFuture();
    }

    @Override
    public @NotNull ListenableFuture<Void> handleSeek(int mediaItemIndex, long positionMs, int seekCommand) {
        switch (seekCommand) {
            case Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_NEXT ->
                    lyrionController.nextTrack(currentPlayer());
            case Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, Player.COMMAND_SEEK_TO_PREVIOUS ->
                    lyrionController.previousTrack(currentPlayer());
            case Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM -> {
                int positionSeconds = (int) ((positionMs + 500) / 1000);
                lyrionController.setSecondsElapsed(positionSeconds);
            }
            case Player.COMMAND_SEEK_TO_MEDIA_ITEM -> {
                int positionSeconds = (int) ((positionMs + 500) / 1000);
                lyrionController.playlistIndex(mediaItemIndex);
                if (positionSeconds > 0) {
                    lyrionController.setSecondsElapsed(positionSeconds);
                }
            }
            default -> {
            }
        }
        return Futures.immediateVoidFuture();
    }


    @Override
    public @NotNull State getState() {
        Log.i(TAG, "::getState()");
        LyrionPlayer player = currentPlayer();
        var status = (player != null ? player.getPlayerState() : null);
        var currentSong = (status != null ? status.getCurrentTrack() : null);
        if (currentSong == null) return new State.Builder().setPlaybackState(STATE_IDLE).build();

        var currentSongDuration = status.getCurrentTrackDuration();
        var currentIndex = status.getCurrentPlaylistIndex();

        var metadata = new MediaMetadata.Builder()
                .setTitle(currentSong.songInfo.title)
                .setArtist(currentSong.songInfo.getArtist())
                .setAlbumTitle(currentSong.songInfo.getAlbumArtists())
                .setArtworkUri(currentSong.getIcon())
                .build();
        var media = new MediaItemData.Builder(Timeline.Window.SINGLE_WINDOW_UID)
                .setMediaItem(
                        new MediaItem.Builder()
                                .setMediaId("0")
                                .setMediaMetadata(metadata)
                                .build()
                )
                .setDurationUs(currentSongDuration * 1_000_000L)
                .setMediaMetadata(metadata)
                .build();
        var playlist = List.of(media);

        var commands = new Commands.Builder()
                .add(Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS)
                .add(Player.COMMAND_GET_DEVICE_VOLUME)
                .add(COMMAND_GET_CURRENT_MEDIA_ITEM)
                .add(COMMAND_GET_METADATA)
                .add(COMMAND_GET_TIMELINE)
                .add(COMMAND_PLAY_PAUSE)
                .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_TO_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_TO_NEXT)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                .add(Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS)
                .add(Player.COMMAND_STOP)
                .build();

        var playWhenReady = status.isPlaying();
        int playbackState =
                !lyrionController.isConnected() ? STATE_BUFFERING
                        : !status.isPoweredOn() ? Player.STATE_IDLE
                        : PlayerState.PLAY_STATE_STOP.equals(status.getPlayStatus()) ? Player.STATE_IDLE : Player.STATE_READY;

        DeviceInfo deviceInfo = new DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE)
                .setMinVolume(0)
                .setMaxVolume(100)
                .build();

        return new State.Builder()
                .setPlaybackState(playbackState)
                .setAvailableCommands(commands)
                .setContentPositionMs(status.getPosition())
                .setDeviceInfo(deviceInfo)
                .setPlayWhenReady(playWhenReady, PLAY_WHEN_READY_CHANGE_REASON_REMOTE)
                .setPlaylist(playlist)
                .setCurrentMediaItemIndex(0)
                .setDeviceVolume(status.getCurrentVolume())
                .setIsDeviceMuted(status.isMuted())
                .build();
    }

}
