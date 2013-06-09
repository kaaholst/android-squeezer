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
import uk.org.ngo.squeezer.menu.MenuFragment;
import uk.org.ngo.squeezer.menu.SqueezerMenuFragment;
import uk.org.ngo.squeezer.service.SqueezeService;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;

/**
 * This class defines the common minimum, which any activity browsing the
 * SqueezeServer's database must implement.
 * 
 * @author Kurt Aaholst
 */
public abstract class SqueezerItemListActivity extends SqueezerBaseActivity implements OrderPages {
    private static final String TAG = SqueezerItemListActivity.class.getName();

    /** The list is being actively scrolled by the user */
    private boolean mListScrolling;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MenuFragment.add(this, SqueezerMenuFragment.class);
    };

    /**
     * This is called when the service is connected.
     * <p>
     * You must register a callback for {@link SqueezeService} to call when the ordered items
     * from {@link #orderPage(int)} are received from SqueezeServer. This callback must pass
     * these items on to {@link SqueezerItemListAdapter#update(int, int, int, List)}.
     *
     * @throws RemoteException
     */
	protected abstract void registerCallback() throws RemoteException;

	/**
	 * This is called when the service is disconnected.
	 * @throws RemoteException
	 */
	protected abstract void unregisterCallback() throws RemoteException;

    /**
     * Implementations must start an asynchronous fetch of items, when this is called.
     * @throws RemoteException
     * @param start Position in list to start the fetch. Pass this on to {@link SqueezeService}
     */
	protected abstract void orderPage(int start) throws RemoteException;

	private final Set<Integer> orderedPages = new HashSet<Integer>();

	/**
	 * Order page at specified position, if it has not already been ordered.
	 * @param pagePosition
	 */
	public void maybeOrderPage(int pagePosition) {
        if (!mListScrolling && !orderedPages.contains(pagePosition)) {
			orderedPages.add(pagePosition);
			try {
				orderPage(pagePosition);
			} catch (RemoteException e) {
				Log.e(getTag(), "Error ordering items (" + pagePosition + "): " + e);
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
        private final int mPageSize = getResources().getInteger(R.integer.PageSize);
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
        }

        // Do not use: is not called when the scroll completes, appears to be
        // called multiple time during a scroll, including during flinging.
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
