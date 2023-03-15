/*
 * Copyright (c) 2020 Kurt Aaholst <kaaholst@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.org.ngo.squeezer.itemlist.dialog;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;

import uk.org.ngo.squeezer.Preferences;
import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.Squeezer;
import uk.org.ngo.squeezer.framework.BaseConfirmDialog;

public class PlaylistClearDialog extends BaseConfirmDialog {
    private static final String TAG = PlaylistClearDialog.class.getSimpleName();
    private PlaylistClearDialogListener host;

    public interface PlaylistClearDialogListener {
        FragmentManager getSupportFragmentManager();
        void clearPlaylist();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        host = (PlaylistClearDialogListener) context;
    }

    @Override
    protected String title() {
        return getString(R.string.CLEAR_PLAYLIST);
    }

    @Override
    protected void ok(boolean persist) {
        if (persist) {
            Squeezer.getPreferences().setClearPlaylistConfirmation(false);
        }
        host.clearPlaylist();
    }

    public static PlaylistClearDialog show(PlaylistClearDialogListener callback) {
        PlaylistClearDialog dialog = new PlaylistClearDialog();

        dialog.show(callback.getSupportFragmentManager(), TAG);

        return dialog;
    }
}
