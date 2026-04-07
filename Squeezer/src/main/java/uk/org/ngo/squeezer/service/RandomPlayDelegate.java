package uk.org.ngo.squeezer.service;

import android.util.Log;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import uk.org.ngo.squeezer.Preferences;
import uk.org.ngo.squeezer.Squeezer;
import uk.org.ngo.squeezer.Util;
import uk.org.ngo.squeezer.model.JiveItem;
import uk.org.ngo.squeezer.model.LyrionPlayer;
import uk.org.ngo.squeezer.model.PlayerState;
import uk.org.ngo.squeezer.model.SlimCommand;

class RandomPlayDelegate {

    private static final String TAG = "RandomPlayDelegate";

    RandomPlayDelegate(LyrionController lyrionController) {
        this.lyrionController = lyrionController;
    }

    private final LyrionController lyrionController;

    static String pickTrack(Set<String> unplayed, String ignore) {
        Log.i(TAG, String.format("Picking track randomly. Ignore '%s'.", ignore));
        Object[] stringArray = unplayed.toArray();
        String track;
        Random random = new Random();
        do {
            int r = random.nextInt(unplayed.size());
            track = (String) stringArray[r];
        } while (ignore.equals(track)); // ignore the last track in case of new cycle
        return track;
    }

    void fillPlaylist(Set<String> unplayed, LyrionPlayer player, String ignore) {
        String nextTrack = pickTrack(unplayed, ignore);
        lyrionController.command(player).cmd("playlistcontrol")
                .param("cmd", "add").param("track_id", nextTrack).exec();
        // Get the next track and set it for this player's instance.
        // It will be loaded to be added to the played tracks when the next track begins
        // to play (the track info does not contain the ID, so we have to do this).
        lyrionController.setNextTrack(player, nextTrack);
    }

    void addItems(String folderID, Set<String> folderTracks) {
        lyrionController.addItems(folderID, folderTracks);
    }

    void setActiveFolderID(String folderID) {
        lyrionController.setActiveFolderID(folderID);
    }

    Set<String> getTracks(String folderID) {
        return lyrionController.getTracks(folderID);
    }

    LyrionController getSlimDelegate() {
        return lyrionController;
    }

    Boolean randomPlayFolder(JiveItem item) {
        SlimCommand command = item.randomPlayFolderCommand();
        String folderID = Util.getString(command.params, "folder_id");
        if (folderID == null) {
            Log.e(TAG, "randomPlayFolder: No folder_id");
            return false;
        }
        Set<String> played = Squeezer.instance().preferences().loadRandomPlayed(folderID);
        LyrionPlayer player = lyrionController.getActivePlayer();
        RandomPlay randomPlay = lyrionController.getRandomPlay(player);
        randomPlay.reset(player);
        RandomPlay.RandomPlayCallback randomPlayCallback
                = randomPlay.new RandomPlayCallback(this, folderID, played);
        lyrionController
                .requestAllItems(randomPlayCallback)
                .params(command.params)
                .cmd(command.cmd())
                .exec();
        return true;
    }

    void handleRandomOnEvent(LyrionPlayer player) {
        RandomPlay randomPlay = lyrionController.getRandomPlay(player);
        Preferences preferences = Squeezer.instance().preferences();
        PlayerState playerState = player.getPlayerState();

        int number = playerState.getCurrentPlaylistTracksNum();
        int index = playerState.getCurrentPlaylistIndex();
        Log.i(TAG, String.format("Random Play event for %s has number %d with index %d.", player.getName(), number, index));
        String nextTrack = randomPlay.getNextTrack();
        if (endRandomPlay(number, index)) {
            Log.i(TAG, String.format("End Random Play and reset '%s'.", player.getName()));
            randomPlay.reset(player);
        } else if (firstTwoTracksLoaded(number, index)) {
            Log.i(TAG, String.format("Ignore event after Random Play initialization for player '%s'.", player.getName()));
        } else {
            Log.i(TAG, String.format("Handle Random Play after event for player '%s'.", player.getName()));
            String folderID = randomPlay.getActiveFolderID();
            Set<String> tracks = randomPlay.getTracks(folderID);
            Set<String> played = preferences.loadRandomPlayed(folderID);
            played.add(nextTrack);
            preferences.saveRandomPlayed(folderID, played);
            Set<String> unplayed = new HashSet<>(tracks);
            if (played.size() == tracks.size()) {
                Log.i(TAG, String.format("All Random played from folder %s on player %s. Clear!", folderID, player.getName()));
                played.clear();
                preferences.saveRandomPlayed(folderID, played);
            } else {
                unplayed.removeAll(played);
                Log.i(TAG, String.format("Loaded %s unplayed tracks from folder %s for Random Play on player %s.", unplayed.size(), folderID, player.getName()));
            }
            if (!unplayed.isEmpty()) {
                fillPlaylist(unplayed, player, nextTrack);
            } else {
                Log.e(TAG, String.format("No unplayed tracks found for Random Play in folder %s on %s!", folderID, player.getName()));
            }
        }
    }

    private boolean endRandomPlay(int number, int index) {
        // After a MusicChanged event we have to check if this meant that the last track of random
        // play is now playing. In this case we load another track. If the track changed but there
        // are more tracks in the playlist after it, it means that the user might have added tracks
        // to the end of the playlist. So we deactivate Random Play.
        // On the other hand the user might have just chosen another track from the already played
        // random tracks (currently we don't consider this).
        // TODO endRandomPlay could be better.
        if ( (number - index == 1) && (number > 1) ) {
            // last track playing
            return false;
        }
        else return !firstTwoTracksLoaded(number, index);
    }

    private boolean firstTwoTracksLoaded(int number, int index) {
        return (number - index == 2) && (number == 2);
    }
}