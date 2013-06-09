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

package uk.org.ngo.squeezer.itemlists;


import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.framework.SqueezerArtworkItem;
import uk.org.ngo.squeezer.framework.SqueezerBaseActivity;
import uk.org.ngo.squeezer.framework.SqueezerBaseItemView;
import uk.org.ngo.squeezer.framework.SqueezerItem;
import uk.org.ngo.squeezer.service.ISqueezeService;
import uk.org.ngo.squeezer.util.ImageFetcher;
import android.os.RemoteException;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * Represents the view hierarchy for a single {@link SqueezerItem} subclass.
 * where the item has track artwork associated with it.
 * 
 * @param <T>
 */
public abstract class SqueezerAlbumArtView<T extends SqueezerArtworkItem> extends
        SqueezerBaseItemView<T> {
    LayoutInflater mLayoutInflater;

    public SqueezerAlbumArtView(SqueezerBaseActivity activity) {
		super(activity);
        mLayoutInflater = activity.getLayoutInflater();
	}

    @Override
    public View getAdapterView(View convertView, T item, ImageFetcher imageFetcher) {
        if (imageFetcher == null) {
            return super.getAdapterView(convertView, item, imageFetcher);
        }

        View view = getAdapterView(convertView);
        ViewHolder viewHolder = (ViewHolder) view.getTag();

        viewHolder.text1.setText(item.getName());

        viewHolder.btnContextMenu.setVisibility(View.VISIBLE);
        viewHolder.btnContextMenu.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                v.showContextMenu();
            }
        });

        bindView(view, item, imageFetcher);
        return view;
    }

    abstract protected void bindView(View view, T item, ImageFetcher imageFetcher);

    @Override
    public View getAdapterView(View convertView, String label) {
        View view = getAdapterView(convertView);
        ViewHolder viewHolder = (ViewHolder) view.getTag();

        viewHolder.icon.setImageResource(R.drawable.icon_pending_artwork);
        viewHolder.btnContextMenu.setVisibility(View.GONE);
        viewHolder.text1.setText(label);
        viewHolder.text2.setText("");

        return view;
    }

    private View getAdapterView(View convertView) {
        ViewHolder viewHolder = (convertView != null && convertView.getTag().getClass() == ViewHolder.class)
                ? (ViewHolder) convertView.getTag()
                : null;

        if (viewHolder == null) {
            convertView = mLayoutInflater.inflate(R.layout.icon_two_line, null);
            viewHolder = new ViewHolder();
            viewHolder.text1 = (TextView) convertView.findViewById(R.id.text1);
            viewHolder.text2 = (TextView) convertView.findViewById(R.id.text2);
            viewHolder.icon = (ImageView) convertView.findViewById(R.id.icon);
            viewHolder.btnContextMenu = (ImageButton) convertView.findViewById(R.id.context_menu);
            convertView.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) convertView.getTag();
        }

        return convertView;
    }

    /**
     * Returns the URL to download the specified album artwork, or null if the
     * artwork does not exist, or there was a problem with the service.
     * 
     * @param artwork_track_id
     * @return
     */
    protected String getAlbumArtUrl(String artwork_track_id) {
		if (artwork_track_id == null)
			return null;

		ISqueezeService service = getActivity().getService();
		if (service == null)
			return null;

		try {
			return service.getAlbumArtUrl(artwork_track_id);
		} catch (RemoteException e) {
			Log.e(getClass().getSimpleName(), "Error requesting album art url: " + e);
			return null;
		}
	}

    public static class ViewHolder extends SqueezerBaseItemView.ViewHolder {
        public TextView text2;
        // XXX: These are public so code in SqueezerItemListActivity can see
        // them. This should be refactored.
        public ImageView icon;
    }
}
