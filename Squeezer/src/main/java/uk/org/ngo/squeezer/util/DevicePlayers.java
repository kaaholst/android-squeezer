package uk.org.ngo.squeezer.util;

import android.content.Context;

import uk.org.ngo.squeezer.Preferences;
import uk.org.ngo.squeezer.Squeezer;

public class DevicePlayers {

    private final Context context;
    private SqueezePlayer squeezePlayer;

    public DevicePlayers(Context context) {
        this.context = context;
    }

    public void onCreate() {
        Preferences preferences = Squeezer.getPreferences();
        SqueezeLite squeezeLite = new SqueezeLite(context);
        if (preferences.controlSqueezelite() && squeezeLite.has()) squeezeLite.start();
    }

    public void onResume() {
        Preferences preferences = Squeezer.getPreferences();
        squeezePlayer = (preferences.controlSqueezePlayer() && SqueezePlayer.has(context)) ? SqueezePlayer.startControllingSqueezePlayer(context) : null;
    }

    public void onPause() {
        if (squeezePlayer != null) {
            squeezePlayer.stopControllingSqueezePlayer();
            squeezePlayer = null;
        }
    }

}
