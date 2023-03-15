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

package uk.org.ngo.squeezer.framework;

import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.model.Item;

/**
 * Represents the view hierarchy for a single {@link Item} subclass, suitable for displaying in a
 * {@link ItemListActivity}.
 * <p>
 * This class supports views that have a {@link TextView} to display the primary information about
 * the {@link Item} and can optionally enable additional views.  The layout is defined in {@code
 * res/layout/list_item.xml}. <ul> <li>A {@link ImageView} suitable for displaying icons</li>
 * <li>A second, smaller {@link TextView} for additional item information</li> <li>A {@link
 * Button} that shows a disclosure triangle for a context menu</li> </ul> The view can
 * display an item in one of two states.  The primary state is when the data to be inserted in to
 * the view is known, and represented by a complete {@link Item} subclass. The loading state is when
 * the data type is known, but has not been fetched from the server yet.
 * <p>
 * To customise the view's display create an int of {@link ViewParam} and pass it to
 * {@link #setViewParams(int)}
 * <p>
 * Override {@link #bindView(Item)}  *
 * @param <T> the Item subclass this view represents.
 */
public class ViewParamItemView<T extends Item> extends ItemViewHolder<T> {

    @IntDef(flag=true, value={
            VIEW_PARAM_ICON, VIEW_PARAM_TWO_LINE, VIEW_PARAM_CONTEXT_BUTTON
    })
    @Retention(RetentionPolicy.SOURCE)
    /* Parameters that control which additional views will be enabled in the item view. */
    public @interface ViewParam {}
    /** Adds a {@link ImageView} for displaying artwork or other iconography. */
    public static final int VIEW_PARAM_ICON = 1;
    /** Adds a second line for detail information ({@code R.id.text2}). */
    public static final int VIEW_PARAM_TWO_LINE = 1 << 1;
    /** Adds a button, with click handler, to display the context menu. */
    public static final int VIEW_PARAM_CONTEXT_BUTTON = 1 << 2;

    /**
     * View parameters for a filled-in view.  One primary line with context button.
     */
    @ViewParam private int itemViewParams = VIEW_PARAM_CONTEXT_BUTTON;

    public final ImageView icon;
    public final TextView text1;
    public final TextView text2;

    public final View contextMenuButtonHolder;
    public Button contextMenuButton;
    protected CheckBox contextMenuCheckbox;
    protected RadioButton contextMenuRadio;

    private @ViewParam int viewParams;

    public ViewParamItemView(@NonNull BaseActivity activity, @NonNull View view) {
        super(activity, view);
        text1 = view.findViewById(R.id.text1);
        text2 = view.findViewById(R.id.text2);
        icon = view.findViewById(R.id.icon);
        contextMenuButtonHolder = view.findViewById(R.id.context_menu);
        if (contextMenuButtonHolder!= null) {
            contextMenuButton = contextMenuButtonHolder.findViewById(R.id.context_menu_button);
            contextMenuCheckbox = contextMenuButtonHolder.findViewById(R.id.checkbox);
            contextMenuRadio = contextMenuButtonHolder.findViewById(R.id.radio);
        }
    }

    private void setViewParams(@ViewParam int viewParams) {
        icon.setVisibility((viewParams & VIEW_PARAM_ICON) != 0 ? View.VISIBLE : View.GONE);
        text2.setVisibility((viewParams & VIEW_PARAM_TWO_LINE) != 0 ? View.VISIBLE : View.GONE);

        if (contextMenuButtonHolder != null) {
            contextMenuButtonHolder.setVisibility(
                    (viewParams & VIEW_PARAM_CONTEXT_BUTTON) != 0 ? View.VISIBLE : View.GONE);
        }
        this.viewParams = viewParams;
    }


    /**
     * Set the view parameters to use for the view when data is loaded.
     */
    protected void setItemViewParams(@ViewParam int viewParams) {
        itemViewParams = viewParams;
    }

    @Override
    public ItemListActivity getActivity() {
        return (ItemListActivity) super.getActivity();
    }

    /**
     * Binds the item's name to {@link #text1}, and set up the context menu.
     */
    @Override
    public void bindView(T item) {
        super.bindView(item);
        text1.setText(item.getName());

        if (contextMenuButton!= null) {
            contextMenuButton.setOnClickListener(v -> showContextMenu());
        }

        if (itemViewParams != viewParams) {
            setViewParams(itemViewParams);
        }
    }

    /**
     * Creates the context menu.
     * <p>
     * The default implementation is empty.
     * <p>
     * Subclasses with a context menu should override this method, create a
     * {@link android.widget.PopupMenu} or a {@link android.app.Dialog} then
     * inflate their context menu and show it.
     *
     */
    public void showContextMenu() {
    }
}
