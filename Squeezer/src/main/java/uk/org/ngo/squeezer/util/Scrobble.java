package uk.org.ngo.squeezer.util;

import android.content.Context;
import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;

import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.Squeezer;
import uk.org.ngo.squeezer.model.CurrentPlaylistItem;
import uk.org.ngo.squeezer.model.PlayerState;

public class Scrobble {

    public static boolean haveScrobbleDroid() {
        return Intents.isBroadcastReceiverAvailable(
                Squeezer.getInstance(),
                "net.jjc1138.android.scrobbler.action.MUSIC_STATUS");
    }

    public static boolean haveSls() {
        return Intents.isBroadcastReceiverAvailable(
                Squeezer.getInstance(),
                "com.adam.aslfms.notify.playstatechanged");
    }

    public static boolean canScrobble() {
        return haveScrobbleDroid() || haveSls();
    }

    /**
     * Conditionally broadcasts a scrobbling intent populated from a player state.
     *
     * @param context the context to use to fetch resources.
     * @param playerState the player state to scrobble from.
     */
    public static void scrobbleFromPlayerState(@NonNull Context context, @Nullable PlayerState playerState) {
        if (playerState == null || !Scrobble.canScrobble())
            return;

        @PlayerState.PlayState String playStatus = playerState.getPlayStatus();
        CurrentPlaylistItem currentSong = playerState.getCurrentSong();

        if (playStatus == null || currentSong == null)
            return;

        Log.d("Scrobble", "Scrobbling, playing is: " + (PlayerState.PLAY_STATE_PLAY.equals(playStatus)));
        Intent i = new Intent();

        if (Scrobble.haveSls()) {
            // http://code.google.com/p/a-simple-lastfm-scrobbler/wiki/Developers
            i.setAction("com.adam.aslfms.notify.playstatechanged");
            i.putExtra("state", PlayerState.PLAY_STATE_PLAY.equals(playStatus) ? 0 : 2);
            i.putExtra("app-name", context.getText(R.string.app_name));
            i.putExtra("app-package", "uk.org.ngo.squeezer");
            i.putExtra("track", currentSong.getName());
            i.putExtra("album", currentSong.getAlbum());
            i.putExtra("artist", currentSong.getArtist());
            i.putExtra("duration", playerState.getCurrentSongDuration());
            i.putExtra("source", "P");
        } else if (Scrobble.haveScrobbleDroid()) {
            // http://code.google.com/p/scrobbledroid/wiki/DeveloperAPI
            i.setAction("net.jjc1138.android.scrobbler.action.MUSIC_STATUS");
            i.putExtra("playing", PlayerState.PLAY_STATE_PLAY.equals(playStatus));
            i.putExtra("track", currentSong.getName());
            i.putExtra("album", currentSong.getAlbum());
            i.putExtra("artist", currentSong.getArtist());
            i.putExtra("secs", playerState.getCurrentSongDuration());
            i.putExtra("source", "P");
        }
        context.sendBroadcast(i);
    }
}
