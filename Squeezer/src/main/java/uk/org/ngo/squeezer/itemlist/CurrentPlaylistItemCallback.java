package uk.org.ngo.squeezer.itemlist;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.model.JiveItem;
import uk.org.ngo.squeezer.service.ISqueezeService;
import uk.org.ngo.squeezer.widget.UndoBarController;

public class CurrentPlaylistItemCallback extends ItemTouchHelper.SimpleCallback {
    private final CurrentPlaylistActivity activity;
    private int viewPosition = -1;
    private int itemPosition = -1;
    private final Rect tmpRect = new Rect();
    private final Drawable deleteIcon;

    public CurrentPlaylistItemCallback(@NonNull CurrentPlaylistActivity activity) {
        super(ItemTouchHelper.UP | ItemTouchHelper.DOWN, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);
        this.activity = activity;
        deleteIcon = AppCompatResources.getDrawable(activity, R.drawable.ic_delete);
    }

    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
        int position = target.getBindingAdapterPosition();

        // Move the highlighted song if necessary
        int selectedIndex = activity.getSelectedIndex();
        if (selectedIndex == viewPosition) {
            activity.setSelectedIndex(position);
        } else if (viewPosition < selectedIndex && position >= selectedIndex) {
            activity.setSelectedIndex(selectedIndex - 1);
        } else if (viewPosition > selectedIndex && position <= selectedIndex) {
            activity.setSelectedIndex(selectedIndex + 1);
        }

        // TODO remember moves so we can do them when items arrives
        activity.getItemAdapter().moveItem(viewHolder.getBindingAdapterPosition(), position);
        viewPosition = position;

        return true;
    }

    @Override
    public void onSelectedChanged(@Nullable RecyclerView.ViewHolder viewHolder, int actionState) {
        super.onSelectedChanged(viewHolder, actionState);
        switch (actionState) {
            case ItemTouchHelper.ACTION_STATE_SWIPE:
                break;
            case ItemTouchHelper.ACTION_STATE_DRAG:
                if (viewHolder != null)  {
                    itemPosition = viewPosition = viewHolder.getBindingAdapterPosition();
                }
                break;
            case ItemTouchHelper.ACTION_STATE_IDLE:
                ISqueezeService service = activity.getService();
                if (viewPosition != itemPosition && service != null) {
                    service.playlistMove(itemPosition, viewPosition);
                    activity.skipPlaylistChanged();
                }
                itemPosition = viewPosition = -1;
                break;
        }
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) {
        CurrentPlaylistItemView viewHolder = (CurrentPlaylistItemView) vh;
        final int position = viewHolder.getBindingAdapterPosition();
        final JiveItem item = activity.getItemAdapter().getItem(position);
        activity.getItemAdapter().removeItem(position);
        Context context = viewHolder.itemView.getContext();
        UndoBarController.show(activity, context.getString(R.string.JIVE_POPUP_REMOVING_FROM_PLAYLIST, item.getName()), new UndoBarController.UndoListener() {
            @Override
            public void onUndo() {
                activity.getItemAdapter().insertItem(position, item);
            }

            @Override
            public void onDone() {
                ISqueezeService service = activity.getService();
                if (service != null) {
                    service.playlistRemove(position);
                    activity.skipPlaylistChanged();
                }
            }
        });
    }

    @Override
    public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
        if (dX != 0 && isCurrentlyActive) {
            var itemView = viewHolder.itemView;
            var left = (dX < 0 ? itemView.getWidth() + (int)dX : 0);
            var right = (dX < 0 ? itemView.getWidth() : (int)dX);
            tmpRect.set(left, itemView.getTop(), right, itemView.getBottom());

            if (Math.abs(dX) > deleteIcon.getIntrinsicWidth()) {
                var iconTop = (tmpRect.centerY() - deleteIcon.getIntrinsicHeight() / 2);
                var iconLeft = (tmpRect.centerX() - deleteIcon.getIntrinsicWidth() / 2);
                deleteIcon.setBounds(
                        iconLeft,
                        iconTop,
                        iconLeft + deleteIcon.getIntrinsicWidth(),
                        iconTop + deleteIcon.getIntrinsicHeight()
                );
                deleteIcon.draw(c);
            }
        }
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
    }

}
