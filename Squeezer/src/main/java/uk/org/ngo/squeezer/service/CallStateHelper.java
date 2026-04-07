package uk.org.ngo.squeezer.service;

import android.Manifest;
import android.app.Service;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;

import java.util.HashSet;
import java.util.Set;

import uk.org.ngo.squeezer.Preferences;
import uk.org.ngo.squeezer.Squeezer;
import uk.org.ngo.squeezer.model.LyrionPlayer;

class CallStateHelper {
    private static final String TAG = CallStateHelper.class.getSimpleName();

    private final LyrionController lyrionController;

    private boolean callStateListenerRegistered = false;
    final Set<String> mutedPlayers = new HashSet<>();

    public CallStateHelper(LyrionController lyrionController) {
        this.lyrionController = lyrionController;
    }

    public void registerCallStateListener(Context context) {
        if (!callStateListenerRegistered) {
            TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Service.TELEPHONY_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "calling registerTelephonyCallback");
                    telephonyManager.registerTelephonyCallback(context.getMainExecutor(), callStateListener);
                }
            } else {
                telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
            }
            callStateListenerRegistered = true;
        }
        mutedPlayers.clear();
    }

    public void unregisterCallStateListener(Context context) {
        if (callStateListenerRegistered) {
            TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Service.TELEPHONY_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                telephonyManager.unregisterTelephonyCallback(callStateListener);
            } else {
                telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
            }
            callStateListenerRegistered = false;
        }
        mutedPlayers.clear();
    }

    private final CallStateListener callStateListener = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) ?
            new CallStateListener() {
                @Override
                public void onCallStateChanged(int state) {
                    CallStateHelper.this.onCallStateChanged(state);
                }
            }
            : null;

    private final PhoneStateListener phoneStateListener = (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) ?
            new PhoneStateListener() {
                @Override
                public void onCallStateChanged(int state, String phoneNumber) {
                    CallStateHelper.this.onCallStateChanged(state);
                }
            }
            : null;

    public void onCallStateChanged(int state) {
        Preferences preferences = Squeezer.instance().preferences();
        Preferences.IncomingCallAction incomingCallAction = preferences.getActionOnIncomingCall();
        if (incomingCallAction != Preferences.IncomingCallAction.NONE) {
            PerformAction action = incomingCallAction.isPause() ? lyrionController::pause : lyrionController::mute;
            if (state == TelephonyManager.CALL_STATE_RINGING || state == TelephonyManager.CALL_STATE_OFFHOOK) {
                boolean restoreMusic = preferences.restoreMusicAfterCall();
                if (incomingCallAction.isAll()) {
                    lyrionController.getPlayers().stream().filter(player -> player.getPlayerState().isPlaying()).forEach(player -> mutePlayer(player, action, restoreMusic));
                } else {
                    LyrionPlayer player = lyrionController.getActivePlayer();
                    if (player != null && player.getPlayerState().isPlaying()) mutePlayer(player, action, restoreMusic);
                }
            } else {
                mutedPlayers.forEach(mutedPlayer -> {
                    LyrionPlayer player = lyrionController.getPlayer(mutedPlayer);
                    if (player != null) action.exec(player, false);
                });
                mutedPlayers.clear();
            }
        }
    }

    private void mutePlayer(LyrionPlayer player, PerformAction action, boolean restoreMusic) {
        if (restoreMusic) mutedPlayers.add(player.getId());
        action.exec(player, true);
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    private static abstract class CallStateListener extends TelephonyCallback implements TelephonyCallback.CallStateListener {
        @Override
        abstract public void onCallStateChanged(int state);
    }

    private interface PerformAction {
        void exec(LyrionPlayer player, boolean flag);
    }
}
