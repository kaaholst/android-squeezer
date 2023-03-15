package uk.org.ngo.squeezer.util;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;

import uk.org.ngo.squeezer.Preferences;
import uk.org.ngo.squeezer.Squeezer;

public class SqueezePlayer extends Handler {
    private static final int MSG_STOP = 0;
    private static final int MSG_TIMEOUT = 1;
    private static final long TIMEOUT_DELAY = 10 * 60 * 1000; // 10 minutes in milliseconds

    private static final String SQUEEZEPLAYER_PACKAGE = "de.bluegaspode.squeezeplayer";
    private static final String SQUEEZEPLAYER_SERVICE = "de.bluegaspode.squeezeplayer.playback.service.PlaybackService";
    private static final String HAS_SERVER_SETTINGS_EXTRA = "intentHasServerSettings";
    private static final String FORCE_SERVER_SETTINGS_EXTRA = "forceSettingsFromIntent";
    private static final String SERVER_URL_EXTRA = "serverURL";
    private static final String SERVER_NAME_EXTRA = "serverName";
    private static final String USER_NAME_EXTRA = "username";
    private static final String PASSWORD_EXTRA = "password";

    private static final String TAG = "SqueezePlayer";

    private final String serverUrl;
    private final String serverName;
    private final String username;
    private final String password;
    private final AppCompatActivity context;

    private SqueezePlayer(AppCompatActivity context, Preferences.ServerAddress serverAddress) {
        this.context = context;

        serverUrl = serverAddress.address();
        serverName = serverAddress.serverName();
        username = serverAddress.userName;
        password = serverAddress.password;

        Log.d(TAG, "startControllingSqueezePlayer");
        startControllingSqueezePlayer();
    }

    public static SqueezePlayer maybeStartControllingSqueezePlayer(AppCompatActivity context) {
        Preferences preferences = Squeezer.getPreferences();
        Preferences.ServerAddress serverAddress = preferences.getServerAddress();

        if (hasSqueezePlayer(context) && preferences.controlSqueezePlayer(serverAddress)) {
            return new SqueezePlayer(context, serverAddress);
        }

        return null;
    }

    private static boolean hasSqueezePlayer(Context context) {
        final PackageManager packageManager = context.getPackageManager();
        Intent intent = packageManager.getLaunchIntentForPackage(SQUEEZEPLAYER_PACKAGE);
        return (intent != null);
    }

    private void startControllingSqueezePlayer() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(getSqueezePlayerIntent());
        } else {
            context.startService(getSqueezePlayerIntent());
        }
        removeMessages(MSG_TIMEOUT);
        sendMessageDelayed(obtainMessage(MSG_TIMEOUT), TIMEOUT_DELAY);
    }

    private Intent getSqueezePlayerIntent() {
        final ComponentName component = new ComponentName(SQUEEZEPLAYER_PACKAGE, SQUEEZEPLAYER_SERVICE);
        Intent intent = new Intent().setComponent(component);
        if (serverUrl != null) {
            intent.putExtra(FORCE_SERVER_SETTINGS_EXTRA, true);
            intent.putExtra(HAS_SERVER_SETTINGS_EXTRA, true);
            intent.putExtra(SERVER_URL_EXTRA, serverUrl);
            intent.putExtra(SERVER_NAME_EXTRA, serverName);
            if (username != null)
                intent.putExtra(USER_NAME_EXTRA, username);
            if (password != null)
                intent.putExtra(PASSWORD_EXTRA, password);
        }
        return intent;
    }

    public void stopControllingSqueezePlayer() {
        sendEmptyMessage(MSG_STOP);
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case MSG_STOP :
                removeMessages(MSG_TIMEOUT);
                break;
            case MSG_TIMEOUT:
                startControllingSqueezePlayer();
                break;
        }
    }
}
