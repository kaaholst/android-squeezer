/*
 * Copyright (c) 2021 Kurt Aaholst <kaaholst@gmail.com>
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

import android.view.View;

import androidx.annotation.NonNull;

import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.framework.ItemViewHolder;
import uk.org.ngo.squeezer.model.JiveItem;
import uk.org.ngo.squeezer.model.Window;

public class JiveItemViewPending extends ItemViewHolder<JiveItem> {

    private final boolean showIcon;
    private final View icon;

    JiveItemViewPending(@NonNull JiveItemListActivity activity, @NonNull View view) {
        super(activity, view);
        icon = view.findViewById(R.id.icon);
        showIcon = activity.window.windowStyle != Window.WindowStyle.TEXT_ONLY;
    }

    @Override
    public void bindView(JiveItem item) {
        super.bindView(item);
        icon.setVisibility(showIcon ? View.VISIBLE : View.GONE);
    }

}
