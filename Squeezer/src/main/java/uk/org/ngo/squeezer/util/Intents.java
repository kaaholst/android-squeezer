package uk.org.ngo.squeezer.util;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;

import java.util.List;

public class Intents {

    /**
     * Indicates whether the specified action can be used as an intent. This method queries the
     * package manager for installed packages that can respond to an intent with the specified
     * action. If no suitable package is found, this method returns false.
     *
     * @param context The application's environment.
     * @param action The Intent action to check for availability.
     *
     * @return True if an Intent with the specified action can be sent and responded to, false
     * otherwise.
     */
    public static boolean isIntentAvailable(Context context, String action) {
        final PackageManager packageManager = context.getPackageManager();
        final Intent intent = new Intent(action);
        List<ResolveInfo> list =
                packageManager.queryIntentActivities(intent,
                        PackageManager.MATCH_DEFAULT_ONLY);
        return !list.isEmpty();
    }

    /**
     * Indicates whether the specified action can be broadcast as an intent. This method queries the
     * package manager for installed packages that can respond to a broadcats intent with the
     * specified action. If no suitable package is found, this method returns false.
     *
     * @param context The application's environment.
     * @param action The Intent action to check for availability.
     *
     * @return True if an Intent with the specified action can be sent and responded to, false
     * otherwise.
     */
    public static boolean isBroadcastReceiverAvailable(Context context, String action) {
        final PackageManager packageManager = context.getPackageManager();
        final Intent intent = new Intent(action);
        List<ResolveInfo> list =
                packageManager.queryBroadcastReceivers(intent, 0);
        return !list.isEmpty();
    }

    /** Return the flag for an immutable intent if it's available */
    public static int immutablePendingIntent() {
        return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) ? PendingIntent.FLAG_IMMUTABLE : 0;
    }
}
