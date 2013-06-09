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
import uk.org.ngo.squeezer.model.SqueezerYear;
import uk.org.ngo.squeezer.service.ISqueezeService;
import uk.org.ngo.squeezer.util.ImageFetcher;
import android.os.Handler;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.widget.Spinner;

public class YearSpinner {
	private static final String TAG = YearSpinner.class.getName();
	YearSpinnerCallback callback;
	private final SqueezerItemListActivity activity;
	private final Spinner spinner;

	public YearSpinner(YearSpinnerCallback callback, SqueezerItemListActivity activity, Spinner spinner) {
		this.callback = callback;
		this.activity = activity;
		this.spinner = spinner;
		registerCallback();
		orderItems(0);
	}

	private void orderItems(int start) {
		if (callback.getService() != null) {
			try {
				callback.getService().years(start);
			} catch (RemoteException e) {
                Log.e(TAG, "Error ordering items: " + e);
			}
		}
	}

	public void registerCallback() {
		if (callback.getService() != null) {
			try {
				callback.getService().registerYearListCallback(yearListCallback);
			} catch (RemoteException e) {
                Log.e(TAG, "Error registering callback: " + e);
			}
		}
	}

	public void unregisterCallback() {
		if (callback.getService() != null) {
			try {
				callback.getService().unregisterYearListCallback(yearListCallback);
			} catch (RemoteException e) {
                Log.e(TAG, "Error unregistering callback: " + e);
			}
		}
	}

    private final IServiceYearListCallback yearListCallback = new IServiceYearListCallback.Stub() {
		private SqueezerItemAdapter<SqueezerYear> adapter;

		public void onYearsReceived(final int count, final int start, final List<SqueezerYear> list) throws RemoteException {
			callback.getUIThreadHandler().post(new Runnable() {
				public void run() {
					if (adapter == null) {
						SqueezerYearView itemView = new SqueezerYearView(activity) {
							@Override
                            public View getAdapterView(View convertView, SqueezerYear item,
                                    ImageFetcher unused) {
								return Util.getSpinnerItemView(getActivity(), convertView, item.getName());
							}
							@Override
							public View getAdapterView(View convertView, String label) {
                                return Util.getSpinnerItemView(getActivity(), convertView, label);
							};
						};
                        adapter = new SqueezerItemAdapter<SqueezerYear>(activity, itemView, true, null);
						spinner.setAdapter(adapter);
					}
					adapter.update(count, start, list);
					spinner.setSelection(adapter.findItem(callback.getYear()));

					if (count > start + list.size())
						if ((start + list.size()) % adapter.getPageSize() == 0 ) {
							orderItems(start + list.size());
						}
				}
			});
		}

    };

    public interface YearSpinnerCallback {
    	ISqueezeService getService();
    	Handler getUIThreadHandler();
    	SqueezerYear getYear();
    	void setYear(SqueezerYear year);
    }

}
