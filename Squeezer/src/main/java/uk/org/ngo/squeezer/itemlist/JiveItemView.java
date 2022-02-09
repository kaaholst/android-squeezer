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

package uk.org.ngo.squeezer.itemlist;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import java.util.EnumSet;

import uk.org.ngo.squeezer.Preferences;
import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.Util;
import uk.org.ngo.squeezer.framework.ItemAdapter;
import uk.org.ngo.squeezer.framework.ItemListActivity;
import uk.org.ngo.squeezer.framework.ItemViewHolder;
import uk.org.ngo.squeezer.framework.ViewParamItemView;
import uk.org.ngo.squeezer.itemlist.dialog.ArtworkListLayout;
import uk.org.ngo.squeezer.model.Action;
import uk.org.ngo.squeezer.model.CustomJiveItemHandling;
import uk.org.ngo.squeezer.model.JiveItem;
import uk.org.ngo.squeezer.model.Window;
import uk.org.ngo.squeezer.util.ImageFetcher;

public class JiveItemView extends ViewParamItemView<JiveItem> {

    private final JiveItemViewLogic logicDelegate;
    private Window.WindowStyle windowStyle;

    Preferences mPreferences = new Preferences(itemView.getContext());
    final boolean isShortcutActive = mPreferences.getCustomizeShortcutsMode() == Preferences.CustomizeShortcutsMode.ENABLED;
    final boolean isArchiveActive = mPreferences.getCustomizeHomeMenuMode() == Preferences.CustomizeHomeMenuMode.ARCHIVE;

    /**
     * Will also be used (and set) in HomeMenuJiveItemView.
     */
    CustomJiveItemHandling mCustomJiveItemHandling = null;

    JiveItemView(@NonNull JiveItemListActivity activity, @NonNull View view) {
        super(activity, view);
        if (mCustomJiveItemHandling == null) {
            mCustomJiveItemHandling = new CustomJiveItemHandling(activity);
        }
        setWindowStyle(activity.window.windowStyle);
        this.logicDelegate = new JiveItemViewLogic(activity);

        // Certain LMS actions (e.g. slider) doesn't have text in their views
        if (text1 != null) {
            int maxLines = getMaxLines();
            if (maxLines > 0) {
                setMaxLines(text1, maxLines);
                setMaxLines(text2, maxLines);
            }
        }
    }

    private int getMaxLines() {
        return new Preferences(getActivity()).getMaxLines(listLayout());
    }

    private void setMaxLines(TextView view, int maxLines) {
        view.setMaxLines(maxLines);
        view.setEllipsize(TextUtils.TruncateAt.END);
    }

    @Override
    public JiveItemListActivity getActivity() {
        return (JiveItemListActivity) super.getActivity();
    }

    void setWindowStyle(Window.WindowStyle windowStyle) {
        this.windowStyle = windowStyle;
    }

    @Override
    public void bindView(JiveItem item) {
        super.bindView(item);
        if (item.radio != null && item.radio) {
            getActivity().setSelectedIndex(getAdapterPosition());
        }

        setItemViewParams((viewParamIcon() | VIEW_PARAM_TWO_LINE | viewParamContext(item)));
        super.bindView(item);

        text2.setText(item.text2);

        // If the item has an image, then fetch and display it
        if (item.hasIconUri()) {
            ImageFetcher.getInstance(getActivity()).loadImage(
                    item.getIcon(),
                    icon,
                    this::onIcon
            );
        } else {
            icon.setImageDrawable(item.getIconDrawable(getActivity()));
            onIcon();
        }

        text1.setAlpha(getAlpha());
        text2.setAlpha(getAlpha());
        itemView.setOnClickListener(view -> onItemSelected());

        if ( isShortcutActive || isArchiveActive ) {
            itemView.setOnLongClickListener(view -> putItemAsShortcut());
        } else {
            itemView.setOnLongClickListener(null);
        }

        itemView.setClickable(isSelectable());

        if (item.hasContextMenu()) {
            contextMenuButton.setVisibility(item.checkbox == null && item.radio == null ? View.VISIBLE : View.GONE);
            contextMenuCheckbox.setVisibility(item.checkbox != null ? View.VISIBLE : View.GONE);
            contextMenuRadio.setVisibility(item.radio != null ? View.VISIBLE : View.GONE);
            if (item.checkbox != null) {
                contextMenuCheckbox.setChecked(item.checkbox);
            } else if (item.radio != null) {
                contextMenuRadio.setChecked(item.radio);
            }
        }
    }

