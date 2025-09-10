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

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.util.Pair;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.Squeezer;
import uk.org.ngo.squeezer.dialog.VolumeSettings;
import uk.org.ngo.squeezer.itemlist.IServiceItemListCallback;
import uk.org.ngo.squeezer.itemlist.dialog.ArtworkListLayout;
import uk.org.ngo.squeezer.model.Item;
import uk.org.ngo.squeezer.model.Player;
import uk.org.ngo.squeezer.service.ISqueezeService;
import uk.org.ngo.squeezer.service.SqueezeService;
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
public abstract class ItemListActivity<VH extends ItemViewHolder<T>, T extends Item> extends BaseActivity implements IServiceItemListCallback<T>, ItemAdapter.PageOrderer {

    private static final String TAG = ItemListActivity.class.getSimpleName();

    /**
     * The list is being actively scrolled by the user
     */
    private boolean mListScrolling;

    /**
     * The number of items per page.
     */
    protected int mPageSize;

    /**
     * The pages that have been requested from the server.
     */
    private final Set<Integer> mOrderedPages = new HashSet<>();

    /**
     * The pages that have been received from the server
     */
    private Set<Integer> mReceivedPages;

    /**
     * Pages requested before the handshake completes. A stack on the assumption
     * that once the service is bound the most recently requested pages should be ordered
     * first.
     */
    private final Stack<Integer> mOrderedPagesBeforeHandshake = new Stack<>();

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
     * Tag for mReceivedPages in mRetainFragment.
     */
    private static final String TAG_RECEIVED_PAGES = "mReceivedPages";

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
    public static final String TAG_ADAPTER = "adapter";

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
        volumeBar = new VolumeBar(requireView(R.id.volume_bar), this::requireService, new Pair<>(AppCompatResources.getDrawable(this, R.drawable.ic_settings), () -> new VolumeSettings().show(getSupportFragmentManager(), VolumeSettings.class.getName())));

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

