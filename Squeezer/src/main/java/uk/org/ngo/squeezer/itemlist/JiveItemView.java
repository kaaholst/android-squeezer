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

import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.EnumSet;

import uk.org.ngo.squeezer.Preferences;
import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.framework.ItemAdapter;
import uk.org.ngo.squeezer.framework.ViewParamItemView;
import uk.org.ngo.squeezer.framework.ItemListActivity;
import uk.org.ngo.squeezer.model.Action;
import uk.org.ngo.squeezer.model.JiveItem;
import uk.org.ngo.squeezer.model.Slider;
import uk.org.ngo.squeezer.model.Window;
import uk.org.ngo.squeezer.itemlist.dialog.ArtworkListLayout;
import uk.org.ngo.squeezer.util.ImageFetcher;

public class JiveItemView extends ViewParamItemView<JiveItem> {
    private JiveItemViewLogic logicDelegate;
    private Window.WindowStyle windowStyle;

    /** Width of the icon, if VIEW_PARAM_ICON is used. */
    private int mIconWidth;

    /** Height of the icon, if VIEW_PARAM_ICON is used. */
    private int mIconHeight;

    JiveItemView(@NonNull JiveItemListActivity activity, @NonNull View view) {
        super(activity, view);
        setWindowStyle(activity.window.windowStyle);
        this.logicDelegate = new JiveItemViewLogic(activity);
        setLoadingViewParams(viewParamIcon() | VIEW_PARAM_TWO_LINE );

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
        if (listLayout() == ArtworkListLayout.grid) {
            mIconWidth = getActivity().getResources().getDimensionPixelSize(R.dimen.album_art_icon_grid_width);
            mIconHeight = getActivity().getResources().getDimensionPixelSize(R.dimen.album_art_icon_grid_height);
        } else {
            mIconWidth = getActivity().getResources().getDimensionPixelSize(R.dimen.album_art_icon_width);
            mIconHeight = getActivity().getResources().getDimensionPixelSize(R.dimen.album_art_icon_height);
        }
    }

    @Override
    public void bindView(JiveItem item) {
        if (item.hasSlider()) {
            bindSlider(item);
            return;
        }

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
                    mIconWidth,
                    mIconHeight,
                    this::onIcon
            );
        } else {
            icon.setImageDrawable(item.getIconDrawable(getActivity()));
            onIcon();
        }

        if (item.isSelectable()) {
            itemView.setOnClickListener(view -> onItemSelected(item));
        }

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


    private void bindSlider(final JiveItem item) {
        com.google.android.material.slider.Slider seekBar = itemView.findViewById(R.id.slider);
        final Slider slider = item.slider;
        seekBar.setValue(slider.initial);
        seekBar.setValueFrom(slider.min);
        seekBar.setValueTo(slider.max);
        seekBar.addOnSliderTouchListener(new com.google.android.material.slider.Slider.OnSliderTouchListener() {

            @Override
            public void onStartTrackingTouch(@NonNull com.google.android.material.slider.Slider seekBar) {
            }

            @Override
            public void onStopTrackingTouch(@NonNull com.google.android.material.slider.Slider seekBar) {
                if (item.goAction != null) {
                    item.inputValue = String.valueOf((int)seekBar.getValue());
                    getActivity().action(item, item.goAction);
                }
            }
        });
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
    }

    private void onItemSelected(JiveItem item) {
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
            ItemAdapter<JiveItemView, JiveItem> itemAdapter = getActivity().getItemAdapter();
            JiveItem prevItem = itemAdapter.getItem(getActivity().getSelectedIndex());
            if (prevItem != null && prevItem.radio != null) {
                prevItem.radio = false;
                itemAdapter.notifyItemChanged(getActivity().getSelectedIndex());
            }

            item.radio = true;
            getActivity().setSelectedIndex(getAdapterPosition());
            itemAdapter.notifyItemChanged(getAdapterPosition());
        }
   }

    @Override
    public void showContextMenu(JiveItem item) {
        logicDelegate.showContextMenu(this, item);
    }

}
