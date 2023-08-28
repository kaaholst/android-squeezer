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
        if (item == null) {
            return R.layout.list_item_pending;
        }
        return R.layout.group_item;
    }

    @Override
    protected JiveItemListActivity getActivity() {
        return (JiveItemListActivity) super.getActivity();
    }

    @Override
    public void update(int count, int start, List<JiveItem> items) {
        super.update(count, start, items);
        for (int i = 0; i < items.size(); i++) {
            JiveItem item = items.get(i);
            ItemAdapter<ItemViewHolder<JiveItem>, JiveItem> childAdapter = ("opml".equals(item.getType())) ? new GroupAdapter(getActivity()) : new ChildAdapter(getActivity());
            ChildAdapterHolder childAdapterHolder = new ChildAdapterHolder(getActivity(), this, i, childAdapter);
            childAdapterHolders.add(childAdapterHolder);
            item.inputValue = getActivity().parent.inputValue;
        }
    }

    @Override
    public void clear() {
        super.clear();
        childAdapterHolders.clear();
    }

    static class ChildAdapterHolder implements IServiceItemListCallback<JiveItem> {
        boolean ordered = false;
        boolean visible = false;
        private final JiveItemListActivity activity;
        private final GroupAdapter parent;
        private final ItemAdapter<ItemViewHolder<JiveItem>, JiveItem> adapter;
        private final int position;

        public ChildAdapterHolder(JiveItemListActivity activity, GroupAdapter parent, int position, ItemAdapter<ItemViewHolder<JiveItem>, JiveItem> adapter) {
            this.activity = activity;
            this.parent = parent;
            this.position = position;
            this.adapter = adapter;
        }

        @Override
        public void onItemsReceived(int count, int start, Map<String, Object> parameters, List<JiveItem> items, Class<JiveItem> dataType) {
            final Window window = JiveItem.extractWindow(Util.getRecord(parameters, "window"), null);
            if (window != null && window.windowStyle != null && adapter instanceof ChildAdapter) {
                ((ChildAdapter)adapter).setWindowStyle(window.windowStyle);
            }
            activity.runOnUiThread(() -> {
                adapter.update(count, start, items);
                parent.notifyItemChanged(position);
            });
        }

        @Override
        public Object getClient() {
            return activity;
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
                int position = getAbsoluteAdapterPosition();
                ChildAdapterHolder childAdapterHolder = childAdapterHolders.get(position);
                childAdapterHolder.visible = !childAdapterHolder.visible;

                notifyItemChanged(position);
            });
        }

        @Override
        public void bindView(JiveItem item) {
            super.bindView(item);
            ChildAdapterHolder childAdapterHolder = childAdapterHolders.get(getAbsoluteAdapterPosition());

            text1.setText(item.getName());
            text2.setText(String.valueOf(childAdapterHolder.adapter.getItemCount()));

            @DrawableRes int drawableRes = (childAdapterHolder.visible ? R.drawable.ic_keyboard_arrow_down : R.drawable.ic_keyboard_arrow_up);
            icon.setImageDrawable(ContextCompat.getDrawable(itemView.getContext(), drawableRes));
            subList.setAdapter(childAdapterHolder.adapter);
            ArtworkListLayout listLayout = (childAdapterHolder.adapter instanceof ChildAdapter) ? ((ChildAdapter) childAdapterHolder.adapter).listLayout : ArtworkListLayout.list;
            if (listLayout == ArtworkListLayout.list) {
                getActivity().addDividerItemDecoration(subList);
            }
            getActivity().setupListView(subList, listLayout);
            subList.setVisibility(childAdapterHolder.visible ? View.VISIBLE : View.GONE);
            if (childAdapterHolder.visible && !childAdapterHolder.ordered) {
                childAdapterHolder.ordered = true;
                getActivity().requireService().pluginItems(0, item, item.goAction, childAdapterHolder);
            }
            text2.setVisibility(childAdapterHolder.ordered ? View.VISIBLE : View.GONE);
        }

        @Override
        public JiveItemListActivity getActivity() {
            return (JiveItemListActivity) super.getActivity();
        }
    }

    private static class ChildAdapter extends ItemAdapter<ItemViewHolder<JiveItem>, JiveItem> {
        private Window.WindowStyle windowStyle = Window.WindowStyle.TEXT_ONLY;
        private ArtworkListLayout listLayout = ArtworkListLayout.list;

        public ChildAdapter(JiveItemListActivity activity) {
            super(activity);
        }

        public void setWindowStyle(Window.WindowStyle windowStyle) {
            this.windowStyle = windowStyle;
            listLayout = JiveItemView.listLayout(Squeezer.getPreferences().getAlbumListLayout(), windowStyle);
        }

        @Override
        public JiveItemListActivity getActivity() {
            return (JiveItemListActivity) super.getActivity();
        }

        @Override
        public ItemViewHolder<JiveItem> createViewHolder(View view, int viewType) {
            return new JiveItemView(getActivity(), windowStyle, Squeezer.getPreferences().getAlbumListLayout(), view);
        }

        @Override
        protected int getItemViewType(JiveItem item) {
            return (listLayout == ArtworkListLayout.list ? R.layout.list_item : R.layout.grid_item);
        }
    }
}
