package uk.org.ngo.squeezer.widget;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import uk.org.ngo.squeezer.Preferences;
import uk.org.ngo.squeezer.Squeezer;
import uk.org.ngo.squeezer.dialog.CallStateDialog;

public class CallStatePermissionLauncher {
    private final ActivityResultLauncher<String> requestPermissionLauncher;
    @androidx.annotation.NonNull
    private final Fragment fragment;
    private Preferences.IncomingCallAction requestedAction;

    public CallStatePermissionLauncher(Fragment fragment) {
        requestPermissionLauncher =
                fragment.registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                    if (isGranted) {
                        Squeezer.getPreferences().setActionOnIncomingCall(requestedAction);
                    } else {
                        Squeezer.getPreferences().setActionOnIncomingCall(Preferences.IncomingCallAction.NONE);
                    }
                });
        this.fragment = fragment;
    }

    public void trySetAction(Preferences.IncomingCallAction requestedAction) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                requestedAction != Preferences.IncomingCallAction.NONE &&
                ContextCompat.checkSelfPermission(fragment.requireContext(), Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            this.requestedAction = requestedAction;
            new CallStateDialog().show(fragment.getChildFragmentManager(), "CallStatePermissionLauncher");
        } else
            Squeezer.getPreferences().setActionOnIncomingCall(requestedAction);

    }

    public void requestCallStatePermission() {
        requestPermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE);
    }

}
