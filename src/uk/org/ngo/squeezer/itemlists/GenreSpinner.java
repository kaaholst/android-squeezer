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

import uk.org.ngo.squeezer.Util;
import uk.org.ngo.squeezer.framework.SqueezerItemAdapter;
import uk.org.ngo.squeezer.framework.SqueezerItemListActivity;
import uk.org.ngo.squeezer.model.SqueezerGenre;
import uk.org.ngo.squeezer.service.ISqueezeService;
import uk.org.ngo.squeezer.util.ImageFetcher;
import android.os.Handler;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.widget.Spinner;

public class GenreSpinner {
	private static final String TAG = GenreSpinner.class.getName();
	GenreSpinnerCallback callback;
	private final SqueezerItemListActivity activity;
	private final Spinner spinner;

	public GenreSpinner(GenreSpinnerCallback callback, SqueezerItemListActivity activity, Spinner spinner) {
		this.callback = callback;
		this.activity = activity;
		this.spinner = spinner;
		registerCallback();
		orderItems(0);
	}

	private void orderItems(int start) {
		if (callback.getService() != null) {
			try {
				callback.getService().genres(start);
			} catch (RemoteException e) {
                Log.e(TAG, "Error ordering items: " + e);
			}
		}
	}

	public void registerCallback() {
		if (callback.getService() != null) {
			try {
				callback.getService().registerGenreListCallback(genreListCallback);
			} catch (RemoteException e) {
                Log.e(TAG, "Error registering callback: " + e);
			}
		}
	}

	public void unregisterCallback() {
		if (callback.getService() != null) {
			try {
				callback.getService().unregisterGenreListCallback(genreListCallback);
			} catch (RemoteException e) {
                Log.e(TAG, "Error unregistering callback: " + e);
			}
		}
	}

    private final IServiceGenreListCallback genreListCallback = new IServiceGenreListCallback.Stub() {
		private SqueezerItemAdapter<SqueezerGenre> adapter;

		public void onGenresReceived(final int count, final int start, final List<SqueezerGenre> list) throws RemoteException {
			callback.getUIThreadHandler().post(new Runnable() {
				public void run() {
					if (adapter == null) {
                        SqueezerGenreView itemView = new SqueezerGenreView(activity) {
                            @Override
                            public View getAdapterView(View convertView, SqueezerGenre item,
                                    ImageFetcher unused) {
                                return Util.getSpinnerItemView(getActivity(), convertView,
                                        item.getName());
                            }

                            @Override
                            public View getAdapterView(View convertView, String label) {
                                return Util.getSpinnerItemView(getActivity(), convertView, label);
                            };

						};
                        adapter = new SqueezerItemAdapter<SqueezerGenre>(activity, itemView, true, null);
						spinner.setAdapter(adapter);
					}
					adapter.update(count, start, list);
					spinner.setSelection(adapter.findItem(callback.getGenre()));

					if (count > start + list.size())
						if ((start + list.size()) % adapter.getPageSize() == 0 ) {
							orderItems(start + list.size());
						}
				}
			});
		}

    };

    public interface GenreSpinnerCallback {
    	ISqueezeService getService();
    	Handler getUIThreadHandler();
        SqueezerGenre getGenre();
        void setGenre(SqueezerGenre genre);
    }

}
