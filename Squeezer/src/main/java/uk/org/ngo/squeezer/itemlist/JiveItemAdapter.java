package uk.org.ngo.squeezer.itemlist;

import android.view.View;

import androidx.annotation.NonNull;

import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller;

import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.framework.ItemAdapter;
import uk.org.ngo.squeezer.framework.ItemViewHolder;
import uk.org.ngo.squeezer.itemlist.dialog.ArtworkListLayout;
import uk.org.ngo.squeezer.model.JiveItem;

class JiveItemAdapter extends ItemAdapter<ItemViewHolder<JiveItem>, JiveItem> implements RecyclerViewFastScroller.OnPopupTextUpdate {

    public JiveItemAdapter(JiveItemListActivity activity) {
        super(activity);
    }

    @Override
    public ItemViewHolder<JiveItem> createViewHolder(View view, int viewType) {
        if (viewType == R.layout.grid_item_pending || viewType == R.layout.list_item_pending) {
            return new JiveItemViewPending(getActivity(), view);
        } else if (viewType == R.layout.slider_item) {
            return new SliderView(getActivity(), view);
        } else {
            return new JiveItemView(getActivity(), view);
        }
    }

    @Override
    protected int getItemViewType(JiveItem item) {
        if (item == null) {
            return (getActivity().getListLayout() == ArtworkListLayout.grid) ? R.layout.grid_item_pending : R.layout.list_item_pending;
        }
        return item.hasSlider()
                ? R.layout.slider_item
                : (getActivity().getListLayout() == ArtworkListLayout.grid) ? R.layout.grid_item : R.layout.list_item;
    }

    @Override
    protected JiveItemListActivity getActivity() {
        return (JiveItemListActivity) super.getActivity();
    }

    @NonNull
    @Override
    public CharSequence onChange(int position) {
        JiveItem item = getItem(position);
        return (item != null ? item.textkey : "");
    }
}
