package uk.org.ngo.squeezer.dialog;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import uk.org.ngo.squeezer.R;

public class CallStateDialog extends DialogFragment {
    public interface CallStateDialogHost {
        void requestCallStatePermission();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        CallStateDialogHost host = (CallStateDialogHost) requireParentFragment();
        return new MaterialAlertDialogBuilder(requireActivity())
                .setTitle(R.string.call_state_permission_title)
                .setMessage(R.string.call_state_permission_message)
                .setPositiveButton(R.string.request_permission, (dialogInterface, i) -> host.requestCallStatePermission())
                .create();
    }
}
