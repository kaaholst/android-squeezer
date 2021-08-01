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
import android.widget.LinearLayout;

import androidx.annotation.CallSuper;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.Stack;

import uk.org.ngo.squeezer.Preferences;
import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.itemlist.dialog.ArtworkListLayout;
import uk.org.ngo.squeezer.model.Item;
import uk.org.ngo.squeezer.service.ISqueezeService;
import uk.org.ngo.squeezer.service.SqueezeService;
import uk.org.ngo.squeezer.service.event.ActivePlayerChanged;
import uk.org.ngo.squeezer.service.event.HandshakeComplete;
import uk.org.ngo.squeezer.util.RetainFragment;

/**
 * This class defines the common minimum, which any activity browsing the SqueezeServer's database
 * must implement.
 *
 * @author Kurt Aaholst
 */
public abstract class ItemListActivity extends BaseActivity {

    private static final String TAG = ItemListActivity.class.getName();

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
     * Progress bar (spinning) while items are loading.
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

    /**
     * Tag for mReceivedPages in mRetainFragment.
     */
    private static final String TAG_RECEIVED_PAGES = "mReceivedPages";

    /**
     * Fragment to retain information across the activity lifecycle.
     */
    private RetainFragment mRetainFragment;

    @Override
    public void setContentView(int layoutResID) {
        LinearLayout fullLayout = (LinearLayout) getLayoutInflater().inflate(R.layout.item_list_activity_layout, findViewById(R.id.activity_layout));
        subActivityContent = fullLayout.findViewById(R.id.content_frame);
        getLayoutInflater().inflate(layoutResID, subActivityContent, true); // Places the activity layout inside the activity content frame.
        super.setContentView(fullLayout);

        loadingProgress = Objects.requireNonNull((View) findViewById(R.id.loading_label),
                "activity layout did not return a view containing R.id.loading_label");

        emptyView = Objects.requireNonNull((View) findViewById(R.id.empty_view),
                "activity layout did not return a view containing R.id.empty_view");

        listView = Objects.requireNonNull(subActivityContent.findViewById(R.id.item_list),
                "getContentView() did not return a view containing R.id.item_list");
        listView.setLayoutManager(new LinearLayoutManager(this));
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

        mRetainFragment = RetainFragment.getInstance(TAG, getSupportFragmentManager());
        setContentView(getContentView());

        mReceivedPages = getRetainedValue(TAG_RECEIVED_PAGES);
        if (mReceivedPages == null) {
            mReceivedPages = new HashSet<>();
            putRetainedValue(TAG_RECEIVED_PAGES, mReceivedPages);
        }
    }

    protected <T> T getRetainedValue(String key) {
        return (T) mRetainFragment.get(key);
    }

    protected Object putRetainedValue(String key, Object value) {
        return mRetainFragment.put(key, value);
    }

    @Override
    public void onPause() {
        super.onPause();

        // Any items coming in after callbacks have been unregistered are discarded.
        // We cancel any outstanding orders, so items can be reordered after the
        // activity resumes.
        cancelOrders();
    }

    private void showLoading() {
        subActivityContent.setVisibility(View.GONE);
        loadingProgress.setVisibility(View.VISIBLE);
        emptyView.setVisibility(View.GONE);
    }

    void showEmptyView() {
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
     * @return True if the LMS command issued by {@link #orderPage(ISqueezeService, int)} requires a player
     */
    protected abstract boolean needPlayer();

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
        return new Preferences(this).getAlbumListLayout();
    }

    /**
     * @return The view listing the items for this acitvity
     */
    public final RecyclerView getListView() {
        return listView;
    }

    /**
     * List can clear any information about which items have been received and ordered, by calling
     * {@link #clearAndReOrderItems()}. This will call back to this method, which must clear any
     * adapters holding items.
     */
    protected abstract void clearItemAdapter();

    /**
     * Call back from {@link #onItemsReceived(int, int, List, Class)}
     */
     protected abstract <T extends Item> void updateAdapter(int count, int start, List<T> items, Class<T> dataType);

    /**
     * Orders a page worth of data, starting at the specified position, if it has not already been
     * ordered, and if the service is connected and the handshake has completed.
     *
     * @param pagePosition position in the list to start the fetch.
     * @return True if the page needed to be ordered (even if the order failed), false otherwise.
     */
    public boolean maybeOrderPage(int pagePosition) {
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
            return true;
        } else {
            return false;
        }
    }

    /**
     * Update the UI with the player change
     */
    @MainThread
    public void onEventMainThread(ActivePlayerChanged event) {
        Log.i(TAG, "ActivePlayerChanged: " + event.player);
        supportInvalidateOptionsMenu();
        if (needPlayer()) {
            if (event.player == null) {
                showEmptyView();
            } else {
                clearAndReOrderItems();
            }
        }
    }

    /**
     * Orders any pages requested before the handshake completed.
     */
    @MainThread
    public void onEventMainThread(HandshakeComplete event) {
        // Order any pages that were requested before the handshake complete.
        while (!mOrderedPagesBeforeHandshake.empty()) {
            maybeOrderPage(mOrderedPagesBeforeHandshake.pop());
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
     * This will call back to {@link #updateAdapter(int, int, List, Class)} on the UI thread
     *
     * @param count The total number of items known by the server.
     * @param start The start position of this update.
     * @param items The items received in this update
     */
    @CallSuper
    protected <T extends Item> void onItemsReceived(final int count, final int start, final List<T> items, final Class<T> dataType) {
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
            updateAdapter(count, start, items, dataType);
        });
    }

    /**
     * Empties the variables that track which pages have been requested, and orders page 0.
     */
    public void clearAndReOrderItems() {
        if (!(needPlayer() && getService().getActivePlayer() == null)) {
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

    /**
     * Tracks scrolling activity.
     * <p>
     * When the list is idle, new pages of data are fetched from the server.
     */
    protected class ScrollListener extends RecyclerView.OnScrollListener {

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
        }
    }
}
