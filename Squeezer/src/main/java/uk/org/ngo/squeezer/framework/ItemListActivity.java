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

package uk.org.ngo.squeezer.framework;


import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.util.Pair;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Map;

import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.Squeezer;
import uk.org.ngo.squeezer.dialog.VolumeSettings;
import uk.org.ngo.squeezer.itemlist.ItemListCallback;
import uk.org.ngo.squeezer.itemlist.dialog.ArtworkListLayout;
import uk.org.ngo.squeezer.model.Item;
import uk.org.ngo.squeezer.model.LyrionPlayer;
import uk.org.ngo.squeezer.service.LyrionController;
import uk.org.ngo.squeezer.service.event.ActivePlayerChanged;
import uk.org.ngo.squeezer.service.event.HandshakeComplete;
import uk.org.ngo.squeezer.service.event.PlayerVolume;
import uk.org.ngo.squeezer.service.event.RefreshEvent;
import uk.org.ngo.squeezer.util.ImageFetcher;
import uk.org.ngo.squeezer.volume.VolumeBar;
import uk.org.ngo.squeezer.widget.ViewUtilities;

/**
 * A generic base class for an activity to list items of a particular slimserver data type. The
 * data type is defined by the generic type argument, and must be an extension of {@link Item}. You
 * must provide an {@link ItemAdapter} to provide the view logic used by this activity. This is done by
 * implementing {@link #createItemListAdapter()}}.
 * <p>
 * When the activity is first created ({@link #onCreate(Bundle)}), an empty {@link ItemAdapter}
 * is created.
 *
 * @param <VH> {@link ItemViewHolder} View holder for items
 * @param <T> Denotes the class of the items this class should list
 *
 * @author Kurt Aaholst
 */
public abstract class ItemListActivity<VH extends ItemViewHolder<T>, T extends Item> extends BaseActivity implements ItemListCallback<T> {

    private static final String TAG = ItemListActivity.class.getSimpleName();

    /**
     * The number of items per page.
     */
    protected int mPageSize;

    /**
     * Progress bar while items are loading.
     */
    private View loadingProgress;

    /**
     * View to show when no players are connected
     */
    private View emptyView;

    /**
     * Layout hosting the sub activity content
     */
    private FrameLayout subActivityContent;

    /**
     * List view to show the received items
     */
    private RecyclerView listView;

    /** Volume bar */
    private VolumeBar volumeBar;

    /**
     * Tag for player id in mRetainFragment.
     */
    private static final String TAG_PLAYER_ID = "PlayerId";

    /**
     * Tag for first visible position in mRetainFragment.
     */
    private static final String TAG_POSITION = "position";

    /**
     * Tag for itemAdapter in mRetainFragment.
     */
    protected static final String TAG_ADAPTER = "adapter";

    private ItemAdapter<VH, T> itemAdapter;

    @Override
    public void setContentView(int layoutResID) {
        View fullLayout = getLayoutInflater().inflate(R.layout.item_list_activity_layout, findViewById(R.id.activity_layout));
        subActivityContent = fullLayout.findViewById(R.id.content_frame);
        getLayoutInflater().inflate(layoutResID, subActivityContent, true); // Places the activity layout inside the activity content frame.
        super.setContentView(fullLayout);

        loadingProgress = requireView(R.id.loading_label);
        emptyView = requireView(R.id.empty_view);
        listView = requireView(R.id.item_list);
        listView.setLayoutManager(new LinearLayoutManager(this));
        volumeBar = new VolumeBar(requireView(R.id.volume_bar), this::lyrionController, new Pair<>(AppCompatResources.getDrawable(this, R.drawable.ic_settings), () -> {
            if (lyrionController().getActivePlayer() != null) {
                new VolumeSettings().show(getSupportFragmentManager(), VolumeSettings.class.getName());
            }
        }));

        getListView().addOnScrollListener(new ScrollListener());

        setupAdapter(getListView());
    }

