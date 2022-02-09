package uk.org.ngo.squeezer.service;

import android.util.Log;

import java.util.Random;
import java.util.Set;

import uk.org.ngo.squeezer.model.Player;

public class RandomPlayDelegate {

    private static final String TAG = "RandomPlayDelegate";

    RandomPlayDelegate(SlimDelegate mDelegate) {
        if (slimDelegate == null) {
            slimDelegate = mDelegate;
        }
    }

    private SlimDelegate slimDelegate;

    static String pickTrack(Set<String> unplayed, String ignore) {
        Log.i(TAG, String.format("Picking track randomly. Ignore '%s'.", ignore));
        Object[] stringArray = unplayed.toArray();
        String track = "";
        Random random = new Random();
        do {
            int r = random.nextInt(unplayed.size());
            track = (String) stringArray[r];
        } while (ignore.equals(track)); // ignore the last track in case of new cycle
        return track;
    }

    void fillPlaylist(Set<String> unplayed, Player player, String ignore) {
        String nextTrack = pickTrack(unplayed, ignore);
        slimDelegate.command(player).cmd("playlistcontrol")
                .param("cmd", "add").param("track_id", nextTrack).exec();
        // Get the next track and set it for this player's instance.
        // It will be loaded to be added to the played tracks when the next track begins
        // to play (the track info does not contain the ID, so we have to do this).
        slimDelegate.setNextTrack(player, nextTrack);
    }

    void addItems(String folderID, Set<String> folderTracks) {
        slimDelegate.addItems(folderID, folderTracks);
    }

    void setActiveFolderID(String folderID) {
        slimDelegate.setActiveFolderID(folderID);
    }

    Set<String> getTracks(String folderID) {
        return slimDelegate.getTracks(folderID);
    }

    SlimDelegate getSlimDelegate() {
        return slimDelegate;
    }
}