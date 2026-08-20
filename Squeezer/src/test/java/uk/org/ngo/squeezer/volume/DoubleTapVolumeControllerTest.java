package uk.org.ngo.squeezer.volume;

import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.List;

public class DoubleTapVolumeControllerTest extends TestCase {

    private static class TestClock implements DoubleTapVolumeController.Clock {
        private long currentTime = 1000;

        @Override
        public long elapsedRealtime() {
            return currentTime;
        }

        public void advance(long ms) {
            currentTime += ms;
        }
    }

    private static class TestCallback implements DoubleTapVolumeController.Callback {
        final List<Integer> volumeAdjustments = new ArrayList<>();
        int nextTrackCalls = 0;
        boolean playing = true;

        @Override
        public void adjustVolume(int direction) {
            volumeAdjustments.add(direction);
        }

        @Override
        public void nextTrack() {
            nextTrackCalls++;
        }

        @Override
        public boolean isPlaying() {
            return playing;
        }
    }

    private TestClock clock;
    private TestCallback callback;
    private DoubleTapVolumeController controller;

    @Override
    protected void setUp() {
        clock = new TestClock();
        callback = new TestCallback();
        controller = new DoubleTapVolumeController(callback, clock, 60, 250);
        controller.setEnabled(true);
        controller.setTimeoutMs(400);
    }

    public void testDisabled() {
        controller.setEnabled(false);

        controller.onAdjustVolume(1);
        clock.advance(150);
        controller.onAdjustVolume(1);

        assertEquals(2, callback.volumeAdjustments.size());
        assertEquals(Integer.valueOf(1), callback.volumeAdjustments.get(0));
        assertEquals(Integer.valueOf(1), callback.volumeAdjustments.get(1));
        assertEquals(0, callback.nextTrackCalls);
    }

    public void testSingleTap() {
        controller.onAdjustVolume(1);

        assertEquals(1, callback.volumeAdjustments.size());
        assertEquals(Integer.valueOf(1), callback.volumeAdjustments.get(0));
        assertEquals(0, callback.nextTrackCalls);
    }

    public void testDoubleTapSameKeyWithinTimeout() {
        // First tap
        controller.onAdjustVolume(1);
        assertEquals(1, callback.volumeAdjustments.size());
        assertEquals(Integer.valueOf(1), callback.volumeAdjustments.get(0));

        // Second tap at 200ms
        clock.advance(200);
        controller.onAdjustVolume(1);

        // Volume should be reverted (-1) and next track called
        assertEquals(2, callback.volumeAdjustments.size());
        assertEquals(Integer.valueOf(-1), callback.volumeAdjustments.get(1));
        assertEquals(1, callback.nextTrackCalls);
    }

    public void testDoubleTapDownKeyWithinTimeout() {
        // First tap down
        controller.onAdjustVolume(-1);
        assertEquals(1, callback.volumeAdjustments.size());
        assertEquals(Integer.valueOf(-1), callback.volumeAdjustments.get(0));

        // Second tap down at 250ms
        clock.advance(250);
        controller.onAdjustVolume(-1);

        // Volume should be reverted (+1) and next track called
        assertEquals(2, callback.volumeAdjustments.size());
        assertEquals(Integer.valueOf(1), callback.volumeAdjustments.get(1));
        assertEquals(1, callback.nextTrackCalls);
    }

    public void testDoubleTapWhileStoppedRevertsVolumeWithoutSkip() {
        callback.playing = false;

        // First tap
        controller.onAdjustVolume(1);
        assertEquals(1, callback.volumeAdjustments.size());
        assertEquals(Integer.valueOf(1), callback.volumeAdjustments.get(0));

        // Second tap at 200ms
        clock.advance(200);
        controller.onAdjustVolume(1);

        // Volume should be reverted (-1) but next track NOT called
        assertEquals(2, callback.volumeAdjustments.size());
        assertEquals(Integer.valueOf(-1), callback.volumeAdjustments.get(1));
        assertEquals(0, callback.nextTrackCalls);
    }

    public void testDoubleTapWithZeroEventsInterleaved() {
        // Tap 1
        controller.onAdjustVolume(1);
        assertEquals(1, callback.volumeAdjustments.size());

        // Interleaved direction=0 (from Android MediaSession key release) at 3ms
        clock.advance(3);
        controller.onAdjustVolume(0);
        assertEquals(1, callback.volumeAdjustments.size());

        // Tap 2 at 180ms
        clock.advance(177);
        controller.onAdjustVolume(1);

        // Should successfully trigger double tap
        assertEquals(2, callback.volumeAdjustments.size());
        assertEquals(Integer.valueOf(-1), callback.volumeAdjustments.get(1));
        assertEquals(1, callback.nextTrackCalls);
    }

    public void testDifferentKeysDoNotSkip() {
        // Tap UP
        controller.onAdjustVolume(1);
        assertEquals(1, callback.volumeAdjustments.size());
        assertEquals(Integer.valueOf(1), callback.volumeAdjustments.get(0));

        // Tap DOWN at 150ms
        clock.advance(150);
        controller.onAdjustVolume(-1);

        // Both volumes applied, no reversion, no skip
        assertEquals(2, callback.volumeAdjustments.size());
        assertEquals(Integer.valueOf(-1), callback.volumeAdjustments.get(1));
        assertEquals(0, callback.nextTrackCalls);
    }

    public void testSlowTapDoesNotSkip() {
        // Tap 1
        controller.onAdjustVolume(1);
        assertEquals(1, callback.volumeAdjustments.size());

        // Tap 2 at 450ms (> 400ms timeout)
        clock.advance(450);
        controller.onAdjustVolume(1);

        // Second tap is treated as a new first tap
        assertEquals(2, callback.volumeAdjustments.size());
        assertEquals(Integer.valueOf(1), callback.volumeAdjustments.get(1));
        assertEquals(0, callback.nextTrackCalls);
    }

    public void testKeyRepeatSuppressed() {
        // Initial tap
        controller.onAdjustVolume(1);
        assertEquals(1, callback.volumeAdjustments.size());

        // OS key repeat event at 40ms (< 60ms min repeat interval)
        clock.advance(40);
        controller.onAdjustVolume(1);

        // Ignored
        assertEquals(1, callback.volumeAdjustments.size());
        assertEquals(0, callback.nextTrackCalls);
    }

    public void testCooldownAfterSkip() {
        // Double tap
        controller.onAdjustVolume(1);
        clock.advance(200);
        controller.onAdjustVolume(1);

        assertEquals(1, callback.nextTrackCalls);
        assertEquals(2, callback.volumeAdjustments.size());

        // Accidental 3rd tap at 100ms after skip (< 250ms cooldown)
        clock.advance(100);
        controller.onAdjustVolume(1);

        // Ignored during cooldown
        assertEquals(1, callback.nextTrackCalls);
        assertEquals(2, callback.volumeAdjustments.size());
    }
}
