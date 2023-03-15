package uk.org.ngo.squeezer.framework;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import androidx.annotation.NonNull;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import uk.org.ngo.squeezer.service.ISqueezeService;
import uk.org.ngo.squeezer.service.SqueezeService;

public abstract class BottomSheetDialogFragmentWithService extends BottomSheetDialogFragment {

    protected ISqueezeService service = null;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            service = (ISqueezeService) binder;
            BottomSheetDialogFragmentWithService.this.onServiceConnected();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            service = null;
        }
    };

    protected final void onServiceConnected() {
        service.getEventBus().register(this);
    }

    @Override
    public void onStart() {
        super.onStart();
        requireActivity().bindService(new Intent(getActivity(), SqueezeService.class), serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (service != null) {
            service.getEventBus().unregister(this);
            service.cancelItemListRequests(this);
        }
        requireActivity().unbindService(serviceConnection);
    }

    /**
     * Return the {@link ISqueezeService} this activity is currently bound to.
     *
     * @throws IllegalStateException if service is not set.
     */
    @NonNull
    protected ISqueezeService requireService() {
        if (service == null) {
            throw new IllegalStateException(this + " service is null");
        }
        return service;
    }

}
