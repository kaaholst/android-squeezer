/*
 * Copyright (c) 2020 Kurt Aaholst <kaaholst@gmail.com>
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

import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.palette.graphics.Palette;

import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.Util;
import uk.org.ngo.squeezer.model.JiveItem;
import uk.org.ngo.squeezer.service.ISqueezeService;

class CurrentPlaylistItemView extends JiveItemView {
    private final CurrentPlaylistActivity activity;

    public CurrentPlaylistItemView(CurrentPlaylistActivity activity, @NonNull View view) {
        super(activity, view);
        this.activity = activity;
    }

    @Override
    public void bindView(JiveItem item) {
        super.bindView(item);
        itemView.setOnLongClickListener(null);

        if (getAdapterPosition() == activity.getSelectedIndex()) {
            itemView.setBackgroundResource(getActivity().getAttributeValue(R.attr.currentTrackBackground));
            text1.setTextAppearance(getActivity(), R.style.SqueezerTextAppearance_ListItem_Primary_Highlight);
            text2.setTextAppearance(getActivity(), R.style.SqueezerTextAppearance_ListItem_Secondary_Highlight);
        } else {
            itemView.setBackgroundResource(getActivity().getAttributeValue(R.attr.selectableItemBackground));
            text1.setTextAppearance(getActivity(), R.style.SqueezerTextAppearance_ListItem_Primary);
            text2.setTextAppearance(getActivity(), R.style.SqueezerTextAppearance_ListItem_Secondary);
        }

        itemView.setAlpha(getAdapterPosition() == activity.getDraggedIndex() ? 0 : 1);
    }

    @Override
    protected boolean isSelectable() {
        return true;
    }

    @Override
    public void onIcon() {
        super.onIcon();
        if (getAdapterPosition() == activity.getSelectedIndex()) {
            Drawable drawable = icon.getDrawable();
            Drawable marker = DrawableCompat.wrap(AppCompatResources.getDrawable(activity, R.drawable.ic_action_nowplaying));
            Palette colorPalette = Palette.from(Util.drawableToBitmap(drawable)).generate();
            DrawableCompat.setTint(marker, colorPalette.getDominantSwatch().getBodyTextColor());

            LayerDrawable layerDrawable = new LayerDrawable(new Drawable[]{drawable, marker});
            int inset = activity.getResources().getDimensionPixelSize(R.dimen.now_playing_emblem_inset);
            layerDrawable.setLayerInset(1, inset, inset, inset, inset);

            icon.setImageDrawable(layerDrawable);
        }
    }

    /**
     * Jumps to whichever song the user chose.
     */
    @Override
    public void onItemSelected() {
        ISqueezeService service = getActivity().getService();
        if (service != null) {
            getActivity().getService().playlistIndex(getAdapterPosition());
        }
    }
}
