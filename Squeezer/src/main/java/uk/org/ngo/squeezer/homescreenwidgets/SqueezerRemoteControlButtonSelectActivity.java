package uk.org.ngo.squeezer.homescreenwidgets;

import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Arrays;
import java.util.function.Consumer;

import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.Util;
import uk.org.ngo.squeezer.framework.BaseActivity;
import uk.org.ngo.squeezer.model.Player;

public class SqueezerRemoteControlButtonSelectActivity extends BaseActivity {

    private static final String TAG = SqueezerRemoteControlButtonSelectActivity.class.getName();

    RecyclerView remoteButtonListView;
    RemoteButton[] remoteButtonListItems = RemoteButton.values();
    ItemAdapter remoteButtonListAdapter;


    private int mAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private Player player;

    /*
    This Activity leverages a base Activity which almost all of squeezer uses, itself adding an
    actionBar, which we don't want on this activity.
     */
    @Override
    protected void addActionBar() {
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        Log.d(TAG, "onCreate");

        setResult(RESULT_CANCELED);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(R.string.configure_select_button);
        }

        setContentView(R.layout.squeezer_remote_control_button_select);


        // Find the widget id from the intent.
        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            mAppWidgetId = extras.getInt(
                    AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
            player = extras.getParcelable(SqueezerRemoteControl.EXTRA_PLAYER);
        }

        // If this activity was started with an intent without an app widget ID, finish with an error.
        if (mAppWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        remoteButtonListView = findViewById(R.id.remoteButtonList);
        remoteButtonListView.setLayoutManager(new LinearLayoutManager(this));
        remoteButtonListAdapter = new ItemAdapter(
                Arrays.stream(remoteButtonListItems).filter(b -> b != RemoteButton.UNKNOWN).toArray(RemoteButton[]::new),
                this::finish);

        remoteButtonListView.setAdapter(remoteButtonListAdapter);


    }

    private class ItemAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private final RemoteButton[] buttons;
        private final Consumer<RemoteButton> clickHandler;

        public ItemAdapter(RemoteButton[] buttons, Consumer<RemoteButton> clickHandler) {
            this.buttons = buttons;
            this.clickHandler = clickHandler;
        }

        public int getItemCount() {
            return buttons.length;
        }

        @Override
        @NonNull
        public RecyclerView.ViewHolder onCreateViewHolder(final @NonNull ViewGroup parent, final int viewType) {
            return new RemoteButtonViewHolder(LayoutInflater.from(parent.getContext()).inflate(viewType, parent, false), clickHandler);
        }

        @Override
        public void onBindViewHolder(final @NonNull RecyclerView.ViewHolder holder, final int position) {
            RemoteButtonViewHolder viewHolder = (RemoteButtonViewHolder) holder;
            viewHolder.bindData(buttons[position]);
        }

        @Override
        public int getItemViewType(final int position) {
            return R.layout.squeezer_remote_control_button_select_item;
        }
    }

    private class RemoteButtonViewHolder extends RecyclerView.ViewHolder {
        private final TextView textView;
        private final ImageView imageView;
        private final Consumer<RemoteButton> clickHandler;

        public RemoteButtonViewHolder(final View itemView, Consumer<RemoteButton> clickHandler) {
            super(itemView);
            textView = itemView.findViewById(R.id.text);
            imageView = itemView.findViewById(R.id.icon);
            this.clickHandler = clickHandler;
        }

        public void bindData(final RemoteButton button) {
            itemView.setOnClickListener(v -> clickHandler.accept(button));
            int buttonImage = button.getButtonImage();
            int description = button.getDescription();

            if (buttonImage != RemoteButton.UNKNOWN_IMAGE) {
                imageView.setImageBitmap(Util.vectorToBitmap(SqueezerRemoteControlButtonSelectActivity.this.getBaseContext(), buttonImage));
            } else {
                final TypedArray a = obtainStyledAttributes(new int[]{R.attr.colorControlNormal});
                final int tintColor = a.getColor(0, 0);
                a.recycle();

                TextDrawable drawable = new TextDrawable(Resources.getSystem(), button.getButtonText(), tintColor);
                imageView.setImageDrawable(drawable);
            }
            textView.setText(description);
        }
    }


    public void finish(RemoteButton button) {
        // Make sure we pass back the original appWidgetId
        Intent resultValue = new Intent();
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
        resultValue.putExtra(SqueezerRemoteControl.EXTRA_PLAYER, player);
        resultValue.putExtra(SqueezerRemoteControl.EXTRA_REMOTE_BUTTON, button);
        setResult(RESULT_OK, resultValue);
        finish();
    }
}
