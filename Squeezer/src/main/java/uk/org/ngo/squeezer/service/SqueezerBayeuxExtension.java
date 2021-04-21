package uk.org.ngo.squeezer.service;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import org.cometd.bayeux.Channel;
import org.cometd.bayeux.Message;
import org.cometd.bayeux.client.ClientSession;

import java.util.HashMap;
import java.util.Map;

import uk.org.ngo.squeezer.Preferences;
import uk.org.ngo.squeezer.Squeezer;

class SqueezerBayeuxExtension extends ClientSession.Extension.Adapter {
    private static final String TAG = SqueezerBayeuxExtension.class.getSimpleName();

    private Map<String, String> ext = initExt();

    private Map<String, String> initExt() {
        Map<String, String> ext = new HashMap<>();
        Context context = Squeezer.getContext();
        Preferences preferences = new Preferences(context);
        ext.put("mac", getMacId(preferences));
        ext.put("rev", getRevision(context));
        ext.put("uuid", getUuid(preferences));

        return ext;
    }

    private static String getMacId(Preferences preferences) {
        return preferences.getMacId();
    }

    private static String getUuid(Preferences preferences) {
        return preferences.getUuid();
    }

    public static String getRevision() {
        return getRevision(Squeezer.getContext());
    }

    private static String getRevision(Context context) {
        PackageManager pm = Squeezer.getContext().getPackageManager();
        String rev;
        try {
            PackageInfo packageInfo = pm.getPackageInfo(context.getPackageName(), 0);
            rev = packageInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            rev = "1.0";
        }
        return rev;
    }

    @Override
    public boolean sendMeta(ClientSession session, Message.Mutable message) {
        // mysqueezebox.com requires an ext field in the handshake message
        if (Channel.META_HANDSHAKE.equals(message.getChannel())) {
            message.put(Message.EXT_FIELD, ext);
            if (message.getClientId() != null) {
                Log.v(TAG, "Reset client id");
                message.setClientId(null);
            }
        }

        return true;
    }
}
