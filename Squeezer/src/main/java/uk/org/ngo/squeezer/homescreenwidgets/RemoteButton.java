package uk.org.ngo.squeezer.homescreenwidgets;

import android.os.Handler;
import android.provider.Settings;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

import uk.org.ngo.squeezer.NowPlayingActivity;
import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.itemlist.CurrentPlaylistActivity;
import uk.org.ngo.squeezer.itemlist.HomeActivity;
import uk.org.ngo.squeezer.service.IRButton;
import uk.org.ngo.squeezer.service.ISqueezeService;

public enum RemoteButton {
    OPEN((context, service, player) -> {
        service.setActivePlayer(service.getPlayer(player.getId()), false);
        Handler handler = new Handler();
        float animationDelay = Settings.Global.getFloat(context.getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1.0f);
        handler.postDelayed(() -> HomeActivity.show(context), (long) (300 * animationDelay));
    }, R.string.remote_openPlayer, R.drawable.ic_home),
    OPEN_NOW_PLAYING((context, service, player) -> {
        service.setActivePlayer(service.getPlayer(player.getId()), false);
        Handler handler = new Handler();
        float animationDelay = Settings.Global.getFloat(context.getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1.0f);
        handler.postDelayed(() -> NowPlayingActivity.show(context), (long) (300 * animationDelay));
    }, R.string.remote_openNowPlaying, R.drawable.ic_action_nowplaying),
    OPEN_CURRENT_PLAYLIST((context, service, player) -> {
        service.setActivePlayer(service.getPlayer(player.getId()), false);
        Handler handler = new Handler();
        float animationDelay = Settings.Global.getFloat(context.getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1.0f);
        handler.postDelayed(() -> CurrentPlaylistActivity.show(context), (long) (300 * animationDelay));
    }, R.string.remote_openCurrentPlaylist, R.drawable.ic_action_playlist),
    POWER(ISqueezeService::togglePower, R.string.remote_powerDescription, R.drawable.ic_action_power_settings_new),
    NEXT(ISqueezeService::nextTrack, R.string.remote_nextDescription, R.drawable.ic_action_next),
    PREVIOUS(ISqueezeService::previousTrack, R.string.remote_previousDescription, R.drawable.ic_action_previous),
    PLAY(ISqueezeService::togglePausePlay, R.string.remote_pausePlayDescription, R.drawable.ic_action_play),
    PRESET_1((context, service, player) -> service.button(player, IRButton.playPreset_1), R.string.remote_preset1Description, "1"),
    PRESET_2((context, service, player) -> service.button(player, IRButton.playPreset_2), R.string.remote_preset2Description, "2"),
    PRESET_3((context, service, player) -> service.button(player, IRButton.playPreset_3), R.string.remote_preset3Description, "3"),
    PRESET_4((context, service, player) -> service.button(player, IRButton.playPreset_4), R.string.remote_preset4Description, "4"),
    PRESET_5((context, service, player) -> service.button(player, IRButton.playPreset_5), R.string.remote_preset5Description, "5"),
    PRESET_6((context, service, player) -> service.button(player, IRButton.playPreset_6), R.string.remote_preset6Description, "6"),

    // Must be last since it's truncated from user-visible lists
    UNKNOWN((context, service, player) -> {
    }, R.string.remote_unknownDescription, "?"),
    ;


    public static final int UNKNOWN_IMAGE = -1;
    private ContextServicePlayerHandler handler;
    private @DrawableRes
    int buttonImage = UNKNOWN_IMAGE;

    private @StringRes
    int description;
    private String buttonText;

    RemoteButton(ContextServicePlayerHandler handler, @StringRes int description) {
        this(handler, description, UNKNOWN_IMAGE);
    }

    RemoteButton(ServicePlayerHandler handler, @StringRes int description, @DrawableRes int buttonImage) {
        this((context, service, player) -> handler.run(service, player), description, buttonImage);
    }

    RemoteButton(ContextServicePlayerHandler handler, @StringRes int description, @DrawableRes int buttonImage) {
        this.handler = handler;
        this.buttonImage = buttonImage;
        this.description = description;
    }

    RemoteButton(ContextServicePlayerHandler handler, @StringRes int description, String buttonText) {
        this.handler = handler;
        this.buttonText = buttonText;
        this.description = description;
    }

    public ContextServicePlayerHandler getHandler() {
        return handler;
    }

    public int getButtonImage() {
        return buttonImage;
    }

    public String getButtonText() {
        return buttonText;
    }

    public @StringRes
    int getDescription() {
        return description;
    }


}
