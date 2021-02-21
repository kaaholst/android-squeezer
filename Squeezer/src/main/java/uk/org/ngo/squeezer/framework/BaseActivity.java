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
import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import androidx.annotation.CallSuper;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NavUtils;
import androidx.core.app.TaskStackBuilder;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import uk.org.ngo.squeezer.Preferences;
import uk.org.ngo.squeezer.dialog.AlertEventDialog;
import uk.org.ngo.squeezer.dialog.DownloadDialog;
import uk.org.ngo.squeezer.itemlist.HomeActivity;
import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.VolumePanel;
import uk.org.ngo.squeezer.model.Action;
import uk.org.ngo.squeezer.model.DisplayMessage;
import uk.org.ngo.squeezer.model.JiveItem;
import uk.org.ngo.squeezer.model.Player;
import uk.org.ngo.squeezer.model.PlayerState;
import uk.org.ngo.squeezer.service.ISqueezeService;
import uk.org.ngo.squeezer.service.SqueezeService;
import uk.org.ngo.squeezer.service.event.AlertEvent;
import uk.org.ngo.squeezer.service.event.DisplayEvent;
import uk.org.ngo.squeezer.service.event.PlayerVolume;
import uk.org.ngo.squeezer.util.ImageFetcher;
import uk.org.ngo.squeezer.util.SqueezePlayer;
import uk.org.ngo.squeezer.util.ThemeManager;

/**
 * Common base class for all activities in Squeezer.
 *
 * @author Kurt Aaholst
 */
public abstract class BaseActivity extends AppCompatActivity {
    private static final String CURRENT_DOWNLOAD_ITEM = "CURRENT_DOWNLOAD_ITEM";


    private static final String TAG = BaseActivity.class.getName();

    @Nullable
    private ISqueezeService mService = null;

    private final ThemeManager mTheme = new ThemeManager();
    private int mThemeId = ThemeManager.getDefaultTheme().mThemeId;

    /** Records whether the activity has registered on the service's event bus. */
    private boolean mRegisteredOnEventBus;

    private SqueezePlayer squeezePlayer;

    /** Whether volume changes should be ignored. */
    private boolean mIgnoreVolumeChange;

    /** True if bindService() completed. */
    private boolean boundService = false;

    /** Volume control panel. */
    @Nullable
    private VolumePanel mVolumePanel;

    /** Set this to true to stop displaying icon-based showBrieflies */
    protected boolean ignoreIconMessages = false;

    /**
     * @return The squeezeservice, or null if not bound
     */
    @Nullable
    public ISqueezeService getService() {
        return mService;
    }

