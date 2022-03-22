package uk.org.ngo.squeezer.homescreenwidgets;

import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;

import org.greenrobot.eventbus.Subscribe;

import uk.org.ngo.squeezer.service.ISqueezeService;
import uk.org.ngo.squeezer.service.SqueezeService;
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
        boolean bound = context.getApplicationContext().bindService(new Intent(context, SqueezeService.class), new ServiceConnection() {
            public void onServiceConnected(ComponentName name, IBinder service1) {
                final ServiceConnection serviceConnection = this;

                if (name != null && service1 instanceof ISqueezeService) {
                    Log.i(SqueezerHomeScreenWidget.TAG, "onServiceConnected connected to ISqueezeService");
                    final ISqueezeService squeezeService = (ISqueezeService) service1;

                    // Wait for the PlayersChanged event
                    squeezeService.getEventBus().register(new Object() {
                        @Subscribe(sticky = true)
                        public void onEvent(PlayersChanged event) {
                            squeezeService.getEventBus().unregister(this);
                            Log.i(SqueezerHomeScreenWidget.TAG, "Players ready, perform action");
                            uiThreadHandler.post(() -> {
                                showToastExceptionIfExists(context, runHandlerAndCatchException(handler, squeezeService));
                                // Handler was called successfully; service no longer needed
                                context.unbindService(serviceConnection);
                            });
                        }
                    });

                    // Auto connect if necessary
                    if (!squeezeService.isConnected()) {
                        Log.i(SqueezerHomeScreenWidget.TAG, "SqueezeService wasn't connected, connecting...");
                        squeezeService.startConnect(false);
                    }
                }
            }

            public void onServiceDisconnected(ComponentName name) {
                Log.i(SqueezerHomeScreenWidget.TAG, "service disconnected");
            }
        }, Context.BIND_AUTO_CREATE);

        if (!bound)
            Log.e(SqueezerHomeScreenWidget.TAG, "Squeezer service not bound");
    }

    protected void showToastExceptionIfExists(Context context, @Nullable Exception possibleException) {
        if (possibleException != null) {
            Toast.makeText(context, possibleException.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private @Nullable
    Exception runHandlerAndCatchException(ServiceHandler handler, ISqueezeService squeezeService) {
        try {
            handler.run(squeezeService);
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
