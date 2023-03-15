package uk.org.ngo.squeezer.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import uk.org.ngo.squeezer.Preferences;
import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.Squeezer;
import uk.org.ngo.squeezer.Util;

public class CuePanelSettings extends DialogFragment {
    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Preferences preferences = Squeezer.getPreferences();
        View view = requireActivity().getLayoutInflater().inflate(R.layout.cue_panel_settings, null);
        TextView backward = (TextView) view.findViewById(R.id.backward_jump);
        TextView forward = (TextView) view.findViewById(R.id.forward_jump);
        backward.setText(String.valueOf(preferences.getBackwardSeconds()));
        forward.setText(String.valueOf(preferences.getForwardSeconds()));

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireActivity());
        builder.setView(view)
                .setPositiveButton(android.R.string.ok, (dialog, id) -> {
                    int back = (int) Util.parseDecimalInt(backward.getText().toString(), -1);
                    if (back > 0) preferences.setBackwardSeconds(back);
                    int ff = (int) Util.parseDecimalInt(forward.getText().toString(), -1);
                    if (ff > 0) preferences.setForwardSeconds(ff);
                })
                .setNegativeButton(android.R.string.cancel, null);
        return builder.create();
    }
}
