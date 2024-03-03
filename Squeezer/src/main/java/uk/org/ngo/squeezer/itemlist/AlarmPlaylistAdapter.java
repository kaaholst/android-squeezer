package uk.org.ngo.squeezer.itemlist;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.framework.BaseActivity;
import uk.org.ngo.squeezer.framework.ItemAdapter;
import uk.org.ngo.squeezer.framework.ItemListActivity;
import uk.org.ngo.squeezer.framework.ItemViewHolder;
import uk.org.ngo.squeezer.model.AlarmPlaylist;

class AlarmPlaylistAdapter extends ItemAdapter<ItemViewHolder<AlarmPlaylist>, AlarmPlaylist> {
    public AlarmPlaylistAdapter(ItemListActivity activity) {
        super(activity);
    }

    @Override
    public ItemViewHolder<AlarmPlaylist> createViewHolder(View view, int viewType) {
        return new ViewHolder(getActivity(), view);
    }

    @Override
    protected int getItemViewType(AlarmPlaylist item) {
        return R.layout.dropdown_item;
    }

    private static class ViewHolder extends ItemViewHolder<AlarmPlaylist> {

        public ViewHolder(@NonNull BaseActivity activity, @NonNull View view) {
            super(activity, view);
        }

        @Override
        public void bindView(AlarmPlaylist item) {
            super.bindView(item);
            ((TextView) itemView).setText(item.getName());
            itemView.setOnClickListener(v -> {
                Intent intent = new Intent().putExtra(AlarmPlaylistActivity.ALARM_PLAYLIST, item);
                getActivity().setResult(Activity.RESULT_OK, intent);
                getActivity().finish();
            });
        }
    }
}