    /**
     * This view handles just shortcuts, but has to display the correct message anyway.
     */
    private boolean putItemAsShortcut() {
        @StringRes int message = !isArchiveActive ? R.string.ITEM_CANNOT_BE_SHORTCUT :
                isShortcutActive ? R.string.ITEM_CAN_NOT_BE_SHORTCUT_OR_ARCHIVED : R.string.ITEM_CANNOT_BE_ARCHIVED;

        if (!mCustomJiveItemHandling.isShortcutable(item)) {
            getActivity().showDisplayMessage(message);
        } else {
            if (isShortcutActive) {
                if (mCustomJiveItemHandling.triggerCustomShortcut(item)) {
                    mPreferences.saveShortcuts(mCustomJiveItemHandling.convertShortcuts());
//                  TODO: check ok?
                    getActivity().showDisplayMessage(R.string.ITEM_PUT_AS_SHORTCUT_ON_HOME_MENU);
                } else {
                    getActivity().showDisplayMessage(R.string.ITEM_IS_ALREADY_A_SHORTCUT);
                }
            } else {
                getActivity().showDisplayMessage(R.string.ITEM_CANNOT_BE_ARCHIVED);
            }
        }
        return true;
    }

    private float getAlpha() {
        return isSelectable() ? 1.0f : (item.checkbox != null || item.radio != null) ? 0.25f : 0.75f;
    }

    protected boolean isSelectable() {
        return item.isSelectable();
    }

    private ArtworkListLayout listLayout() {
        return listLayout(getActivity(), windowStyle);
    }

    static ArtworkListLayout listLayout(ItemListActivity activity, Window.WindowStyle windowStyle) {
        if (canChangeListLayout(windowStyle)) {
            return activity.getPreferredListLayout();
        }
        return ArtworkListLayout.list;
    }

    static boolean canChangeListLayout(Window.WindowStyle windowStyle) {
        return EnumSet.of(Window.WindowStyle.HOME_MENU, Window.WindowStyle.ICON_LIST).contains(windowStyle);
    }

    private int viewParamIcon() {
        return windowStyle == Window.WindowStyle.TEXT_ONLY ? 0 : VIEW_PARAM_ICON;
    }

    private int viewParamContext(JiveItem item) {
        return item.hasContextMenu() ? VIEW_PARAM_CONTEXT_BUTTON : 0;
    }

    protected void onIcon() {
        Drawable logo = item.getLogo(getActivity());
        if (logo != null) {
            Drawable drawable = icon.getDrawable();
            Bitmap drawableBitmap = Util.drawableToBitmap(drawable);

            boolean large = (listLayout() == ArtworkListLayout.grid);
            int iconSize = drawable.getIntrinsicWidth();
            if (iconSize <= 0) {
                iconSize = icon.getWidth();
            }
            int logoSize = (int)(iconSize * (large ? 0.2 : 0.3));
            int start = (int)(iconSize * (large ? 0.78 : 0.68));
            int top = (int)(iconSize * 0.02);
            Bitmap logoBitmap = Util.getBitmap(logo, logoSize, logoSize);

            Canvas canvas = new Canvas(drawableBitmap);
            Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
            canvas.drawBitmap(logoBitmap, start, top, paint);

            icon.setImageBitmap(drawableBitmap);
        }
    }

    public void onItemSelected() {
        Action.JsonAction action = (item.goAction != null && item.goAction.action != null) ? item.goAction.action : null;
        Action.NextWindow nextWindow = (action != null ? action.nextWindow : item.nextWindow);
        if (item.checkbox != null) {
            item.checkbox = !item.checkbox;
            Action checkboxAction = item.checkboxActions.get(item.checkbox);
            if (checkboxAction != null) {
                getActivity().action(item, checkboxAction);
            }
            contextMenuCheckbox.setChecked(item.checkbox);
        } else if (nextWindow != null && !item.hasInput()) {
            getActivity().action(item, item.goAction);
        } else {
            if (item.goAction != null)
                logicDelegate.execGoAction(this, item, 0);
            else if (item.hasSubItems())
                JiveItemListActivity.show(getActivity(), item);
            else if (item.getNode() != null) {
                HomeMenuActivity.show(getActivity(), item);
            }
        }

        if (item.radio != null) {
            ItemAdapter<ItemViewHolder<JiveItem>, JiveItem> itemAdapter = getActivity().getItemAdapter();
            int prevIndex = getActivity().getSelectedIndex();
            if (prevIndex >= 0 && prevIndex < itemAdapter.getItemCount()) {
                JiveItem prevItem = itemAdapter.getItem(prevIndex);
                if (prevItem != null && prevItem.radio != null) {
                    prevItem.radio = false;
                    itemAdapter.notifyItemChanged(prevIndex);
                }
            }

            item.radio = true;
            getActivity().setSelectedIndex(getAdapterPosition());
            itemAdapter.notifyItemChanged(getAdapterPosition());
        }
    }

    @Override
    public void showContextMenu() {
        logicDelegate.showContextMenu(this, item);
    }

}
