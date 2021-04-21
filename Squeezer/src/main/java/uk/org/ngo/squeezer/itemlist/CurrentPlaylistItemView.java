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

import android.content.ClipData;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.view.GestureDetectorCompat;
import androidx.palette.graphics.Palette;

import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.Util;
import uk.org.ngo.squeezer.model.JiveItem;
import uk.org.ngo.squeezer.service.ISqueezeService;
import uk.org.ngo.squeezer.widget.OnSwipeListener;
import uk.org.ngo.squeezer.widget.UndoBarController;

class CurrentPlaylistItemView extends JiveItemView {
    private final CurrentPlaylistActivity activity;

    public CurrentPlaylistItemView(CurrentPlaylistActivity activity, @NonNull View view) {
        super(activity, view);
        this.activity = activity;
    }

    @Override
    public void bindView(JiveItem item) {
        super.bindView(item);

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

        final GestureDetectorCompat detector = new GestureDetectorCompat(getActivity(), new OnSwipeListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                itemView.setPressed(true);
                return super.onDown(e);
            }

            @Override
            public void onLongPress(MotionEvent e) {
                activity.setDraggedIndex(getAdapterPosition());
                itemView.setPressed(false);
                ClipData data = ClipData.newPlainText("", "");
                View.DragShadowBuilder shadowBuilder = new View.DragShadowBuilder(itemView);
                itemView.setActivated(true);
                itemView.startDrag(data, shadowBuilder, null, 0);
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                onItemSelected();
                return true;
            }

            @Override
            public boolean onSwipeLeft() {
                removeItem(item);
                return true;
            }

            @Override
            public boolean onSwipeRight() {
                removeItem(item);
                return true;
            }
        });

        itemView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                itemView.setPressed(false);
            }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                itemView.setPressed(false);
                itemView.performClick();
            }
            return detector.onTouchEvent(event);
        });
    }

    @Override
    protected boolean isSelectable(JiveItem item) {
        return true;
    }

    @Override
    public void onIcon() {
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

    private void removeItem(JiveItem item) {
        final int position = getAdapterPosition();
        activity.getItemAdapter().removeItem(position);
        UndoBarController.show(activity, activity.getString(R.string.JIVE_POPUP_REMOVING_FROM_PLAYLIST, item.getName()), new UndoBarController.UndoListener() {
            @Override
            public void onUndo() {
                activity.getItemAdapter().insertItem(position, item);
            }

            @Override
            public void onDone() {
                ISqueezeService service = activity.getService();
                if (service != null) {
                    service.playlistRemove(position);
                    activity.skipPlaylistChanged();
                }
            }
        });
    }

    /**
     * Jumps to whichever song the user chose.
     */
    public void onItemSelected() {
        ISqueezeService service = getActivity().getService();
        if (service != null) {
            getActivity().getService().playlistIndex(getAdapterPosition());
        }
    }
}
