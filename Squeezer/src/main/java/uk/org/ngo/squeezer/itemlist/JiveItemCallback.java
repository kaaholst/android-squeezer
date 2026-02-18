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

import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.itemlist.dialog.ArtworkListLayout;
import uk.org.ngo.squeezer.model.Action;

public class JiveItemCallback extends ItemTouchHelper.Callback {
    private final JiveItemListActivity activity;
    private final int margin;
    private final TextPaint textPaint;

    public JiveItemCallback(@NonNull JiveItemListActivity activity) {
        this.activity = activity;
        DisplayMetrics displayMetrics = activity.getResources().getDisplayMetrics();
        float textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 15, displayMetrics);
        margin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, displayMetrics);
        textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(activity.getResources().getColor(activity.getAttributeValue(android.R.attr.colorForeground)));
        textPaint.setTextSize(textSize);
    }

    @Override
    public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
        JiveItemView itemView = (JiveItemView) viewHolder;
        int swipeFlags = 0;
        if (activity.getListLayout() == ArtworkListLayout.list && itemView.getItem().insertAction != null) swipeFlags |= ItemTouchHelper.RIGHT;
        if (activity.getListLayout() == ArtworkListLayout.list && itemView.getItem().addAction != null) swipeFlags |= ItemTouchHelper.LEFT;
        return makeMovementFlags(0, swipeFlags);
    }

    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
        return false;
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) {
        JiveItemView itemView = (JiveItemView) vh;
        Action action = direction == ItemTouchHelper.RIGHT ? itemView.getItem().insertAction : itemView.getItem().addAction;
        activity.action(itemView.getItem(), action);
        itemView.getAdapter().notifyItemChanged(itemView.getBindingAdapterPosition());
    }

    @Override
    public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
        if (dX != 0 && isCurrentlyActive) {
            var itemView = viewHolder.itemView;
            int textLeft = (dX > 0 ? itemView.getLeft() + margin : itemView.getRight() + (int) dX - margin);
            int textTop = (itemView.getTop() + (itemView.getBottom() - itemView.getTop()) / 2 + (int) textPaint.getTextSize() / 2);
            String s = activity.getString(dX > 0 ? R.string.PLAY_NEXT : R.string.ADD_TO_END);
            CharSequence ellipsized = TextUtils.ellipsize(s, textPaint, Math.abs(dX), TextUtils.TruncateAt.END);
            c.drawText(ellipsized, 0, ellipsized.length(), textLeft, textTop, textPaint);
        }
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
    }

}
