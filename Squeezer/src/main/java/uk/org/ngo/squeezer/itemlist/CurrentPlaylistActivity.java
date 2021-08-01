/*
 * Copyright (c) 2011 Kurt Aaholst <kaaholst@gmail.com>
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
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.core.view.GestureDetectorCompat;

import java.util.Map;

import uk.org.ngo.squeezer.Preferences;
import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.framework.ItemAdapter;
import uk.org.ngo.squeezer.framework.ItemViewHolder;
import uk.org.ngo.squeezer.itemlist.dialog.PlaylistClearDialog;
import uk.org.ngo.squeezer.itemlist.dialog.PlaylistSaveDialog;
import uk.org.ngo.squeezer.model.JiveItem;
import uk.org.ngo.squeezer.service.ISqueezeService;
import uk.org.ngo.squeezer.service.event.MusicChanged;
import uk.org.ngo.squeezer.service.event.PlaylistChanged;
import uk.org.ngo.squeezer.widget.OnSwipeListener;
import uk.org.ngo.squeezer.widget.UndoBarController;

/**
 * Activity that shows the songs in the current playlist.
 */
public class CurrentPlaylistActivity extends JiveItemListActivity implements PlaylistClearDialog.PlaylistClearDialogListener {
    private int skipPlaylistChanged = 0;
    private int draggedIndex = -1;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setHomeAsUpIndicator(R.drawable.ic_action_close);
        }

        final GestureDetectorCompat detector = new GestureDetectorCompat(this, new OnSwipeListener() {
            @Override
            public boolean onSwipeDown() {
                finish();
                return true;
            }
        });
        findViewById(R.id.parent_container).setOnTouchListener((v, event) -> {
            detector.onTouchEvent(event);
            return true;
        });
    }

    @Override
    public void onPause() {
        if (isFinishing()) {
            overridePendingTransition(android.R.anim.fade_in, R.anim.slide_out_down);
        }
        super.onPause();
    }

    @Override
    protected void orderPage(@NonNull ISqueezeService service, int start) {
        service.pluginItems(start, "status", this);
    }

    @Override
    protected boolean needPlayer() {
        return true;
    }

    @Override
    public void setContentView(@LayoutRes int layoutResID) {
        super.setContentView(layoutResID);
        getListView().setOnDragListener(new ListDragListener(this));
    }

    @Override
    protected ItemAdapter<ItemViewHolder<JiveItem>, JiveItem> createItemListAdapter() {
        return new JiveItemAdapter(this) {
            @Override
            public ItemViewHolder<JiveItem> createViewHolder(View view, int viewType) {
                if (viewType == R.layout.list_item_pending) {
                    return new JiveItemViewPending(getActivity(), view);
                } else {
                    return new CurrentPlaylistItemView(CurrentPlaylistActivity.this, view);
                }
            }

            @Override
            protected int getItemViewType(JiveItem item) {
                return (item != null) ? R.layout.list_item : R.layout.list_item_pending;
            }
        };
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.currentplaylistmenu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    /**
     * Sets the enabled state of the R.menu.currentplaylistmenu items.
     */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        final int[] ids = {R.id.menu_item_playlist_clear, R.id.menu_item_playlist_save,
                R.id.menu_item_playlist_show_current_song};

        final boolean knowCurrentPlaylist = getCurrentPlaylist() != null;

        for (int id : ids) {
            MenuItem item = menu.findItem(id);
            item.setVisible(knowCurrentPlaylist);
        }

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_item_playlist_clear:
                if (new Preferences(this).isClearPlaylistConfirmation()) {
                    PlaylistClearDialog.show(this);
                } else {
                    clearPlaylist();
                }
                return true;
            case R.id.menu_item_playlist_save:
                PlaylistSaveDialog.addTo(this, getCurrentPlaylist());
                return true;
            case R.id.menu_item_playlist_show_current_song:
                getListView().smoothScrollToPosition(getSelectedIndex());
                return true;

        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void clearPlaylist() {
        UndoBarController.show(this, R.string.CLEARING_PLAYLIST, new UndoBarController.UndoListener() {
            @Override
            public void onUndo() {
            }

            @Override
            public void onDone() {
                if (getService() != null) {
                    getService().playlistClear();
                }
            }
        });
    }

    private String getCurrentPlaylist() {
        if (getService() == null) {
            return null;
        }
        return getService().getCurrentPlaylist();
    }

    public void onEventMainThread(MusicChanged event) {
        if (getService() == null) {
            return;
        }
        if (event.player.equals(getService().getActivePlayer())) {
            int prevSelectedIndex = getSelectedIndex();
            setSelectedIndex(event.playerState.getCurrentPlaylistIndex());
            getItemAdapter().notifyItemChanged(prevSelectedIndex);
            getItemAdapter().notifyItemChanged(getSelectedIndex());
        }
    }

    public void onEventMainThread(PlaylistChanged event) {
        if (getService() == null) {
            return;
        }
        if (skipPlaylistChanged > 0) {
            skipPlaylistChanged--;
            return;
        }
        if (event.player.equals(getService().getActivePlayer())) {
            clearAndReOrderItems();
            getItemAdapter().notifyDataSetChanged();
        }
    }

    public void skipPlaylistChanged() {
        skipPlaylistChanged++;
    }

    public int getDraggedIndex() {
        return draggedIndex;
    }

    public void setDraggedIndex(int draggedIndex) {
        this.draggedIndex = draggedIndex;
        getItemAdapter().notifyDataSetChanged();
    }

    @Override
    public void onItemsReceived(int count, int start, Map<String, Object> parameters, List<JiveItem> items, Class<JiveItem> dataType) {
        List<JiveItem> playlistItems = new ArrayList<>();
        for (JiveItem item : items) {
            // Skip special items (global actions) as there are handled locally
            if ((item.hasSubItems() || item.hasInput())) {
                count--;
            } else {
                playlistItems.add(item);
                if (item.moreAction == null) {
                    item.moreAction = item.goAction;
                    item.goAction = null;
                }
            }
        }
        super.onItemsReceived(count, start, parameters, playlistItems, dataType);

        ISqueezeService service = getService();
        if (service == null) {
            return;
        }

        int selectedIndex = service.getPlayerState().getCurrentPlaylistIndex();
        setSelectedIndex(selectedIndex);
        // Initially position the list at the currently playing song.
        // Do it again once it has loaded because the newly displayed items
        // may push the current song outside the displayed area
        if (start == 0 || (start <= selectedIndex && selectedIndex < start + playlistItems.size())) {
            runOnUiThread(() -> getListView().scrollToPosition(selectedIndex));
        }
    }

    public static void show(Context context) {
        final Intent intent = new Intent(context, CurrentPlaylistActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        intent.putExtra(JiveItem.class.getName(), JiveItem.CURRENT_PLAYLIST);

        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
        if (context instanceof Activity) {
            ((Activity) context).overridePendingTransition(R.anim.slide_in_up, android.R.anim.fade_out);
        }
    }

}