    /**
     * Returns the ID of a content view to be used by this list activity.
     * <p>
     * The content view must contain a {@link RecyclerView} with the id {@literal item_list} in order
     * to be valid.
     *
     * @return The ID
     */
    protected int getContentView() {
        return R.layout.slim_browser_layout;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPageSize = getResources().getInteger(R.integer.PageSize);

        setContentView(getContentView());
        setSupportActionBar(findViewById(R.id.toolbar));
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.collapsing_toolbar), null);
        ViewUtilities.setInsetsListener(findViewById(R.id.toolbar), true, false, false);
        ViewUtilities.setInsetsListener(findViewById(R.id.top_app_bar), false, false, false);
        ViewUtilities.setInsetsListener(subActivityContent, false, false, false);
        ViewUtilities.setInsetsListener(findViewById(R.id.now_playing_fragment), false, true, false);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (itemAdapter != null) {
            itemAdapter.setActivity(null);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        saveVisiblePosition();
    }

    @Override
    public void onPause() {
        super.onPause();

        // Any items coming in after callbacks have been unregistered are discarded.
        // We cancel any outstanding orders, so items can be reordered after the
        // activity resumes.
        getItemAdapter().cancelOrders();
    }

    @Override
    protected void registerObservers() {
        super.registerObservers();
        repository().observe(this, (HandshakeComplete event) -> onHandshakeComplete());
        repository().observe(this, (ActivePlayerChanged event) -> setPlayer(event.player));
        repository().observe(this, (RefreshEvent event) -> clearAndReOrderItems());
        repository().observe(this, (PlayerVolume event) -> {
            if (event.player == lyrionController().getActivePlayer()) {
                volumeBar.update(lyrionController().getVolume());
            }
        });
    }

    private void showLoading() {
        subActivityContent.setVisibility(View.VISIBLE);
        loadingProgress.setVisibility(View.VISIBLE);
        emptyView.setVisibility(View.GONE);
    }

    private void showEmptyView() {
        subActivityContent.setVisibility(View.GONE);
        loadingProgress.setVisibility(View.GONE);
        emptyView.setVisibility(View.VISIBLE);
    }

    protected void showContent() {
        subActivityContent.setVisibility(View.VISIBLE);
        loadingProgress.setVisibility(View.GONE);
        emptyView.setVisibility(View.GONE);
    }

    /**
     * Starts an asynchronous fetch of items from the server.
     *
     * @param start Position in list to start the fetch. Pass this on to {@link LyrionController}
     */
    protected abstract void orderPage(int start);

    public ArtworkListLayout getPreferredListLayout() {
        return Squeezer.instance().preferences().getAlbumListLayout();
    }

    /**
     * @return The view listing the items for this acitvity
     */
    public final RecyclerView getListView() {
        return listView;
    }

    protected abstract ItemAdapter<VH, T> createItemListAdapter();

    /**
     * @return The current {@link ItemAdapter}, creating it if necessary.
     */
    public ItemAdapter<VH, T> getItemAdapter() {
        if (itemAdapter == null) {
            itemAdapter = getRetainedValue(TAG_ADAPTER);
            if (itemAdapter == null) {
                itemAdapter = createItemListAdapter();
                putRetainedValue(TAG_ADAPTER, itemAdapter);
            } else {
                itemAdapter.setActivity(this);
                // Update views with the count from the retained item adapter
                itemAdapter.onCountUpdated();
            }
        }

        return itemAdapter;
    }

    /** Update the UI if the player changed */
    private void setPlayer(LyrionPlayer player) {
        String oldPlayerId = getRetainedValue(TAG_PLAYER_ID);
        String activePlayerId = (player != null ? player.getId() : "");
        if (!activePlayerId.equals(oldPlayerId)) {
            Log.i(TAG, "setPlayer(" + player + ")");
            putRetainedValue(TAG_PLAYER_ID, activePlayerId);
            supportInvalidateOptionsMenu();
            if (player == null) {
                showEmptyView();
            } else {
                clearAndReOrderItems();
                volumeBar.update(lyrionController().getVolume());
            }
        }
    }

    private void onHandshakeComplete() {
        Log.i(TAG, "Handshake complete");
        LyrionPlayer activePlayer = lyrionController().getActivePlayer();
        setPlayer(activePlayer);
        if (activePlayer != null) {
            volumeBar.update(lyrionController().getVolume());
            maybeOrderVisiblePages(getListView());
        } else {
            showEmptyView();
        }
    }

    /**
     * Store the first visible position of {@link #getListView()}, in the retain fragment, so
     * we can later retrieve it.
     *
     * @see android.widget.AbsListView#getFirstVisiblePosition()
     */
    private void saveVisiblePosition() {
        LinearLayoutManager layoutManager = (LinearLayoutManager) getListView().getLayoutManager();
        putRetainedValue(TAG_POSITION, layoutManager.findFirstVisibleItemPosition());
    }

    /**
     * Set our adapter on the list view.
     * <p>
     * This can't be done in {@link #onCreate(android.os.Bundle)} because getView might be called
     * before the handshake is complete, so we need to delay it.
     * <p>
     * However when we set the adapter after onCreate the list is scrolled to top, so we retain the
     * visible position.
     * <p>
     * Call this method after the handshake is complete.
     */
    private void setupAdapter(RecyclerView listView) {
        listView.setAdapter(getItemAdapter());
        // TODO call setHasFixedSize (not for grid)

        Integer position = getRetainedValue(TAG_POSITION);
        if (position != null) {
            listView.scrollToPosition(position);
        }
    }

    /**
     * Orders pages that correspond to visible rows in the listview.
     * <p>
     * Computes the pages that correspond to the rows that are currently being displayed by the
     * listview, and calls {@link ItemAdapter#maybeOrderPage(int)} to fetch the page if necessary.
     *
     * @param listView The listview with visible rows.
     */
    public void maybeOrderVisiblePages(RecyclerView listView) {
        LinearLayoutManager layoutManager = (LinearLayoutManager) listView.getLayoutManager();
        int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();
        if (firstVisibleItemPosition == RecyclerView.NO_POSITION) {
            getItemAdapter().maybeOrderPage(0);
        } else {
            int pos = (firstVisibleItemPosition / mPageSize) * mPageSize;
            int end = firstVisibleItemPosition + listView.getChildCount();

            while (pos < end) {
                getItemAdapter().maybeOrderPage(pos);
                pos += mPageSize;
            }
        }
    }

    /**
     * This will call back to {@link ItemAdapter#update(int, int, List)} on the UI thread
     *
     * @param count The total number of items known by the server.
     * @param start The start position of this update.
     * @param items The items received in this update
     */
    protected void onItemsReceived(final int count, final int start, final List<T> items) {
        Log.d(TAG, "onItemsReceived(" + count + ", " + start + ", " + items.size() + ")");
        runOnUiThread(() -> {
            showContent();
            getItemAdapter().update(count, start, items);
        });
    }

    @Override
    public void onItemsReceived(int count, int start, Map<String, Object> parameters, List<T> items, Class<T> dataType) {
        onItemsReceived(count, start, items);
    }

    /**
     * Empties the variables that track which pages have been requested, and orders page 0.
     */
    public void clearAndReOrderItems() {
        if (lyrionController().getActivePlayer() != null) {
            Log.i(TAG, "clearAndReOrderItems()");
            showLoading();
            getItemAdapter().clear();
            getItemAdapter().maybeOrderPage(0);
        }
    }

    @Override
    public Object getClient() {
        return this;
    }

    /**
     * Tracks scrolling activity.
     * <p>
     * When the list is idle, new pages of data are fetched from the server.
     */
    private class ScrollListener extends RecyclerView.OnScrollListener {
        private int prevScrollState = RecyclerView.SCROLL_STATE_IDLE;

        @Override
        public void onScrollStateChanged(@NonNull RecyclerView listView, int scrollState) {
            if (scrollState == prevScrollState) return;
            prevScrollState = scrollState;

            boolean listScrolling = (scrollState != RecyclerView.SCROLL_STATE_IDLE);
            getItemAdapter().setListScrolling(listScrolling);
            if (!listScrolling) maybeOrderVisiblePages(listView);

            // Pauses cache disk fetches if the list is scrolling
            ImageFetcher.getInstance(ItemListActivity.this).setPauseWork(listScrolling);
        }
    }
}
