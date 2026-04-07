package uk.org.ngo.squeezer.framework;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import uk.org.ngo.squeezer.Squeezer;
import uk.org.ngo.squeezer.service.LyrionController;

public abstract class SqueezerBottomSheetDialogFragment extends BottomSheetDialogFragment {

    protected LyrionController lyrionController = null;

    @Override
    public void onCreate(@Nullable @org.jetbrains.annotations.Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        lyrionController = Squeezer.instance().lyrionController();
        registerObservers();
    }

    protected void registerObservers() {
    }

    @Override
    public void onStart() {
        super.onStart();
        PackageManager packageManager = requireContext().getPackageManager();
        boolean isTelevision = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK);
        if (isTelevision) {
            BottomSheetBehavior.from((View)requireView().getParent()).setState(BottomSheetBehavior.STATE_EXPANDED);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        lyrionController.cancelClientRequests(this);
    }

    /**
     * Return the {@link LyrionController} this activity is currently bound to.
     *
     * @throws IllegalStateException if service is not set.
     */
    @NonNull
    protected LyrionController lyrionController() {
        return lyrionController;
    }

}
