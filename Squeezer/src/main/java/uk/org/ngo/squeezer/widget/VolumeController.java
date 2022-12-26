package uk.org.ngo.squeezer.widget;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.sdsmdg.harjot.crollerTest.Croller;
import com.sdsmdg.harjot.crollerTest.OnCrollerChangeListener;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.dialog.VolumeSettings;
import uk.org.ngo.squeezer.framework.BaseActivity;
import uk.org.ngo.squeezer.service.ISqueezeService;
import uk.org.ngo.squeezer.service.SqueezeService;
import uk.org.ngo.squeezer.service.event.PlayerVolume;

public class VolumeController extends BottomSheetDialogFragment implements OnCrollerChangeListener {
    private static final String TAG = VolumeController.class.getSimpleName();

    private TextView label;
    private CheckBox mute;
    private Croller seekbar;
    private int currentProgress = 0;
    private boolean trackingTouch;

    private ISqueezeService service = null;


    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            service = (ISqueezeService) binder;
            service.getEventBus().register(VolumeController.this);
            showVolumeChanged();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            service = null;
        }
    };


    @Override
    public void onStart() {
        super.onStart();
        requireActivity().bindService(new Intent(getActivity(), SqueezeService.class), serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onStop() {
        super.onStop();
        requireService().getEventBus().unregister(this);
        requireActivity().unbindService(serviceConnection);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.volume_adjust, container, false);

        label = view.findViewById(R.id.label);

        view.findViewById(R.id.settings).setOnClickListener(view1 -> {
            FragmentManager fragmentManager = getParentFragmentManager();
            new VolumeSettings().show(fragmentManager, VolumeSettings.class.getName());
        });

        seekbar = view.findViewById(R.id.level);
        seekbar.setOnCrollerChangeListener(this);

        mute = view.findViewById(R.id.mute);
        mute.setOnClickListener(v -> requireService().toggleMute());

        view.findViewById(R.id.volume_down).setOnClickListener(v -> service.adjustVolume(-1));
        view.findViewById(R.id.volume_up).setOnClickListener(v -> service.adjustVolume(1));

        requireDialog().setOnKeyListener((dialogInterface, keyCode, event) -> {
            switch (event.getAction()) {
                case KeyEvent.ACTION_DOWN:
                    return VolumeKeysDelegate.onKeyDown(keyCode, service);
                case KeyEvent.ACTION_UP:
                    return VolumeKeysDelegate.onKeyUp(keyCode);
                default:
                    return false;
            }
        });

        return view;
    }

    /**
     * Return the {@link ISqueezeService} this activity is currently bound to.
     *
     * @throws IllegalStateException if service is not set.
     */
    @NonNull
    private ISqueezeService requireService() {
        if (service == null) {
            throw new IllegalStateException(this + " service is null");
        }
        return service;
    }

    public void showVolumeChanged() {
        if (trackingTouch) {
            return;
        }

        ISqueezeService.VolumeInfo volumeInfo = requireService().getVolume();

        mute.setChecked(volumeInfo.muted);
        currentProgress = volumeInfo.volume;
        seekbar.setProgress(volumeInfo.volume);
        seekbar.setLabel(String.valueOf(volumeInfo.volume));
        label.setText(volumeInfo.name);

        seekbar.setIndicatorColor(ColorUtils.setAlphaComponent(seekbar.getIndicatorColor(), volumeInfo.muted ? 63 : 255));
        seekbar.setProgressPrimaryColor(ColorUtils.setAlphaComponent(seekbar.getProgressPrimaryColor(), volumeInfo.muted ? 63 : 255));
        seekbar.setProgressSecondaryColor(ColorUtils.setAlphaComponent(seekbar.getProgressSecondaryColor(), volumeInfo.muted ? 63 : 255));
        seekbar.setOnCrollerChangeListener(volumeInfo.muted ? null : this);
        seekbar.setOnTouchListener(volumeInfo.muted ? (view, motionEvent) -> true : null);
    }

    @Override
    public void onProgressChanged(Croller croller, int progress) {
        if (currentProgress != progress) {
            currentProgress = progress;
            seekbar.setLabel(String.valueOf(progress));
            requireService().setVolumeTo(progress);
        }
    }

    @Override
    public void onStartTrackingTouch(Croller croller) {
        trackingTouch = true;
    }

    @Override
    public void onStopTrackingTouch(Croller croller) {
        trackingTouch = false;
    }

    @MainThread
    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onEventMainThread(PlayerVolume event) {
        if (service != null && event.player == service.getActivePlayer()) {
            showVolumeChanged();
        }
    }

    public static void show(BaseActivity activity) {
        VolumeController volumeController = new VolumeController();
        volumeController.show(activity.getSupportFragmentManager(), TAG);
    }

}
