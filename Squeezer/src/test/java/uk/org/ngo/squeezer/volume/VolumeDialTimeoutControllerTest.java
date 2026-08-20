package uk.org.ngo.squeezer.volume;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class VolumeDialTimeoutControllerTest {

    private AtomicReference<Runnable> scheduledRunnable;
    private AtomicLong scheduledDelay;
    private AtomicInteger cancellationCount;
    private AtomicBoolean actionExecuted;

    private boolean timeoutEnabled;
    private long timeoutDelayMillis;

    private VolumeDialTimeoutController controller;

    @Before
    public void setUp() {
        scheduledRunnable = new AtomicReference<>(null);
        scheduledDelay = new AtomicLong(-1);
        cancellationCount = new AtomicInteger(0);
        actionExecuted = new AtomicBoolean(false);

        timeoutEnabled = true;
        timeoutDelayMillis = 5000L;

        controller = new VolumeDialTimeoutController(
                (runnable, delay) -> {
                    scheduledRunnable.set(runnable);
                    scheduledDelay.set(delay);
                },
                runnable -> {
                    scheduledRunnable.set(null);
                    scheduledDelay.set(-1);
                    cancellationCount.incrementAndGet();
                },
                () -> actionExecuted.set(true),
                () -> timeoutEnabled,
                () -> timeoutDelayMillis
        );
    }

    @Test
    public void testResetWhenEnabledSchedulesTimeoutWithCorrectDelay() {
        controller.reset();

        assertTrue(controller.isScheduled());
        assertNotNull(scheduledRunnable.get());
        assertEquals(5000L, scheduledDelay.get());
    }

    @Test
    public void testResetWhenDisabledDoesNotScheduleTimeout() {
        timeoutEnabled = false;

        controller.reset();

        assertFalse(controller.isScheduled());
        assertNull(scheduledRunnable.get());
        assertEquals(-1L, scheduledDelay.get());
    }

    @Test
    public void testTimeoutExecutionWhenEnabledExecutesAction() {
        controller.reset();
        assertTrue(controller.isScheduled());

        Runnable runnable = controller.getTimeoutRunnable();
        runnable.run();

        assertFalse(controller.isScheduled());
        assertTrue(actionExecuted.get());
    }

    @Test
    public void testTimeoutExecutionWhenDisabledDoesNotExecuteAction() {
        controller.reset();
        timeoutEnabled = false;

        Runnable runnable = controller.getTimeoutRunnable();
        runnable.run();

        assertFalse(controller.isScheduled());
        assertFalse(actionExecuted.get());
    }

    @Test
    public void testTimeoutExecutionWhenTrackingTouchDoesNotExecuteAction() {
        controller.onTrackingTouch(true);

        Runnable runnable = controller.getTimeoutRunnable();
        runnable.run();

        assertFalse(actionExecuted.get());
    }

    @Test
    public void testOnUserInteractionResetsTimeout() {
        controller.onUserInteraction();

        assertTrue(controller.isScheduled());
        assertNotNull(scheduledRunnable.get());
        assertEquals(5000L, scheduledDelay.get());
    }

    @Test
    public void testOnUserInteractionIgnoredWhileTrackingTouch() {
        controller.onTrackingTouch(true);
        assertFalse(controller.isScheduled());

        controller.onUserInteraction();

        assertFalse(controller.isScheduled());
        assertNull(scheduledRunnable.get());
    }

    @Test
    public void testOnTrackingTouchTrueCancelsActiveTimeout() {
        controller.reset();
        assertTrue(controller.isScheduled());

        controller.onTrackingTouch(true);

        assertTrue(controller.isTrackingTouch());
        assertFalse(controller.isScheduled());
        assertNull(scheduledRunnable.get());
        assertEquals(1, cancellationCount.get());
    }

    @Test
    public void testOnTrackingTouchFalseRestartsTimeout() {
        controller.onTrackingTouch(true);
        assertFalse(controller.isScheduled());

        controller.onTrackingTouch(false);

        assertFalse(controller.isTrackingTouch());
        assertTrue(controller.isScheduled());
        assertNotNull(scheduledRunnable.get());
        assertEquals(5000L, scheduledDelay.get());
    }

    @Test
    public void testCancelCancelsScheduledTimeout() {
        controller.reset();
        assertTrue(controller.isScheduled());

        controller.cancel();

        assertFalse(controller.isScheduled());
        assertNull(scheduledRunnable.get());
        assertEquals(1, cancellationCount.get());
    }

    @Test
    public void testMultipleResetsCancelPreviousAndScheduleNew() {
        controller.reset();
        assertEquals(0, cancellationCount.get());

        controller.reset();
        assertEquals(1, cancellationCount.get());
        assertTrue(controller.isScheduled());
    }
}
