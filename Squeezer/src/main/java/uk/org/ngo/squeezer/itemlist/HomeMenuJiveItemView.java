package uk.org.ngo.squeezer.itemlist;

import android.view.View;

import uk.org.ngo.squeezer.Preferences;
import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.Squeezer;
import uk.org.ngo.squeezer.framework.ItemAdapter;
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
    }

    @Override
    public void bindView(JiveItem item) {
        super.bindView(item);
        if (new Preferences(itemView.getContext()).getCustomizeHomeMenuMode() == Preferences.CustomizeHomeMenuMode.ARCHIVE) {
            itemView.setOnLongClickListener(view -> {
                if (!item.getId().equals(JiveItem.ARCHIVE.getId())) {
                    if (!item.getNode().equals(JiveItem.ARCHIVE.getId())) {
                        if (mHomeMenuActivity.getService().isInArchive(item)) {
                            mHomeMenuActivity.showDisplayMessage(R.string.MENU_IS_SUBMENU_IN_ARCHIVE);
                            return true;
                        }
                    }
                    mItemAdapter.removeItem(getAdapterPosition());
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
            });
        } else {
            itemView.setOnLongClickListener(null);
        }
    }

}
