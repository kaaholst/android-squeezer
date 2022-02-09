package uk.org.ngo.squeezer.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.Slider;
import com.google.android.material.switchmaterial.SwitchMaterial;

import uk.org.ngo.squeezer.Preferences;
import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.framework.BaseActivity;
import uk.org.ngo.squeezer.model.Player;
import uk.org.ngo.squeezer.service.ISqueezeService;

public class VolumeSettings extends DialogFragment {
    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        BaseActivity activity = (BaseActivity)requireActivity();
        ISqueezeService service = activity.getService();
        Preferences preferences = new Preferences(requireActivity());

        View view = requireActivity().getLayoutInflater().inflate(R.layout.volume_settings, null);

        SwitchMaterial backgroundVolume = view.findViewById(R.id.background_volume);
        backgroundVolume.setOnCheckedChangeListener((buttonView, isChecked) -> backgroundVolume.setText(isChecked ? R.string.settings_background_volume_on : R.string.settings_background_volume_off));
        backgroundVolume.setChecked(preferences.isBackgroundVolume());

        Slider volumeIncrements = view.findViewById(R.id.volume_increments);
        volumeIncrements.setValue(preferences.getVolumeIncrements());

        boolean canAdjustVolumeForSyncGroup = service.canAdjustVolumeForSyncGroup();
        SwitchMaterial groupVolume = view.findViewById(R.id.group_volume);
        view.findViewById(R.id.group_volume_label).setVisibility(canAdjustVolumeForSyncGroup ? View.VISIBLE : View.GONE);
        groupVolume.setVisibility(canAdjustVolumeForSyncGroup ? View.VISIBLE : View.GONE);
        groupVolume.setOnCheckedChangeListener((buttonView, isChecked) -> groupVolume.setText(isChecked ? R.string.player_group_volume_on : R.string.player_group_volume_off));
        groupVolume.setChecked(preferences.isGroupVolume());

        String digitalVolumeControl = service.getActivePlayerState().prefs.get(Player.Pref.DIGITAL_VOLUME_CONTROL);
        boolean canFixedVolume = digitalVolumeControl != null; // TODO check for hasDigitalOut
        SwitchMaterial fixedVolume = view.findViewById(R.id.fixed_volume);
        view.findViewById(R.id.fixed_volume_label).setVisibility(canFixedVolume ? View.VISIBLE : View.GONE);
        fixedVolume.setVisibility(canFixedVolume ? View.VISIBLE : View.GONE);
        fixedVolume.setOnCheckedChangeListener((buttonView, isChecked) -> fixedVolume.setText(isChecked ? R.string.SETUP_DIGITALVOLUMECONTROL_ON : R.string.SETUP_DIGITALVOLUMECONTROL_OFF));
        fixedVolume.setChecked("1".equals(digitalVolumeControl));

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireActivity());
        builder.setTitle(R.string.settings_volume_title)
                .setView(view)
                .setPositiveButton(android.R.string.ok, (dialog, id) -> {
                    preferences.setBackgroundVolume(backgroundVolume.isChecked());
                    preferences.setVolumeIncrements((int) volumeIncrements.getValue());
                    preferences.setGroupVolume(groupVolume.isChecked());
                    service.preferenceChanged(null);
                    service.playerPref(Player.Pref.DIGITAL_VOLUME_CONTROL, fixedVolume.isChecked() ? "1" : "0");
                })
                .setNegativeButton(android.R.string.cancel, null);
        return builder.create();
    }
}
