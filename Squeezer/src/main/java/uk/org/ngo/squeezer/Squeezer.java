package uk.org.ngo.squeezer;


import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.StrictMode;

import androidx.preference.PreferenceManager;

import org.eclipse.jetty.util.ajax.JSON;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import uk.org.ngo.squeezer.util.ImageFetcher;

// Trick to make the app context useful available everywhere.
// See http://stackoverflow.com/questions/987072/using-application-context-everywhere

public class Squeezer extends Application {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler uiThreadHandler = new Handler(Looper.getMainLooper());

    private static Squeezer instance;
    private static SharedPreferences preferences;

    public Squeezer() {
        instance = this;
    }

    public static Squeezer getInstance() {
        return instance;
    }

    @Override
    public void onCreate() {
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()   // or .detectAll() for all detectable problems
                    .penaltyLog()
                    .build());
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
//                .penaltyDeath()
                    .build());
        }

        // Initiate an off thread load of our preferences
        getPreferences(Preferences::isDownloadEnabled);

        // Read the default shared preferences cause it's used in de.cketti.library.changelog.ChangeLog
        instance.executor.execute(() -> PreferenceManager.getDefaultSharedPreferences(Squeezer.this).getString("dummy", ""));

        // Jetty JSON has a loader which has a static logger property which use disk read.
        // We load the class off thread to avoid a StrictMode violation.
        instance.executor.execute(JSON::new);

        // Instantiate the image fetcher off thread.
        instance.executor.execute(() -> ImageFetcher.getInstance(Squeezer.this));

        super.onCreate();
    }

    public void doInBackground(Runnable task) {
        executor.execute(task);
    }

    /**
     * Load the preferences from external storage
     * <p>
     * If the preferences are already loaded, the callback will be called immediately,
     * otherwise the callback will be called when they are loaded.
     * <p>
     * The result is given directly to a {@link ResultFuture}, otherwise it is
     * posted to the UI thread.
     *
     * @param callback This will be called when the remotes are ready.
     */
    public static void getPreferences(final Consumer<Preferences> callback) {
        instance.executor.execute(() -> {
            if (preferences == null) {
                preferences = instance.getSharedPreferences(Preferences.NAME, Context.MODE_PRIVATE);
            }
            Preferences preferences = new Preferences(instance, Squeezer.preferences);
            if (callback instanceof ResultFuture) {
                callback.accept(preferences);
            } else {
                instance.uiThreadHandler.post(() -> callback.accept(preferences));
            }

        });
    }

    /** Synchronous fetch of preferences. */
    public static Preferences getPreferences() {
        final ResultFuture<Preferences> resultFuture = new ResultFuture<>();
        getPreferences(resultFuture);
        return resultFuture.get();
    }

    /**
     * Helper to run async tasks synchronously.
     *
     * @param <T> Type of result for the async callback
     * @see Consumer
     */
    private static class ResultFuture<T> implements Future<T>, Consumer<T> {
        private T result;
        private final CountDownLatch countDownLatch = new CountDownLatch(1);

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return countDownLatch.getCount() == 0;
        }

        public T get() {
            try {
                countDownLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return result;
        }

        @Override
        public T get(long timeout, TimeUnit unit) {
            throw new UnsupportedOperationException("Only synchronous operation is allowed");
        }

        @Override
        public void accept(T result) {
            this.result = result;
            countDownLatch.countDown();
        }
    }
}

