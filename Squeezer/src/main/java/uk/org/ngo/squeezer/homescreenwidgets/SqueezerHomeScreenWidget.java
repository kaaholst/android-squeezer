package uk.org.ngo.squeezer.homescreenwidgets;

import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;

import uk.org.ngo.squeezer.Squeezer;
import uk.org.ngo.squeezer.service.LyrionController;
import uk.org.ngo.squeezer.service.event.PlayersChanged;

public class SqueezerHomeScreenWidget extends AppWidgetProvider {

    private static final String TAG = SqueezerHomeScreenWidget.class.getName();

    public static final String PLAYER_ID = "playerId";

    private final Handler uiThreadHandler = new Handler(Looper.getMainLooper());

    /**
     * Returns number of cells needed for given size of the widget.
     *
     * @param size Widget size in dp.
     * @return Size in number of cells.
     */
    protected static int getCellsForSize(int size) {
        int n = 2;
        while (70 * n - 30 < size) {
            ++n;
        }
        return n - 1;
    }

    protected void runOnService(final Context context, final ServiceHandler handler) {
        LyrionController lyrionController = Squeezer.instance().lyrionController();

        // Wait for the PlayersChanged event
        Squeezer.instance().repository().observeForever((PlayersChanged event) -> {
            Log.i(SqueezerHomeScreenWidget.TAG, "Players ready, perform action");
            uiThreadHandler.post(() -> {
                showToastExceptionIfExists(context, runHandlerAndCatchException(handler, lyrionController));
                // Handler was called successfully; service no longer needed
                // TODO remove observer
            });
        });

        // Auto connect if necessary
        if (!lyrionController.isConnected()) {
            Log.i(SqueezerHomeScreenWidget.TAG, "SqueezeService wasn't connected, connecting...");
            lyrionController.startConnect(false);
        }
    }

    protected void showToastExceptionIfExists(Context context, @Nullable Exception possibleException) {
        if (possibleException != null) {
            Toast.makeText(context, possibleException.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private @Nullable
    Exception runHandlerAndCatchException(ServiceHandler handler, LyrionController lyrionController) {
        try {
            handler.run(lyrionController);
            return null;
        } catch (Exception ex) {
            Log.e(SqueezerHomeScreenWidget.TAG, "Exception while handling serviceHandler", ex);
            return ex;
        }
    }

    protected void runOnPlayer(final Context context, final String playerId, final ContextServicePlayerHandler handler) {
        runOnService(context, service -> handler.run(context, service, service.getPlayer(playerId)));
    }

}
