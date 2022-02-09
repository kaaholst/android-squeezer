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
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;

import java.util.List;
import java.util.Map;

import uk.org.ngo.squeezer.Preferences;
import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.framework.ViewParamItemView;
import uk.org.ngo.squeezer.model.Action;
import uk.org.ngo.squeezer.framework.BaseActivity;
import uk.org.ngo.squeezer.itemlist.dialog.ArtworkDialog;
import uk.org.ngo.squeezer.itemlist.dialog.ChoicesDialog;
import uk.org.ngo.squeezer.itemlist.dialog.InputTextDialog;
import uk.org.ngo.squeezer.itemlist.dialog.InputTimeDialog;
import uk.org.ngo.squeezer.itemlist.dialog.SlideShow;
import uk.org.ngo.squeezer.model.JiveItem;
import uk.org.ngo.squeezer.service.ISqueezeService;

/**
 * Delegate with view logic for {@link JiveItem} which can be used from any {@link BaseActivity}
 */
public class JiveItemViewLogic implements IServiceItemListCallback<JiveItem>, PopupMenu.OnDismissListener {
    private final BaseActivity activity;

    public JiveItemViewLogic(BaseActivity activity) {
        this.activity = activity;
    }

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

    private static final String TAG = "JiveItemViewLogic";

