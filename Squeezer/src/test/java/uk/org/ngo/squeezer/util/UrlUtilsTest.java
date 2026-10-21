package uk.org.ngo.squeezer.util;

import junit.framework.TestCase;

public class UrlUtilsTest extends TestCase {

    public void testValidUrl() {
        assertFalse(UrlUtils.isValid(null));
        assertFalse(UrlUtils.isValid(""));
        assertFalse(UrlUtils.isValid("host"));
        assertFalse(UrlUtils.isValid("ftp:host"));
        assertFalse(UrlUtils.isValid("ftp//:host"));
        assertFalse(UrlUtils.isValid("ftp//:host:20"));
        assertFalse(UrlUtils.isValid("a//:host"));
        assertFalse(UrlUtils.isValid("https://host:abc"));

        assertTrue(UrlUtils.isValid("https://host"));
        assertTrue(UrlUtils.isValid("https://host:9000"));
        assertTrue(UrlUtils.isValid("https://host/cometd"));
        assertTrue(UrlUtils.isValid("https://host:9000/cometd"));

        assertTrue(UrlUtils.isValid("http://host"));
        assertTrue(UrlUtils.isValid("http://host:9000"));
        assertTrue(UrlUtils.isValid("http://host/cometd"));
        assertTrue(UrlUtils.isValid("http://host:9000/cometd"));
    }

    public void testAddDefaults() {
        assertNull(UrlUtils.addDefaults(null));
        assertEquals("", UrlUtils.addDefaults(""));

        assertEquals("http://host:9000", UrlUtils.addDefaults("host"));
        assertEquals("http://host:80", UrlUtils.addDefaults("host:80"));
        assertEquals("http://host:9000", UrlUtils.addDefaults("host:9000"));
        assertEquals("http://host/cometd", UrlUtils.addDefaults("host/cometd"));
        assertEquals("http://host:80/cometd", UrlUtils.addDefaults("host:80/cometd"));
        assertEquals("http://host:9000/cometd", UrlUtils.addDefaults("host:9000/cometd"));

        assertEquals("//host:9000", UrlUtils.addDefaults("//host"));
        assertEquals("//host:80", UrlUtils.addDefaults("//host:80"));
        assertEquals("//host/cometd", UrlUtils.addDefaults("//host/cometd"));
        assertEquals("//host:80/cometd", UrlUtils.addDefaults("//host:80/cometd"));
        assertEquals("//host:9000/cometd", UrlUtils.addDefaults("//host:9000/cometd"));

        assertEquals("http://host:9000", UrlUtils.addDefaults("http://host"));
        assertEquals("http://host:80", UrlUtils.addDefaults("http://host:80"));
        assertEquals("http://host:9000", UrlUtils.addDefaults("http://host:9000"));
        assertEquals("http://host/cometd", UrlUtils.addDefaults("http://host/cometd"));
        assertEquals("http://host:80/cometd", UrlUtils.addDefaults("http://host:80/cometd"));
        assertEquals("http://host:9000/cometd", UrlUtils.addDefaults("http://host:9000/cometd"));
    }

}
