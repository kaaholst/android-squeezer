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

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Map;

import uk.org.ngo.squeezer.itemlist.IServiceItemListCallback;
import uk.org.ngo.squeezer.model.Item;
import uk.org.ngo.squeezer.service.event.HandshakeComplete;
import uk.org.ngo.squeezer.util.ImageFetcher;


/**
 * A generic base class for an activity to list items of a particular SqueezeServer data type. The
 * data type is defined by the generic type argument, and must be an extension of {@link Item}. You
 * must provide an {@link ItemAdapter} to provide the view logic used by this activity. This is done by
 * implementing {@link #createItemListAdapter()}}.
 * <p>
 * When the activity is first created ({@link #onCreate(Bundle)}), an empty {@link ItemAdapter}
 * is created. See {@link ItemListActivity} for see details of
 * ordering and receiving of list items from SqueezeServer.
 *
 * @param <T> Denotes the class of the items this class should list
 *
 * @author Kurt Aaholst
 */
public abstract class BaseListActivity<VH extends ItemViewHolder<T>, T extends Item> extends ItemListActivity implements IServiceItemListCallback<T> {
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
        super.setContentView(layoutResID);

        getListView().addOnScrollListener(new ScrollListener());

        setupAdapter(getListView());
    }

    @MainThread
    public void onEventMainThread(HandshakeComplete event) {
        Log.d("BaseLitActivity", "Handshake complete");
        super.onEventMainThread(event);
        if (!needPlayer() || requireService().getActivePlayer() != null) {
            maybeOrderVisiblePages(getListView());
        } else {
            showEmptyView();
        }
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

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        saveVisiblePosition();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (itemAdapter != null) {
            itemAdapter.setActivity(null);
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

    @Override
    protected void clearItemAdapter() {
        getItemAdapter().clear();
    }

    protected abstract ItemAdapter<VH, T> createItemListAdapter();

    @Override
    @SuppressWarnings("unchecked")
    protected <IT extends Item> void updateAdapter(int count, int start, List<IT> items, Class<IT> dataType) {
        getItemAdapter().update(count, start, (List<T>) items);
    }

    @Override
    public void onItemsReceived(int count, int start, Map<String, Object> parameters, List<T> items, Class<T> dataType) {
        super.onItemsReceived(count, start, items, dataType);
    }

    @Override
    public Object getClient() {
        return this;
    }

    protected class ScrollListener extends ItemListActivity.ScrollListener {

        /**
         * Pauses cache disk fetches if the user is flinging the list, or if their finger is still
         * on the screen.
         */
        @Override
        public void onScrollStateChanged(@NonNull RecyclerView listView, int scrollState) {
            super.onScrollStateChanged(listView, scrollState);

            ImageFetcher.getInstance(BaseListActivity.this).setPauseWork(scrollState == RecyclerView.SCROLL_STATE_SETTLING ||
                    scrollState == RecyclerView.SCROLL_STATE_DRAGGING);
        }
    }
}
