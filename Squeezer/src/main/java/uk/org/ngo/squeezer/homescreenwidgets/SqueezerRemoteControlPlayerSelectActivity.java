package uk.org.ngo.squeezer.homescreenwidgets;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.framework.BaseActivity;
import uk.org.ngo.squeezer.itemlist.PlayerBaseView;
import uk.org.ngo.squeezer.model.Player;
import uk.org.ngo.squeezer.service.ISqueezeService;
import uk.org.ngo.squeezer.service.event.HandshakeComplete;
import uk.org.ngo.squeezer.service.event.PlayerStateChanged;
import uk.org.ngo.squeezer.widget.DividerItemDecoration;

/**
 * The configuration screen for the {@link SqueezerRemoteControl SqueezerRemoteControl} AppWidget.
 */
public class SqueezerRemoteControlPlayerSelectActivity extends BaseActivity {

    private static final String TAG = SqueezerRemoteControlPlayerSelectActivity.class.getName();

    private static final int GET_BUTTON_ACTIVITY = 1001;

    private int mAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private PlayerAdapter adapter;


    @Override
    public void onCreate(Bundle icicle) {
        adapter = new PlayerAdapter();
        super.onCreate(icicle);
        setContentView(R.layout.slim_browser_layout);

        // Set the result to CANCELED.  This will cause the widget host to cancel
        // out of the widget placement if the user presses the back button.
        // Actual result, when successful is below in the onGroupSelected handler
        setResult(RESULT_CANCELED);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(R.string.configure_select_player);
        }

        // Find the widget id from the intent.
        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            mAppWidgetId = extras.getInt(
                    AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        }

        // If this activity was started with an intent without an app widget ID, finish with an error.
        if (mAppWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        RecyclerView listView = findViewById(R.id.item_list);
        listView.setAdapter(adapter);
        listView.setLayoutManager(new LinearLayoutManager(this));
        listView.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
    }

    @Override
    protected void onServiceConnected(@NonNull ISqueezeService service) {
        super.onServiceConnected(service);
        Log.d(TAG, "onServiceConnected: service.isConnected=" + service.isConnected());

        if (!service.isConnected()) {
            service.startConnect(false);
        }
    }

    private class PlayerAdapter extends RecyclerView.Adapter<SqueezerRemoteControlConfigureActivityPlayerView> {
        private List<Player> players = Collections.emptyList();

        @NonNull
        @Override
        public SqueezerRemoteControlConfigureActivityPlayerView onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item, parent, false);
            return new SqueezerRemoteControlConfigureActivityPlayerView(SqueezerRemoteControlPlayerSelectActivity.this, view);
        }

        @Override
        public void onBindViewHolder(@NonNull SqueezerRemoteControlConfigureActivityPlayerView holder, int position) {
            holder.bindView(players.get(position));
        }

        @Override
        public int getItemCount() {
            return players.size();
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onEventMainThread(HandshakeComplete event) {
        updatePlayerList();
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onEventMainThread(PlayerStateChanged event) {
        updatePlayerList();
    }

    protected void updatePlayerList() {
        adapter.players = getService().getPlayers();
        adapter.notifyDataSetChanged();
    }

    /*
    This Activity leverages a base Activity which almost all of squeezer uses, itself adding an
    actionBar, which we don't want on this activity.
     */
    @Override
    protected void addActionBar() {
        Log.d(TAG, "addActionBar");
    }

    public class SqueezerRemoteControlConfigureActivityPlayerView extends PlayerBaseView {
        public SqueezerRemoteControlConfigureActivityPlayerView(BaseActivity activity, View view) {
            super(activity, view);
            setItemViewParams(VIEW_PARAM_ICON);
        }

        @Override
        public void bindView(Player player) {
            super.bindView(player);
            itemView.setOnClickListener(view -> {
                final Context context = SqueezerRemoteControlPlayerSelectActivity.this;

                Intent intent = new Intent(context, SqueezerRemoteControlButtonSelectActivity.class);
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
                intent.putExtra(SqueezerRemoteControl.EXTRA_PLAYER, player);

                startActivityForResult(intent, GET_BUTTON_ACTIVITY);
            });

        }

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case GET_BUTTON_ACTIVITY:
                if (resultCode != RESULT_CANCELED) {
                    SqueezerRemoteControl.savePrefs(this.getBaseContext(), data);

                    Intent resultValue = new Intent();
                    resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
                    setResult(RESULT_OK, resultValue);
                    finish();
                }
                break;
            default:
                Log.w(TAG, "Unknown request code: " + requestCode);
        }
    }
}

