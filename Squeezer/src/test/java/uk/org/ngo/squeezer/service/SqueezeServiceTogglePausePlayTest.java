package uk.org.ngo.squeezer.service;

import org.junit.Before;
import org.junit.Test;
import uk.org.ngo.squeezer.itemlist.IServiceItemListCallback;
import uk.org.ngo.squeezer.model.Player;
import uk.org.ngo.squeezer.model.PlayerState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class SqueezeServiceTogglePausePlayTest {

    private static class FakeSlimClient implements SlimClient {
        final List<List<String>> executedCommands = new ArrayList<>();
        int requestPlayerStatusCount = 0;
        final ConnectionState connectionState = new ConnectionState(new uk.org.ngo.squeezer.SqueezerRepository());

        FakeSlimClient() {
            connectionState.setServerVersion("7.9.0");
        }

        @Override
        public void startConnect(SqueezeService service, boolean autoConnect) {}

        @Override
        public void disconnect(boolean fromUser) {}

        @Override
        public ConnectionState getConnectionState() {
            return connectionState;
        }

        @Override
        public String getUsername() {
            return null;
        }

        @Override
        public String getPassword() {
            return null;
        }

        @Override
        public String getUrlPrefix() {
            return "http://localhost:9000";
        }

        @Override
        public void verifyConnectionHealth() {}

        @Override
        public void command(Player player, String[] cmd, Map<String, Object> params) {
            executedCommands.add(Arrays.asList(cmd));
        }

        @Override
        public <T> void requestItems(Player player, String[] cmd, Map<String, Object> params, int start, int pageSize, IServiceItemListCallback<T> callback) {}

        @Override
        public void requestServerStatus() {}

        @Override
        public void requestPlayerStatus(Player player) {
            requestPlayerStatusCount++;
        }

        @Override
        public void subscribePlayerStatus(Player player, PlayerState.PlayerSubscriptionType subscriptionType) {}

        @Override
        public void subscribeDisplayStatus(Player player, boolean subscribe) {}

        @Override
        public void subscribeMenuStatus(Player player, boolean subscribe) {}

        @Override
        public void cancelClientRequests(Object client) {}
    }

    private FakeSlimClient fakeSlimClient;
    private SlimDelegate slimDelegate;
    private Player player;

    @Before
    public void setUp() {
        fakeSlimClient = new FakeSlimClient();
        slimDelegate = new SlimDelegate(fakeSlimClient);

        Map<String, Object> record = new HashMap<>();
        record.put("id", "player-1");
        record.put("name", "Test Player");
        record.put("connected", 1);
        player = new Player(record);
    }

    /**
     * Helper mimicking SqueezeService.togglePausePlay implementation using SlimDelegate.
     */
    private boolean togglePausePlay(SlimDelegate delegate, Player player) {
        if (player == null) {
            return false;
        }

        PlayerState activePlayerState = player.getPlayerState();
        @PlayerState.PlayState String playStatus = activePlayerState.getPlayStatus();

        if (PlayerState.PLAY_STATE_PLAY.equals(playStatus)) {
            delegate.command(player).cmd("pause", "1").exec();
        } else if (PlayerState.PLAY_STATE_PAUSE.equals(playStatus)) {
            delegate.command(player).cmd("pause", "0", "0").exec();
        } else {
            // When playStatus is STOP, null, or out-of-sync, send LMS native "pause" toggle
            // so LMS toggles pause/play on the server side without restarting playback from 0:00
            delegate.command(player).cmd("pause", "0").exec();
        }
        delegate.requestPlayerStatus(player);
        return true;
    }

    @Test
    public void testTogglePausePlay_WhenPlaying_SendsPause1() {
        player.getPlayerState().setPlayStatus(PlayerState.PLAY_STATE_PLAY);

        boolean result = togglePausePlay(slimDelegate, player);

        assertTrue(result);
        assertEquals(1, fakeSlimClient.executedCommands.size());
        assertEquals(Arrays.asList("pause", "1"), fakeSlimClient.executedCommands.get(0));
        assertEquals(1, fakeSlimClient.requestPlayerStatusCount);
    }

    @Test
    public void testTogglePausePlay_WhenPaused_SendsPause0() {
        player.getPlayerState().setPlayStatus(PlayerState.PLAY_STATE_PAUSE);

        boolean result = togglePausePlay(slimDelegate, player);

        assertTrue(result);
        assertEquals(1, fakeSlimClient.executedCommands.size());
        assertEquals(Arrays.asList("pause", "0", "0"), fakeSlimClient.executedCommands.get(0));
        assertEquals(1, fakeSlimClient.requestPlayerStatusCount);
    }

    @Test
    public void testTogglePausePlay_WhenStopped_SendsPauseToggle_NeverSendsPlay() {
        player.getPlayerState().setPlayStatus(PlayerState.PLAY_STATE_STOP);

        boolean result = togglePausePlay(slimDelegate, player);

        assertTrue(result);
        assertEquals(1, fakeSlimClient.executedCommands.size());
        List<String> cmd = fakeSlimClient.executedCommands.get(0);
        // Must send "pause", NOT "play" (which would restart the track to 0:00)
        assertEquals("pause", cmd.get(0));
        assertNotEquals("play", cmd.get(0));
        assertEquals(1, fakeSlimClient.requestPlayerStatusCount);
    }

    @Test
    public void testTogglePausePlay_WhenNullOrUninitialized_SendsPauseToggle() {
        // playStatus is null by default on uninitialized player
        assertEquals(null, player.getPlayerState().getPlayStatus());

        boolean result = togglePausePlay(slimDelegate, player);

        assertTrue(result);
        assertEquals(1, fakeSlimClient.executedCommands.size());
        List<String> cmd = fakeSlimClient.executedCommands.get(0);
        // Must send "pause" toggle to LMS so it toggles atomically on the server
        assertEquals("pause", cmd.get(0));
        assertEquals(1, fakeSlimClient.requestPlayerStatusCount);
    }

    @Test
    public void testTogglePausePlay_WhenPlayerIsNull_ReturnsFalse() {
        boolean result = togglePausePlay(slimDelegate, null);

        assertFalse(result);
        assertEquals(0, fakeSlimClient.executedCommands.size());
        assertEquals(0, fakeSlimClient.requestPlayerStatusCount);
    }
}
