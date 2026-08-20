package uk.org.ngo.squeezer.test.model;

import android.test.AndroidTestCase;

import java.util.HashMap;
import java.util.Map;

import uk.org.ngo.squeezer.model.CurrentTrack;

public class CurrentTrackTest extends AndroidTestCase {

    public void testGetNameFallsBackToSongInfoTitle() {
        Map<String, Object> record = new HashMap<>();
        record.put("title", "Test Song Title");
        CurrentTrack track = new CurrentTrack(record);

        assertEquals("Test Song Title", track.getName());
    }

    public void testGetNameUsesTrackFieldWhenPresent() {
        Map<String, Object> record = new HashMap<>();
        record.put("track", "Test Track Title");
        CurrentTrack track = new CurrentTrack(record);

        assertEquals("Test Track Title", track.getName());
    }

    public void testGetNamePrefersJiveNameWhenPresent() {
        Map<String, Object> record = new HashMap<>();
        record.put("name", "Jive Item Name");
        record.put("title", "Song Info Title");
        CurrentTrack track = new CurrentTrack(record);

        assertEquals("Jive Item Name", track.getName());
    }
}
