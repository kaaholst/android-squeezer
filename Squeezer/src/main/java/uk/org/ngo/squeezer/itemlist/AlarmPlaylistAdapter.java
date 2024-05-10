package uk.org.ngo.squeezer.itemlist;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;

import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.framework.BaseActivity;
import uk.org.ngo.squeezer.framework.ItemAdapter;
import uk.org.ngo.squeezer.framework.ItemListActivity;
import uk.org.ngo.squeezer.framework.ItemViewHolder;
import uk.org.ngo.squeezer.model.Alarm;
import uk.org.ngo.squeezer.model.AlarmPlaylist;

class AlarmPlaylistAdapter extends ItemAdapter<ItemViewHolder<AlarmPlaylist>, AlarmPlaylist> {
    private final Alarm alarm;

    public AlarmPlaylistAdapter(ItemListActivity activity, Alarm alarm) {
        super(activity);
        this.alarm = alarm;
    }

    @Override
    public ItemViewHolder<AlarmPlaylist> createViewHolder(View view, int viewType) {
        return new ViewHolder(getActivity(), view);
    }

    @Override
    protected int getItemViewType(AlarmPlaylist item) {
        return R.layout.dropdown_item;
    }

    private class ViewHolder extends ItemViewHolder<AlarmPlaylist> {

        public ViewHolder(@NonNull BaseActivity activity, @NonNull View view) {
            super(activity, view);
        }

        @Override
        public void bindView(AlarmPlaylist item) {
            super.bindView(item);
            ((TextView) itemView).setText(item.getName());
            @AttrRes int background = (item.getId().equals(alarm.getPlayListId())) ? R.attr.currentTrackBackground : R.attr.selectableItemBackground;
            itemView.setBackgroundResource(getActivity().getAttributeValue(background));
            itemView.setOnClickListener(v -> {
                Intent intent = new Intent().putExtra(AlarmPlaylistActivity.ALARM_PLAYLIST, item);
                getActivity().setResult(Activity.RESULT_OK, intent);
                getActivity().finish();
            });
        }
    }
}
