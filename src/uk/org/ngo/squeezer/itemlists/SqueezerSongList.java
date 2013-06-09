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

import java.util.List;

import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.framework.SqueezerBaseActivity;
import uk.org.ngo.squeezer.framework.SqueezerBaseList;
import uk.org.ngo.squeezer.itemlists.dialogs.SqueezerSongOrderDialog.SongsSortOrder;
import uk.org.ngo.squeezer.model.SqueezerAlbum;
import uk.org.ngo.squeezer.model.SqueezerSong;
import android.os.RemoteException;
import android.view.View;
import android.widget.ListView;
import android.widget.ProgressBar;

public class SqueezerSongList extends SqueezerBaseList<SqueezerSong> {

    private SqueezerAlbum mAlbum;
    private SqueezerSongView songViewLogic;

	public SqueezerSongList(SqueezerBaseActivity activity, View pane, SqueezerAlbum album) {
        ListView listView = (ListView) pane.findViewById(R.id.item_list);
        ProgressBar progressBar = (ProgressBar) pane.findViewById(R.id.loading_progress);
	    mAlbum = album;
	    songViewLogic = new SqueezerSongView(activity);
	    create(activity, listView, progressBar, songViewLogic);
	}
	

    @Override
    protected void registerCallback() throws RemoteException {
        mActivity.getService().registerSongListCallback(songListCallback);
    }

    @Override
    protected void unregisterCallback() throws RemoteException {
        mActivity.getService().unregisterSongListCallback(songListCallback);
    }

    private final IServiceSongListCallback songListCallback = new IServiceSongListCallback.Stub() {
        @Override
        public void onSongsReceived(int count, int start, List<SqueezerSong> items) {
            onItemsReceived(count, start, items);
        }
    };

    @Override
    protected void orderPage(int start) throws RemoteException {
        mActivity.getService().songs(start, SongsSortOrder.title.name(), null, mAlbum, null, null, null);
    }
}
