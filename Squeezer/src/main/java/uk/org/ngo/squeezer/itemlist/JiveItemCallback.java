package uk.org.ngo.squeezer.itemlist;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import uk.org.ngo.squeezer.Preferences;
import uk.org.ngo.squeezer.Squeezer;
import uk.org.ngo.squeezer.itemlist.dialog.ArtworkListLayout;
import uk.org.ngo.squeezer.model.Action;
import uk.org.ngo.squeezer.model.JiveItem;
import uk.org.ngo.squeezer.model.PlayableItemAction;

public class JiveItemCallback extends ItemTouchHelper.Callback {
    private final JiveItemListActivity activity;
    private final PlayableItemAction swipeRightAction;
    private final PlayableItemAction swipeLeftAction;
    private final int margin;
    private final TextPaint textPaint;

    public JiveItemCallback(@NonNull JiveItemListActivity activity) {
        this.activity = activity;
        Preferences preferences = Squeezer.getPreferences();
        swipeRightAction = preferences.getSwipeRightAction();
        swipeLeftAction = preferences.getSwipeLeftAction();
        DisplayMetrics displayMetrics = activity.getResources().getDisplayMetrics();
        float textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 15, displayMetrics);
        margin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, displayMetrics);
        textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(activity.getResources().getColor(activity.getAttributeValue(android.R.attr.colorForeground)));
        textPaint.setTextSize(textSize);
    }

    @Override
    public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
        JiveItem item = ((JiveItemView) viewHolder).getItem();
        int swipeFlags = 0;
        if (activity.getListLayout() == ArtworkListLayout.list && swipeRightAction.action(item) != null) swipeFlags |= ItemTouchHelper.RIGHT;
        if (activity.getListLayout() == ArtworkListLayout.list && swipeLeftAction.action(item) != null) swipeFlags |= ItemTouchHelper.LEFT;
        return makeMovementFlags(0, swipeFlags);
    }

    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
        return false;
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) {
        JiveItemView itemView = (JiveItemView) vh;
        PlayableItemAction a = (direction == ItemTouchHelper.RIGHT ? swipeRightAction : swipeLeftAction);
        JiveItem item = itemView.getItem();
        activity.action(item, a.action(item));
        itemView.getAdapter().notifyItemChanged(itemView.getBindingAdapterPosition());
    }

    @Override
    public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
        if (dX != 0 && isCurrentlyActive) {
            var itemView = viewHolder.itemView;
            int textLeft = (dX > 0 ? itemView.getLeft() + margin : itemView.getRight() + (int) dX - margin);
            int textTop = (itemView.getTop() + (itemView.getBottom() - itemView.getTop()) / 2 + (int) textPaint.getTextSize() / 2);
            PlayableItemAction a = (dX > 0 ? swipeRightAction : swipeLeftAction);
            CharSequence ellipsized = TextUtils.ellipsize(a.getText(activity), textPaint, Math.abs(dX), TextUtils.TruncateAt.END);
            c.drawText(ellipsized, 0, ellipsized.length(), textLeft, textTop, textPaint);
        }
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
    }

}
