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


import android.app.Activity;
import android.content.Intent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.recyclerview.widget.RecyclerView;

import org.greenrobot.eventbus.Subscribe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.Squeezer;
import uk.org.ngo.squeezer.framework.ItemAdapter;
import uk.org.ngo.squeezer.framework.ItemViewHolder;
import uk.org.ngo.squeezer.itemlist.dialog.ArtworkListLayout;
import uk.org.ngo.squeezer.model.JiveItem;
import uk.org.ngo.squeezer.model.Window;
import uk.org.ngo.squeezer.service.ISqueezeService;
import uk.org.ngo.squeezer.service.event.HomeMenuEvent;

public class HomeMenuActivity extends JiveItemListActivity {

    @Override
    protected void orderPage(@NonNull ISqueezeService service, int start) {
        // Do nothing we get the home menu from the sticky HomeMenuEvent
    }

    @Override
    public void clearAndReOrderItems() {
        // Do nothing we get the home menu from the sticky HomeMenuEvent
    }

    @Override
    public void maybeOrderVisiblePages(RecyclerView listView) {
        // Do nothing we get the home menu from the sticky HomeMenuEvent
    }

    @Override
    public ArtworkListLayout getPreferredListLayout() {
        return Squeezer.getPreferences().getHomeMenuLayout();
    }

    @Override
    protected void saveListLayout(ArtworkListLayout listLayout) {
        Squeezer.getPreferences().setHomeMenuLayout(listLayout);
    }

    @Override
    protected Window.WindowStyle defaultWindowStyle() {
        return Window.WindowStyle.HOME_MENU;
    }

    @Subscribe(sticky = true)
    public void onEvent(HomeMenuEvent event) {
        runOnUiThread(() -> {
            clearItemAdapter();

            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                if (JiveItem.HOME.equals(parent)) {
                    parentViewHolder.itemView.setVisibility(View.GONE);
                    // Turn off the home icon.
                    actionBar.setDisplayHomeAsUpEnabled(false);
                } else {
                    boolean inArchive = JiveItem.ARCHIVE.equals(parent) || requireService().isInArchive(parent);
                    actionBar.setHomeAsUpIndicator(inArchive ? R.drawable.ic_action_archive : R.drawable.ic_action_home);
                }
            }
        });
        List<JiveItem> menu = getMenuNode(parent.getId(), event.menuItems);
        onItemsReceived(menu.size(), 0, menu, JiveItem.class);
    }

    /**
     * Return a list of menu items filtered by the given node and player specific items, and ordered
     * by weight, name.
     */
    private List<JiveItem> getMenuNode(String node, List<JiveItem> homeMenu) {
        ArrayList<JiveItem> menu = new ArrayList<>();
        for (JiveItem item : homeMenu) {
            if (node.equals(item.getNode()) && (item.goAction == null || forActivePlayer(item.goAction))) {
                menu.add(item);
            }
        }
        Collections.sort(menu, (o1, o2) -> {
            if (o1.getWeight() == o2.getWeight()) {
                return o1.getName().compareTo(o2.getName());
            }
            return o1.getWeight() - o2.getWeight();
        });
        return menu;
    }

    public static void show(Activity activity, JiveItem item) {
        final Intent intent = new Intent(activity, HomeMenuActivity.class);
        intent.putExtra(JiveItem.class.getName(), item);
        activity.startActivity(intent);
    }

    @Override
    protected ItemAdapter<ItemViewHolder<JiveItem>, JiveItem> createItemListAdapter() {

        return new JiveItemAdapter(this) {
            @Override
            public ItemViewHolder<JiveItem> createViewHolder(View view, int viewType) {
                return new HomeMenuJiveItemView(HomeMenuActivity.this, view, this);
            }
        };
    }

}
