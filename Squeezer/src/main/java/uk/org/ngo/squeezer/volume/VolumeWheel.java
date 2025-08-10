package uk.org.ngo.squeezer.volume;

import android.view.View;
import android.widget.CheckBox;

import java.util.function.Supplier;

import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.service.ISqueezeService;
import uk.org.ngo.squeezer.widget.RadialSeekBar;

public class VolumeWheel {
    private final CheckBox muteToggle;
    private final RadialSeekBar volumeWheel;
    private int currentProgress = 0;
    private boolean trackingTouch;

    public VolumeWheel(View v, Supplier<ISqueezeService> serviceSupplier, Runnable volumeToggleListener, Runnable settingsListener) {
        volumeWheel = v.findViewById(R.id.level);
        muteToggle = v.findViewById(R.id.muteToggle);

        volumeWheel.setOnRadialSeekBarChangeListener(new RadialSeekBar.OnRadialSeekBarChangeListener() {
            @Override
            public void onProgressChanged(RadialSeekBar seekBar, int progress) {
                if (currentProgress != progress) {
                    currentProgress = progress;
                    volumeWheel.setLabel(String.valueOf(progress));
                    serviceSupplier.get().setVolumeTo(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(RadialSeekBar seekBar) {
                trackingTouch = true;
            }

            @Override
            public void onStopTrackingTouch(RadialSeekBar seekBar) {
                trackingTouch = false;
            }
        });
        muteToggle.setOnClickListener(view -> serviceSupplier.get().toggleMute());
        v.findViewById(R.id.down).setOnClickListener(view -> volumeToggleListener.run());
        v.findViewById(R.id.settings).setOnClickListener(view -> settingsListener.run());
        v.findViewById(R.id.volume_down).setOnClickListener(view -> serviceSupplier.get().adjustVolume(-1));
        v.findViewById(R.id.volume_up).setOnClickListener(view -> serviceSupplier.get().adjustVolume(1));
    }

    public void update(ISqueezeService.VolumeInfo volumeInfo) {
        if (trackingTouch) return;

        muteToggle.setChecked(volumeInfo.muted);
        currentProgress = volumeInfo.volume;
        volumeWheel.setEnabled(!volumeInfo.muted);
        volumeWheel.setProgress(volumeInfo.volume);
        volumeWheel.setLabel(String.valueOf(volumeInfo.volume));
        // label.setText(volumeInfo.name);
    }
}
