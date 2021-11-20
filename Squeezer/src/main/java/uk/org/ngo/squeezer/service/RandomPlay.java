package uk.org.ngo.squeezer.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import uk.org.ngo.squeezer.Preferences;
import uk.org.ngo.squeezer.Squeezer;
import uk.org.ngo.squeezer.itemlist.IServiceItemListCallback;
import uk.org.ngo.squeezer.model.MusicFolderItem;
import uk.org.ngo.squeezer.model.Player;

/*
One instance of class per Player
 */
public class RandomPlay {

    public RandomPlay(Player player) {
        this.player = player;
        this.firstFound = false;
        this.nextTrack = "inactive";
        this.activeFolderID = "";
    }

    private final Player player;
    private String activeFolderID;

    private Boolean firstFound;
    private String nextTrack;
    private Map<String, Set<String>> tracks = new HashMap<>();
    private BiFunction<Set<String>, Set<String>, Set<String>> mergeSets = (set1, set2) -> set1 == null ?
            set1 : Stream.concat(set1.stream(), set2.stream()).collect(Collectors.toSet());

    public String getNextTrack() {
        return this.nextTrack;
    }

    public String getActiveFolderID() {
        return this.activeFolderID;
    }

    public String getNext() {
        return this.nextTrack;
    }

    public int addItems(String folderID, Set<String> stringSetOfFifty) {
        tracks.merge(folderID, stringSetOfFifty, mergeSets);
        return tracks.get(folderID).size();
    }

    public Set<String> getTracks(String folderID) {
        return this.tracks.get(folderID);
    }

    public void setNextTrack(String nextTrack) {
        this.nextTrack = nextTrack;
    }

    public void setActiveFolderID(String folderID) {
        this.activeFolderID = folderID;
    }

    public void resetFirstFound() {
        this.firstFound = false;
    }

    class RandomPlayCallback implements IServiceItemListCallback<MusicFolderItem> {

        String folderID;
        Set<String> played;
        final RandomPlayDelegate rDelegate;

        public RandomPlayCallback(RandomPlayDelegate randomPlayDelegate,
                                  String folderID, Set<String> played) {
            this.folderID = folderID;
            this.played = played;
            this.rDelegate = randomPlayDelegate;
        }

        public void onItemsReceived(int count, int start, Map<String, Object> parameters,
                                    List<MusicFolderItem> items, Class<MusicFolderItem> dataType) {

            Set<String> folderTracks = new HashSet<>();
            for (MusicFolderItem item : items) {
                if ("track".equals(item.type)) {
                    folderTracks.add(item.id);
                }
            }

            // Add 50 items and folderID to correct RandomPlay(player), not this instance!
            rDelegate.addItems(this.folderID, folderTracks);
            rDelegate.setActiveFolderID(this.folderID);

            // Get Set of all current items and try to find one unplayed, if this has not yet been done
            if (!RandomPlay.this.firstFound) {
                Set<String> loaded = new HashSet<>(rDelegate.getTracks(this.folderID));
                loaded.removeAll(this.played);
                try {
                    playFirst(loaded);
                } catch (Exception e) {
                }
            }

            // All items loaded, if no unplayed are found, clear played
            if (start + items.size() >= count) {
                if (!RandomPlay.this.firstFound) {
                    this.played.clear();
                    try {
                        playFirst(new HashSet<>(rDelegate.getTracks(this.folderID)));
                    } catch (Exception e) {
                    }
                }

                // Generate playlist
                try {
                    rDelegate.fillPlaylist(new HashSet<>(rDelegate.getTracks(this.folderID)),
                            player, "no_ignore");
                } catch (Exception e) {
                }
            }
        }

        @Override
        public Object getClient() {
            return null;
        }

        private String playFirst(Set<String> unplayed) throws Exception {
            return playFirst(unplayed, "no_ignore");
        }

        // Get a track to play it, add it to played, save played to pref
        private String playFirst(Set<String> unplayed, String ignore) throws Exception {
            if (unplayed.size() > 0 ) {
                String first = rDelegate.pickTrack(unplayed, ignore);
                rDelegate.getSlimDelegate().activePlayerCommand().cmd("playlist", "clear").exec();
                rDelegate.getSlimDelegate().command(rDelegate.getSlimDelegate().getActivePlayer())
                        .cmd("playlistcontrol").param("cmd", "load")
                        .param("play_index", "1").param("track_id", first).exec();
                this.played.add(first);
                RandomPlay.this.firstFound = true;
                new Preferences(Squeezer.getContext()).saveRandomPlayed(folderID, played);
                return first;
            } else {
                throw new Exception("Could not find a first track to play it");
            }
        }
    }
}