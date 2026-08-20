package uk.org.ngo.squeezer.volume;

import androidx.annotation.NonNull;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

public class VolumeDialTimeoutController {

    @FunctionalInterface
    public interface Scheduler {
        void schedule(@NonNull Runnable runnable, long delayMillis);
    }

    private final Scheduler scheduler;
    private final Consumer<Runnable> canceller;
    private final Runnable timeoutAction;
    private final BooleanSupplier isTimeoutEnabled;
    private final LongSupplier timeoutDelayMillis;

    private boolean isTrackingTouch = false;
    private boolean isScheduled = false;

    private final Runnable internalRunnable = new Runnable() {
        @Override
        public void run() {
            isScheduled = false;
            if (!isTrackingTouch && isTimeoutEnabled.getAsBoolean()) {
                timeoutAction.run();
            }
        }
    };

    public VolumeDialTimeoutController(
            @NonNull Scheduler scheduler,
            @NonNull Consumer<Runnable> canceller,
            @NonNull Runnable timeoutAction,
            @NonNull BooleanSupplier isTimeoutEnabled,
            @NonNull LongSupplier timeoutDelayMillis
    ) {
        this.scheduler = scheduler;
        this.canceller = canceller;
        this.timeoutAction = timeoutAction;
        this.isTimeoutEnabled = isTimeoutEnabled;
        this.timeoutDelayMillis = timeoutDelayMillis;
    }

    public void reset() {
        cancel();
        if (isTimeoutEnabled.getAsBoolean() && !isTrackingTouch) {
            isScheduled = true;
            scheduler.schedule(internalRunnable, timeoutDelayMillis.getAsLong());
        }
    }

    public void cancel() {
        if (isScheduled) {
            canceller.accept(internalRunnable);
            isScheduled = false;
        }
    }

    public void onUserInteraction() {
        if (!isTrackingTouch) {
            reset();
        }
    }

    public void onTrackingTouch(boolean trackingTouch) {
        this.isTrackingTouch = trackingTouch;
        if (trackingTouch) {
            cancel();
        } else {
            reset();
        }
    }

    public boolean isScheduled() {
        return isScheduled;
    }

    public boolean isTrackingTouch() {
        return isTrackingTouch;
    }

    public Runnable getTimeoutRunnable() {
        return internalRunnable;
    }
}
