/*
 * Copyright (c) 2011 Kurt Aaholst <kaaholst@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.org.ngo.squeezer.framework;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.CallSuper;
import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NavUtils;
import androidx.core.app.TaskStackBuilder;
import androidx.lifecycle.ViewModelProvider;

import java.util.Arrays;
import java.util.HashSet;

import uk.org.ngo.squeezer.Preferences;
import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.Squeezer;
import uk.org.ngo.squeezer.SqueezerRepository;
import uk.org.ngo.squeezer.dialog.AlertEventDialog;
import uk.org.ngo.squeezer.dialog.DownloadDialog;
import uk.org.ngo.squeezer.itemlist.HomeActivity;
import uk.org.ngo.squeezer.model.Action;
import uk.org.ngo.squeezer.model.DisplayMessage;
import uk.org.ngo.squeezer.model.JiveItem;
import uk.org.ngo.squeezer.model.Player;
import uk.org.ngo.squeezer.screensaver.Screensaver;
import uk.org.ngo.squeezer.service.ISqueezeService;
import uk.org.ngo.squeezer.service.SqueezeService;
import uk.org.ngo.squeezer.service.event.AlertEvent;
import uk.org.ngo.squeezer.service.event.DisplayEvent;
import uk.org.ngo.squeezer.util.ImageFetcher;
import uk.org.ngo.squeezer.util.DevicePlayers;
import uk.org.ngo.squeezer.util.ThemeManager;
import uk.org.ngo.squeezer.widget.UndoBarController;
import uk.org.ngo.squeezer.volume.VolumeKeysDelegate;

/**
 * Common base class for all activities in Squeezer.
 *
 * @author Kurt Aaholst
 */
public abstract class BaseActivity extends AppCompatActivity implements DownloadDialog.DownloadDialogListener {
    private static final String CURRENT_DOWNLOAD_ITEM = "CURRENT_DOWNLOAD_ITEM";

    private static final String TAG = BaseActivity.class.getSimpleName();

    @Nullable
    private ISqueezeService mService = null;

    private final ThemeManager themeManager = new ThemeManager();

    /** Control device  players */
    private DevicePlayers devicePlayers;

    /** Whether volume keys shall be handled. */
    private boolean handleVolumeKeys = true;

    /** True if bindService() completed. */
    private boolean boundService = false;

    private Toast lastShownToast;

    /** Holds information to be retained across activity lifecycle */
    private StateHolder stateHolder;

    /**
     * @return The {@link ISqueezeService}, or null if not bound
     */
    @Nullable
    public ISqueezeService getService() {
        return mService;
    }

    /**
     * Return the {@link ISqueezeService} this activity is currently bound to.
     *
     * @throws IllegalStateException if service is not bound.
     * @see #getService()
     */
    @NonNull
    public final ISqueezeService requireService() {
        ISqueezeService service = getService();
        if (service == null) {
            throw new IllegalStateException(this + " service is not bound");
        }
        return service;
    }

