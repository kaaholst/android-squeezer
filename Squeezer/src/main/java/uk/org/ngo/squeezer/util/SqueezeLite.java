package uk.org.ngo.squeezer.util;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

class SqueezeLite {
    private static final String PACKAGE = "org.lyrion.squeezelite";
    private static final String SERVICE = "org.lyrion.squeezelite.PlayerService";

    private final Context context;

    public SqueezeLite(Context context) {
        this.context = context;
    }

    public boolean has() {
        final PackageManager packageManager = context.getPackageManager();
        Intent intent = packageManager.getLaunchIntentForPackage(PACKAGE);
        return (intent != null);
    }

    public void start() {
        Log.i("SqueezeLite", "start");
        Intent intent = new Intent().setClassName(PACKAGE, SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

}
