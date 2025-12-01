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
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import uk.org.ngo.squeezer.Preferences;
import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.Squeezer;
import uk.org.ngo.squeezer.dialog.ChangeLogDialog;
import uk.org.ngo.squeezer.dialog.TipsDialog;
import uk.org.ngo.squeezer.framework.ItemAdapter;
import uk.org.ngo.squeezer.framework.ItemViewHolder;
import uk.org.ngo.squeezer.itemlist.dialog.ArtworkListLayout;
import uk.org.ngo.squeezer.model.JiveItem;
import uk.org.ngo.squeezer.model.Window;
import uk.org.ngo.squeezer.service.ISqueezeService;
import uk.org.ngo.squeezer.service.event.HandshakeComplete;

public class HomeActivity extends HomeMenuActivity {
    public static final String TAG = "HomeActivity";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        getIntent().putExtra(JiveItem.class.getName(), JiveItem.HOME);
        super.onCreate(savedInstanceState);

        // Show the change log if necessary.
        Squeezer.getInstance().doInBackground(() -> {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(HomeActivity.this);
            runOnUiThread(() -> {
                ChangeLogDialog changeLog = new ChangeLogDialog(this, preferences);
                if (changeLog.isFirstRun()) {
                    if (changeLog.isFirstRunEver()) {
                        changeLog.skipLogDialog();
                    } else {
                        changeLog.getThemedLogDialog().show();
                    }
                }
            });
        });
    }

    @Override
    public void recreate() {
        putRetainedValue(TAG_ADAPTER, null);
        super.recreate();
    }

    @Override
    protected ItemAdapter<ItemViewHolder<JiveItem>, JiveItem> createItemListAdapter() {
        return new JiveItemAdapter(this) {
            private final List<JiveItemAdapter> childAdapters = new ArrayList<>();

            @Override
            public ItemViewHolder<JiveItem> createViewHolder(View view, int viewType) {
                if (listLayout == ArtworkListLayout.grouped)
                    return new GroupedItemView(HomeActivity.this, view);
                return new HomeMenuJiveItemView(HomeActivity.this, view);
            }

            @Override
            protected int getItemViewType(JiveItem item) {
                if (listLayout == ArtworkListLayout.grouped) return R.layout.home_group;
                return super.getItemViewType(item);
            }

            @Override
            public void clear() {
                super.clear();
                childAdapters.clear();
            }

            @Override
            public void update(int count, int start, List<JiveItem> items) {
                super.update(count, start, items);
                if (listLayout != ArtworkListLayout.grouped) return;
                for (JiveItem item : items) {
                    JiveItemAdapter adapter = new JiveItemAdapter(HomeActivity.this) {
                        @Override
                        public ItemViewHolder<JiveItem> createViewHolder(View view, int viewType) {
                            return new HomeMenuJiveItemView(HomeActivity.this, view) {
                                @Override
                                public void bindView(JiveItem item) {
                                    super.bindView(item);
                                    icon.setBackgroundResource(getActivity().getAttributeValue(R.attr.colorAppBar));
                                }
                            };
                        }
                    };
                    adapter.setWindowStyle(ArtworkListLayout.grid, Window.WindowStyle.ICON_LIST);
                    List<JiveItem> node = getMenuNode(item.getId(), homeMenu);
                    if (!node.isEmpty()) adapter.update(node);
                    if (node.isEmpty() && !item.doAction && item.goAction != null) {
                        adapter.setOrderer(pagePosition -> requireService().pluginItems(pagePosition, item, item.goAction, adapter));
                        adapter.maybeOrderPage(0);
                    }
                    childAdapters.add(adapter);
                }
            }

            private class GroupedItemView extends HomeMenuJiveItemView {
                private final RecyclerView group;

                public GroupedItemView(@NonNull HomeMenuActivity activity, @NonNull View itemView) {
                    super(activity, Window.WindowStyle.ICON_LIST, ArtworkListLayout.list, itemView);
                    group = itemView.findViewById(R.id.list);
                }

                @Override
                public void bindView(JiveItem item) {
                    super.bindView(item);
                    text1.setTextAppearance(getActivity(), R.style.SqueezerTextAppearance_ListItem_Primary_Highlight);
                    text2.setTextAppearance(getActivity(), R.style.SqueezerTextAppearance_ListItem_Secondary_Highlight);
                    group.setAdapter(childAdapters.get(getBindingAdapterPosition()));
                    contextMenuButtonHolder.setVisibility(View.VISIBLE);
                    contextMenuButton.setVisibility(View.GONE);
                    contextMenuIcon.setVisibility(View.VISIBLE);
                }
            }
        };
    }

    @Override
    public ArtworkListLayout getPreferredListLayout() {
        return Squeezer.getPreferences().getHomeLayout();
    }

    @Override
    public void setPreferredListLayout(ArtworkListLayout listLayout) {
        super.setPreferredListLayout(listLayout);
        requireService().setCustomShortcuts();
    }

    @Override
    protected void saveListLayout(ArtworkListLayout listLayout) {
        Squeezer.getPreferences().setHomeLayout(listLayout);
    }

    @Override
    protected void onServiceConnected(@NonNull ISqueezeService service) {
        super.onServiceConnected(service);
        repository().observe(this, (HandshakeComplete event) -> onHandshakeComplete());
    }

    private void onHandshakeComplete() {
        Log.d(TAG, "Handshake complete");

        // Show a tip about volume controls, if this is the first time this app
        // has run. TODO: Add more robust and general 'tips' functionality.
        PackageInfo pInfo;
        try {
            final Preferences preferences = Squeezer.getPreferences();

            pInfo = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_META_DATA);
            if (preferences.getLastRunVersionCode() == 0) {
                new TipsDialog().show(getSupportFragmentManager(), "TipsDialog");
                preferences.setLastRunVersionCode(pInfo.versionCode);
            }
        } catch (PackageManager.NameNotFoundException e) {
            // Nothing to do, don't crash.
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_item_grouped) {
            setPreferredListLayout(ArtworkListLayout.grouped);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void updateViewMenuItems(ArtworkListLayout listLayout, Window.WindowStyle windowStyle) {
        super.updateViewMenuItems(listLayout, windowStyle);
        if (menuItemList != null) {
            menuItemGrouped.setVisible(true);
            (switch (listLayout) {
                case grouped -> menuItemGrouped;
                case grid -> menuItemGrid;
                case list -> menuItemList;
            }).setChecked(true);
        }
    }

    public static void show(Context context) {
        Intent intent = new Intent(context, HomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (!(context instanceof Activity))
            intent = intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        context.startActivity(intent);
        if (context instanceof Activity activity) {
            activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }
    }

}
