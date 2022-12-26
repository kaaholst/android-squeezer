package uk.org.ngo.squeezer.homescreenwidgets;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.Util;
import uk.org.ngo.squeezer.model.Player;
import uk.org.ngo.squeezer.util.Intents;


/**
 * Implementation of App Widget functionality.
 * App Widget Configuration implemented in {@link SqueezerRemoteControlPlayerSelectActivity SqueezerRemoteControlConfigureActivity}
 */
public class SqueezerRemoteControl extends SqueezerHomeScreenWidget {

    public static final String UNKNOWN_PLAYER = "UNKNOWN_PLAYER";
    private static final String ACTION_PREFIX = "ngo.squeezer.homescreenwidgets.";
    private static final String TAG = SqueezerRemoteControl.class.getName();
    static final String PREFS_NAME = "uk.org.ngo.squeezer.homescreenwidgets.SqueezerRemoteControl";
    static final String PREF_PREFIX_KEY = "squeezerRemote_";
    static final String PREF_SUFFIX_PLAYER_ID = "playerId";
    static final String PREF_SUFFIX_PLAYER_NAME = "playerName";
    static final String PREF_SUFFIX_BUTTON = "button";

    static final String EXTRA_PLAYER = "player";
    static final String EXTRA_REMOTE_BUTTON = "remoteButton";


    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager,
                                int appWidgetId) {

        String playerId = loadPlayerId(context, appWidgetId);
        String playerName = loadPlayerName(context, appWidgetId);
        String action = loadAction(context, appWidgetId);
        RemoteButton button = RemoteButton.valueOf(action);

        // Construct the RemoteViews object
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.squeezer_remote_control);
        appWidgetManager.updateAppWidget(appWidgetId, views);

        Log.d(TAG, "wiring up widget for player " + playerName + " with id " + playerId);
        views.setTextViewText(R.id.squeezerRemote_playerButton, playerName);
        views.setOnClickPendingIntent(R.id.squeezerRemote_playerButton, getPendingSelfIntent(context, RemoteButton.OPEN, playerId));


        int buttonId;
        if (button.getButtonImage() != RemoteButton.UNKNOWN_IMAGE) {
            buttonId = R.id.squeezerRemote_imageButton;
            views.setImageViewBitmap(buttonId, Util.vectorToBitmap(context, button.getButtonImage()));
        } else {
            buttonId = R.id.squeezerRemote_textButton;
            views.setTextViewText(buttonId, button.getButtonText());
        }
        views.setViewVisibility(buttonId, View.VISIBLE);
        views.setContentDescription(buttonId, context.getString(button.getDescription()));
        views.setOnClickPendingIntent(buttonId, getPendingSelfIntent(context, button, playerId));


        // Instruct the widget manager to update the widget
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    static PendingIntent getPendingSelfIntent(Context context, RemoteButton button, String playerId) {
        Intent intent = new Intent(context, SqueezerRemoteControl.class);
        intent.setAction(ACTION_PREFIX + button.name());
        intent.putExtra(PLAYER_ID, playerId);

        return PendingIntent.getBroadcast(context, playerId.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT | Intents.immutablePendingIntent());
    }

    // Read the prefix from the SharedPreferences object for this widget.
    // If there is no preference saved, get the default from a resource
    static String loadPlayerId(Context context, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, 0);
        return prefs.getString(PREF_PREFIX_KEY + appWidgetId + PREF_SUFFIX_PLAYER_ID, UNKNOWN_PLAYER);
    }

    static String loadPlayerName(Context context, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, 0);
        return prefs.getString(PREF_PREFIX_KEY + appWidgetId + PREF_SUFFIX_PLAYER_NAME, UNKNOWN_PLAYER);
    }

    static String loadAction(Context context, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, 0);
        return prefs.getString(PREF_PREFIX_KEY + appWidgetId + PREF_SUFFIX_BUTTON, RemoteButton.UNKNOWN.name());
    }

    static void deletePrefs(Context context, int appWidgetId) {
        SharedPreferences.Editor prefs = context.getSharedPreferences(PREFS_NAME, 0).edit();
        prefs.remove(PREF_PREFIX_KEY + appWidgetId + PREF_SUFFIX_PLAYER_ID);
        prefs.remove(PREF_PREFIX_KEY + appWidgetId + PREF_SUFFIX_PLAYER_NAME);
        prefs.remove(PREF_PREFIX_KEY + appWidgetId + PREF_SUFFIX_BUTTON);
        prefs.apply();
    }

    public static void savePrefs(Context context, Intent intent) {
        SharedPreferences.Editor prefs = context.getSharedPreferences(SqueezerRemoteControl.PREFS_NAME, Context.MODE_PRIVATE).edit();

        Player player = intent.getParcelableExtra(EXTRA_PLAYER);
        RemoteButton button = (RemoteButton) intent.getSerializableExtra(EXTRA_REMOTE_BUTTON);

        int widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);

        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);

        prefs.putString(SqueezerRemoteControl.PREF_PREFIX_KEY + widgetId + SqueezerRemoteControl.PREF_SUFFIX_PLAYER_ID, player.getId());
        prefs.putString(SqueezerRemoteControl.PREF_PREFIX_KEY + widgetId + SqueezerRemoteControl.PREF_SUFFIX_PLAYER_NAME, player.getName());
        prefs.putString(SqueezerRemoteControl.PREF_PREFIX_KEY + widgetId + SqueezerRemoteControl.PREF_SUFFIX_BUTTON, button.name());
        prefs.apply();

        SqueezerRemoteControl.updateAppWidget(context, appWidgetManager, widgetId);

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

    public void onReceive(final Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent.getAction();
        final String playerId = intent.getStringExtra(PLAYER_ID);

        Log.d(TAG, "recieved intent with action " + action + " and playerid " + playerId);

        if (action.startsWith(ACTION_PREFIX)) {
            RemoteButton button = RemoteButton.valueOf(action.substring(ACTION_PREFIX.length()));
            runOnPlayer(context, playerId, button.getHandler());
        }
    }


    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        // When the user deletes the widget, delete the preference associated with it.
        for (int appWidgetId : appWidgetIds) {
            deletePrefs(context, appWidgetId);
        }
    }

}