        mReceivedPages = getRetainedValue(TAG_RECEIVED_PAGES);
        if (mReceivedPages == null) {
            mReceivedPages = new HashSet<>();
            putRetainedValue(TAG_RECEIVED_PAGES, mReceivedPages);
        }
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
        cancelOrders();
    }

    @Override
    protected void onServiceConnected(@NonNull ISqueezeService service) {
        super.onServiceConnected(service);
        repository().observe(this, (HandshakeComplete event) -> onHandshakeComplete());
        repository().observe(this, this::onActivePlayerChanged);
        repository().observe(this, (RefreshEvent event) -> clearAndReOrderItems());
        repository().observe(this, (PlayerVolume event) -> {
            if (event.player == requireService().getActivePlayer()) {
                volumeBar.update(requireService().getVolume());
            }
        });
    }

    private void showLoading() {
        subActivityContent.setVisibility(View.GONE);
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
     * Starts an asynchronous fetch of items from the server. Will only be called after the
     * service connection has been bound.
     *
     * @param service The connection to the bound service.
     * @param start Position in list to start the fetch. Pass this on to {@link
     *     uk.org.ngo.squeezer.service.SqueezeService}
     */
    protected abstract void orderPage(@NonNull ISqueezeService service, int start);

    public ArtworkListLayout getPreferredListLayout() {
        return Squeezer.getPreferences().getAlbumListLayout();
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

    /**
     * List can clear any information about which items have been received and ordered, by calling
     * {@link #clearAndReOrderItems()}. This will call back to this method, which must clear any
     * adapters holding items.
     */
    protected void clearItemAdapter() {
        getItemAdapter().clear();
    }

    /**
     * Call back from {@link #onItemsReceived(int, int, List)}
     */
    protected void updateAdapter(int count, int start, List<T> items) {
        getItemAdapter().update(count, start, items);
    }

    /**
     * Orders a page worth of data, starting at the specified position, if it has not already been
     * ordered, and if the service is connected and the handshake has completed.
     *
     * @param pagePosition position in the list to start the fetch.
     */
    public void maybeOrderPage(int pagePosition) {
        if (!mListScrolling && !mReceivedPages.contains(pagePosition) && !mOrderedPages
                .contains(pagePosition) && !mOrderedPagesBeforeHandshake.contains(pagePosition)) {
            ISqueezeService service = getService();

            // If the service connection hasn't happened yet then store the page
            // request where it can be used in mHandshakeComplete.
            if (service == null) {
                mOrderedPagesBeforeHandshake.push(pagePosition);
            } else {
                try {
                    orderPage(service, pagePosition);
                    mOrderedPages.add(pagePosition);
                } catch (SqueezeService.HandshakeNotCompleteException e) {
                    mOrderedPagesBeforeHandshake.push(pagePosition);
                }
            }
        }
    }

    /**
     * Update the UI with the player change
     */
    private void onActivePlayerChanged(ActivePlayerChanged event) {
        Log.i(TAG, "ActivePlayerChanged: " + event.player);
        String activePlayerId = (event.player != null ? event.player.getId() : "");
        putRetainedValue(TAG_PLAYER_ID, activePlayerId);
        supportInvalidateOptionsMenu();
        if (event.player == null) {
            showEmptyView();
        } else {
            clearAndReOrderItems();
        }
        if (event.player != null) volumeBar.update(requireService().getVolume());
    }

    private void onHandshakeComplete() {
        Log.i(TAG, "Handshake complete");
        String oldPlayerId = getRetainedValue(TAG_PLAYER_ID);
        Player activePlayer = requireService().getActivePlayer();
        String activePlayerId = (activePlayer != null ? activePlayer.getId() : "");
        putRetainedValue(TAG_PLAYER_ID, activePlayerId);
        if (oldPlayerId != null && !oldPlayerId.equals(activePlayerId)) {
            onActivePlayerChanged(new ActivePlayerChanged(activePlayer));
        } else {
            // Order any pages that were requested before the handshake complete.
            while (!mOrderedPagesBeforeHandshake.empty()) {
                maybeOrderPage(mOrderedPagesBeforeHandshake.pop());
            }
        }

        if (activePlayer != null) {
            volumeBar.update(requireService().getVolume());
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
     * listview, and calls {@link #maybeOrderPage(int)} to fetch the page if necessary.
     *
     * @param listView The listview with visible rows.
     */
    public void maybeOrderVisiblePages(RecyclerView listView) {
        LinearLayoutManager layoutManager = (LinearLayoutManager) listView.getLayoutManager();
        int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();
        if (firstVisibleItemPosition == RecyclerView.NO_POSITION) {
            clearAndReOrderItems();
        } else {
            int pos = (firstVisibleItemPosition / mPageSize) * mPageSize;
            int end = firstVisibleItemPosition + listView.getChildCount();

            while (pos < end) {
                maybeOrderPage(pos);
                pos += mPageSize;
            }
        }
    }

    /**
     * Tracks items that have been received from the server.
     * <p>
     * Subclasses <b>must</b> call this method when receiving data from the server to ensure that
     * internal bookkeeping about pages that have/have not been ordered is kept consistent.
     * <p>
     * This will call back to {@link #updateAdapter(int, int, List)} on the UI thread
     *
     * @param count The total number of items known by the server.
     * @param start The start position of this update.
     * @param items The items received in this update
     */
    @CallSuper
    protected void onItemsReceived(final int count, final int start, final List<T> items) {
        int size = items.size();
        Log.d(TAG, "onItemsReceived(" + count + ", " + start + ", " + size + ")");

        // If this doesn't add any items, then don't register the page as received
        if (start < count && size != 0) {
            // Because we might receive a page in chunks, we test if this is the end of a page
            // before we register the page as received.
            if (((start + size) % mPageSize == 0) || (start + size == count)) {
                // Add this page of data to mReceivedPages and remove from mOrderedPages.
                int pageStart = (start / mPageSize) * mPageSize;
                mReceivedPages.add(pageStart);
                mOrderedPages.remove(pageStart);
            }
        }

        runOnUiThread(() -> {
            showContent();
            updateAdapter(count, start, items);
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
        if (requireService().getActivePlayer() != null) {
            Log.i(TAG, "clearAndReOrderItems()");
            showLoading();
            clearItems();
            maybeOrderPage(0);
        }
    }

    /** Empty the variables that track which pages have been requested. */
    public void clearItems() {
        mOrderedPagesBeforeHandshake.clear();
        mOrderedPages.clear();
        mReceivedPages.clear();
        clearItemAdapter();
    }

    /**
     * Removes any outstanding requests from mOrderedPages.
     */
    private void cancelOrders() {
        mOrderedPages.clear();
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

        private int mPrevScrollState = RecyclerView.SCROLL_STATE_IDLE;

        @Override
        public void onScrollStateChanged(@NonNull RecyclerView listView, int scrollState) {
            if (scrollState == mPrevScrollState) {
                return;
            }

            switch (scrollState) {
                case RecyclerView.SCROLL_STATE_IDLE:
                    mListScrolling = false;
                    maybeOrderVisiblePages(listView);
                    break;

                case RecyclerView.SCROLL_STATE_SETTLING:
                case RecyclerView.SCROLL_STATE_DRAGGING:
                    mListScrolling = true;
                    break;
            }

            mPrevScrollState = scrollState;

            /*
             * Pauses cache disk fetches if the user is flinging the list, or if their finger is still
             * on the screen.
             */
            ImageFetcher.getInstance(ItemListActivity.this).setPauseWork(scrollState == RecyclerView.SCROLL_STATE_SETTLING ||
                    scrollState == RecyclerView.SCROLL_STATE_DRAGGING);
        }
    }
}