    void execGoAction(ViewParamItemView<JiveItem> viewHolder, JiveItem item, int alreadyPopped) {
        if (item.showBigArtwork) {
            ArtworkDialog.show(activity, item.goAction);
        } else if (item.goAction.isSlideShow()) {
            GalleryActivity.show(activity, item.goAction);
        } else if (item.goAction.isTypeSlideShow()) {
            SlideShow.show(activity, item.goAction);
        } else if (item.goAction.isContextMenu()) {
            showContextMenu(viewHolder, item, item.goAction);
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
    }

    // Only touch these from the main thread
    private int contextStack = 0;
    private JiveItem contextMenuItem;
    private PopupMenu contextPopup;
    private ViewParamItemView<JiveItem> contextMenuViewHolder;

    public void showContextMenu(ViewParamItemView<JiveItem> viewHolder, JiveItem item) {
        if (item.moreAction != null) {
            showContextMenu(viewHolder, item, item.moreAction);
        } else {
            showStandardContextMenu(viewHolder.contextMenuButtonHolder, item);
        }
    }

    private void showContextMenu(ViewParamItemView<JiveItem> viewHolder, JiveItem item, Action action) {
        contextMenuViewHolder = viewHolder;
        contextStack = 1;
        contextMenuItem = item;
        orderContextMenu(action);
    }

    private void showStandardContextMenu(View v, final JiveItem item) {
        contextPopup = new PopupMenu(activity, v);
        Menu menu = contextPopup.getMenu();

        if (item.playAction != null) {
            menu.add(Menu.NONE, R.id.play_now, Menu.NONE, R.string.PLAY_NOW);
        }
        if (item.addAction != null) {
            menu.add(Menu.NONE, R.id.add_to_playlist, Menu.NONE, R.string.ADD_TO_END);
        }
        if (item.insertAction != null) {
            menu.add(Menu.NONE, R.id.play_next, Menu.NONE, R.string.PLAY_NEXT);
        }
        if (item.moreAction != null) {
            menu.add(Menu.NONE, R.id.more, Menu.NONE, R.string.MORE);
        }

        contextPopup.setOnMenuItemClickListener(menuItem -> doStandardItemContext(menuItem, item));
        contextPopup.setOnDismissListener(this);
        contextPopup.show();
    }

    private boolean doStandardItemContext(MenuItem menuItem, JiveItem item) {
        switch (menuItem.getItemId()) {
            case R.id.play_now:
                activity.action(item, item.playAction);
                return true;
            case R.id.add_to_playlist:
                activity.action(item, item.addAction);
                return true;
            case R.id.play_next:
                activity.action(item, item.insertAction);
                return true;
            case R.id.more:
                JiveItemListActivity.show(activity, item, item.moreAction);
                return true;
        }
        return false;
    }

    private void showContextMenu(final ViewParamItemView<JiveItem> viewHolder, final List<JiveItem> items) {
        Preferences preferences = new Preferences(activity);
        contextPopup = new PopupMenu(activity, viewHolder.contextMenuButtonHolder);
        Menu menu = contextPopup.getMenu();

        int index = 0;
        if (preferences.isDownloadEnabled() && contextMenuItem != null && contextMenuItem.canDownload()) {
            menu.add(Menu.NONE, index++, Menu.NONE, R.string.DOWNLOAD);
        }
        if (canRandomPlay(contextMenuItem)) {
            menu.add(Menu.NONE, index++, Menu.NONE, R.string.PLAY_RANDOM_FOLDER);
        }

        final int offset = index;
        for (JiveItem jiveItem : items) {
            menu.add(Menu.NONE, index++, Menu.NONE, jiveItem.getName()).setEnabled(jiveItem.goAction != null);
        }

        contextPopup.setOnMenuItemClickListener(menuItem -> {
            if ( (menuItem.getItemId() == offset - 1)
                    && (canRandomPlay(contextMenuItem))) {
                activity.randomPlayFolder(contextMenuItem); //
            }
            else if (menuItem.getItemId() < offset) {
                activity.downloadItem(contextMenuItem);
            } else {
                doItemContext(viewHolder, items.get(menuItem.getItemId() - offset));
            }
            return true;
        });
        contextPopup.setOnDismissListener(this);
        contextPopup.show();
    }

    private boolean canRandomPlay(JiveItem contextMenuItem) {
        // Do not set Random Play in the context menu of the headline item of the folder if
        // Nullobject. It works fine on first level.
        // TODO: Maybe make this ignore work for all
        //  folders. See JiveItem.randomPlayFolderCommand()
        return (contextMenuItem != null) &&
                (contextMenuItem.moreAction != null) &&
                contextMenuItem.moreAction.action.cmd.contains("folderinfo") &&
                (contextMenuItem.randomPlayFolderCommand() != null);
    }

    private void doItemContext(ViewParamItemView<JiveItem> viewHolder, JiveItem item) {
        Action.NextWindow nextWindow = (item.goAction != null ? item.goAction.action.nextWindow : item.nextWindow);
        if (nextWindow != null) {
            activity.action(item, item.goAction, contextStack);
        } else {
            execGoAction(viewHolder, item, contextStack);
        }
    }

    private void orderContextMenu(Action action) {
        ISqueezeService service = activity.getService();
        if (service != null) {
            contextMenuViewHolder.contextMenuButton.setVisibility(View.GONE);
            contextMenuViewHolder.contextMenuLoading.setVisibility(View.VISIBLE);
            service.pluginItems(action, this);
        }
    }

    @Override
    public Object getClient() {
        return activity;
    }

    @Override
    public void onItemsReceived(int count, int start, final Map<String, Object> parameters, final List<JiveItem> items, Class<JiveItem> dataType) {
        activity.runOnUiThread(() -> {
                // If #resetContextMenu has been called while we were in the main looper #contextMenuViewHolder will be null, so skip the items
                if (contextMenuViewHolder != null) {
                    contextMenuViewHolder.contextMenuButton.setVisibility(View.VISIBLE);
                    contextMenuViewHolder.contextMenuLoading.setVisibility(View.GONE);
                    showContextMenu(contextMenuViewHolder, items);
                }
        });
    }

    public void resetContextMenu() {
        if (contextMenuViewHolder != null) {
            contextMenuViewHolder.contextMenuButton.setVisibility(View.VISIBLE);
            contextMenuViewHolder.contextMenuLoading.setVisibility(View.GONE);
        }

        if (contextPopup != null) {
            contextPopup.dismiss();
            contextPopup = null;
        }

        contextStack = 0;
        contextMenuViewHolder = null;
    }

    @Override
    public void onDismiss(PopupMenu menu) {
        contextPopup = null;
    }
}
