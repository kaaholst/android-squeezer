/*
 * Copyright (c) 2014 Google Inc.  All Rights Reserved.
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
import android.content.DialogInterface;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.Button;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.model.Player;

/**
 * A dialog that shows sync group options -- either joining an existing group, or
 * removing a player from a sync group. The hosting activity must implement
 * {@link PlayerSyncDialogHost} to provide the information that the dialog needs.
 */
public class PlayerSyncDialog extends DialogFragment {
    /**
     * Activities that host this dialog must implement this interface.
     */
    public interface PlayerSyncDialogHost {
        Map<String, Collection<Player>> getPlayerSyncGroups();
        Player getCurrentPlayer();
        void syncPlayerToPlayer(@NonNull Player slave, @NonNull String masterId);
        void unsyncPlayer(@NonNull Player player);
    }

    private PlayerSyncDialogHost mHost;

    /** The sync group the user selected. */
    private int mSelectedGroup = -1;

    /** Button to accept selection, disabled until a group is selected */
    private Button positiveButton;

    // Override the Fragment.onAttach() method to instantiate the PlayerSyncDialogHost.
    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        // Verify that the host activity implements the callback interface
        try {
            // Instantiate the PlayerSyncDialogHost.
            mHost = (PlayerSyncDialogHost) context;
        } catch (ClassCastException e) {
            // The activity doesn't implement the interface, throw exception
            throw new ClassCastException(context.toString()
                    + " must implement PlayerSyncDialogHost");
        }
    }

    @Override
    @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Map<String, Collection<Player>> playerSyncGroups = mHost.getPlayerSyncGroups();
        final Player currentPlayer = mHost.getCurrentPlayer();

        /* The names of each sync group. */
        List<String> playerSyncGroupNames = new ArrayList<>();

        /*
         * The master ID of the player for each sync group. Indices in this correspond
         * 1:1 with {@link playerSyncGroupNames}.
         */
        final List<String> playerSyncGroupMasterIds = new ArrayList<>();

        // Build the list of sync groups to show the user.

        // Collect and sort the master IDs.
        List<String> masterIds = new ArrayList<>(playerSyncGroups.keySet());
        Collections.sort(masterIds);

        // Generate descriptive text for each sync group.
        for (String masterId : masterIds) {
            // Do not show an entry for the group the current player is in.  That might be...
            // 1. Because it's the master of this group, or...
            if (masterId.equals(currentPlayer.getId()))
                continue;

            // 2. Because it's a member of this group.
            if (masterId.equals(currentPlayer.getPlayerState().getSyncMaster()))
                continue;

            // Collect the player names and master ID for this sync group.
            List<String> playerNames = new ArrayList<>();
            List<Player> slaves = new ArrayList<>(playerSyncGroups.get(masterId));
            Collections.sort(slaves, Player.compareById);

            for (Player slave : slaves) {
                playerNames.add(slave.getName());
            }
            playerSyncGroupNames.add(TextUtils.join(", ", playerNames));
            playerSyncGroupMasterIds.add(masterId);
        }

        // Add an additional entry for the "No synchronisation" option.
        playerSyncGroupNames.add(getString(R.string.menu_item_player_unsync));

        ArrayAdapter<String> playerSyncGroupAdapter = new ArrayAdapter<>(getActivity(),
                android.R.layout.simple_list_item_single_choice, playerSyncGroupNames);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getActivity());
        builder.setTitle(getString(R.string.sync_title, currentPlayer.getName()))
                .setSingleChoiceItems(playerSyncGroupAdapter, mSelectedGroup,
                        (dialog, which) -> {
                            positiveButton.setEnabled(true);
                            mSelectedGroup = which;
                        })
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    // The "No synchronisation" option is always last.
                    if (mSelectedGroup == playerSyncGroupMasterIds.size()) {
                        mHost.unsyncPlayer(currentPlayer);
                    } else {
                        mHost.syncPlayerToPlayer(currentPlayer,
                                playerSyncGroupMasterIds.get(mSelectedGroup));
                    }
                })
                .setNegativeButton(android.R.string.cancel, null);
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(dialogInterface -> {
            positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
            positiveButton.setEnabled(false);
        });
        return dialog;
    }

}
