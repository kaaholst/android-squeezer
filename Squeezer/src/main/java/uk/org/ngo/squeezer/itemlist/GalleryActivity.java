/*
 * Copyright (c) 2009 Google Inc.  All Rights Reserved.
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

package uk.org.ngo.squeezer.itemlist;


import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;


import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.framework.BaseActivity;
import uk.org.ngo.squeezer.itemlist.dialog.SlideShow;
import uk.org.ngo.squeezer.model.Action;
import uk.org.ngo.squeezer.model.Image;
import uk.org.ngo.squeezer.model.JiveItem;
import uk.org.ngo.squeezer.service.event.HandshakeComplete;
import uk.org.ngo.squeezer.util.ImageFetcher;
import uk.org.ngo.squeezer.widget.GridAutofitLayoutManager;

public class GalleryActivity extends BaseActivity implements IServiceItemListCallback<JiveItem> {

    private ImageAdapter imageAdapter;
    private Action action;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle extras = Objects.requireNonNull(getIntent().getExtras(), "intent did not contain extras");
        action = extras.getParcelable(Action.class.getName());

        imageAdapter = new ImageAdapter();

        setContentView(R.layout.item_list);
        RecyclerView listView = findViewById(R.id.item_list);
        listView.setAdapter(imageAdapter);
        listView.setLayoutManager(new GridAutofitLayoutManager(this, R.dimen.grid_column_width));

    }

    public void onEventMainThread(HandshakeComplete event) {
        getService().pluginItems(action, this);
    }

    private class ImageAdapter extends RecyclerView.Adapter<ImageViewHolder> {
        private Image[] images = new Image[0];

        @Override
        public int getItemCount() {
            return images.length;
        }

        @NonNull
        @Override
        public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ImageViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.grid_item, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
            holder.bindData(images[position]);
        }
    }

    private class ImageViewHolder extends RecyclerView.ViewHolder {
        private final ImageView artwork;
        private final TextView caption;

        public ImageViewHolder(@NonNull View itemView) {
            super(itemView);
            artwork = itemView.findViewById(R.id.icon);
            caption = itemView.findViewById(R.id.text1);
        }

        public void bindData(final Image image) {
            ImageFetcher.getInstance(artwork.getContext()).loadImage(image.artworkId, artwork);
            caption.setText(image.caption);
            artwork.setOnClickListener(view -> SlideShow.show(GalleryActivity.this, getBindingAdapterPosition(), ((ImageAdapter)getBindingAdapter()).images));
        }
    }

    @Override
    public void onItemsReceived(int count, int start, Map<String, Object> parameters, List<JiveItem> items, Class<JiveItem> dataType) {
        Object[] item_data = (Object[]) parameters.get("data");
        if (item_data != null && item_data.length > 0) {
            imageAdapter.images = new Image[item_data.length];
            for (int i = 0; i < item_data.length; i++) {
                Object item_d = item_data[i];
                Map<String, Object> record = (Map<String, Object>) item_d;
                record.put("urlPrefix", parameters.get("urlPrefix"));
                imageAdapter.images[i] = new Image(record);
            }
            runOnUiThread(() -> imageAdapter.notifyDataSetChanged());
        }
    }

    @Override
    public Object getClient() {
        return this;
    }


    /**
     * Create an activity to show a gallery.
     */
    public static void show(Context context, Action action) {
        Intent intent = new Intent(context, GalleryActivity.class);
        intent.putExtra(Action.class.getName(), action);

        context.startActivity(intent);
    }
}
