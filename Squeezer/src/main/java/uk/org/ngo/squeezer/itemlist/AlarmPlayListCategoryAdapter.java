package uk.org.ngo.squeezer.itemlist;

import android.os.Parcel;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.framework.BaseActivity;
import uk.org.ngo.squeezer.framework.ItemAdapter;
import uk.org.ngo.squeezer.framework.ItemListActivity;
import uk.org.ngo.squeezer.framework.ItemViewHolder;
import uk.org.ngo.squeezer.model.Alarm;
import uk.org.ngo.squeezer.model.AlarmPlaylist;
import uk.org.ngo.squeezer.model.Item;

public class AlarmPlayListCategoryAdapter extends ItemAdapter<AlarmPlayListCategoryAdapter.ViewHolder, AlarmPlayListCategoryAdapter.PlayListCategory> {
    private final List<ChildAdapterHolder> childAdapterHolders = new ArrayList<>();

    public AlarmPlayListCategoryAdapter(ItemListActivity activity, Alarm alarm, List<AlarmPlaylist> alarmPlaylists) {
        super(activity);
        PlayListCategory currentCategory = null;
        List<PlayListCategory> categories = new ArrayList<>();
        for (int position = 0; position < alarmPlaylists.size(); position++) {
            AlarmPlaylist alarmPlaylist = alarmPlaylists.get(position);
            if (currentCategory == null || !alarmPlaylist.getCategory().equals(currentCategory.category)) {
                categories.add(currentCategory = new PlayListCategory(alarmPlaylist.getCategory()));
                childAdapterHolders.add(new ChildAdapterHolder(activity));
            }
            currentCategory.playlists.add(alarmPlaylist);
            if (alarmPlaylist.getId() != null && alarmPlaylist.getId().equals(alarm.getPlayListId())) {
                childAdapterHolders.get(childAdapterHolders.size()-1).visible = true;
            }
        }
        update(categories.size(), 0, categories);
        for (int i = 0; i < categories.size(); i++) {
            PlayListCategory category = categories.get(i);
            List<AlarmPlaylist> playlists = category.playlists;
            childAdapterHolders.get(i).adapter.update(playlists.size(), 0, playlists);
        }
    }

    @Override
    public ViewHolder createViewHolder(View view, int viewType) {
        return new ViewHolder(getActivity(), view);
    }

    @Override
    protected int getItemViewType(PlayListCategory item) {
        return R.layout.group_item;
    }

    public class ViewHolder extends ItemViewHolder<PlayListCategory> {

        private final TextView text1;
        private final ImageView icon;
        private final RecyclerView subList;

        public ViewHolder(@NonNull BaseActivity activity, @NonNull View view) {
            super(activity, view);
            text1 = view.findViewById(R.id.text1);
            view.findViewById(R.id.text2).setVisibility(View.GONE);
            icon = view.findViewById(R.id.icon);
            subList = view.findViewById(R.id.list);
            itemView.setOnClickListener(v -> {
                int position = getAbsoluteAdapterPosition();

                ChildAdapterHolder childAdapterHolder = childAdapterHolders.get(position);
                childAdapterHolder.visible = !childAdapterHolder.visible;

                notifyItemChanged(position);
            });
        }

        @Override
        public void bindView(PlayListCategory item) {
            super.bindView(item);
            ChildAdapterHolder childAdapterHolder = childAdapterHolders.get(getAbsoluteAdapterPosition());

            text1.setText(item.category);

            @DrawableRes int drawableRes = (childAdapterHolder.visible ? R.drawable.ic_keyboard_arrow_up : R.drawable.ic_keyboard_arrow_down);
            icon.setImageDrawable(ContextCompat.getDrawable(itemView.getContext(), drawableRes));
            subList.setAdapter(childAdapterHolder.adapter);
            subList.setVisibility(childAdapterHolder.visible ? View.VISIBLE : View.GONE);
        }

    }

    static class ChildAdapterHolder {
        boolean visible = false;
        private final AlarmPlaylistAdapter adapter;

        public ChildAdapterHolder(ItemListActivity activity) {
            adapter = new AlarmPlaylistAdapter(activity);
        }
    }

    public static class PlayListCategory extends Item {
        final String category;
        List<AlarmPlaylist> playlists = new ArrayList<>();

        private PlayListCategory(String category) {
            this.category = category;
        }

        @Override
        public String getName() {
            return category;
        }

        public static final Creator<PlayListCategory> CREATOR = new Creator<>() {
            public PlayListCategory[] newArray(int size) {
                return new PlayListCategory[size];
            }

            public PlayListCategory createFromParcel(Parcel source) {
                return new PlayListCategory(source);
            }
        };

        private PlayListCategory(Parcel source) {
            setId(source.readString());
            category = source.readString();
            source.readTypedList(playlists, AlarmPlaylist.CREATOR);
        }

        @Override
        public void writeToParcel(@NonNull Parcel parcel, int flags) {
            parcel.writeString(getId());
            parcel.writeString(category);
            parcel.writeTypedList(playlists);
        }
    }
}