    public int getThemeId() {
        return themeManager.getCurrentThemeId();
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            mService = (ISqueezeService) binder;
            BaseActivity.this.onServiceConnected(mService);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
        }
    };

    @Override
    @CallSuper
    protected void onCreate(Bundle savedInstanceState) {
        themeManager.onCreate(this);
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setNavigationBarContrastEnforced(false);
        }

        boundService = bindService(new Intent(this, SqueezeService.class), serviceConnection,
                Context.BIND_AUTO_CREATE);
        Log.d(TAG, "did bindService; serviceStub = " + getService());

        if (savedInstanceState != null) {
            currentDownloadItem = savedInstanceState.getParcelable(CURRENT_DOWNLOAD_ITEM);
        }

        Squeezer.getPreferences(preferences -> {
            if (preferences.getScreensaverMode() != Preferences.ScreensaverMode.OFF) {
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                if (preferences.getScreensaverMode() == Preferences.ScreensaverMode.CLOCK) {
                    inactivityHandler = new Handler();
                    inactivityAction = () -> startActivity(new Intent(this, Screensaver.class));
                    setInactivityTimer();
                }
            }
        });

        devicePlayers = new DevicePlayers(this);
        stateHolder = new ViewModelProvider(this).get(StateHolder.class);
    }

    protected <T> T getRetainedValue(String key) {
        return stateHolder.get(key);
    }

    protected <T> void putRetainedValue(String key, T value) {
        stateHolder.put(key, value);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putParcelable(CURRENT_DOWNLOAD_ITEM, currentDownloadItem);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onResume() {
        super.onResume();

        themeManager.onResume(this);

        if (inactivityHandler != null) {
            setInactivityTimer();
        }

        devicePlayers.onResume();

        // Ensure that any image fetching tasks started by this activity do not finish prematurely.
        ImageFetcher.getInstance(this).setExitTasksEarly(false);
    }

    private void setInactivityTimer() {
        inactivityHandler.removeCallbacks(inactivityAction);
        inactivityHandler.postDelayed(inactivityAction, INACTIVITY_TIME);
    }

    @Override
    @CallSuper
    public void onPause() {
        if (inactivityHandler != null) {
            inactivityHandler.removeCallbacks(inactivityAction);
        }

        devicePlayers.onPause();

        // Ensure that any pending image fetching tasks are unpaused, and finish quickly.
        ImageFetcher imageFetcher = ImageFetcher.getInstance(this);
        imageFetcher.setExitTasksEarly(true);
        imageFetcher.setPauseWork(false);

        // Ensure that any pending undo bar onDone listener is called immediately
        UndoBarController.hide(this);

        super.onPause();
    }

    /**
     * Clear the image memory cache if memory gets low.
     */
    @Override
    @CallSuper
    public void onLowMemory() {
        ImageFetcher.onLowMemory();
    }

    @Override
    @CallSuper
    public void onDestroy() {
        super.onDestroy();

        // If we are not bound to the service, it's process is no longer
        // running, so the callbacks are already cleaned up.
        if (mService != null) {
            mService.cancelItemListRequests(this);
        }

        if (boundService) {
            unbindService(serviceConnection);
        }
    }

    /**
     * Performs any actions necessary after the service has been connected. Derived classes
     * should call through to the base class.
     * <ul>
     *     <li>Invalidates the options menu so that menu items can be adjusted based on
     *     the state of the service connection.</li>
     *     <li>Ensures that callbacks are registered.</li>
     * </ul>
     *
     * @param service The connection to the bound service.
     */
    @CallSuper
    protected void onServiceConnected(@NonNull ISqueezeService service) {
        Log.d(TAG, "onServiceConnected");
        supportInvalidateOptionsMenu();
        repository().observe(this, (AlertEvent event) -> {
            if (!event.isShown) AlertEventDialog.show(getSupportFragmentManager(), event.message.title, event.message.text);
            event.isShown = true;
        });
        repository().observe(this, (DisplayEvent event) -> {
            if (!event.isShown) showDisplayMessage(event.message);
            event.isShown = true;
        });

        devicePlayers.onCreate();
    }

    public SqueezerRepository repository() {
        return ((Squeezer)getApplicationContext()).repository();
    }

    @Override
    @CallSuper
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            Intent upIntent = NavUtils.getParentActivityIntent(this);
            if (upIntent != null) {
                if (NavUtils.shouldUpRecreateTask(this, upIntent)) {
                    TaskStackBuilder.create(this)
                            .addNextIntentWithParentStack(upIntent)
                            .startActivities();
                } else {
                    upIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    NavUtils.navigateUpTo(this, upIntent);
                }
            } else {
                HomeActivity.show(this);
            }
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @NonNull
    public final <T extends View> T requireView(@IdRes int id) {
        T v = findViewById(id);
        if (v == null) {
            throw new IllegalArgumentException("View " + id + " must be present");
        }
        return v;
    }


    @Override
    @CallSuper
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (handleVolumeKeys && VolumeKeysDelegate.onKeyDown(keyCode, getService())) return true;
        return super.onKeyDown(keyCode, event);
    }

    @Override
    @CallSuper
    public boolean onKeyUp(int keyCode, @NonNull KeyEvent event) {
        if (handleVolumeKeys && VolumeKeysDelegate.onKeyUp(keyCode)) return true;
        return super.onKeyUp(keyCode, event);
    }

    public void setHandleVolumeKeys(boolean handleVolumeKeys) {
        this.handleVolumeKeys = handleVolumeKeys;
    }

    private static final int INACTIVITY_TIME = 5 * 60 * 1000;
    Handler inactivityHandler;
    Runnable inactivityAction;

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();

        if (inactivityHandler != null) {
            setInactivityTimer();
        }
    }

    public void showDisplayMessage(@StringRes int resId) {
        showDisplayMessage(getString(resId));
    }

    public void showDisplayMessage(String text) {
        showDisplayMessage(new DisplayMessage(text));
    }

    public void showDisplayMessage(DisplayMessage display) {
        boolean showMe = true;
        View layout = getLayoutInflater().inflate(R.layout.display_message, findViewById(R.id.display_message_container));
        ImageView artwork = layout.findViewById(R.id.artwork);
        artwork.setVisibility(View.GONE);
        ImageView icon = layout.findViewById(R.id.icon);
        icon.setVisibility(View.GONE);
        TextView text = layout.findViewById(R.id.text);
        text.setVisibility(TextUtils.isEmpty(display.text) ? View.GONE : View.VISIBLE);
        text.setText(display.text);

        if (display.isIcon() || display.isMixed() || display.isPopupAlbum()) {
            if (display.isIcon() && new HashSet<>(Arrays.asList("play", "pause", "stop", "fwd", "rew")).contains(display.style)) {
                // Play status is updated in the NowPlayingFragment (either full-screen or mini)
                showMe = false;
            } else {
                @DrawableRes int iconResource = display.getIconResource();
                if (iconResource != 0) {
                    icon.setVisibility(View.VISIBLE);
                    icon.setImageResource(iconResource);
                }
                if (display.hasIcon()) {
                    artwork.setVisibility(View.VISIBLE);
                    ImageFetcher.getInstance(this).loadImage(display.icon, artwork);
                }
            }
        } else if (display.isSong()) {
            //These are for the NowPlaying screen, which we update via player status messages
            showMe = false;
        }

        if (!showMe) {
            return;
        }
        if (lastShownToast != null) {
            lastShownToast.cancel();
        }
        int duration = (display.duration >=0 && display.duration <= 3000 ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG);
        lastShownToast = new Toast(this);
        //TODO handle duration == -1 => LENGTH.INDEFINITE and custom (server side) duration,
        // once we have material design and BaseTransientBottomBar
        lastShownToast.setDuration(duration);
        lastShownToast.setView(layout);
        lastShownToast.show();
    }

    // Safe accessors

    public Player getActivePlayer() {
        if (mService == null) {
            return null;
        }
        return mService.getActivePlayer();
    }

    /**
     * Perform the supplied <code>action</code> using parameters in <code>item</code> via
     * {@link ISqueezeService#action(JiveItem, Action)}
     * <p>
     * Navigate to <code>nextWindow</code> if it exists in <code>action</code>. The
     * <code>alreadyPopped</code> parameter is used to modify nextWindow if any windows has already
     * been popped by the Android system.
     */
    public void action(JiveItem item, Action action, int alreadyPopped) {
        if (mService == null) {
            return;
        }

        mService.action(item, action);
    }

    /**
     * Same as calling {@link #action(JiveItem, Action, int)} with <code>alreadyPopped</code> = 0
     */
    public void action(JiveItem item, Action action) {
        action(item, action, 0);
    }

    /**
     * Perform the supplied <code>action</code> using parameters in <code>item</code> via
     * {@link ISqueezeService#action(Action.JsonAction)}
     */
    public void action(JiveItem item, Action.JsonAction action, int alreadyPopped) {
        if (mService == null) {
            return;
        }

        mService.action(action);
    }

    /**
     * Initiate download of songs for the supplied item.
     *
     * @param item Song or item with songs to download
     * @see ISqueezeService#downloadItem(JiveItem)
     */
    public void downloadItem(JiveItem item) {
        if (Squeezer.getPreferences().isDownloadConfirmation()) {
            DownloadDialog.show(item, this);
        } else {
            doDownload(item);
        }
    }

    public void randomPlayFolder(JiveItem item) {
        if (!requireService().randomPlayFolder(item)) {
            showDisplayMessage(R.string.RANDOM_PLAY_UNABLE);
        } else {
            showDisplayMessage(R.string.RANDOM_PLAY_STARTED);
        }
    }

    @Override
    public void doDownload(JiveItem item) {
        if (Build.VERSION_CODES.M <= Build.VERSION.SDK_INT && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            currentDownloadItem = item;
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        } else
            requireService().downloadItem(item);
    }

    private JiveItem currentDownloadItem;

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case 1:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (currentDownloadItem != null) {
                        requireService().downloadItem(currentDownloadItem);
                        currentDownloadItem = null;
                    } else
                        Toast.makeText(this, "Please select download again now that we have permission to save it", Toast.LENGTH_LONG).show();
                } else
                    Toast.makeText(this, R.string.DOWNLOAD_REQUIRES_WRITE_PERMISSION, Toast.LENGTH_LONG).show();
                break;
        }
    }

    /**
     * Look up an attribute resource styled for the current theme.
     *
     * @param attribute Attribute identifier to look up.
     * @return The resource identifier for the given attribute.
     */
    public int getAttributeValue(int attribute) {
        TypedValue v = new TypedValue();
        getTheme().resolveAttribute(attribute, v, true);
        return v.resourceId;
    }
}