    public int getThemeId() {
        return mThemeId;
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

    protected void addActionBar() {
        // Set the icon as the home button, and display it.
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setHomeAsUpIndicator(R.drawable.ic_action_home);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    @CallSuper
    protected void onCreate(android.os.Bundle savedInstanceState) {
        mTheme.onCreate(this);
        super.onCreate(savedInstanceState);

        addActionBar();

        boundService = bindService(new Intent(this, SqueezeService.class), serviceConnection,
                Context.BIND_AUTO_CREATE);
        Log.d(TAG, "did bindService; serviceStub = " + getService());

        if (savedInstanceState != null)
            currentDownloadItem = savedInstanceState.getParcelable(CURRENT_DOWNLOAD_ITEM);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putParcelable(CURRENT_DOWNLOAD_ITEM, currentDownloadItem);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void setTheme(int resId) {
        super.setTheme(resId);
        mThemeId = resId;
    }

    @Override
    public void onResume() {
        super.onResume();

        mTheme.onResume(this);

        if (mService != null) {
            maybeRegisterOnEventBus(mService);
        }

        mVolumePanel = new VolumePanel(this);

        // If SqueezePlayer is installed, start it
        squeezePlayer = SqueezePlayer.maybeStartControllingSqueezePlayer(this);

        // Ensure that any image fetching tasks started by this activity do not finish prematurely.
        ImageFetcher.getInstance(this).setExitTasksEarly(false);
    }

    @Override
    @CallSuper
    public void onPause() {
        // At least some Samsung devices call onPause without ensuring that onResume is called
        // first, per https://code.google.com/p/android/issues/detail?id=74464, so mVolumePanel
        // may be null on those devices.
        if (mVolumePanel != null) {
            mVolumePanel.dismiss();
            mVolumePanel = null;
        }

        if (squeezePlayer != null) {
            squeezePlayer.stopControllingSqueezePlayer();
            squeezePlayer = null;
        }
        if (mRegisteredOnEventBus) {
            // If we are not bound to the service, it's process is no longer
            // running, so the callbacks are already cleaned up.
            if (mService != null) {
                mService.getEventBus().unregister(this);
                mService.cancelItemListRequests(this);
            }
            mRegisteredOnEventBus = false;
        }

        // Ensure that any pending image fetching tasks are unpaused, and finish quickly.
        ImageFetcher imageFetcher = ImageFetcher.getInstance(this);
        imageFetcher.setExitTasksEarly(true);
        imageFetcher.setPauseWork(false);

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
        if (boundService) {
            unbindService(serviceConnection);
        }
    }

    /** Fix for https://code.google.com/p/android/issues/detail?id=63570. */
    private boolean mIsRestoredToTop;

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if ((intent.getFlags() | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT) > 0) {
            mIsRestoredToTop = true;
        }
    }

    @Override
    @TargetApi(Build.VERSION_CODES.KITKAT)
    public void finish() {
        super.finish();
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT && !isTaskRoot()
                && mIsRestoredToTop) {
            // 4.4.2 platform issues for FLAG_ACTIVITY_REORDER_TO_FRONT,
            // reordered activity back press will go to home unexpectedly,
            // Workaround: move reordered activity current task to front when it's finished.
            ActivityManager tasksManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            tasksManager.moveTaskToFront(getTaskId(), ActivityManager.MOVE_TASK_NO_USER_ACTION);
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
        maybeRegisterOnEventBus(service);
    }

    /**
     * Conditionally registers with the service's EventBus.
     * <p>
     * Registration can happen in {@link #onResume()} and {@link
     * #onServiceConnected(uk.org.ngo.squeezer.service.ISqueezeService)}, this ensures that it only
     * happens once.
     *
     * @param service The connection to the bound service.
     */
    private void maybeRegisterOnEventBus(@NonNull ISqueezeService service) {
        if (!mRegisteredOnEventBus) {
            service.getEventBus().registerSticky(this);
            mRegisteredOnEventBus = true;
        }
    }

    @Override
    @CallSuper
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
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


    /*
     * Intercept hardware volume control keys to control Squeezeserver
     * volume.
     *
     * Change the volume when the key is depressed.  Suppress the keyUp
     * event, otherwise you get a notification beep as well as the volume
     * changing.
     */
    @Override
    @CallSuper
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                return changeVolumeBy(+5);
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                return changeVolumeBy(-5);
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    @CallSuper
    public boolean onKeyUp(int keyCode, @NonNull KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                return true;
        }

        return super.onKeyUp(keyCode, event);
    }

    private boolean changeVolumeBy(int delta) {
        ISqueezeService service = getService();
        if (service == null) {
            return false;
        }
        Log.v(TAG, "Adjust volume by: " + delta);
        service.adjustVolumeBy(delta);
        return true;
    }

    public void onEvent(PlayerVolume event) {
        if (!mIgnoreVolumeChange && mVolumePanel != null && event.player == mService.getActivePlayer()) {
            mVolumePanel.postVolumeChanged(event.volume, event.player.getName());
        }
    }

    // Show the volume dialog.
    public boolean showVolumePanel() {
        if (mService != null) {
            PlayerState playerState = mService.getPlayerState();
            Player player = mService.getActivePlayer();

            if (playerState != null  && mVolumePanel != null) {
                mVolumePanel.postVolumeChanged(playerState.getCurrentVolume(),
                        player == null ? "" : player.getName());
            }

            return true;
        } else {
            return false;
        }
    }

    public void setIgnoreVolumeChange(boolean ignoreVolumeChange) {
        mIgnoreVolumeChange = ignoreVolumeChange;
    }

    public void onEventMainThread(DisplayEvent displayEvent) {
        boolean showMe = true;
        DisplayMessage display = displayEvent.message;
        View layout = getLayoutInflater().inflate(R.layout.display_message, findViewById(R.id.display_message_container));
        ImageView artwork = layout.findViewById(R.id.artwork);
        artwork.setVisibility(View.GONE);
        ImageView icon = layout.findViewById(R.id.icon);
        icon.setVisibility(View.GONE);
        TextView text = layout.findViewById(R.id.text);
        text.setVisibility(TextUtils.isEmpty(display.text) ? View.GONE : View.VISIBLE);
        text.setText(display.text);

        if (display.isIcon() || display.isMixed() || display.isPopupAlbum()) {
            if (display.isIcon() && ignoreIconMessages) {
                //icon based messages afre ignored for the now playing screen
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

        if (showMe) {
            if (!(icon.getVisibility() == View.VISIBLE &&text.getVisibility() == View.VISIBLE)) {
                layout.findViewById(R.id.divider).setVisibility(View.GONE);
            }
            int duration = (display.duration >=0 && display.duration <= 3000 ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG);
            Toast toast = new Toast(getApplicationContext());
            //TODO handle duration == -1 => LENGTH.INDEFINITE and custom (server side) duration,
            // once we have material design and BaseTransientBottomBar
            toast.setDuration(duration);
            toast.setView(layout);
            toast.show();
        }
    }

    public void onEventMainThread(AlertEvent alert) {
        AlertEventDialog.show(getSupportFragmentManager(), alert.message.title, alert.message.text);
    }

    // Safe accessors

    public boolean isConnected() {
        return mService != null && mService.isConnected();
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
    public void action(Action.JsonAction action, int alreadyPopped) {
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
        if (new Preferences(this).isDownloadConfirmation()) {
            DownloadDialog.show(getSupportFragmentManager(), item, new BaseConfirmDialog.ConfirmDialogListener() {
                @Override
                public void ok(boolean persist) {
                    if (persist) {
                        new Preferences(BaseActivity.this).setDownloadConfirmation(false);
                    }
                    doDownload(item);
                }

                @Override
                public void cancel(boolean persist) {
                    if (persist) {
                        new Preferences(BaseActivity.this).setDownloadEnabled(false);
                    }
                }
            });
        } else {
            doDownload(item);
        }
    }

    private void doDownload(JiveItem item) {
        if (Build.VERSION_CODES.M <= Build.VERSION.SDK_INT && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            currentDownloadItem = item;
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        } else
            mService.downloadItem(item);
    }

    private JiveItem currentDownloadItem;

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case 1:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (currentDownloadItem != null) {
                        mService.downloadItem(currentDownloadItem);
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
