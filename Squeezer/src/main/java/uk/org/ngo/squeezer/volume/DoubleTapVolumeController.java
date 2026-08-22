package uk.org.ngo.squeezer.volume;

import android.os.SystemClock;
import android.util.Log;

/**
 * Handles double-tap volume button gestures with optimistic volume adjustment and reversal.
 * On the first volume tap, the volume is adjusted immediately for zero-latency response.
 *
 * If the SAME volume key is tapped again within the timeout window (e.g. 400ms), the first
 * volume change is reverted and a track skip is triggered.
 *
 * Continuous key holding (long-press) is suppressed using hold lockout so holding the button
 * never causes runaway track skips.
 */
public class DoubleTapVolumeController {

    private static final String TAG = "DoubleTapVolume";

    public interface Callback {
        void adjustVolume(int direction);
        void nextTrack();
        boolean isPlaying();
    }

    public interface Clock {
        long elapsedRealtime();
    }

    private static final Clock SYSTEM_CLOCK = () -> {
        try {
            return SystemClock.elapsedRealtime();
        } catch (Throwable t) {
            return System.currentTimeMillis();
        }
    };
    private static final int DEFAULT_MIN_REPEAT_INTERVAL_MS = 60;
    private static final int DEFAULT_HOLD_LOCKOUT_MS = 600;

    private final Callback callback;
    private final Clock clock;
    private final int minRepeatIntervalMs;
    private final int holdLockoutMs;

    private boolean enabled;
    private int timeoutMs = 400;

    private int lastDirection = 0;
    private long lastTapTime = 0;
    private long holdLockoutUntil = 0;

    public DoubleTapVolumeController(Callback callback) {
        this(callback, SYSTEM_CLOCK, DEFAULT_MIN_REPEAT_INTERVAL_MS, DEFAULT_HOLD_LOCKOUT_MS);
    }

    public DoubleTapVolumeController(Callback callback, Clock clock, int minRepeatIntervalMs, int holdLockoutMs) {
        this.callback = callback;
        this.clock = clock;
        this.minRepeatIntervalMs = minRepeatIntervalMs;
        this.holdLockoutMs = holdLockoutMs;
    }

    private static void logD(String msg) {
        try {
            Log.d(TAG, msg);
        } catch (Throwable ignored) {
        }
    }

    private static void logI(String msg) {
        try {
            Log.i(TAG, msg);
        } catch (Throwable ignored) {
        }
    }

    public synchronized void setEnabled(boolean enabled) {
        this.enabled = enabled;
        logI("Double-tap volume skip enabled=" + enabled);
        if (!enabled) {
            reset();
        }
    }

    public synchronized boolean isEnabled() {
        return enabled;
    }

    public synchronized void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
        logI("Double-tap volume timeout set to " + timeoutMs + "ms");
    }

    public synchronized int getTimeoutMs() {
        return timeoutMs;
    }

    public synchronized void reset() {
        lastDirection = 0;
        lastTapTime = 0;
        holdLockoutUntil = 0;
    }

    /**
     * Handles a volume adjustment event.
     *
     * @param direction +1 for volume up, -1 for volume down
     */
    public synchronized void onAdjustVolume(int direction) {
        if (direction == 0) {
            // Ignore no-op / ADJUST_SAME / key-release events from MediaSession
            return;
        }

        if (!enabled) {
            logD("Disabled -> adjusting volume: " + direction);
            callback.adjustVolume(direction);
            return;
        }

        long now = clock.elapsedRealtime();

        // If in hold lockout, extend the lockout window as long as events continue arriving
        if (now < holdLockoutUntil) {
            holdLockoutUntil = now + holdLockoutMs;
            logD("Ignoring event during hold lockout (extended lockout to " + holdLockoutMs + "ms)");
            return;
        }

        long elapsed = now - lastTapTime;

        // Ignore rapid key repeats (e.g. holding down the volume key)
        if (lastDirection != 0 && direction == lastDirection && elapsed < minRepeatIntervalMs) {
            logD("Ignoring rapid key repeat (elapsed: " + elapsed + "ms < " + minRepeatIntervalMs + "ms)");
            return;
        }

        // Check if this is a valid double-tap of the same key within timeout
        if (lastDirection != 0 && direction == lastDirection && elapsed <= timeoutMs) {
            // Revert optimistic volume adjustment from first tap (undoing the volume change)
            callback.adjustVolume(-direction);

            if (callback.isPlaying()) {
                logI("Double-tap detected while playing! elapsed=" + elapsed + "ms, direction=" + direction + ". Reverted volume and skipping track.");
                callback.nextTrack();
            } else {
                logI("Double-tap detected while stopped/paused! elapsed=" + elapsed + "ms, direction=" + direction + ". Reverted volume (undone, no track skip).");
            }

            // Reset and enter hold lockout so continuous holding does not trigger runaway skips
            lastDirection = 0;
            lastTapTime = 0;
            holdLockoutUntil = now + holdLockoutMs;
            return;
        }

        // First tap (or new direction / timed out) -> apply volume immediately
        logD("First tap / new tap: direction=" + direction + ", elapsed=" + elapsed + "ms. Applying optimistic volume.");
        lastDirection = direction;
        lastTapTime = now;
        callback.adjustVolume(direction);
    }
}
