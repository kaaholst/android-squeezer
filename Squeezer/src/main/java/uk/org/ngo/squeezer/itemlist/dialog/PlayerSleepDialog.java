package uk.org.ngo.squeezer.itemlist.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.text.InputType;

import androidx.annotation.NonNull;

import uk.org.ngo.squeezer.Preferences;
import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.Util;
import uk.org.ngo.squeezer.framework.BaseActivity;
import uk.org.ngo.squeezer.model.Player;
import uk.org.ngo.squeezer.service.ISqueezeService;

public class PlayerSleepDialog extends BaseEditTextDialog {

    private BaseActivity activity;
    private Player player;

    public PlayerSleepDialog(Player player) {
        this.player = player;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);

        activity = (BaseActivity) getActivity();
        editTextLayout.setHint(R.string.set_sleep_timer);
        editTextLayout.setSuffixText(getString(R.string.minutes));
        editText.setInputType(InputType.TYPE_CLASS_NUMBER);
        editText.setText(String.valueOf(new Preferences(activity).getSleepMinutes()));

        return dialog;
    }

    @Override
    protected boolean commit(String sleep) {
        ISqueezeService service = activity.getService();
        if (service == null) return false;

        int minutes = (int) Util.parseDecimalInt(sleep, -1);
        if (minutes <= 0) return false;

        service.sleep(player, minutes*60);
        new Preferences(activity).setSleepMinutes(minutes);
        return true;
    }

}
