/*
 * Copyright (c) 2026 Thomas Malcher
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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.framework.BaseActivity;
import uk.org.ngo.squeezer.model.AlarmPlaylist;
import uk.org.ngo.squeezer.model.Player;
import uk.org.ngo.squeezer.model.Preset;
import uk.org.ngo.squeezer.service.IRButton;
import uk.org.ngo.squeezer.service.ISqueezeService;
import uk.org.ngo.squeezer.service.event.ActivePlayerChanged;
import uk.org.ngo.squeezer.service.event.PlayerStateChanged;
import uk.org.ngo.squeezer.widget.ViewUtilities;

/**
 * Edit the presets of the active player, i.e. assign favorites to the preset buttons of the
 * player, like the presets editor of the LMS web interface.
 */
public class PresetsActivity extends BaseActivity {
    /** The number of preset buttons on the players. */
    private static final int NUM_PRESETS = IRButton.values().length;

    private static final String CURRENT_PRESET = "currentPreset";

    /** The most recent active player. */
    private Player activePlayer;

    /** The items available for the presets, from the "alarm playlists" command. */
    private final List<AlarmPlaylist> alarmPlaylists = new ArrayList<>();

    /** The preset slot (0-based) a new item is currently selected for. */
    private int currentPreset;

    private PresetsAdapter adapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.list_activity_layout);

        if (savedInstanceState != null) {
            currentPreset = savedInstanceState.getInt(CURRENT_PRESET);
        }

        adapter = new PresetsAdapter();
        RecyclerView listView = requireView(R.id.item_list);
        listView.setAdapter(adapter);
        listView.setLayoutManager(new LinearLayoutManager(this));

        setSupportActionBar(requireView(R.id.toolbar));
        ViewUtilities.setInsetsListener(requireView(R.id.toolbar), true, false, false);
        ViewUtilities.setInsetsListener(listView, false, true, false);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(R.string.menu_item_presets);
        }
    }

    @Override
    protected void onServiceConnected(@NonNull ISqueezeService service) {
        super.onServiceConnected(service);
        repository().observe(this, this::onPlayerStateChanged);
        repository().observe(this, (ActivePlayerChanged event) -> {
            activePlayer = event.player;
            adapter.notifyDataSetChanged();
        });
        activePlayer = service.getActivePlayer();
        service.alarmPlaylists(alarmPlaylistsCallback);
        adapter.notifyDataSetChanged();
    }

    private void onPlayerStateChanged(PlayerStateChanged event) {
        if (event.player.equals(activePlayer)) {
            adapter.notifyDataSetChanged();
        }
    }

    private final IServiceItemListCallback<AlarmPlaylist> alarmPlaylistsCallback = new IServiceItemListCallback<>() {
        private final List<AlarmPlaylist> items = new ArrayList<>();

        @Override
        public void onItemsReceived(final int count, final int start, Map<String, Object> parameters, final List<AlarmPlaylist> newItems, Class<AlarmPlaylist> dataType) {
            runOnUiThread(() -> {
                if (start == 0) {
                    items.clear();
                }

                // A preset needs an URL, so skip items without, e.g. "Use current playlist",
                // like the presets editor of the LMS web interface.
                newItems.stream().filter(item -> !item.getId().isEmpty()).forEach(items::add);
                if (start + newItems.size() >= count) {
                    alarmPlaylists.clear();
                    alarmPlaylists.addAll(items);
                }
            });
        }

        @Override
        public Object getClient() {
            return PresetsActivity.this;
        }
    };

    /** @return The preset assigned to the supplied slot (0-based), null if unassigned. */
    private Preset getPreset(int position) {
        List<Preset> presets = (activePlayer != null) ? activePlayer.getPlayerState().getPresets() : null;
        if (presets == null || position >= presets.size()) {
            return null;
        }

        Preset preset = presets.get(position);
        return preset.isSet() ? preset : null;
    }

    void selectPreset(int position) {
        currentPreset = position;
        Preset preset = getPreset(position);
        AlarmPlaylistActivity.show(this, (preset != null) ? preset.url : null, alarmPlaylists);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == AlarmPlaylistActivity.GET_ALARM_PLAYLIST && resultCode == Activity.RESULT_OK) {
            AlarmPlaylist alarmPlaylist = Objects.requireNonNull(data.getParcelableExtra(AlarmPlaylistActivity.ALARM_PLAYLIST));
            requireService().setPreset(activePlayer, currentPreset + 1, alarmPlaylist);

            // Update the local state, in case the server doesn't push a status update.
            List<Preset> presets = new ArrayList<>(activePlayer.getPlayerState().getPresets());
            while (presets.size() <= currentPreset) {
                presets.add(new Preset("", "", ""));
            }
            presets.set(currentPreset, new Preset(alarmPlaylist.getId(), alarmPlaylist.getName(), "audio"));
            activePlayer.getPlayerState().setPresets(presets);
            adapter.notifyItemChanged(currentPreset);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(CURRENT_PRESET, currentPreset);
    }

    public static void show(Activity context) {
        final Intent intent = new Intent(context, PresetsActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        context.startActivity(intent);
    }

    private class PresetsAdapter extends RecyclerView.Adapter<PresetsAdapter.ViewHolder> {

        @Override
        public int getItemCount() {
            return NUM_PRESETS;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_preset, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.bindView(position);
        }

        private class ViewHolder extends RecyclerView.ViewHolder {
            private final TextView number;
            private final TextView text1;
            private final ImageButton play;

            public ViewHolder(@NonNull View view) {
                super(view);
                number = view.findViewById(R.id.number);
                text1 = view.findViewById(R.id.text1);
                play = view.findViewById(R.id.play);
                view.setOnClickListener(v -> selectPreset(getBindingAdapterPosition()));
                play.setOnClickListener(v -> {
                    if (activePlayer != null) {
                        requireService().button(activePlayer, IRButton.values()[getBindingAdapterPosition()]);
                    }
                });
            }

            public void bindView(int position) {
                Preset preset = getPreset(position);
                number.setText(String.valueOf(position + 1));
                text1.setText((preset != null) ? preset.text
                        : getString(R.string.PRESETS_NOT_DEFINED, String.valueOf(position + 1)));
                play.setVisibility((preset != null) ? View.VISIBLE : View.GONE);
            }
        }
    }
}
