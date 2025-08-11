package uk.org.ngo.squeezer.util;

import android.content.Context;

public class DevicePlayers {

    private final Context context;
    private SqueezePlayer squeezePlayer;

    public DevicePlayers(Context context) {
        this.context = context;
    }

    public void onCreate() {
        SqueezeLite squeezeLite = new SqueezeLite(context);
        if (squeezeLite.has()) squeezeLite.start();
    }

    public void onResume() {
        squeezePlayer = SqueezePlayer.maybeStartControllingSqueezePlayer(context);
    }

    public void onPause() {
        if (squeezePlayer != null) {
            squeezePlayer.stopControllingSqueezePlayer();
            squeezePlayer = null;
        }
    }

}
