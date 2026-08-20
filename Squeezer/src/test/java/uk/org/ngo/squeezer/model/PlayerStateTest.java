package uk.org.ngo.squeezer.model;

import org.junit.Test;

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
}
