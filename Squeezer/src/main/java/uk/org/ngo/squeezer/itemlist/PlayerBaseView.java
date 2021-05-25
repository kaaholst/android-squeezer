package uk.org.ngo.squeezer.itemlist;

import android.view.View;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;

import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.framework.BaseActivity;
import uk.org.ngo.squeezer.framework.ViewParamItemView;
import uk.org.ngo.squeezer.model.Player;
import uk.org.ngo.squeezer.model.PlayerState;

public abstract class PlayerBaseView extends ViewParamItemView<Player> {
    private static final Map<String, Integer> modelIcons = PlayerBaseView.initializeModelIcons();

    public PlayerBaseView(BaseActivity activity, @NonNull View view) {
        super(activity, view);
    }

    private static Map<String, Integer> initializeModelIcons() {
        Map<String, Integer> modelIcons = new HashMap<>();
        modelIcons.put("baby", R.drawable.ic_baby);
        modelIcons.put("boom", R.drawable.ic_boom);
        modelIcons.put("fab4", R.drawable.ic_fab4);
        modelIcons.put("receiver", R.drawable.ic_receiver);
        modelIcons.put("controller", R.drawable.ic_controller);
        modelIcons.put("sb1n2", R.drawable.ic_sb1n2);
        modelIcons.put("sb3", R.drawable.ic_sb3);
        modelIcons.put("slimp3", R.drawable.ic_slimp3);
        modelIcons.put("softsqueeze", R.drawable.ic_softsqueeze);
        modelIcons.put("squeezelite", R.drawable.ic_softsqueeze);
        modelIcons.put("squeezeplay", R.drawable.ic_squeezeplay);
        modelIcons.put("transporter", R.drawable.ic_transporter);
        modelIcons.put("squeezeplayer", R.drawable.ic_squeezeplayer);
        return modelIcons;
    }

    protected static int getModelIcon(String model) {
        Integer icon = modelIcons.get(model);
        return (icon != null ? icon : R.drawable.ic_blank);
    }

    @Override
    public void bindView(Player player) {
        super.bindView(player);
        icon.setImageResource(getModelIcon(player.getModel()));

        PlayerState playerState = player.getPlayerState();

        text1.setEnabled(playerState.isPoweredOn());
        text2.setEnabled(playerState.isPoweredOn());
    }

}
