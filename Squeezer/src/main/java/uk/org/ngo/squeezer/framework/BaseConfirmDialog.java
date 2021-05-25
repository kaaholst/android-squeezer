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

package uk.org.ngo.squeezer.framework;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import uk.org.ngo.squeezer.R;

public abstract class BaseConfirmDialog extends DialogFragment {

    @NonNull
    @Override
    public AlertDialog onCreateDialog(Bundle savedInstanceState) {
        return new MaterialAlertDialogBuilder(getActivity())
                .setTitle(title())
                .setMultiChoiceItems(new String[]{getString(R.string.DONT_ASK_AGAIN)}, new boolean[]{false}, (dialogInterface, i, b) -> onPersistChecked(b))
                .setPositiveButton(okText(), (dialogInterface, i) -> ok(isPersistChecked()))
                .setNegativeButton(android.R.string.cancel, (dialogInterface, i) -> cancel(isPersistChecked()))
                .create();
    }

    protected abstract String title();

    protected String okText() {
        return title();
    }

    protected void onPersistChecked(boolean persist) {
    }

    private boolean isPersistChecked() {
        return getDialog().getListView().isItemChecked(0);
    }

    @Override
    public AlertDialog getDialog() {
        return (AlertDialog) super.getDialog();
    }

    protected abstract void ok(boolean persist);

    protected void cancel(boolean persist) {
    }
}
