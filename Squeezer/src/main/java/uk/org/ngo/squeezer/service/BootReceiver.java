package uk.org.ngo.squeezer.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.core.content.ContextCompat;

import uk.org.ngo.squeezer.Preferences;
import uk.org.ngo.squeezer.Squeezer;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "Received boot intent: " + intent.getAction());

        Preferences preferences = Squeezer.getPreferences();
        if (preferences.hasServerConfig()) {
            Log.i(TAG, "Starting SqueezeService to initiate auto-connect");
            Intent serviceIntent = new Intent(context, SqueezeService.class);
            serviceIntent.setAction(SqueezeService.ACTION_AUTO_CONNECT);
            ContextCompat.startForegroundService(context, serviceIntent);
        }
    }
}
