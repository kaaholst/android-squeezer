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

package uk.org.ngo.squeezer.dialog;

import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.LifecycleOwner;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class ConfirmDialog extends DialogFragment {
    private static final String TITLE_RESOURCE_KEY = "TITLE_RESOURCE_KEY";
    private static final String REQUEST_KEY = "REQUEST_KEY";

    @NonNull
    @Override
    public AlertDialog onCreateDialog(Bundle savedInstanceState) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        builder.setTitle(getArguments().getInt(TITLE_RESOURCE_KEY));
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.setPositiveButton(android.R.string.ok, (dialogInterface, i) -> {
            getParentFragmentManager().setFragmentResult(REQUEST_KEY, getArguments());
        });
        return builder.create();
    }

    public static void show(FragmentManager fragmentManager, LifecycleOwner owner, @StringRes int titleResourceId, Runnable listener) {
        ConfirmDialog dialog = new ConfirmDialog();

        Bundle args = new Bundle();
        args.putInt(TITLE_RESOURCE_KEY, titleResourceId);
        dialog.setArguments(args);

        fragmentManager.setFragmentResultListener(REQUEST_KEY, owner, (requestKey, result) -> listener.run());
        dialog.show(fragmentManager, dialog.getClass().getSimpleName());
    }
}
