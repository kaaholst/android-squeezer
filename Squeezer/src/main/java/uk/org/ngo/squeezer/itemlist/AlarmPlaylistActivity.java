package uk.org.ngo.squeezer.itemlist;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;

import java.util.ArrayList;
import java.util.List;

import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.framework.BaseListActivity;
import uk.org.ngo.squeezer.framework.ItemAdapter;
import uk.org.ngo.squeezer.model.Alarm;
import uk.org.ngo.squeezer.model.AlarmPlaylist;
import uk.org.ngo.squeezer.service.ISqueezeService;

public class AlarmPlaylistActivity extends BaseListActivity<AlarmPlayListCategoryAdapter.ViewHolder, AlarmPlayListCategoryAdapter.PlayListCategory> {
    static final int GET_ALARM_PLAYLIST = 1;
    static final String ALARM_PLAYLIST = "ALARM_PLAYLIST";
    private static final String ALARM = "alarm";
    private static final String PLAYLISTS = "playlists";

    private Alarm alarm;
    private List<AlarmPlaylist> alarmPlaylists;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        alarm = getIntent().getParcelableExtra(ALARM);
        alarmPlaylists = getIntent().getParcelableArrayListExtra(PLAYLISTS);
        super.onCreate(savedInstanceState);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setHomeAsUpIndicator(R.drawable.ic_keyboard_arrow_left);
        }
    }

    @Override
    protected ItemAdapter<AlarmPlayListCategoryAdapter.ViewHolder, AlarmPlayListCategoryAdapter.PlayListCategory> createItemListAdapter() {
        return new AlarmPlayListCategoryAdapter(this, alarm, alarmPlaylists);
    }

    @Override
    protected boolean needPlayer() {
        return true;
    }

    @Override
    protected void orderPage(@NonNull ISqueezeService service, int start) {
    }

    public static void show(Activity context, Alarm alarm, List<AlarmPlaylist> alarmPlaylists) {
        Intent intent = new Intent(context, AlarmPlaylistActivity.class);
        intent.putExtra(ALARM, alarm);
        intent.putParcelableArrayListExtra(PLAYLISTS, new ArrayList<>(alarmPlaylists));
        context.startActivityForResult(intent, GET_ALARM_PLAYLIST);
    }

    @Override
    protected int getContentView() {
        return R.layout.item_list;
    }
}
