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


import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.menu.BaseMenuFragment;
import uk.org.ngo.squeezer.menu.MenuFragment;
import uk.org.ngo.squeezer.service.SqueezeService;
import uk.org.ngo.squeezer.util.ImageCache;
import uk.org.ngo.squeezer.util.ImageFetcher;
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
     * Keep track of whether callbacks have been registered
     */
    private boolean mRegisteredCallbacks;

    /**
     * The number of items per page.
     */
    private int mPageSize;

    /**
     * The pages that have been requested from the server.
     */
    private Set<Integer> mOrderedPages = new HashSet<Integer>();

    /**
     * The pages that have been received from the server
     */
    private Set<Integer> mReceivedPages;

    /**
     * Tag for mReceivedPages in mRetainFragment.
     */
    private static final String TAG_RECEIVED_PAGES = "mReceivedPages";

    /**
     * An ImageFetcher for loading thumbnails.
     */
    private ImageFetcher mImageFetcher;

    /**
     * Tag for _mImageFetcher in mRetainFragment.
     */
    public static final String TAG_IMAGE_FETCHER = "imageFetcher";

    /**
     * ImageCache parameters for the album art.
     */
    private ImageCache.ImageCacheParams mImageCacheParams;

    /* Fragment to retain information across orientation changes. */
    private RetainFragment mRetainFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPageSize = getResources().getInteger(R.integer.PageSize);

        BaseMenuFragment.add(this, MenuFragment.class);

        mRetainFragment = RetainFragment.getInstance(TAG, getSupportFragmentManager());

        //noinspection unchecked
        mReceivedPages = (Set<Integer>) mRetainFragment.get(TAG_RECEIVED_PAGES);
        if (mReceivedPages == null) {
            mReceivedPages = new HashSet<Integer>();
            mRetainFragment.put(TAG_RECEIVED_PAGES, mReceivedPages);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        getImageFetcher().addImageCache(getSupportFragmentManager(), mImageCacheParams);

        if (getService() != null) {
            maybeRegisterCallbacks();
        }
    }

    @Override
    public void onPause() {
        if (mImageFetcher != null) {
            mImageFetcher.closeCache();
        }

        if (mRegisteredCallbacks) {
            if (getService() != null) {
                try {
                    unregisterCallback();
                } catch (RemoteException e) {
                    Log.e(getTag(), "Error unregistering list callback: " + e);
                }
            }
            mRegisteredCallbacks = false;
        }

        // Any items coming in after callbacks have been unregistered are discarded.
        // We cancel any outstanding orders, so items can be reordered after the
        // activity resumes.
        cancelOrders();

        super.onPause();
    }

    @Override
    protected void onServiceConnected() {
        maybeRegisterCallbacks();
    }

    /**
     * This is called when the service is first connected, and whenever the activity is resumed.
     */
    private void maybeRegisterCallbacks() {
        if (!mRegisteredCallbacks) {
            try {
                registerCallback();
            } catch (RemoteException e) {
                Log.e(getTag(), "Error registering list callback: " + e);
            }
            mRegisteredCallbacks = true;
        }
    }

    /**
     * This is called when the service is connected.
     * <p/>
     * You must register a callback for {@link SqueezeService} to call when the ordered items from
     * {@link #orderPage(int)} are received from SqueezeServer. This callback must pass these items
     * on to {@link ItemListAdapter#update(int, int, List)}.
     *
     * @throws RemoteException
     */
    protected abstract void registerCallback() throws RemoteException;

    /**
     * This is called when the service is disconnected.
     *
     * @throws RemoteException
     */
    protected abstract void unregisterCallback() throws RemoteException;

    protected ImageFetcher createImageFetcher(int height, int width) {
        // Get an ImageFetcher to scale artwork to the supplied size.
        int iconSize = (Math.max(height, width));
        ImageFetcher imageFetcher = new ImageFetcher(this, iconSize);
        imageFetcher.setLoadingImage(R.drawable.icon_pending_artwork);
        return imageFetcher;
    }

    protected ImageFetcher createImageFetcher() {
        // Get an ImageFetcher to scale artwork to the size of the icon view.
        Resources resources = getResources();
        return createImageFetcher(
                resources.getDimensionPixelSize(R.dimen.album_art_icon_height),
                resources.getDimensionPixelSize(R.dimen.album_art_icon_width));
    }

    protected void createImageCacheParams() {
        mImageCacheParams = new ImageCache.ImageCacheParams(this, "artwork");
        mImageCacheParams.setMemCacheSizePercent(this, 0.12f);
    }

    public ImageFetcher getImageFetcher() {
        if (mImageFetcher == null) {
            mImageFetcher = (ImageFetcher) mRetainFragment.get(TAG_IMAGE_FETCHER);
            if (mImageFetcher == null) {
                mImageFetcher = createImageFetcher();
                createImageCacheParams();
                mRetainFragment.put(TAG_IMAGE_FETCHER, mImageFetcher);
            }
        }

        return mImageFetcher;
    }


    /**
     * Implementations must start an asynchronous fetch of items, when this is called.
     *
     * @param start Position in list to start the fetch. Pass this on to {@link
     * uk.org.ngo.squeezer.service.SqueezeService}
     *
     * @throws RemoteException
     */
    protected abstract void orderPage(int start) throws RemoteException;

    /**
     * List can clear any information about which items have been received and ordered, by calling
     * {@link #clearAndReOrderItems()}. This will call back to this method, which must clear any
     * adapters holding items.
     */
    protected abstract void clearItemAdapter();

    /**
     * Order a page worth of data, starting at the specified position, if it has not already been
     * ordered.
     *
     * @param pagePosition position in the list to start the fetch.
     *
     * @return True if the page needed to be ordered (even if the order failed), false otherwise.
     */
    public boolean maybeOrderPage(int pagePosition) {
        if (!mListScrolling && !mReceivedPages.contains(pagePosition) && !mOrderedPages
                .contains(pagePosition)) {
            mOrderedPages.add(pagePosition);
            try {
                orderPage(pagePosition);
            } catch (RemoteException e) {
                mOrderedPages.remove(pagePosition);
                Log.e(getTag(), "Error ordering items (" + pagePosition + "): " + e);
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * Orders pages that correspond to visible rows in the listview.
     * <p/>
     * Computes the pages that correspond to the rows that are currently being displayed by the
     * listview, and calls {@link #maybeOrderPage(int)} to fetch the page if necessary.
     *
     * @param listView The listview with visible rows.
     */
    public void maybeOrderVisiblePages(AbsListView listView) {
        int pos = (listView.getFirstVisiblePosition() / mPageSize) * mPageSize;
        int end = listView.getFirstVisiblePosition() + listView.getChildCount();

        while (pos <= end) {
            maybeOrderPage(pos);
            pos += mPageSize;
        }
    }

    /**
     * Tracks items that have been received from the server.
     * <p/>
     * Subclasses <b>must</b> call this method when receiving data from the server to ensure that
     * internal bookkeeping about pages that have/have not been ordered is kept consistent.
     *
     * @param count The total number of items known by the server.
     * @param start The start position of this update.
     * @param size The number of items in this update
     */
    protected void onItemsReceived(final int count, final int start, int size) {
        Log.d(getTag(), "onItemsReceived(" + count + ", " + start + ", " + size + ")");

        // Add this page of data to mReceivedPages and remove from mOrderedPages.
        // Because we might receive a page in chunks, we test for the end of a page,
        // before we register the page as being received.
        if (((start + size) % mPageSize == 0) || (start + size == count)) {
            int pageStart = (start + size == count) ? start : start + size - mPageSize;
            mReceivedPages.add(pageStart);
            mOrderedPages.remove(pageStart);
        }
    }

    /**
     * Empties the variables that track which pages have been requested, and orders page 0.
     */
    public void clearAndReOrderItems() {
        mOrderedPages.clear();
        mReceivedPages.clear();
        maybeOrderPage(0);
        clearItemAdapter();
    }

    /**
     * Removes any outstanding requests from mOrderedPages.
     */
    private void cancelOrders() {
        if (mRegisteredCallbacks) {
            throw new IllegalStateException(
                    "Cannot call cancelOrders with mRegisteredCallbacks == true");
        }

        mOrderedPages.clear();
    }

    /**
     * Tracks scrolling activity.
     * <p/>
     * When the list is idle, new pages of data are fetched from the server.
     * <p/>
     * Use a TouchListener to work around an Android bug where SCROLL_STATE_IDLE messages are not
     * delivered after SCROLL_STATE_TOUCH_SCROLL messages.
     */
    protected class ScrollListener implements AbsListView.OnScrollListener {

        private TouchListener mTouchListener = null;

        private boolean mAttachedTouchListener = false;

        private int mPrevScrollState = OnScrollListener.SCROLL_STATE_IDLE;

        /**
         * Sets up the TouchListener.
         * <p/>
         * Subclasses must call this.
         */
        public ScrollListener() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR &&
                    Build.VERSION.SDK_INT <= Build.VERSION_CODES.FROYO) {
                mTouchListener = new TouchListener(this);
            }
        }

        @Override
        public void onScrollStateChanged(AbsListView listView, int scrollState) {
            if (scrollState == mPrevScrollState) {
                return;
            }

            if (mAttachedTouchListener == false) {
                if (mTouchListener != null) {
                    listView.setOnTouchListener(mTouchListener);
                }
                mAttachedTouchListener = true;
            }

            switch (scrollState) {
                case OnScrollListener.SCROLL_STATE_IDLE:
                    mListScrolling = false;
                    maybeOrderVisiblePages(listView);
                    break;

                case OnScrollListener.SCROLL_STATE_FLING:
                case OnScrollListener.SCROLL_STATE_TOUCH_SCROLL:
                    mListScrolling = true;
                    break;
            }

            mPrevScrollState = scrollState;
        }

        // Do not use: is not called when the scroll completes, appears to be
        // called multiple time during a scroll, including during flinging.
        @Override
        public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
                int totalItemCount) {
        }

        /**
         * Work around a bug in (at least) API levels 7 and 8.
         * <p/>
         * The bug manifests itself like so: after completing a TOUCH_SCROLL the system does not
         * deliver a SCROLL_STATE_IDLE message to any attached listeners.
         * <p/>
         * In addition, if the user does TOUCH_SCROLL, IDLE, TOUCH_SCROLL you would expect to
         * receive three messages. You don't -- you get the first TOUCH_SCROLL, no IDLE message, and
         * then the second touch doesn't generate a second TOUCH_SCROLL message.
         * <p/>
         * This state clears when the user flings the list.
         * <p/>
         * The simplest work around for this app is to track the user's finger, and if the previous
         * state was TOUCH_SCROLL then pretend that they finished with a FLING and an IDLE event was
         * triggered. This serves to unstick the message pipeline.
         */
        protected class TouchListener implements View.OnTouchListener {

            private final OnScrollListener mOnScrollListener;

            public TouchListener(OnScrollListener onScrollListener) {
                mOnScrollListener = onScrollListener;
            }

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                final int action = event.getAction();
                boolean mFingerUp = action == MotionEvent.ACTION_UP
                        || action == MotionEvent.ACTION_CANCEL;
                if (mFingerUp && mPrevScrollState == OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) {
                    Log.v(TAG, "Sending special scroll state bump");
                    mOnScrollListener.onScrollStateChanged((AbsListView) view,
                            OnScrollListener.SCROLL_STATE_FLING);
                    mOnScrollListener.onScrollStateChanged((AbsListView) view,
                            OnScrollListener.SCROLL_STATE_IDLE);
                }
                return false;
            }
        }
    }
}
