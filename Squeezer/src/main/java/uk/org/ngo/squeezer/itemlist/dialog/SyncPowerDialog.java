/*
 * Copyright (c) 2020 Kurt Aaholst.  All Rights Reserved.
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

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;

import uk.org.ngo.squeezer.R;

public class SyncPowerDialog extends BaseChoicesDialog {
    /**
     * Activities that host this dialog must implement this interface.
     */
    public interface SyncPowerDialogHost {
        FragmentManager getSupportFragmentManager();
        int getSyncPower();
        void setSyncPower(@NonNull String option);
    }

    private SyncPowerDialogHost host;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        host = (SyncPowerDialogHost) context;
    }

    @Override
    @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        String[] options = {
            getString(R.string.SETUP_SYNCPOWER_OFF),
            getString(R.string.SETUP_SYNCPOWER_ON)
        };
        return createDialog(
                getString(R.string.SETUP_SYNCPOWER),
                getString(R.string.SETUP_SYNCPOWER_DESC),
                host.getSyncPower(),
                options
        );
    }

    @Override
    protected void onSelectOption(int checkedId) {
        host.setSyncPower(String.valueOf(checkedId));
    }

    /**
     * Create a dialog to select play track album option.
     *
     * @param host The hosting activity must implement {@link SyncPowerDialogHost} to provide
     *            the information that the dialog needs.
     */
    public static SyncPowerDialog show(SyncPowerDialogHost host) {
        SyncPowerDialog dialog = new SyncPowerDialog();

        dialog.show(host.getSupportFragmentManager(), SyncPowerDialog.class.getSimpleName());

        return dialog;
    }
}
