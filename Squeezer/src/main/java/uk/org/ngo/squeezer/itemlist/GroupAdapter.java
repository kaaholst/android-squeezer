package uk.org.ngo.squeezer.itemlist;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.Squeezer;
import uk.org.ngo.squeezer.Util;
import uk.org.ngo.squeezer.framework.ItemAdapter;
import uk.org.ngo.squeezer.framework.ItemViewHolder;
import uk.org.ngo.squeezer.itemlist.dialog.ArtworkListLayout;
import uk.org.ngo.squeezer.model.JiveItem;
import uk.org.ngo.squeezer.model.Window;

class GroupAdapter extends ItemAdapter<ItemViewHolder<JiveItem>, JiveItem> {
    private static final int SUBLIST_UPDATED = 1;
    private final List<ChildAdapterHolder> childAdapterHolders = new ArrayList<>();

    public GroupAdapter(JiveItemListActivity activity) {
        super(activity);
    }

    @Override
    public ItemViewHolder<JiveItem> createViewHolder(View view, int viewType) {
        return new GroupView(getActivity(), view);
    }


    @Override
    protected int getItemViewType(JiveItem item) {
        return R.layout.group_item;
    }

    @Override
    protected JiveItemListActivity getActivity() {
        return (JiveItemListActivity) super.getActivity();
    }

    @Override
    public void onBindViewHolder(@NonNull ItemViewHolder<JiveItem> holder, int position, @NonNull List<Object> payloads) {
        if (payloads.contains(SUBLIST_UPDATED)) {
            ((GroupView)holder).updateCount(childAdapterHolders.get(position).adapter);
        } else {
            onBindViewHolder(holder, position);
        }
    }

    @Override
    public void update(int count, int start, List<JiveItem> items) {
        super.update(count, start, items);
        for (int i = 0; i < items.size(); i++) {
            JiveItem item = items.get(i);
            ItemAdapter<ItemViewHolder<JiveItem>, JiveItem> childAdapter = ("opml".equals(item.getType())) ? new GroupAdapter(getActivity()) : new JiveItemAdapter(getActivity());
            ChildAdapterHolder childAdapterHolder = new ChildAdapterHolder(childAdapter, this, i);
            childAdapterHolders.add(childAdapterHolder);
            item.inputValue = getActivity().parent.inputValue;
        }
    }

    @Override
    public void clear() {
        super.clear();
        childAdapterHolders.clear();
    }

    static class ChildAdapterHolder {
        boolean ordered = false;
        boolean visible = false;
        private final ItemAdapter<ItemViewHolder<JiveItem>, JiveItem> adapter;

        public ChildAdapterHolder(ItemAdapter<ItemViewHolder<JiveItem>, JiveItem> adapter, GroupAdapter parent, int position) {
            this.adapter = adapter;
            adapter.setItemReceiver((int count, int start, Map<String, Object> parameters, List<JiveItem> items, Class<JiveItem> dataType) -> {
                if (adapter instanceof JiveItemAdapter jiveItemAdapter) {
                    final Window window = JiveItem.extractWindow(Util.getRecord(parameters, "window"), null);
                    if (window != null && window.windowStyle != null) {
                        jiveItemAdapter.setWindowStyle(Squeezer.getPreferences().getAlbumListLayout(), window.windowStyle);
                    }
                }
                parent.notifyItemChanged(position, SUBLIST_UPDATED);
            });
        }
    }

    private class GroupView extends ItemViewHolder<JiveItem>  {
        private final ImageView icon;
        private final TextView text1;
        private final TextView text2;
        private final RecyclerView subList;

        GroupView(@NonNull JiveItemListActivity activity, @NonNull View view) {
            super(activity, view);
            icon = view.findViewById(R.id.icon);
            text1 = view.findViewById(R.id.text1);
            text2 = view.findViewById(R.id.text2);
            subList = view.findViewById(R.id.list);
            itemView.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    ChildAdapterHolder childAdapterHolder = childAdapterHolders.get(position);
                    childAdapterHolder.visible = !childAdapterHolder.visible;
                    notifyItemChanged(position);
                }
            });
        }

        public void updateCount(ItemAdapter<ItemViewHolder<JiveItem>, JiveItem> adapter) {
            ArtworkListLayout listLayout = (adapter instanceof JiveItemAdapter jiveItemAdapter) ? jiveItemAdapter.getListLayout() : ArtworkListLayout.list;
            getActivity().setupListView(subList, listLayout);
            text2.setText(String.valueOf(adapter.getActiveCount()));
            subList.setAdapter(adapter);
        }

        @Override
        public void bindView(JiveItem item) {
            super.bindView(item);
            ChildAdapterHolder childAdapterHolder = childAdapterHolders.get(getBindingAdapterPosition());
            ItemAdapter<ItemViewHolder<JiveItem>, JiveItem> adapter = childAdapterHolder.adapter;

            text1.setText(item.getName());
            updateCount(adapter);

            @DrawableRes int drawableRes = (childAdapterHolder.visible ? R.drawable.ic_keyboard_arrow_up : R.drawable.ic_keyboard_arrow_down);
            icon.setImageDrawable(ContextCompat.getDrawable(itemView.getContext(), drawableRes));
            subList.setVisibility(childAdapterHolder.visible ? View.VISIBLE : View.GONE);
            if (childAdapterHolder.visible && !childAdapterHolder.ordered) {
                childAdapterHolder.ordered = true;
                adapter.setOrderer(pagePosition -> getActivity().requireService().pluginItems(pagePosition, item, item.goAction, adapter));
                adapter.maybeOrderPage(0);
            }
            text2.setVisibility(childAdapterHolder.ordered ? View.VISIBLE : View.GONE);
        }

        @Override
        public JiveItemListActivity getActivity() {
            return (JiveItemListActivity) super.getActivity();
        }
    }
}
