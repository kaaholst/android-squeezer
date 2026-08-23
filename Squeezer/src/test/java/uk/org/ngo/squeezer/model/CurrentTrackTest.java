package uk.org.ngo.squeezer.model;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class CurrentTrackTest {

    @Test
    public void testEqualsAndHashCodeSameSongInfo() {
        Map<String, Object> record1 = new HashMap<>();
        record1.put("id", "123");
        record1.put("title", "Song A");
        record1.put("artist", "Artist A");
        record1.put("album", "Album A");

        Map<String, Object> record2 = new HashMap<>();
        record2.put("id", "123");
        record2.put("title", "Song A");
        record2.put("artist", "Artist A");
        record2.put("album", "Album A");

        CurrentTrack track1 = new CurrentTrack(record1);
        CurrentTrack track2 = new CurrentTrack(record2);

        assertEquals(track1, track2);
        assertEquals(track1.hashCode(), track2.hashCode());
    }

    @Test
    public void testNotEqualsWhenSongInfoDiffers() {
        Map<String, Object> record1 = new HashMap<>();
        record1.put("id", "123");
        record1.put("title", "Song A");
        record1.put("artist", "Artist A");

        Map<String, Object> record2 = new HashMap<>();
        record2.put("id", "123");
        record2.put("title", "Song A");
        record2.put("artist", "Artist A");
        record2.put("album", "Album Enriched");

        CurrentTrack track1 = new CurrentTrack(record1);
        CurrentTrack track2 = new CurrentTrack(record2);

        assertNotEquals(track1, track2);
    }

    @Test
    public void testNotEqualsWhenEnrichedAfterCreation() {
        Map<String, Object> record1 = new HashMap<>();
        record1.put("id", "123");
        record1.put("title", "Song A");

        Map<String, Object> record2 = new HashMap<>();
        record2.put("id", "123");
        record2.put("title", "Song A");

        CurrentTrack track1 = new CurrentTrack(record1);
        CurrentTrack track2 = new CurrentTrack(record2);
        assertEquals(track1, track2);

        // Enrich track2 with detailed tag info
        Map<String, Object> enrichedTagRecord = new HashMap<>();
        enrichedTagRecord.put("id", "123");
        enrichedTagRecord.put("title", "Song A");
        enrichedTagRecord.put("album", "Newly Resolved Album");
        enrichedTagRecord.put("artist", "Newly Resolved Artist");
        track2.songInfo = new Song(enrichedTagRecord);

        assertNotEquals(track1, track2);
    }

    @Test
    public void testIsSameSongWithEnrichedSongInfo() {
        Map<String, Object> basicStatusRecord = new HashMap<>();
        basicStatusRecord.put("id", "123");
        basicStatusRecord.put("title", "Song A");

        CurrentTrack basicTrack = new CurrentTrack(basicStatusRecord);

        // Enriched track
        Map<String, Object> enrichedTagRecord = new HashMap<>();
        enrichedTagRecord.put("id", "123");
        enrichedTagRecord.put("title", "Song A");
        enrichedTagRecord.put("album", "Newly Resolved Album");
        enrichedTagRecord.put("artist", "Newly Resolved Artist");
        enrichedTagRecord.put("composer", "Mozart");
        CurrentTrack enrichedTrack = new CurrentTrack(basicStatusRecord);
        enrichedTrack.songInfo = new Song(enrichedTagRecord);

        org.junit.Assert.assertTrue(enrichedTrack.isSameSong(basicTrack));
        org.junit.Assert.assertTrue(basicTrack.isSameSong(enrichedTrack));
    }
}
