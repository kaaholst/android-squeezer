package uk.org.ngo.squeezer.widget;

import android.view.KeyEvent;

import uk.org.ngo.squeezer.service.ISqueezeService;

/**
 * Intercept hardware volume control keys to control Squeezeserver
 * volume.
 *
 * Change the volume when the key is depressed.  Suppress the keyUp
 * event, otherwise you get a notification beep as well as the volume
 * changing.
 */
public class VolumeKeysDelegate {

    public static boolean onKeyDown(int keyCode, ISqueezeService service) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                return adjustVolume(1, service);
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                return adjustVolume(-1, service);
            default:
                return false;
        }
    }

    public static boolean onKeyUp(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                return true;
            default:
                return false;
        }
    }

    private static boolean adjustVolume(int direction, ISqueezeService service) {
        if (service == null) {
            return false;
        }
        service.adjustVolume(direction);
        return true;
    }

}
