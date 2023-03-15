/*
 * Copyright (c) 2019 Kurt Aaholst <kaaholst@gmail.com>
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

import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.Util;
import uk.org.ngo.squeezer.framework.ContextMenu;
import uk.org.ngo.squeezer.model.Action;
import uk.org.ngo.squeezer.framework.BaseActivity;
import uk.org.ngo.squeezer.itemlist.dialog.ArtworkDialog;
import uk.org.ngo.squeezer.itemlist.dialog.ChoicesDialog;
import uk.org.ngo.squeezer.itemlist.dialog.InputTextDialog;
import uk.org.ngo.squeezer.itemlist.dialog.InputTimeDialog;
import uk.org.ngo.squeezer.itemlist.dialog.SlideShow;
import uk.org.ngo.squeezer.model.JiveItem;
import uk.org.ngo.squeezer.util.ImageFetcher;
import uk.org.ngo.squeezer.util.ImageWorker;

/**
 * Delegate with view logic for {@link JiveItem} which can be used from any {@link BaseActivity}
 */
public class JiveItemViewLogic {

    /**
     * Perform the <code>go</code> action of the supplied item.
     * <p>
     * If this is a <code>do</code> action and it doesn't require input, it is performed immediately
     * by calling {@link BaseActivity#action(JiveItem, Action) }.
     * <p>
     * Otherwise we pass the action to a sub <code>activity</code> (window in slim terminology) which
     * collects the input if required and performs the action. See {@link JiveItemListActivity#show(Activity, JiveItem, Action)}
     * <p>
     * Finally if the (unsupported) "showBigArtwork" flag is present in an item the <code>do</code>
     * action will return an artwork id or URL, which can be used the fetch an image to display in a
     * popup. See {@link ArtworkDialog#show(BaseActivity, Action)}
     */
    public static void execGoAction(BaseActivity activity, ContextMenu contextMenu, JiveItem item, int alreadyPopped) {
        boolean dismissContextMenu = (contextMenu != null);
        if (item.showBigArtwork) {
            ArtworkDialog.show(activity, item.goAction);
        } else if (item.goAction.isSlideShow()) {
            GalleryActivity.show(activity, item.goAction);
        } else if (item.goAction.isTypeSlideShow()) {
            SlideShow.show(activity, item.goAction);
        } else if (item.goAction.isContextMenu()) {
            if (contextMenu != null) {
                dismissContextMenu = false;
                contextMenu.show(item, item.goAction);
            } else {
                ContextMenu.show(activity, item, item.goAction);
            }
        } else if (item.doAction) {
            if (item.hasInput()) {
                if (item.hasChoices()) {
                    ChoicesDialog.show(activity, item, alreadyPopped);
                } else if ("time".equals(item.input.inputStyle)) {
                    InputTimeDialog.show(activity, item, alreadyPopped);
                } else {
                    InputTextDialog.show(activity, item, alreadyPopped);
                }
            } else {
                activity.action(item, item.goAction, alreadyPopped);
            }
        } else {
            JiveItemListActivity.show(activity, item, item.goAction);
        }
        if (dismissContextMenu) contextMenu.dismiss();
    }

    public static void execGoAction(BaseActivity activity, JiveItem item) {
        execGoAction(activity, null, item, 0);
    }

    /** Fetch and show album art or use embedded icon */
    public static void icon(ImageView icon, JiveItem item, ImageWorker.LoadImageCallback callback) {
        if (item.useIcon()) {
            ImageFetcher.getInstance(icon.getContext()).loadImage(item.getIcon(), icon, callback);
        } else {
            icon.setImageDrawable(item.getIconDrawable(icon.getContext()));
        }
    }

    public static void addLogo(ImageView icon, JiveItem item) {
        Drawable logo = item.getLogo(icon.getContext());
        if (logo != null) {
            Drawable drawable = icon.getDrawable();
            Bitmap drawableBitmap = Util.drawableToBitmap(drawable);

            int iconSize = drawable.getIntrinsicWidth();
            if (iconSize <= 0) {
                iconSize = icon.getWidth();
            }

            // The same logo size looks different on different size artwork, so we adjust it slightly
            Resources resources = icon.getResources();
            double baseIconSize = resources.getDimensionPixelSize(R.dimen.album_art_icon_size);
            double factor = 1 + ((iconSize / baseIconSize) - 1) / 5;
            int logoInset = (int)(resources.getDimensionPixelSize(R.dimen.logo_inset) * factor);
            int logoSize = (int)(resources.getDimensionPixelSize(R.dimen.logo_size) * factor);
            int start = iconSize - logoSize - logoInset;
            Bitmap logoBitmap = Util.getBitmap(logo, logoSize, logoSize);

            Canvas canvas = new Canvas(drawableBitmap);
            Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
            canvas.drawBitmap(logoBitmap, start, logoInset, paint);

            icon.setImageBitmap(drawableBitmap);
        }
    }

}
