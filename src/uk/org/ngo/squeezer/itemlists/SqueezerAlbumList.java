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
import uk.org.ngo.squeezer.itemlists.dialogs.SqueezerAlbumOrderDialog.AlbumsSortOrder;
import uk.org.ngo.squeezer.model.SqueezerAlbum;
import android.os.RemoteException;
import android.view.View;
import android.widget.ListView;
import android.widget.ProgressBar;


public class SqueezerAlbumList extends SqueezerBaseList<SqueezerAlbum> {

    public SqueezerAlbumList(SqueezerBaseActivity activity, View pane, final View songsPane) {
        ListView listView = (ListView) pane.findViewById(R.id.item_list);
        ProgressBar progressBar = (ProgressBar) pane.findViewById(R.id.loading_progress);
        create(activity, listView, progressBar, new SqueezerAlbumView(activity) {
            @Override
            public void onItemSelected(int index, SqueezerAlbum item) throws RemoteException {
                new SqueezerSongList(getActivity(), songsPane, item);
            }
        });
    }

	@Override
    protected void registerCallback() throws RemoteException {
		mActivity.getService().registerAlbumListCallback(albumListCallback);
	}

	@Override
    protected void unregisterCallback() throws RemoteException {
		mActivity.getService().unregisterAlbumListCallback(albumListCallback);
	}

    @Override
    protected void orderPage(int start) throws RemoteException {
        mActivity.getService().albums(start, AlbumsSortOrder.album.name().replace("__", ""), null, null,
                null, null, null);
    }


    private final IServiceAlbumListCallback albumListCallback = new IServiceAlbumListCallback.Stub() {
		@Override
        public void onAlbumsReceived(int count, int start, List<SqueezerAlbum> items) throws RemoteException {
		    onItemsReceived(count, start, items);
		}
    };

}
