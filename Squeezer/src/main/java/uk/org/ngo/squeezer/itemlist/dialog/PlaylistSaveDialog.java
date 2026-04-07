package uk.org.ngo.squeezer.itemlist.dialog;

import android.app.Dialog;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.text.InputType;

import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.framework.BaseActivity;

public class PlaylistSaveDialog extends BaseEditTextDialog {

    private BaseActivity activity;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);

        Bundle args = getArguments();
        String name = args.getString("name");

        activity = (BaseActivity) getActivity();
        dialog.setTitle(R.string.save_playlist_title);
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editText.setHint(R.string.save_playlist_hint);
        if (name != null && name.length() > 0) {
            editText.setText(name);
        }

        return dialog;
    }

    @Override
    protected boolean commit(String name) {
        activity.lyrionController().playlistSave(name);
        return true;
    }

    public static void addTo(BaseActivity activity, @Nullable String name) {
        PlaylistSaveDialog dialog = new PlaylistSaveDialog();
        Bundle args = new Bundle();
        args.putString("name", name);
        dialog.setArguments(args);
        dialog.show(activity.getSupportFragmentManager(), "SaveDialog");
    }
}