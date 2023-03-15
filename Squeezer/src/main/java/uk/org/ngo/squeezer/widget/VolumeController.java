package uk.org.ngo.squeezer.widget;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.fragment.app.FragmentManager;

import com.sdsmdg.harjot.crollerTest.Croller;
import com.sdsmdg.harjot.crollerTest.OnCrollerChangeListener;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.dialog.VolumeSettings;
import uk.org.ngo.squeezer.framework.BaseActivity;
import uk.org.ngo.squeezer.framework.BottomSheetDialogFragmentWithService;
import uk.org.ngo.squeezer.service.ISqueezeService;
import uk.org.ngo.squeezer.service.event.PlayerVolume;
import uk.org.ngo.squeezer.service.event.PlayersChanged;

public class VolumeController extends BottomSheetDialogFragmentWithService implements OnCrollerChangeListener {
    private static final String TAG = VolumeController.class.getSimpleName();

    private TextView label;
    private CheckBox mute;
    private Croller seekbar;
    private int currentProgress = 0;
    private boolean trackingTouch;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.volume_adjust, container, false);

        label = view.findViewById(R.id.label);

        view.findViewById(R.id.settings).setOnClickListener(view1 -> {
            if (requireService().getActivePlayer() != null) {
                FragmentManager fragmentManager = getParentFragmentManager();
                new VolumeSettings().show(fragmentManager, VolumeSettings.class.getName());
            }
        });

        seekbar = view.findViewById(R.id.level);
        seekbar.setOnCrollerChangeListener(this);

        mute = view.findViewById(R.id.mute);
        mute.setOnClickListener(v -> requireService().toggleMute());

        view.findViewById(R.id.volume_down).setOnClickListener(v -> requireService().adjustVolume(-1));
        view.findViewById(R.id.volume_up).setOnClickListener(v -> requireService().adjustVolume(1));

        requireDialog().setOnKeyListener((dialogInterface, keyCode, event) -> {
            switch (event.getAction()) {
                case KeyEvent.ACTION_DOWN:
                    return VolumeKeysDelegate.onKeyDown(keyCode, requireService());
                case KeyEvent.ACTION_UP:
                    return VolumeKeysDelegate.onKeyUp(keyCode);
                default:
                    return false;
            }
        });

        return view;
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

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onEvent(PlayersChanged event) {
        showVolumeChanged();
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onEvent(PlayerVolume event) {
        if (service != null && event.player == service.getActivePlayer()) {
            showVolumeChanged();
        }
    }

    public static void show(BaseActivity activity) {
        VolumeController volumeController = new VolumeController();
        volumeController.show(activity.getSupportFragmentManager(), TAG);
    }

}
