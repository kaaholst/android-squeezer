package uk.org.ngo.squeezer.volume;

import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.core.util.Pair;

import com.google.android.material.button.MaterialButton;

import java.util.function.Supplier;

import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.service.ISqueezeService;

public class VolumeBar {
    private final MaterialButton muteButton;
    private final SeekBar volumeBar;

    private boolean trackingTouch;

    public VolumeBar(View v, Supplier<ISqueezeService> serviceSupplier, Pair<Drawable, Runnable> volumeToggleListener) {
        muteButton = v.findViewById(R.id.muteButton);
        volumeBar = v.findViewById(R.id.volume_slider);

        MaterialButton volumeToggleButton = v.findViewById(R.id.volumeToggleButton);
        TextView volumeLabel = v.findViewById(R.id.label);

        muteButton.setOnClickListener(view -> serviceSupplier.get().toggleMute());
        if (volumeToggleListener != null) {
            volumeToggleButton.setIcon(volumeToggleListener.first);
            volumeToggleButton.setOnClickListener(view -> volumeToggleListener.second.run());
        } else
            volumeToggleButton.setVisibility(View.INVISIBLE);
        volumeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                trackingTouch = true;
                if (volumeToggleListener != null) volumeToggleButton.setVisibility(View.INVISIBLE);
                volumeLabel.setVisibility(View.VISIBLE);
                volumeLabel.setText(String.valueOf(seekBar.getProgress()));
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                trackingTouch = false;
                if (volumeToggleListener != null) volumeToggleButton.setVisibility(View.VISIBLE);
                volumeLabel.setVisibility(View.INVISIBLE);
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    volumeLabel.setText(String.valueOf(progress));
                    serviceSupplier.get().setVolumeTo(progress);
                }
            }
        });
    }

    public void update(ISqueezeService.VolumeInfo volumeInfo) {
        if (!trackingTouch) {
            muteButton.setIconResource(volumeInfo.muted ? R.drawable.ic_volume_off : R.drawable.ic_volume_down);
            volumeBar.setEnabled(!volumeInfo.muted);
            volumeBar.setProgress(volumeInfo.volume);
        }
    }
}
