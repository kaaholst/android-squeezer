package uk.org.ngo.squeezer.service;

import androidx.media.VolumeProviderCompat;

class SqueezerVolumeProvider extends VolumeProviderCompat {
    private final LyrionController lyrionController;
    final int step;

    public SqueezerVolumeProvider(LyrionController lyrionController, int step) {
        super(VolumeProviderCompat.VOLUME_CONTROL_ABSOLUTE, 100 / step, 1);
        this.lyrionController = lyrionController;
        this.step = step;
    }

    @Override
    public void onAdjustVolume(int direction) {
        lyrionController.adjustVolume(direction * step);
    }

    @Override
    public void onSetVolumeTo(int volume) {
        lyrionController.setVolumeTo(volume * step);
    }
}
