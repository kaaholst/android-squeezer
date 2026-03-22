package uk.org.ngo.squeezer.service;

import android.os.Bundle;
import android.support.v4.media.session.MediaSessionCompat;

class SqueezerMediaSessionCallback extends MediaSessionCompat.Callback {
    private final LyrionController lyrionController;

    public SqueezerMediaSessionCallback(LyrionController lyrionController) {
        this.lyrionController = lyrionController;
    }

    @Override
    public void onPlay() {
        lyrionController.play();
    }

    @Override
    public void onPause() {
        lyrionController.pause();
    }

    @Override
    public void onSkipToNext() {
        lyrionController.nextTrack(lyrionController.getActivePlayer());
    }

    @Override
    public void onSkipToPrevious() {
        lyrionController.previousTrack(lyrionController.getActivePlayer());
    }

    @Override
    public void onSeekTo(long pos) {
        lyrionController.setSecondsElapsed((int) (pos/1000));
    }

    @Override
    public void onCustomAction(String action, Bundle extras) {
        if (SqueezeService.ACTION_DISCONNECT.equals(action)) {
            lyrionController.disconnect(true);
        } else
        if (SqueezeService.ACTION_POWER.equals(action)) {
            lyrionController.togglePower(lyrionController.getActivePlayer());
        }
    }
}
