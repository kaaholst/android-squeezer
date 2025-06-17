package uk.org.ngo.squeezer.test.util;

import junit.framework.TestCase;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicReference;

import uk.org.ngo.squeezer.Util;
import uk.org.ngo.squeezer.model.Item;
import uk.org.ngo.squeezer.model.CurrentTrack;
import uk.org.ngo.squeezer.model.JiveItem;

public class UtilTest extends TestCase {

    public void testAtomicReferenceUpdated() {
        AtomicReference<String> atomicString = new AtomicReference<>();
        assertFalse(Util.atomicReferenceUpdated(atomicString, null));
        assertNull(atomicString.get());
        assertTrue(Util.atomicReferenceUpdated(atomicString, "test"));
        assertEquals("test", atomicString.get());
        assertFalse(Util.atomicReferenceUpdated(atomicString, "test"));
        assertEquals("test", atomicString.get());
        assertTrue(Util.atomicReferenceUpdated(atomicString, "change"));
        assertEquals("change", atomicString.get());
        assertTrue(Util.atomicReferenceUpdated(atomicString, null));
        assertNull(atomicString.get());
        assertTrue(Util.atomicReferenceUpdated(atomicString, "change"));
        assertEquals("change", atomicString.get());
        assertTrue(Util.atomicReferenceUpdated(atomicString, null));
        assertNull(atomicString.get());
        assertFalse(Util.atomicReferenceUpdated(atomicString, null));
        assertNull(atomicString.get());

        AtomicReference<Item> atomicItem = new AtomicReference<>();
        JiveItem album = new JiveItem(new HashMap<String, Object>());
        album.setId("1");
        album.setName("Album");
        CurrentTrack song = new CurrentTrack(new HashMap<String, Object>());
        song.setId("1");

        assertFalse(Util.atomicReferenceUpdated(atomicItem, null));
        assertNull(atomicItem.get());
        assertTrue(Util.atomicReferenceUpdated(atomicItem, album));
        assertEquals(album, atomicItem.get());

        album.setName("new_name");
        assertFalse(Util.atomicReferenceUpdated(atomicItem, album));
        assertEquals(album, atomicItem.get());

        assertTrue(Util.atomicReferenceUpdated(atomicItem, song));
        assertEquals(song, atomicItem.get());

        album.setId("2");
        assertTrue(Util.atomicReferenceUpdated(atomicItem, album));
        assertEquals(album, atomicItem.get());

        assertTrue(Util.atomicReferenceUpdated(atomicItem, null));
        assertNull(atomicItem.get());
    }

    public void testParseInt() {
        assertEquals(2, Util.parseDecimalIntOrZero("2"));
        assertEquals(0, Util.parseDecimalIntOrZero("2x"));
        assertEquals(2, Util.parseDecimalIntOrZero("2.0"));
        assertEquals(2, Util.parseDecimalIntOrZero("2.9"));
        assertEquals(0, Util.parseDecimalIntOrZero(null));
        assertEquals(-2, Util.parseDecimalIntOrZero("-2"));
        assertEquals(0, Util.parseDecimalIntOrZero("2,0"));

        assertEquals(123456789, Util.parseDecimalInt("123456789", -1));
        assertEquals(-1, Util.parseDecimalInt("0x8", -1));
    }

    public void testTimeString() {
        assertEquals("0:00", Util.formatElapsedTime(0));
        assertEquals("0:01", Util.formatElapsedTime(1));
        assertEquals("0:10", Util.formatElapsedTime(10));
        assertEquals("0:59", Util.formatElapsedTime(59));
        assertEquals("1:00", Util.formatElapsedTime(60));
        assertEquals("1:01", Util.formatElapsedTime(61));
        assertEquals("1:59", Util.formatElapsedTime(119));
        assertEquals("2:00", Util.formatElapsedTime(120));
        assertEquals("2:01", Util.formatElapsedTime(121));
        assertEquals("18:39", Util.formatElapsedTime(1119));
        assertEquals("19:59", Util.formatElapsedTime(1199));
        assertEquals("20:00", Util.formatElapsedTime(1200));
        assertEquals("20:01", Util.formatElapsedTime(1201));
        assertEquals("20:11", Util.formatElapsedTime(1211));
    }
}
