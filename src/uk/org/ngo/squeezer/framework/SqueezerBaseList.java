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


import java.util.HashSet;
import java.util.List;
import java.util.Set;

import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.util.ImageCache.ImageCacheParams;
import uk.org.ngo.squeezer.util.ImageFetcher;
import android.content.res.Resources;
import android.os.Build;
import android.os.RemoteException;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AbsListView.RecyclerListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;

/**
 * A generic base class to list items of a particular
 * SqueezeServer data type. The data type is defined by the generic type
 * argument, and must be an extension of {@link SqueezerItem}. You must provide
 * an {@link SqueezerItemView} to provide the view logic used by this activity.
 * This is done by implementing
 * {@link SqueezerItemListActivity#createItemView()}.
 * <p>
 *
 * @param <T> Denotes the class of the items this class should list
 * @author Kurt Aaholst
 */
public abstract class SqueezerBaseList<T extends SqueezerItem> implements OrderPages {
    private static String TAG = SqueezerBaseList.class.getSimpleName();

    protected SqueezerBaseActivity mActivity;
    private ListView mListView;
	private SqueezerItemAdapter<T> itemAdapter;

    /** Progress bar (spinning) while items are loading. */
    private ProgressBar loadingProgress;
	private SqueezerItemView<T> mItemView;

    /** An ImageFetcher for loading thumbnails. */
    protected ImageFetcher mImageFetcher;

    /** ImageCache parameters for the album art. */
    private ImageCacheParams mImageCacheParams;

    protected abstract void registerCallback() throws RemoteException;
    protected abstract void unregisterCallback() throws RemoteException;
    protected abstract void orderPage(int start) throws RemoteException;

    /** The list is being actively scrolled by the user */
    private boolean mListScrolling;

    private final Set<Integer> orderedPages = new HashSet<Integer>();

    /**
     * Order page at specified position, if it has not already been ordered.
     * @param pagePosition
     */
    @Override
    public void maybeOrderPage(int pagePosition) {
        if (!mListScrolling && !orderedPages.contains(pagePosition)) {
            orderedPages.add(pagePosition);
            try {
                orderPage(pagePosition);
            } catch (RemoteException e) {
                Log.e(TAG, "Error ordering items (" + pagePosition + "): " + e);
            }
        }
    }

    /**
     * Clear all information about which pages has been ordered, and reorder the first page
     */
    public void reorderItems() {
        orderedPages.clear();
        maybeOrderPage(0);
    }
    
