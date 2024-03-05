/*
 * Copyright (c) 2019 Kurt Aaholst.  All Rights Reserved.
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
import android.os.Bundle;

import androidx.annotation.NonNull;

import uk.org.ngo.squeezer.framework.BaseActivity;
import uk.org.ngo.squeezer.itemlist.JiveItemListActivity;
import uk.org.ngo.squeezer.model.JiveItem;

public class ChoicesDialog extends BaseChoicesDialog {

    private BaseActivity activity;
    private JiveItem item;
    private int alreadyPopped;
    private int position;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        activity = (BaseActivity)getActivity();
        item = getArguments().getParcelable(JiveItem.class.getName());
        alreadyPopped = getArguments().getInt("alreadyPopped", 0);
        position = getArguments().getInt("position", 0);
        return createDialog(item.getName(), null, item.selectedIndex-1, item.choiceStrings);
    }

    @Override
    protected void onSelectOption(int checkedId) {
        activity.action(item.goAction.choices[checkedId], alreadyPopped);
        item.selectedIndex = checkedId+1;
        if (activity instanceof JiveItemListActivity) {
            JiveItemListActivity a = (JiveItemListActivity) activity;
            a.getItemAdapter().notifyItemChanged(position);
        }
    }

    /**
     * Create a dialog to select from choices
     * <p>
     * See http://wiki.slimdevices.com/index.php/SBS_SqueezePlay_interface#Choices_array_in_Do_action
     * and choiceStrings of
     * http://wiki.slimdevices.com/index.php/SBS_SqueezePlay_interface#.3Citem_fields.3E
     */
    public static ChoicesDialog show(BaseActivity activity, JiveItem item, int position, int alreadyPopped) {
        // Create and show the dialog
        ChoicesDialog dialog = new ChoicesDialog();

        Bundle args = new Bundle();
        args.putParcelable(JiveItem.class.getName(), item);
        args.putInt("alreadyPopped", alreadyPopped);
        args.putInt("position", position);
        dialog.setArguments(args);

        dialog.show(activity.getSupportFragmentManager(), ChoicesDialog.class.getSimpleName());
        return dialog;
    }
}
