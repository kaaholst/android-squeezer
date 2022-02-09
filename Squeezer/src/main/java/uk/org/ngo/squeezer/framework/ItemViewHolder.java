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

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;


import uk.org.ngo.squeezer.model.Item;

/**
 * Defines a view holder for a {@link Item}
 * <p>
 * Implementations should add fields for caching potentially expensive View.findViewById(int) results
 *
 * @param <T> Denotes the class of the item this class implements view logic for
 *
 * @author Kurt Aaholst
 */
public abstract class ItemViewHolder<T extends Item> extends RecyclerView.ViewHolder {

    private final BaseActivity activity;
    protected T item;

    public ItemViewHolder(@NonNull BaseActivity activity, @NonNull View view) {
        super(view);
        this.activity = activity;
    }

    /**
     * @return The activity associated with this view holder
     */
    public BaseActivity getActivity() {
        return activity;
    }


    /**
     * Display the item's data in this view holders item view.
     *
     * @param item The item to be bound
     */
    @CallSuper
    public void bindView(final T item) {
        this.item = item;
    }

}
