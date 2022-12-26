package uk.org.ngo.squeezer.homescreenwidgets;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.widget.RemoteViews;

import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.itemlist.HomeActivity;

/**
 * TODO this will eventually be a player status widget but is currently WIP
 * App Widget Configuration implemented in {@link SqueezerRemoteControlPlayerSelectActivity SqueezerRemoteControlConfigureActivity}
 */
public class SqueezerInfoScreen extends SqueezerHomeScreenWidget {

    private static final String TAG = SqueezerInfoScreen.class.getName();


    private static final String SQUEEZER_REMOTE_OPEN = "squeezeRemoteOpen";


    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager,
                                int appWidgetId) {

        String playerId = SqueezerRemoteControl.loadPlayerId(context, appWidgetId);
        String playerName = SqueezerRemoteControl.loadPlayerName(context, appWidgetId);

        // Construct the RemoteViews object

        // See the dimensions and
        Bundle options = appWidgetManager.getAppWidgetOptions(appWidgetId);

        // Get min width and height.
        int minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
        int minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT);
        RemoteViews views = getRemoteViews(context, minWidth, minHeight);
        appWidgetManager.updateAppWidget(appWidgetId, views);

        Log.d(TAG, "wiring up widget for player " + playerName + " with id " + playerId);
        views.setTextViewText(R.id.squeezerRemote_playerButton, playerName);
        views.setOnClickPendingIntent(R.id.squeezerRemote_playerButton, getPendingSelfIntent(context, SQUEEZER_REMOTE_OPEN, playerId));

        // Instruct the widget manager to update the widget
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    static PendingIntent getPendingSelfIntent(Context context, String action, String playerId) {
        Intent intent = new Intent(context, SqueezerInfoScreen.class);
        intent.setAction(action);
        intent.putExtra(PLAYER_ID, playerId);

        return PendingIntent.getBroadcast(context, playerId.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        // There may be multiple widgets active, so update all of them
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager, int appWidgetId, Bundle newOptions) {

        updateAppWidget(context, appWidgetManager, appWidgetId);
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions);
    }

    /**
     * Determine appropriate view based on row or column provided.
     *
     * @param minWidth
     * @param minHeight
     * @return
     */
    private static RemoteViews getRemoteViews(Context context, int minWidth, int minHeight) {
        // First find out rows and columns based on width provided.
        int rows = getCellsForSize(minHeight);
        int columns = getCellsForSize(minWidth);
        // Now you changing layout base on you column count
        // In this code from 1 column to 4
        // you can make code for more columns on your own.
        switch (columns) {
            default:
                return new RemoteViews(context.getPackageName(), R.layout.squeezer_remote_control);
        }
    }

    public void onReceive(final Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent.getAction();
        final String playerId = intent.getStringExtra(PLAYER_ID);


        Log.d(TAG, "recieved intent with action " + action + " and playerid " + playerId);

        if (SQUEEZER_REMOTE_OPEN.equals(action)) {
            runOnService(context, service -> {
                Log.d(TAG, "setting active player: " + playerId);
                service.setActivePlayer(service.getPlayer(playerId));
                Handler handler = new Handler();
                float animationDelay = Settings.Global.getFloat(context.getContentResolver(),
                        Settings.Global.ANIMATOR_DURATION_SCALE, 1.0f);
                handler.postDelayed(() -> HomeActivity.show(context), (long) (300 * animationDelay));
            });

        }
    }


    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        // When the user deletes the widget, delete the preference associated with it.
        for (int appWidgetId : appWidgetIds) {
            SqueezerRemoteControl.deletePrefs(context, appWidgetId);
        }
    }

}

