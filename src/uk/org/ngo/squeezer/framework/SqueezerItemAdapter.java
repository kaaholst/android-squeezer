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

import java.util.Iterator;
import java.util.List;

import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.util.ImageFetcher;
import android.os.RemoteException;
import android.util.SparseArray;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnCreateContextMenuListener;
import android.view.ViewGroup;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.BaseAdapter;


/**
 * A generic class for an adapter to list items of a particular
 * SqueezeServer data type. The data type is defined by the generic type
 * argument, and must be an extension of {@link SqueezerItem}.
 * <p>
 * If you need an adapter for a {@link SqueezerBaseListActivity}, then use
 * {@link SqueezerItemListAdapter} instead.
 * <p>
 * Normally there is no need to extend this (or {@link SqueezerItemListAdapter}),
 * as we delegate all type dependent stuff to {@link SqueezerItemView}.
 *
 * @param <T>
 *            Denotes the class of the items this class should list
 * @see SqueezerItemView
 * @author Kurt Aaholst
 */
public class SqueezerItemAdapter<T extends SqueezerItem> extends BaseAdapter implements
        OnCreateContextMenuListener
{
	/**
	 * View logic for this adapter
	 */
    private final SqueezerItemView<T> mItemView;

	/**
	 * List of items, possibly headed with an empty item.
	 * <p>
	 * As the items are received from SqueezeServer they will be inserted in the
	 * list.
	 */
	private int count;
    private final SparseArray<T[]> pages = new SparseArray<T[]>();

	/**
	 *  This is set if the list shall start with an empty item.
	 */
	private final boolean mEmptyItem;

	/**
	 * Text to display before the items are received from SqueezeServer
	 */
	private final String loadingText;

    /**
     * Number of elements to by fetched at a time
     */
	private int pageSize;

    /** ImageFetcher for thumbnails */
    private ImageFetcher mImageFetcher;

    private OrderPages mOrderPages;

	public int getPageSize() { return pageSize; }

    /**
     * Creates a new adapter. Initially the item list is populated with items
     * displaying the localized "loading" text. Call
     * {@link #update(int, int, int, List)} as items arrives from SqueezeServer.
     * 
     * @param itemView The {@link SqueezerItemView} to use with this adapter
     * @param emptyItem If set the list of items shall start with an empty item
     * @param imageFetcher ImageFetcher to use for loading thumbnails
     */
    public SqueezerItemAdapter(OrderPages orderPages, SqueezerItemView<T> itemView, boolean emptyItem,
            ImageFetcher imageFetcher) {
        mOrderPages = orderPages;
        mItemView = itemView;
        mEmptyItem = emptyItem;
        mImageFetcher = imageFetcher;
		loadingText = itemView.getActivity().getString(R.string.loading_text);
		pageSize = itemView.getActivity().getResources().getInteger(R.integer.PageSize);
		pages.clear();
	}

    /**
     * Calls
     * {@link #SqueezerBaseAdapter(SqueezerItemView, boolean, ImageFetcher)},
     * with emptyItem = false
     */
    public SqueezerItemAdapter(OrderPages orderPages, SqueezerItemView<T> itemView, ImageFetcher imageFetcher) {
        this(orderPages, itemView, false, imageFetcher);
    }

    /**
     * Calls
     * {@link #SqueezerBaseAdapter(SqueezerItemView, boolean, ImageFetcher)},
     * with emptyItem = false and a null ImageFetcher.
     */
    public SqueezerItemAdapter(OrderPages orderPages, SqueezerItemView<T> itemView) {
        this(orderPages, itemView, false, null);
    }

    private int pageNumber(int position) {
		return position / pageSize;
	}

    /**
     * Removes all items from this adapter leaving it empty.
     */
    public void clear() {
        this.count = (mEmptyItem ? 1 : 0);
        pages.clear();
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        T item = getItem(position);
        if (item != null) {
            // XXX: This is ugly -- not all adapters need an ImageFetcher.
            // We should really have subclasses of types in the model classes,
            // with the hierarchy probably being:
            //
            // [basic item] -> [item with artwork] -> [artwork is downloaded]
            //
            // instead of special-casing whether or not mImageFetcher is null
            // in getAdapterView().
            return mItemView.getAdapterView(convertView, item, mImageFetcher);
        }

        return mItemView.getAdapterView(convertView,
                (position == 0 && mEmptyItem ? "" : loadingText));
    }

    public String getQuantityString(int size) {
        return mItemView.getQuantityString(size);
    }

    public SqueezerBaseActivity getActivity() {
        return mItemView.getActivity();
    }

    /**
     * Creates the context menu for the selected item by calling
     * {@link SqueezerItemView.onCreateContextMenu} which the subclass should
     * have specialised.
     * <p>
     * Unpacks the {@link ContextMenu.ContextMenuInfo} passed to this method,
     * and creates a {@link SqueezerItemView.ContextMenuInfo} suitable for
     * passing to subclasses of {@link SqueezerBaseItemView}.
     */
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        AdapterContextMenuInfo adapterMenuInfo = (AdapterContextMenuInfo) menuInfo;
        final T selectedItem = getItem(adapterMenuInfo.position);

        SqueezerItemView.ContextMenuInfo c = new SqueezerItemView.ContextMenuInfo(
                adapterMenuInfo.position, selectedItem, this,
                getActivity().getMenuInflater());

        if (selectedItem != null && selectedItem.getId() != null) {
            mItemView.onCreateContextMenu(menu, v, c);
        }
    }

    public boolean doItemContext(MenuItem menuItem, int position) throws RemoteException {
        return mItemView.doItemContext(menuItem, position, getItem(position));
    }

    public SqueezerItemView<T> getItemView() {
        return mItemView;
    }

	public int getCount() {
		return count;
	}

	private T[] getPage(int position) {
		int pageNumber = pageNumber(position);
		T[] page = pages.get(pageNumber);
		if (page == null) {
			pages.put(pageNumber, page = arrayInstance(pageSize));
		}
		return page;
	}

	private void setItems(int start, List<T> items) {
		T[] page = getPage(start);
		int offset = start % pageSize;
		Iterator<T> it = items.iterator();
		while (it.hasNext()) {
			if (offset >= pageSize) {
				start += offset;
				page = getPage(start);
				offset = 0;
			}
			page[offset++] = it.next();
		}
	}

	public T getItem(int position) {
		T item = getPage(position)[position % pageSize];
		if (item == null) {
			if (mEmptyItem) position--;
			mOrderPages.maybeOrderPage(pageNumber(position) * pageSize);
		}
		return item;
	}

	public long getItemId(int position) {
		return position;
	}

    /**
     * Generates a string suitable for use as an activity's title.
     *
     * @return the title.
     */
	public String getHeader() {
		String item_text = getQuantityString(getCount());
		String header = getActivity().getString(R.string.browse_items_text, item_text, getCount());
		return header;
	}


	/**
	 * Called when the number of items in the list changes.
	 * The default implementation is empty.
	 */
	protected void onCountUpdated() {
	}

	/**
	 * Update the contents of the items in this list.
	 * <p>
	 * The size of the list of items is automatically adjusted if necessary, to obey the given
	 * parameters.
	 *
	 * @param count
	 *            Number of items as reported by squeezeserver.
	 * @param start
	 *            The start position of items in this update.
	 * @param items
	 *            New items to insert in the main list
	 */
	public void update(int count, int start, List<T> items) {
		int offset = (mEmptyItem ? 1 : 0);
		count += offset;
		start += offset;
		if (count == 0 || count != getCount()) {
			this.count = count;
			onCountUpdated();
		}
		setItems(start, items);

		notifyDataSetChanged();
	}

	/**
	 * @param item
	 * @return The position of the given item in this adapter or 0 if not found
	 */
	public int findItem(T item) {
		for (int pos = 0; pos < getCount(); pos++)
			if (getItem(pos) == null) {
				if (item == null) return pos;
			} else
				if (getItem(pos).equals(item)) return pos;
		return 0;
	}

    protected T[] arrayInstance(int size) {
        return mItemView.getCreator().newArray(size);
    }

}
