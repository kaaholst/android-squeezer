package uk.org.ngo.squeezer.volume;

import android.view.View;
import android.widget.CheckBox;

import androidx.core.graphics.ColorUtils;

import java.util.function.Supplier;

import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.service.ISqueezeService;
import uk.org.ngo.squeezer.widget.RadialSeekBar;

public class VolumeWheel {
    private final RadialSeekBar.OnRadialSeekBarChangeListener changeListener;
    private final CheckBox muteToggle;
    private final RadialSeekBar volumeWheel;
    private int currentProgress = 0;
    private boolean trackingTouch;

    public VolumeWheel(View v, Supplier<ISqueezeService> serviceSupplier, Runnable volumeToggleListener, Runnable settingsListener) {
        volumeWheel = v.findViewById(R.id.level);
        muteToggle = v.findViewById(R.id.muteToggle);

        changeListener = new RadialSeekBar.OnRadialSeekBarChangeListener() {
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
        };

        volumeWheel.setOnRadialSeekBarChangeListener(changeListener);
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
        volumeWheel.setProgress(volumeInfo.volume);
        volumeWheel.setLabel(String.valueOf(volumeInfo.volume));
        // label.setText(volumeInfo.name);

        volumeWheel.setIndicatorColor(ColorUtils.setAlphaComponent(volumeWheel.getIndicatorColor(), volumeInfo.muted ? 63 : 255));
        volumeWheel.setProgressPrimaryColor(ColorUtils.setAlphaComponent(volumeWheel.getProgressPrimaryColor(), volumeInfo.muted ? 63 : 255));
        volumeWheel.setProgressSecondaryColor(ColorUtils.setAlphaComponent(volumeWheel.getProgressSecondaryColor(), volumeInfo.muted ? 63 : 255));
        volumeWheel.setOnRadialSeekBarChangeListener(volumeInfo.muted ? null : changeListener);
        volumeWheel.setOnTouchListener(volumeInfo.muted ? (view, motionEvent) -> true : null);
    }
}
