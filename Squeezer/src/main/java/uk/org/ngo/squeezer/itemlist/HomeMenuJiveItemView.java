package uk.org.ngo.squeezer.itemlist;

import android.view.View;

import uk.org.ngo.squeezer.Preferences;
import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.Squeezer;
import uk.org.ngo.squeezer.framework.ItemAdapter;
import uk.org.ngo.squeezer.model.CustomJiveItemHandling;
import uk.org.ngo.squeezer.framework.ItemViewHolder;
import uk.org.ngo.squeezer.model.JiveItem;
import uk.org.ngo.squeezer.widget.UndoBarController;


/*
 Class for the long click listener that puts menu items into the Archive node and provides an UndoBar.
 */

public class HomeMenuJiveItemView extends JiveItemView {

    HomeMenuActivity mHomeMenuActivity;
    ItemAdapter<ItemViewHolder<JiveItem>, JiveItem> mItemAdapter;

    public HomeMenuJiveItemView(HomeMenuActivity homeMenuActivity, View view, ItemAdapter<ItemViewHolder<JiveItem>, JiveItem> adapter) {
        super(homeMenuActivity, view);
        mHomeMenuActivity = homeMenuActivity;
        mItemAdapter = adapter;
        if (mCustomJiveItemHandling == null) {
            mCustomJiveItemHandling = new CustomJiveItemHandling(getActivity());
        }
    }

    @Override
    public void bindView(JiveItem item) {
        super.bindView(item);
        final boolean isArchiveActive = new Preferences(itemView.getContext()).getCustomizeHomeMenuMode() == Preferences.CustomizeHomeMenuMode.ARCHIVE;
        final boolean isShortcutsActive = new Preferences(itemView.getContext()).getCustomizeShortcutsMode() == Preferences.CustomizeShortcutsMode.ENABLED;

        if (isArchiveActive) {
            itemView.setOnLongClickListener(view -> setArchive(item, isShortcutsActive));
        } else { // archive DISABLED
            if (isShortcutsActive) {
                itemView.setOnLongClickListener(view -> setShortcuts(item));
            } else { // no archive and no shortcuts
                itemView.setOnLongClickListener(null);
            }
        }
    }

    private boolean setArchive(JiveItem item, boolean isShortcutsActive) {
        if (!item.getId().equals(JiveItem.ARCHIVE.getId())) {  // not the Archive node itself
            if (!item.getNode().equals(JiveItem.ARCHIVE.getId())) {  // not INSIDE archive node
                if (mHomeMenuActivity.getService().isInArchive(item)) {
                    mHomeMenuActivity.showDisplayMessage(R.string.MENU_IS_SUBMENU_IN_ARCHIVE);
                    return true;
                }
                if (mCustomJiveItemHandling.isCustomShortcut(item)) {
                    if (isShortcutsActive) {
                        return removeShortcuts(item);
                    } else {
                        return true; // is shortcut but setting DISABLED, do nothing
                    }
                } else {
//                  is not a shortcut, remove the item and bring up UndoBar
                    mItemAdapter.removeItem(getAdapterPosition());
                }
            } else {
                mItemAdapter.removeItem(getAdapterPosition()); // remove an item inside the archive
            }

            UndoBarController.show(mHomeMenuActivity, R.string.MENU_ITEM_MOVED, new UndoBarController.UndoListener() {
                @Override
                public void onUndo() {
                    mHomeMenuActivity.getService().toggleArchiveItem(item);
                    mHomeMenuActivity.getService().triggerHomeMenuEvent();
                }

                @Override
                public void onDone() {
                }
            });

            if ((mHomeMenuActivity.getService().toggleArchiveItem(item))) {
                // TODO: Do not instantly show the next screen or put UndoBar onto next screen
                HomeActivity.show(Squeezer.getContext());
                mHomeMenuActivity.showDisplayMessage(R.string.ARCHIVE_NODE_REMOVED);
            }
        } else {
            mHomeMenuActivity.showDisplayMessage(R.string.ARCHIVE_CANNOT_BE_ARCHIVED);
        }
        return true;
    }

    private boolean setShortcuts(JiveItem item) {
        if (mCustomJiveItemHandling.isCustomShortcut(item)) {
            return removeShortcuts(item);
        }
        return true;
    }

    private boolean removeShortcuts(JiveItem item) {
        mItemAdapter.removeItem(getAdapterPosition());
        mHomeMenuActivity.showDisplayMessage(R.string.CUSTOM_SHORTCUT_REMOVED);
        mHomeMenuActivity.getService().removeCustomShortcut(item);
        mPreferences.saveShortcuts(mCustomJiveItemHandling.convertShortcuts());
        return true; // don't show UndoBar if Custom Shortcut
    }
}