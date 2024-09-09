/*
 * Copyright (c) 2014 Kurt Aaholst <kaaholst@gmail.com>
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

package uk.org.ngo.squeezer.itemlist;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.slider.Slider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.Util;
import uk.org.ngo.squeezer.framework.ItemAdapter;
import uk.org.ngo.squeezer.itemlist.dialog.SyncPowerDialog;
import uk.org.ngo.squeezer.itemlist.dialog.SyncVolumeDialog;
import uk.org.ngo.squeezer.model.CurrentPlaylistItem;
import uk.org.ngo.squeezer.model.Player;
import uk.org.ngo.squeezer.service.ISqueezeService;

public class PlayerListAdapter extends RecyclerView.Adapter<PlayerListAdapter.PlayerGroupViewHolder> {
    private static final int UPDATE_VOLUME = 1;

    private final PlayerListActivity mActivity;

    private final List<SyncGroup> childAdapters = new ArrayList<>();

    public void notifyItemChanged(Player player) {
        notifyItemChanged(player, null);
    }

    public void notifyVolumeChanged(Player player) {
        notifyItemChanged(player, UPDATE_VOLUME);
    }

    private void notifyItemChanged(Player player, @Nullable Object payload) {
        for (SyncGroup childAdapter : childAdapters) {
            for (int i = 0; i < childAdapter.getItemCount(); i++) {
                if (player == childAdapter.getItem(i)) {
                    childAdapter.notifyItemChanged(i, payload);
                    return;
                }
            }
        }
    }

    public void notifyGroupVolumeChanged(Player player) {
        for (int groupPos = 0; groupPos < getItemCount(); groupPos++) {
            SyncGroup syncGroup = childAdapters.get(groupPos);
            for (int playerPos = 0; playerPos < syncGroup.getItemCount(); playerPos++) {
                if (player == syncGroup.getItem(playerPos)) {
                    if (mActivity.getListView().isComputingLayout()) {
                        final int finalGroupPos = groupPos;
                        mActivity.getListView().post(() -> notifyItemChanged(finalGroupPos, UPDATE_VOLUME));
                    } else {
                        notifyItemChanged(groupPos, UPDATE_VOLUME);
                    }
                    return;
                }
            }
        }
    }

    /**
     * A list adapter for a synchronization group, containing players.
     * This class is comparable and it has a name for the synchronization group.
     */
    class SyncGroup extends ItemAdapter<PlayerView, Player> implements Comparable<SyncGroup> {

        public String syncGroupName; // the name of the synchronization group as displayed in the players screen

        public SyncGroup() {
            super(mActivity);
        }

        @Override
        public PlayerView createViewHolder(View view, int viewType) {
            return new PlayerView((PlayerListActivity) getActivity(), view);
        }

        @Override
        public void onBindViewHolder(@NonNull PlayerView holder, int position, @NonNull List<Object> payloads) {
            if (payloads.contains(UPDATE_VOLUME)) {
                holder.updateVolume(getItem(position));
            } else {
                onBindViewHolder(holder, position);
            }
        }

        @Override
        protected int getItemViewType(Player item) {
            return R.layout.list_item_player;
        }

        @Override
        public int compareTo(SyncGroup otherSyncGroup) {
            // compare this sync group name with the other one, alphabetically
            return this.syncGroupName.compareToIgnoreCase((otherSyncGroup).syncGroupName);
        }

        @Override
        public void update(int count, int start, List<Player> syncedPlayersList) {
            Collections.sort(syncedPlayersList); // first order players in sync group alphabetically

            // add the list
            super.update(count, start, syncedPlayersList);

            // determine and set synchronization group name (player names divided by commas)
            List<String> playerNames = new ArrayList<>();
            for (int i = 0; i < this.getItemCount(); i++) {
                Player p = this.getItem(i);
                playerNames.add(p.getName());
            }
            syncGroupName = TextUtils.join(", ", playerNames);
        }

    }
    /** The last set of player sync groups that were provided. */
    private Map<String, Collection<Player>> prevPlayerSyncGroups;

    /** Indicates if the list of players has changed. */
    boolean mPlayersChanged;

    /** Count of how many players are in the adapter. */
    int mPlayerCount;

    public PlayerListAdapter(PlayerListActivity activity) {
        mActivity = activity;
    }


    public void clear() {
        mPlayersChanged = true;
        childAdapters.clear();
        mPlayerCount = 0;
        notifyDataSetChanged();
    }

    /**
     * Sets the players in to the adapter.
     *
     * @param playerSyncGroups Multimap, mapping from the player ID of the sync master to the
     *     Players synced to that master. See
     *     {@link PlayerListActivity#updateSyncGroups(List)} for how this map is
     *     generated.
     */
    void setSyncGroups(Map<String, Collection<Player>> playerSyncGroups) {
        // The players might not have changed (so there's no need to reset the contents of the
        // adapter) but information about an individual player might have done.
        if (prevPlayerSyncGroups != null && prevPlayerSyncGroups.equals(playerSyncGroups)) {
            notifyDataSetChanged();
            return;
        }

        prevPlayerSyncGroups = new HashMap<>(playerSyncGroups);
        clear();

        // Get a list of slaves for every synchronization group
        for (Collection<Player> slaves: playerSyncGroups.values()) {
            // create a new synchronization group
            SyncGroup syncGroup = new SyncGroup();
            mPlayerCount += slaves.size();
            // add the slaves (the players) to the synchronization group
            syncGroup.update(slaves.size(), 0, new ArrayList<>(slaves));
            // add synchronization group to the child adapters
            childAdapters.add(syncGroup);
        }
        Collections.sort(childAdapters); // sort sync group list alphabetically by sync group name
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return childAdapters.size();
    }

    @NonNull
    @Override
    public PlayerGroupViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new PlayerGroupViewHolder(mActivity, LayoutInflater.from(parent.getContext()).inflate(R.layout.player_group_layout, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull PlayerGroupViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.contains(UPDATE_VOLUME)) {
            holder.calcGroupOffsets(childAdapters.get(position));
        } else {
            onBindViewHolder(holder, position);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull PlayerGroupViewHolder holder, int position) {
        SyncGroup syncGroup = childAdapters.get(position);
        holder.item = syncGroup;
        holder.text1.setText(mActivity.getString(R.string.player_group_header, syncGroup.syncGroupName));

        CurrentPlaylistItem groupSong = syncGroup.getItem(0).getPlayerState().getCurrentSong();
        if (groupSong != null) {
            holder.text2.setText(Util.joinSkipEmpty(" - ", groupSong.getName(), groupSong.artistAlbum()));
        }

        holder.contextMenuButton.setVisibility(syncGroup.getItemCount() > 1 ? View.VISIBLE : View.GONE);

        holder.groupVolume.setVisibility(syncGroup.getItemCount() > 1 && !syncGroup.getItem(0).isSyncVolume() ? View.VISIBLE : View.GONE);
        holder.calcGroupOffsets(syncGroup);

        holder.players.setAdapter(syncGroup);
    }

    public static class PlayerGroupViewHolder extends RecyclerView.ViewHolder {
        SyncGroup item;
        private final PlayerListActivity activity;
        final TextView text1;
        final TextView text2;
        final View groupVolume;
        final Slider volumeBar;
        final Button contextMenuButton;
        final RecyclerView players;

        int[] volumeOffsets;

        public PlayerGroupViewHolder(PlayerListActivity activity, @NonNull View itemView) {
            super(itemView);
            this.activity = activity;
            text1 = itemView.findViewById(R.id.text1);
            text2 = itemView.findViewById(R.id.text2);
            groupVolume = itemView.findViewById(R.id.group_volume);
            volumeBar = itemView.findViewById(R.id.group_volume_slider);
            contextMenuButton = itemView.findViewById(R.id.context_menu_button);
            players = itemView.findViewById(R.id.players_container);
            players.setLayoutManager(new LinearLayoutManager(players.getContext()));

            contextMenuButton.setOnClickListener(v -> showContextMenu());

            volumeBar.addOnChangeListener((slider, value, fromUser) -> {
                if (fromUser) {
                    ISqueezeService service = activity.getService();
                    if (service == null) {
                        return;
                    }
                    int groupVolume = (int)value;
                    for (int i = 0; i < item.getItemCount(); i++) {
                        service.setVolumeTo(item.getItem(i), trimVolume(groupVolume + volumeOffsets[i]));
                    }
                }
            });
        }

        private void calcGroupOffsets(SyncGroup syncGroup) {
            volumeOffsets = new int[syncGroup.getItemCount()];

            int lowestVolume = item.getItem(0).getPlayerState().getCurrentVolume();
            for (int i = 0; i < item.getItemCount(); i++) {
                int currentVolume = item.getItem(i).getPlayerState().getCurrentVolume();
                if (currentVolume < lowestVolume) lowestVolume = currentVolume;
            }
            int groupVolumeOffset = 0;
            for (int i = 0; i < item.getItemCount(); i++) {
                int currentVolume = item.getItem(i).getPlayerState().getCurrentVolume();
                volumeOffsets[i] = currentVolume - lowestVolume;
                if (volumeOffsets[i] > groupVolumeOffset) groupVolumeOffset = volumeOffsets[i];
            }

            volumeBar.setValueFrom(-groupVolumeOffset);
            volumeBar.setValue(item.getItem(0).getPlayerState().getCurrentVolume() - volumeOffsets[0]);
        }

        private int trimVolume(int volume) {
            return volume < 0 ? 0 : Math.min(volume, 100);
        }

        private void showContextMenu() {
            PopupMenu popup = new PopupMenu(activity, contextMenuButton);
            popup.inflate(R.menu.player_group_menu);
            popup.setOnMenuItemClickListener(menuItem -> doItemContext(menuItem, item));
            popup.show();
        }

        private boolean doItemContext(MenuItem menuItem, SyncGroup selectedItem) {
            activity.setCurrentSyncGroup(selectedItem);
            if (menuItem.getItemId() == R.id.sync_volume) {
                SyncVolumeDialog.show(activity);
                return true;
            } else if (menuItem.getItemId() == R.id.sync_power) {
                SyncPowerDialog.show(activity);
                return true;
            }
            return false;
        }
    }
}
