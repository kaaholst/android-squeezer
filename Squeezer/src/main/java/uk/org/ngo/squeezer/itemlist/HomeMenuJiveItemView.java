package uk.org.ngo.squeezer.itemlist;

import android.view.View;

import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.framework.ItemAdapter;
import uk.org.ngo.squeezer.framework.ItemViewHolder;
import uk.org.ngo.squeezer.model.JiveItem;
import uk.org.ngo.squeezer.service.HomeMenuHandling;
import uk.org.ngo.squeezer.service.ISqueezeService;
import uk.org.ngo.squeezer.widget.UndoBarController;


/*
 Class for the long click listener that puts menu items into the Archive node and provides an UndoBar.
 */

public class HomeMenuJiveItemView extends JiveItemView {

    ItemAdapter<ItemViewHolder<JiveItem>, JiveItem> mItemAdapter;

    public HomeMenuJiveItemView(HomeMenuActivity homeMenuActivity, View view, ItemAdapter<ItemViewHolder<JiveItem>, JiveItem> adapter) {
        super(homeMenuActivity, view);
        mItemAdapter = adapter;
    }

    @Override
    public void bindView(JiveItem item) {
        super.bindView(item);

        // archive DISABLED
        if (isArchiveActive) {
            itemView.setOnLongClickListener(view -> setArchive(item));
        } else if (isShortcutsActive) {
            itemView.setOnLongClickListener(view -> setShortcut(item));
        } else { // no archive and no shortcuts
            itemView.setOnLongClickListener(null);
        }
    }

    private boolean setArchive(JiveItem item) {
        if (!item.getId().equals(JiveItem.ARCHIVE.getId())) {  // not the Archive node itself
            ISqueezeService service = getActivity().requireService();
            if (!item.getNode().equals(JiveItem.ARCHIVE.getId())) {  // not INSIDE archive node
                if (service.isInArchive(item)) {
                    getActivity().showDisplayMessage(R.string.MENU_IS_SUBMENU_IN_ARCHIVE);
                    return true;
                }
                if (service.getHomeMenuHandling().isCustomShortcut(item)) {
                    if (isShortcutsActive) {
                        removeShortcut(item);
                    }
                    return true; // Don't show UndoBar for shortcuts
                } else {
//                  is not a shortcut, remove the item and bring up UndoBar
                    mItemAdapter.removeItem(getBindingAdapterPosition());
                }
            } else {
                mItemAdapter.removeItem(getBindingAdapterPosition()); // remove an item inside the archive
            }

            UndoBarController.show(getActivity(), R.string.MENU_ITEM_MOVED, new UndoBarController.UndoListener() {
                @Override
                public void onUndo() {
                    service.toggleArchiveItem(item);
                    service.triggerHomeMenuEvent();
                }

                @Override
                public void onDone() {
                }
            });

            if ((service.toggleArchiveItem(item))) {
                // TODO: Do not instantly show the next screen or put UndoBar onto next screen
                HomeActivity.show(getActivity());
                getActivity().showDisplayMessage(R.string.ARCHIVE_NODE_REMOVED);
            }
        } else {
            getActivity().showDisplayMessage(R.string.ARCHIVE_CANNOT_BE_ARCHIVED);
        }
        return true;
    }

    private boolean setShortcut(JiveItem item) {
        HomeMenuHandling homeMenuHandling = getActivity().requireService().getHomeMenuHandling();
        if (homeMenuHandling.isCustomShortcut(item)) {
            removeShortcut(item);
        }
        return true;
    }

    private void removeShortcut(JiveItem item) {
        HomeMenuHandling homeMenuHandling = getActivity().requireService().getHomeMenuHandling();
        mItemAdapter.removeItem(getBindingAdapterPosition());
        getActivity().showDisplayMessage(R.string.CUSTOM_SHORTCUT_REMOVED);
        getActivity().requireService().removeCustomShortcut(item);
        mPreferences.saveShortcuts(homeMenuHandling.getCustomShortcuts());
    }
}