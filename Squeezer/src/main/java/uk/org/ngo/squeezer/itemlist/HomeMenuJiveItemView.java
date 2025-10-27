package uk.org.ngo.squeezer.itemlist;

import android.view.View;

import androidx.annotation.NonNull;

import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.framework.ItemAdapter;
import uk.org.ngo.squeezer.framework.ItemViewHolder;
import uk.org.ngo.squeezer.itemlist.dialog.ArtworkListLayout;
import uk.org.ngo.squeezer.model.JiveItem;
import uk.org.ngo.squeezer.model.Window;
import uk.org.ngo.squeezer.service.HomeMenuHandling;
import uk.org.ngo.squeezer.service.ISqueezeService;
import uk.org.ngo.squeezer.widget.UndoBarController;


/*
 Class for the long click listener that puts menu items into the Archive node and provides an UndoBar.
 */

public class HomeMenuJiveItemView extends JiveItemView {

    public HomeMenuJiveItemView(@NonNull HomeMenuActivity homeMenuActivity, @NonNull View view) {
        this(homeMenuActivity, homeMenuActivity.window.windowStyle, homeMenuActivity.getListLayout(), view);
    }

    public HomeMenuJiveItemView(@NonNull HomeMenuActivity activity, Window.WindowStyle windowStyle, ArtworkListLayout listLayout, @NonNull View view) {
        super(activity, windowStyle, listLayout, view);
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
            if (service.getHomeMenuHandling().isCustomShortcut(item)) {
                if (isShortcutsActive) {
                    removeShortcut(item);
                }
                return true; // Don't show UndoBar for shortcuts
            }

            final int position = getBindingAdapterPosition();
            ItemAdapter<ItemViewHolder<JiveItem>, JiveItem> adapter = getAdapter();
            adapter.removeItem(position);
            UndoBarController.show(getActivity(), R.string.MENU_ITEM_MOVED, new UndoBarController.UndoListener() {
                @Override
                public void onUndo() {
                    adapter.insertItem(position, item);
                }

                @Override
                public void onDone() {
                    service.toggleArchiveItem(item);
                }
            });
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
        getActivity().getItemAdapter().removeItem(getBindingAdapterPosition());
        getActivity().showDisplayMessage(R.string.CUSTOM_SHORTCUT_REMOVED);
        getActivity().requireService().removeCustomShortcut(item);
    }
}