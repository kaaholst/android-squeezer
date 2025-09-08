package uk.org.ngo.squeezer.itemlist;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.ActionBar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.framework.BaseActivity;
import uk.org.ngo.squeezer.model.Alarm;
import uk.org.ngo.squeezer.model.AlarmPlaylist;
import uk.org.ngo.squeezer.widget.ViewUtilities;

public class AlarmPlaylistActivity extends BaseActivity {
    static final int GET_ALARM_PLAYLIST = 1;
    static final String ALARM_PLAYLIST = "ALARM_PLAYLIST";
    private static final String ALARM = "alarm";
    private static final String PLAYLISTS = "playlists";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Alarm alarm = getIntent().getParcelableExtra(ALARM);
        List<AlarmPlaylist> alarmPlaylists = getIntent().getParcelableArrayListExtra(PLAYLISTS);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.list_activity_layout);

        AlarmPlayListCategoryAdapter adapter = new AlarmPlayListCategoryAdapter(this, alarm, alarmPlaylists);
        RecyclerView listView = requireView(R.id.item_list);
        listView.setAdapter(adapter);
        listView.setLayoutManager(new LinearLayoutManager(this));

        setSupportActionBar(requireView(R.id.toolbar));
        ViewUtilities.setInsetsListener(requireView(R.id.toolbar), true, false, false);
        ViewUtilities.setInsetsListener(listView, false, true, false);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setHomeAsUpIndicator(R.drawable.ic_keyboard_arrow_left);
        }
    }

    public static void show(Activity context, Alarm alarm, List<AlarmPlaylist> alarmPlaylists) {
        Intent intent = new Intent(context, AlarmPlaylistActivity.class);
        intent.putExtra(ALARM, alarm);
        intent.putParcelableArrayListExtra(PLAYLISTS, new ArrayList<>(alarmPlaylists));
        context.startActivityForResult(intent, GET_ALARM_PLAYLIST);
    }
}
