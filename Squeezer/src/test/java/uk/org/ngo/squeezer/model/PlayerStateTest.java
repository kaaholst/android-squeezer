package uk.org.ngo.squeezer.model;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlayerStateTest {

    @Test
    public void testSetCurrentPlaylistIndexReturnsTrueOnlyOnChange() {
        PlayerState playerState = new PlayerState();
        playerState.setCurrentPlaylistIndex(0);

        assertFalse(playerState.setCurrentPlaylistIndex(0));
        assertTrue(playerState.setCurrentPlaylistIndex(1));
        assertFalse(playerState.setCurrentPlaylistIndex(1));
        assertTrue(playerState.setCurrentPlaylistIndex(5));
    }

    @Test
    public void testSetCurrentSongReturnsTrueOnlyOnChangeOrEnrichment() {
        PlayerState playerState = new PlayerState();

        Map<String, Object> initialRecord = new HashMap<>();
        initialRecord.put("id", "track-1");
        initialRecord.put("title", "Song 1");
        CurrentTrack track1 = new CurrentTrack(initialRecord);

        // Setting a song when current is null should return true
        assertTrue(playerState.setCurrentSong(track1));

        // Setting the identical song should return false (no change)
        CurrentTrack track1Duplicate = new CurrentTrack(initialRecord);
        assertFalse(playerState.setCurrentSong(track1Duplicate));

        // Setting an enriched song (e.g. detailed tags arrived from LMS) should return true
        Map<String, Object> enrichedRecord = new HashMap<>();
        enrichedRecord.put("id", "track-1");
        enrichedRecord.put("title", "Song 1");
        enrichedRecord.put("album", "Enriched Album Name");
        enrichedRecord.put("artist", "Enriched Artist Name");
        CurrentTrack track1Enriched = new CurrentTrack(enrichedRecord);
        assertTrue(playerState.setCurrentSong(track1Enriched));

        // Setting a different song should return true
        Map<String, Object> secondRecord = new HashMap<>();
        secondRecord.put("id", "track-2");
        secondRecord.put("title", "Song 2");
        CurrentTrack track2 = new CurrentTrack(secondRecord);
        assertTrue(playerState.setCurrentSong(track2));
    }

    @Test
    public void testSubscriptionTypeReset() {
        PlayerState playerState = new PlayerState();
        assertEquals(PlayerState.PlayerSubscriptionType.NOTIFY_NONE, playerState.getSubscriptionType());

        playerState.setSubscriptionType(PlayerState.PlayerSubscriptionType.NOTIFY_ON_CHANGE);
        assertEquals(PlayerState.PlayerSubscriptionType.NOTIFY_ON_CHANGE, playerState.getSubscriptionType());

        // Reset to NOTIFY_NONE on disconnect/reconnect
        playerState.setSubscriptionType(PlayerState.PlayerSubscriptionType.NOTIFY_NONE);
        assertEquals(PlayerState.PlayerSubscriptionType.NOTIFY_NONE, playerState.getSubscriptionType());
    }

    @Test
    public void testShuffleStatus() {
        PlayerState playerState = new PlayerState();
        playerState.setShuffleStatus(PlayerState.ShuffleStatus.SHUFFLE_OFF);
        assertEquals(PlayerState.ShuffleStatus.SHUFFLE_OFF, playerState.getShuffleStatus());

        playerState.setShuffleStatus(PlayerState.ShuffleStatus.SHUFFLE_SONG);
        assertEquals(PlayerState.ShuffleStatus.SHUFFLE_SONG, playerState.getShuffleStatus());

        playerState.setShuffleStatus(PlayerState.ShuffleStatus.SHUFFLE_ALBUM);
        assertEquals(PlayerState.ShuffleStatus.SHUFFLE_ALBUM, playerState.getShuffleStatus());

        assertEquals(0, PlayerState.ShuffleStatus.SHUFFLE_OFF.getId());
        assertEquals(1, PlayerState.ShuffleStatus.SHUFFLE_SONG.getId());
        assertEquals(2, PlayerState.ShuffleStatus.SHUFFLE_ALBUM.getId());
    }
}
