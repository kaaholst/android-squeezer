package uk.org.ngo.squeezer;


import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.StrictMode;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;

import org.eclipse.jetty.util.ajax.JSON;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import uk.org.ngo.squeezer.util.ImageFetcher;

// Trick to make the app context useful available everywhere.
// See http://stackoverflow.com/questions/987072/using-application-context-everywhere

public class Squeezer extends Application implements SharedPreferences.OnSharedPreferenceChangeListener {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler uiThreadHandler = new Handler(Looper.getMainLooper());

    private static Squeezer instance;
    private Preferences preferences;

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

        instance = this;
        preferences = new Preferences(this, getSharedPreferences(Preferences.NAME, Context.MODE_PRIVATE));
        AppCompatDelegate.setDefaultNightMode(preferences.getTheme().getNightMode());
        preferences.getSharedPreferences().registerOnSharedPreferenceChangeListener(this);


        // Read the default shared preferences cause it's used in de.cketti.library.changelog.ChangeLog
        doInBackground(() -> PreferenceManager.getDefaultSharedPreferences(Squeezer.this).getString("dummy", ""));

        // Jetty JSON has a loader which has a static logger property which use disk read.
        // We load the class off thread to avoid a StrictMode violation.
        doInBackground(JSON::new);

        // Instantiate the image fetcher off thread.
        doInBackground(() -> ImageFetcher.getInstance(Squeezer.this));

        super.onCreate();
    }

    public void doInBackground(Runnable task) {
        executor.execute(task);
    }

    /**
     * Return the preferences to the UI thread
     * <p>
     * If this is called from the UI thread directly to the callback, otherwise it is
     * posted to the UI thread.
     *
     * @param callback This will be called with the preferences.
     */
    public static void getPreferences(final Consumer<Preferences> callback) {
        if (instance.uiThreadHandler.getLooper() == Looper.myLooper()) {
            callback.accept(instance.preferences);
        } else {
            instance.uiThreadHandler.post(() -> callback.accept(instance.preferences));
        }
    }

    public static Preferences getPreferences() {
        return instance.preferences;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, @Nullable String key) {
        if (Preferences.KEY_ON_THEME_SELECT_ACTION.equals(key)) {
            AppCompatDelegate.setDefaultNightMode(preferences.getTheme().getNightMode());
        }
    }
}