	public void create(SqueezerBaseActivity activity, ListView listView, ProgressBar progressBar, SqueezerItemView<T> itemView) {
	    mActivity = activity;
		mListView = listView;
        loadingProgress = progressBar;
        mItemView = itemView;

        mListView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // XXX: Adapter should implement onItemClickListener and pass
                // this down to the views.
    			T item = getItemAdapter().getItem(position);
    			if (item != null && item.getId() != null) {
    	   			try {
                        // XXX: Why does this need to be itemView?
                        // Using "view" should suffice.
    					mItemView.onItemSelected(position, item);
    	            } catch (RemoteException e) {
    	                Log.e(TAG, "Error from default action for '" + item + "': " + e);
    	            }
    			}
    		}
    	});

        mListView.setOnScrollListener(new ScrollListener());

        mListView.setRecyclerListener(new RecyclerListener() {
            @Override
            public void onMovedToScrapHeap(View view) {
                // Release strong reference when a view is recycled
                final ImageView imageView = (ImageView) view.findViewById(R.id.icon);
                if (imageView != null) {
                    imageView.setImageBitmap(null);
                }
            }
        });

        // Get an ImageFetcher to scale artwork to the size of the icon view.
        Resources resources = mActivity.getResources();
        int iconSize = (Math.max(
                resources.getDimensionPixelSize(R.dimen.album_art_icon_height),
                resources.getDimensionPixelSize(R.dimen.album_art_icon_width)));
        mImageFetcher = new ImageFetcher(mActivity, iconSize);
        mImageFetcher.setLoadingImage(R.drawable.icon_pending_artwork);
        mImageCacheParams = new ImageCacheParams(mActivity, "artwork");
        mImageCacheParams.setMemCacheSizePercent(mActivity, 0.12f);

        // Delegate context menu creation to the adapter.
        mListView.setOnCreateContextMenuListener(getItemAdapter());

        try {
            registerCallback();
        } catch (RemoteException e) {
            Log.e(TAG, "Error in registerCallback: ", e);
        }
        orderItems();
        mImageFetcher.addImageCache(mActivity.getSupportFragmentManager(), mImageCacheParams);
	}

    public ImageFetcher getImageFetcher() {
        return mImageFetcher;
    }


    public void close() {
        mImageFetcher.closeCache();

        if (mActivity.getService() != null) {
        	try {
				unregisterCallback();
			} catch (RemoteException e) {
                Log.e(TAG, "Error unregistering list callback: " + e);
			}
        }
    }


    public SqueezerItemView<T> getItemView() {
        return mItemView;
    }

    /**
     * @return The current {@link SqueezerItemAdapter}, creating it if
     *         necessary.
     */
    public SqueezerItemAdapter<T> getItemAdapter() {
        return itemAdapter == null ? (itemAdapter = createItemListAdapter(getItemView()))
                : itemAdapter;
    }

	/**
	 * @return The {@link ListView} used by this activity
	 */
    public ListView getListView() {
        return mListView;
    }


    protected SqueezerItemAdapter<T> createItemListAdapter(SqueezerItemView<T> itemView) {
        return new SqueezerItemListAdapter<T>(this, itemView, mImageFetcher);
	}

	/**
	 * Order items from the start, and prepare an adapter to receive them
	 * @throws RemoteException
	 */
	public void orderItems() {
		reorderItems();
        mListView.setVisibility(View.GONE);
        loadingProgress.setVisibility(View.VISIBLE);
		clearItemListAdapter();
	}

	public void onItemsReceived(final int count, final int start, final List<T> items) {
		mActivity.getUIThreadHandler().post(new Runnable() {
            @Override
            public void run() {
                mListView.setVisibility(View.VISIBLE);
                loadingProgress.setVisibility(View.GONE);
				getItemAdapter().update(count, start, items);
			}
		});
	}

	/**
	 * Set the adapter to handle the display of the items, see also {@link #setListAdapter(android.widget.ListAdapter)}
	 * @param listAdapter
	 */
	private void clearItemListAdapter() {
        mListView.setAdapter(getItemAdapter());
	}

    /**
     * Tracks scrolling activity.
     * <p>
     * When the list is idle, new pages of data are fetched from the server.
     * <p>
     * Use a TouchListener to work around an Android bug where SCROLL_STATE_IDLE
     * messages are not delivered after SCROLL_STATE_TOUCH_SCROLL messages. *
     */
    protected class ScrollListener implements AbsListView.OnScrollListener {
        private TouchListener mTouchListener = null;
        private final int mPageSize = mActivity.getResources().getInteger(R.integer.PageSize);
        private boolean mAttachedTouchListener = false;

        private int mPrevScrollState = OnScrollListener.SCROLL_STATE_IDLE;

        /**
         * Sets up the TouchListener.
         * <p>
         * Subclasses must call this.
         */
        public ScrollListener() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR &&
                    Build.VERSION.SDK_INT <= Build.VERSION_CODES.FROYO) {
                mTouchListener = new TouchListener(this);
            }
        }

        /**
         * Pauses cache disk fetches if the user is flinging the list, or if
         * their finger is still on the screen.
         */
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

                    int pos = (listView.getFirstVisiblePosition() / mPageSize) * mPageSize;
                    int end = listView.getFirstVisiblePosition() + listView.getChildCount();

                    while (pos < end) {
                        maybeOrderPage(pos);
                        pos += mPageSize;
                    }

                    break;

                case OnScrollListener.SCROLL_STATE_FLING:
                case OnScrollListener.SCROLL_STATE_TOUCH_SCROLL:
                    mListScrolling = true;
                    break;
            }

            mPrevScrollState = scrollState;

            if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_FLING ||
                    scrollState == AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) {
                mImageFetcher.setPauseWork(true);
            } else {
                mImageFetcher.setPauseWork(false);
            }
        }

        // Do not use: is not called when the scroll completes, appears to be
        // called multiple time during a scroll, including during flinging.
        @Override
        public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
                int totalItemCount) {
        }

        /**
         * Work around a bug in (at least) API levels 7 and 8.
         * <p>
         * The bug manifests itself like so: after completing a TOUCH_SCROLL the
         * system does not deliver a SCROLL_STATE_IDLE message to any attached
         * listeners.
         * <p>
         * In addition, if the user does TOUCH_SCROLL, IDLE, TOUCH_SCROLL you
         * would expect to receive three messages. You don't -- you get the
         * first TOUCH_SCROLL, no IDLE message, and then the second touch
         * doesn't generate a second TOUCH_SCROLL message.
         * <p>
         * This state clears when the user flings the list.
         * <p>
         * The simplest work around for this app is to track the user's finger,
         * and if the previous state was TOUCH_SCROLL then pretend that they
         * finished with a FLING and an IDLE event was triggered. This serves to
         * unstick the message pipeline.
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
