/*
 * Copyright (c) 2012 Google Inc.  All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.org.ngo.squeezer;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.ColorStateList;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.GestureDetectorCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.Slider;
import com.sdsmdg.harjot.crollerTest.Croller;
import com.sdsmdg.harjot.crollerTest.OnCrollerChangeListener;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import uk.org.ngo.squeezer.dialog.AboutDialog;
import uk.org.ngo.squeezer.dialog.CallStateDialog;
import uk.org.ngo.squeezer.dialog.VolumeSettings;
import uk.org.ngo.squeezer.framework.BaseActivity;
import uk.org.ngo.squeezer.framework.ContextMenu;
import uk.org.ngo.squeezer.framework.ViewParamItemView;
import uk.org.ngo.squeezer.itemlist.AlarmsActivity;
import uk.org.ngo.squeezer.itemlist.CurrentPlaylistActivity;
import uk.org.ngo.squeezer.itemlist.IServiceItemListCallback;
import uk.org.ngo.squeezer.itemlist.JiveItemListActivity;
import uk.org.ngo.squeezer.itemlist.PlayerListActivity;
import uk.org.ngo.squeezer.itemlist.PlayerViewLogic;
import uk.org.ngo.squeezer.model.CurrentPlaylistItem;
import uk.org.ngo.squeezer.model.Input;
import uk.org.ngo.squeezer.model.JiveItem;
import uk.org.ngo.squeezer.model.Player;
import uk.org.ngo.squeezer.model.PlayerState;
import uk.org.ngo.squeezer.model.PlayerState.RepeatStatus;
import uk.org.ngo.squeezer.model.PlayerState.ShuffleStatus;
import uk.org.ngo.squeezer.service.ISqueezeService;
import uk.org.ngo.squeezer.service.SqueezeService;
import uk.org.ngo.squeezer.service.event.ActivePlayerChanged;
import uk.org.ngo.squeezer.service.event.ConnectionChanged;
import uk.org.ngo.squeezer.service.event.HandshakeComplete;
import uk.org.ngo.squeezer.service.event.HomeMenuEvent;
import uk.org.ngo.squeezer.service.event.MusicChanged;
import uk.org.ngo.squeezer.service.event.PlayStatusChanged;
import uk.org.ngo.squeezer.service.event.PlayerVolume;
import uk.org.ngo.squeezer.service.event.PlayersChanged;
import uk.org.ngo.squeezer.service.event.PowerStatusChanged;
import uk.org.ngo.squeezer.service.event.RegisterSqueezeNetwork;
import uk.org.ngo.squeezer.service.event.RepeatStatusChanged;
import uk.org.ngo.squeezer.service.event.ShuffleStatusChanged;
import uk.org.ngo.squeezer.service.event.SongTimeChanged;
import uk.org.ngo.squeezer.util.ImageFetcher;
import uk.org.ngo.squeezer.widget.CallStatePermissionLauncher;
import uk.org.ngo.squeezer.widget.OnSwipeListener;

public class NowPlayingFragment extends Fragment  implements OnCrollerChangeListener, CallStateDialog.CallStateDialogHost {

    private static final String TAG = "NowPlayingFragment";

    private BaseActivity mActivity;

    @Nullable
    private ISqueezeService mService = null;

    private TextView albumText;

    private TextView artistAlbumText;

    private TextView artistText;

    private TextView trackText;
    private TextView conductorText;
    private TextView composerText;
    private TextView trackInfo;

    private JiveItem albumItem;
    private JiveItem artistItem;

    private JiveItem conductorItem;
    private JiveItem composerItem;

    @Nullable
    private View btnContextMenu;

    private TextView currentTime;

    private TextView totalTime;
    private boolean showRemainingTime;

    private MenuItem menuItemDisconnect;

    private JiveItem topBarSearch;
    private MenuItem menuItemSearch;

    private MenuItem menuItemPlaylist;

    private MenuItem menuItemPlayers;

    private MenuItem menuItemTogglePower;
    private MenuItem menuItemSleep;
    private MenuItem menuItemSleepAtEndOfSong;
    private MenuItem menuItemCancelSleep;

    private MenuItem menuItemAlarm;

    private MaterialButton playPauseButton;

    @Nullable
    private Button nextButton;

    @Nullable
    private Button prevButton;

    private MaterialButton shuffleButton;

    private MaterialButton repeatButton;

    private ImageView albumArt;

    /** In full-screen mode, shows the current progress through the track. */
    private Slider slider;

    /** In mini-mode, shows the current progress through the track. */
    private ProgressBar mProgressBar;

    // Updating the seekbar
    private boolean updateSeekBar = true;

    // For the large artwork layout
    private MaterialButton muteButton;
    private SeekBar volumeBar;

    // For the small artwork layout
    private CheckBox muteToggle;
    private Croller volumeWheel;
    private int currentProgress = 0;
    private boolean trackingTouch;

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            ConnectivityManager connMgr = (ConnectivityManager) context
                    .getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo networkInfo = connMgr.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            if (networkInfo.isConnected()) {
                Log.v(TAG, "Received WIFI connected broadcast");
                if (!isConnected()) {
                    // Requires a serviceStub. Else we'll do this on the service
                    // connection callback.
                    if (canAutoConnect()) {
                        Log.v(TAG, "Initiated connect on WIFI connected");
                        startVisibleConnection(true);
                    }
                }
            }
        }
    };

    /** Dialog displayed while connecting to the server. */
    private Dialog connectingDialog = null;

    /**
     * Shows the "connecting" dialog if it's not already showing.
     */
    @UiThread
    private void showConnectingDialog() {
        if (connectingDialog == null || !connectingDialog.isShowing()) {
            Squeezer.getPreferences(preferences -> {
                // We may no longer be attached to the parent activity. If so, do nothing.
                if (isAdded()) {
                    return;
                }

                Preferences.ServerAddress serverAddress = preferences.getServerAddress();

                final View view = LayoutInflater.from(mActivity).inflate(R.layout.connecting, null);
                final TextView connectingTo = view.findViewById(R.id.connecting_to);
                connectingTo.setText(getString(R.string.connecting_to_text, serverAddress.serverName()));

                connectingDialog = new MaterialAlertDialogBuilder(mActivity)
                        .setView(view)
                        .setCancelable(false)
                        .show();
            });
        }
    }

    /**
     * Dismisses the "connecting" dialog if it's showing.
     */
    @UiThread
    private void dismissConnectingDialog() {
        if (connectingDialog != null && connectingDialog.isShowing()) {
            connectingDialog.dismiss();
        }
        connectingDialog = null;
    }


    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            Log.v(TAG, "ServiceConnection.onServiceConnected()");
            NowPlayingFragment.this.onServiceConnected((ISqueezeService) binder);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
        }
    };

    private boolean mFullHeightLayout;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mActivity = (BaseActivity) context;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        mActivity.bindService(new Intent(mActivity, SqueezeService.class), serviceConnection, Context.BIND_AUTO_CREATE);
        Log.d(TAG, "did bindService; serviceStub = " + mService);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v;

        mFullHeightLayout = (container.getLayoutParams().height == ViewGroup.LayoutParams.MATCH_PARENT);
        Preferences preferences = Squeezer.getPreferences();
        boolean largeArtwork = preferences.isLargeArtwork();

        if (mFullHeightLayout) {
            v = inflater.inflate(largeArtwork ? R.layout.now_playing_fragment_full_large_artwork : R.layout.now_playing_fragment_full, container, false);

            artistText = v.findViewById(R.id.artistname);
            composerText = v.findViewById(R.id.composer);
            conductorText = v.findViewById(R.id.conductorname);
            albumText = v.findViewById(R.id.albumname);
            trackInfo = v.findViewById(R.id.track_info);
            shuffleButton = v.findViewById(R.id.shuffle);
            repeatButton = v.findViewById(R.id.repeat);
            currentTime = v.findViewById(R.id.currenttime);
            totalTime = v.findViewById(R.id.totaltime);
            showRemainingTime = preferences.isShowRemainingTime();
            slider = v.findViewById(R.id.seekbar);

            if (largeArtwork) {
                albumArt = v.findViewById(R.id.album);
                v.findViewById(R.id.icon).setVisibility(View.GONE);
                muteButton = v.findViewById(R.id.muteButton);
                volumeBar = v.findViewById(R.id.volume_slider);
            } else {
                albumArt = v.findViewById(R.id.icon);
                volumeWheel = v.findViewById(R.id.level);
                muteToggle = v.findViewById(R.id.muteToggle);
            }

            final ViewParamItemView<JiveItem> viewHolder = new ViewParamItemView<>(mActivity, v);
            viewHolder.contextMenuButton.setOnClickListener(view -> {
                CurrentPlaylistItem currentSong = getCurrentSong();
                // This extra check is if user pressed the button before visibility is set to GONE
                if (currentSong != null) {
                    ContextMenu.show(mActivity, currentSong);
                }
            });
            btnContextMenu = viewHolder.contextMenuButtonHolder;
        } else {
            v = inflater.inflate(R.layout.now_playing_fragment_mini, container, false);

            albumArt = v.findViewById(R.id.album);
            mProgressBar = v.findViewById(R.id.progressbar);
            artistAlbumText = v.findViewById(R.id.artistalbumname);
        }

        trackText = v.findViewById(R.id.trackname);
        playPauseButton = v.findViewById(R.id.pause);

        nextButton = v.findViewById(R.id.next);
        prevButton = v.findViewById(R.id.prev);

        // Marquee effect on TextViews only works if they're focused.
        trackText.requestFocus();

        playPauseButton.setOnClickListener(view -> requireService().togglePausePlay());

        nextButton.setOnClickListener(view -> requireService().nextTrack());
        prevButton.setOnClickListener(view -> requireService().previousTrack());

        if (mFullHeightLayout) {
            artistText.setOnClickListener(v1 -> {
                if (artistItem != null) {
                    JiveItemListActivity.show(mActivity, artistItem, artistItem.goAction);
                }
            });

            albumText.setOnClickListener(v12 -> {
                if (albumItem != null) {
                    JiveItemListActivity.show(mActivity, albumItem, albumItem.goAction);
                }
            });

            trackText.setOnClickListener(v13 -> {
                CurrentPlaylistItem song = getCurrentSong();
                if (song != null && topBarSearch != null) {
                    topBarSearch.input.initialText = song.getName();
                    JiveItemListActivity.show(mActivity, topBarSearch, topBarSearch.goAction);
                }
            });

            composerText.setOnClickListener(v14 -> {
                if (composerItem != null) {
                    JiveItemListActivity.show(mActivity, composerItem, composerItem.goAction);
                }
            });

            conductorText.setOnClickListener(v15 -> {
                if (conductorItem != null) {
                    JiveItemListActivity.show(mActivity, conductorItem, conductorItem.goAction);
                }
            });

            final GestureDetectorCompat detector = new GestureDetectorCompat(mActivity, new OnSwipeListener() {
                @Override
                public boolean onSwipeDown() {
                    mActivity.finish();
                    return true;
                }

                @Override
                public boolean onSingleTapUp(MotionEvent e) {
                    View cueParent = (largeArtwork ? albumArt : v);
                    if (mService != null) new CuePanel(requireActivity(), cueParent, mService);
                    return true;
                }
            });
            albumArt.setOnTouchListener((view, event) -> {
                return detector.onTouchEvent(event);
            });

            shuffleButton.setOnClickListener(view -> requireService().toggleShuffle());

            repeatButton.setOnClickListener(view -> requireService().toggleRepeat());

            mActivity.setNotifyVolumePanel(false);
            if (largeArtwork) {
                View volumeButton = v.findViewById(R.id.volume);
                TextView volumeLabel = v.findViewById(R.id.label);

                volumeButton.setOnClickListener(view -> {
                    preferences.setLargeArtwork(false);
                    mActivity.recreate();

                });

                muteButton.setOnClickListener(view -> requireService().toggleMute());
                volumeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                        trackingTouch = true;
                        volumeButton.setVisibility(View.INVISIBLE);
                        volumeLabel.setVisibility(View.VISIBLE);
                        volumeLabel.setText(String.valueOf(seekBar.getProgress()));
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        trackingTouch = false;
                        volumeButton.setVisibility(View.VISIBLE);
                        volumeLabel.setVisibility(View.INVISIBLE);
                    }

                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        if (fromUser) {
                            volumeLabel.setText(String.valueOf(progress));
                            requireService().setVolumeTo(progress);
                        }
                    }
                });
            } else {
                v.findViewById(R.id.down).setOnClickListener(view -> {
                    preferences.setLargeArtwork(true);
                    mActivity.recreate();
                });

                volumeWheel.setOnCrollerChangeListener(this);
                muteToggle.setOnClickListener(view -> requireService().toggleMute());

                v.findViewById(R.id.settings).setOnClickListener(view1 -> {
                    if (requireService().getActivePlayer() != null) {
                        FragmentManager fragmentManager = getParentFragmentManager();
                        new VolumeSettings().show(fragmentManager, VolumeSettings.class.getName());
                    }
                });

                v.findViewById(R.id.volume_down).setOnClickListener(view -> requireService().adjustVolume(-1));
                v.findViewById(R.id.volume_up).setOnClickListener(view -> requireService().adjustVolume(1));
            }

            // Update the time indicator to reflect the dragged thumb position.
            slider.addOnChangeListener((s, value, fromUser) -> {
                if (fromUser) {
                    currentTime.setText(Util.formatElapsedTime((int)value));
                }
            });

            totalTime.setOnClickListener(view -> {
                showRemainingTime = !showRemainingTime;
                Squeezer.getPreferences().setShowRemainingTime(showRemainingTime);
                PlayerState playerState = getPlayerState();
                if (playerState != null) {
                    updateTimeDisplayTo(playerState.getTrackElapsed(), playerState.getCurrentSongDuration());
                }
            });

            slider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
                CurrentPlaylistItem seekingSong;

                // Disable updates when user drags the thumb.
                @Override
                @SuppressLint("RestrictedApi")
                public void onStartTrackingTouch(@NonNull Slider s) {
                    seekingSong = getCurrentSong();
                    updateSeekBar = false;
                }

                // Re-enable updates. If the current song is the same as when
                // we started seeking then jump to the new point in the track,
                // otherwise ignore the seek.
                @Override
                @SuppressLint("RestrictedApi")
                public void onStopTrackingTouch(@NonNull Slider s) {
                    CurrentPlaylistItem thisSong = getCurrentSong();

                    updateSeekBar = true;

                    if (seekingSong == thisSong) {
                        setSecondsElapsed((int)s.getValue());
                    }
                }
            });
        } else {
            int screenWidthDp = getResources().getConfiguration().screenWidthDp;
            if (screenWidthDp < 456) {
                nextButton.setVisibility(View.GONE);
                prevButton.setVisibility(View.GONE);
            }

            final GestureDetectorCompat detector = new GestureDetectorCompat(mActivity, new OnSwipeListener() {
                // Clicking on the layout goes to NowPlayingActivity.
                @Override
                public boolean onSingleTapUp(MotionEvent e) {
                    NowPlayingActivity.show(mActivity);
                    return true;
                }

                // Swipe up on the layout goes to NowPlayingActivity.
                @Override
                public boolean onSwipeUp() {
                    NowPlayingActivity.show(mActivity);
                    return true;
                }
            });
            v.setOnTouchListener((view, event) -> detector.onTouchEvent(event));
        }

        return v;
    }

    @UiThread
    private void updatePlayPauseIcon(@PlayerState.PlayState String playStatus) {
        playPauseButton.setIconResource((PlayerState.PLAY_STATE_PLAY.equals(playStatus)) ? R.drawable.ic_action_pause : R.drawable.ic_action_play);
    }

    @UiThread
    private void updateShuffleStatus(ShuffleStatus shuffleStatus) {
        if (mFullHeightLayout && shuffleStatus != null) {
            shuffleButton.setIconResource(shuffleStatus.getIcon());
            shuffleButton.setIconTint(getTint(shuffleStatus == ShuffleStatus.SHUFFLE_OFF));
        }
    }

    @UiThread
    private void updateRepeatStatus(RepeatStatus repeatStatus) {
        if (mFullHeightLayout && repeatStatus != null) {
            repeatButton.setIconResource(repeatStatus.getIcon());
            repeatButton.setIconTint(getTint(repeatStatus == RepeatStatus.REPEAT_OFF));
        }
    }

    private ColorStateList getTint(boolean off) {
        return AppCompatResources.getColorStateList(mActivity, mActivity.getAttributeValue(off ? R.attr.colorControlNormal : R.attr.colorPrimary));
    }

    @UiThread
    private void updatePlayerMenuItems() {
        // The fragment may no longer be attached to the parent activity.  If so, do nothing.
        if (!isAdded()) {
            return;
        }

        Player player = getActivePlayer();
        PlayerState playerState = player != null ? player.getPlayerState() : null;
        String playerName = player != null ? player.getName() : "";

        if (menuItemTogglePower != null) {
            if (playerState != null && player.isCanpoweroff()) {
                menuItemTogglePower.setTitle(getString(playerState.isPoweredOn() ? R.string.menu_item_poweroff : R.string.menu_item_poweron, playerName));
                menuItemTogglePower.setVisible(true);
            } else {
                menuItemTogglePower.setVisible(false);
            }
        }

        if (menuItemCancelSleep != null) {
            menuItemCancelSleep.setVisible(playerState != null && playerState.getSleepDuration() != 0);
        }

        if (menuItemSleepAtEndOfSong != null) {
            menuItemSleepAtEndOfSong.setVisible(playerState != null && playerState.isPlaying());
        }
    }

    /**
     * Manages the list of connected players in the action bar.
     *
     * @param connectedPlayers A list of players to show. May be empty but not null.
     * @param activePlayer The currently active player. May be null.
     */
    @UiThread
    private void updatePlayerDropDown(@NonNull List<Player> connectedPlayers, @Nullable Player activePlayer) {
        if (!isAdded()) {
            return;
        }

        ActionBar actionBar = mActivity.getSupportActionBar();

        // If there are multiple players connected then show a spinner allowing the user to
        // choose between them.
        if (connectedPlayers.size() > 1) {
            actionBar.setDisplayShowTitleEnabled(false);
            actionBar.setDisplayShowCustomEnabled(true);
            actionBar.setCustomView(R.layout.action_bar_custom_view);
            AutoCompleteTextView spinner = actionBar.getCustomView().findViewById(R.id.player);
            final Context actionBarContext = actionBar.getThemedContext();
            final ArrayAdapter<Player> playerAdapter = new ArrayAdapter<>(actionBarContext, R.layout.dropdown_item, connectedPlayers) {
                @Override
                public @NonNull View getView(int position, View convertView, @NonNull ViewGroup parent) {
                    TextView view = (TextView) requireActivity().getLayoutInflater().inflate(R.layout.dropdown_item, parent, false);
                    view.setText(getItem(position).getName());
                    return view;
                }
            };
            spinner.setAdapter(playerAdapter);
            playerAdapter.notifyDataSetChanged();
            spinner.setText((activePlayer != null) ? activePlayer.getName() : "", false);
            spinner.setOnItemClickListener((adapterView, parent, position, id) -> {
                Player selectedItem = playerAdapter.getItem(position);
                spinner.setText(selectedItem.getName(), false);
                if (getActivePlayer() != selectedItem) {
                    requireService().setActivePlayer(selectedItem);
                }
            });
        } else {
            // 0 or 1 players, disable the spinner, and either show the sole player in the
            // action bar, or the app name if there are no players.
            actionBar.setDisplayShowTitleEnabled(true);
            actionBar.setDisplayShowCustomEnabled(false);

            if (connectedPlayers.size() == 1) {
                actionBar.setTitle(connectedPlayers.get(0).getName());
            } else {
                actionBar.setTitle(R.string.app_name);
            }
        }
    }

    protected void onServiceConnected(@NonNull ISqueezeService service) {
        Log.v(TAG, "Service bound");
        mService = service;

        maybeRegisterCallbacks(mService);

        // Assume they want to connect
        if (canAutoConnect()) {
            startVisibleConnection(true);
        }
    }

    /**
     * Return the {@link ISqueezeService} this activity is currently bound to.
     *
     * @throws IllegalStateException if service is not bound.
     */
    @NonNull
    private ISqueezeService requireService() {
        if (mService == null) {
            throw new IllegalStateException(this + " service is not bound");
        }
        return mService;
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume...");

        if (mService != null) {
            maybeRegisterCallbacks(mService);
        }

        mActivity.registerReceiver(broadcastReceiver, new IntentFilter(
                ConnectivityManager.CONNECTIVITY_ACTION));
    }

    /**
     * Keep track of whether callbacks have been registered
     */
    private boolean mRegisteredCallbacks;

    /**
     * This is called when the service is first connected, and whenever the activity is resumed.
     */
    private void maybeRegisterCallbacks(@NonNull ISqueezeService service) {
        if (!mRegisteredCallbacks) {
            service.getEventBus().register(this);

            mRegisteredCallbacks = true;
        }
    }

    @UiThread
    private void updateTimeDisplayTo(int secondsIn, int secondsTotal) {
        if (mFullHeightLayout) {
            if (updateSeekBar) {
                if (slider.getValueTo() != secondsTotal) {
                    slider.setValueTo(secondsTotal > 0 ? secondsTotal : 1);
                }
                slider.setEnabled(secondsTotal > 0);
                slider.setValue(secondsTotal > 0 ? Math.min(secondsIn, secondsTotal) : 0);
                totalTime.setText(Util.formatElapsedTime(showRemainingTime ? secondsTotal - secondsIn : secondsTotal));
                currentTime.setText(Util.formatElapsedTime(secondsIn));
            }
        } else {
            if (mProgressBar.getMax() != secondsTotal) {
                mProgressBar.setMax(secondsTotal);
            }
            mProgressBar.setProgress(secondsIn);
        }
    }

    /**
     * Update the UI based on the player state. Call this when the active player
     * changes.
     *
     * @param playerState the player state to reflect in the UI.
     */
    @UiThread
    private void updateUiFromPlayerState(@NonNull PlayerState playerState) {
        updateSongInfo(playerState);

        updatePlayPauseIcon(playerState.getPlayStatus());
        updateShuffleStatus(playerState.getShuffleStatus());
        updateRepeatStatus(playerState.getRepeatStatus());
        updatePlayerMenuItems();

        updateVolumeInfo();
    }

    /**
     * Update the UI when the song changes, either because the track has changed, or the
     * active player has changed.
     *
     * @param playerState the player state for the song.
     */
    @UiThread
    private void updateSongInfo(@NonNull PlayerState playerState) {
        updateTimeDisplayTo(playerState.getTrackElapsed(), playerState.getCurrentSongDuration());

        Preferences preferences = Squeezer.getPreferences();
        CurrentPlaylistItem song = playerState.getCurrentSong();
        if (song == null) {
            // Create empty song if this is called (via _HandshakeComplete) before status is received
            song = new CurrentPlaylistItem(new HashMap<>());
        }

        // TODO handle button remapping (buttons in status response)
        if (!song.getName().isEmpty()) {

            // don't remove rew and fwd for remote tracks, because a single track playlist
            // is not an indication that fwd and rwd are invalid actions
            boolean canSkip = !((playerState.getCurrentPlaylistTracksNum() == 1) && !playerState.isRemote());
            nextButton.setEnabled(canSkip);
            prevButton.setEnabled(canSkip);

            boolean addComposerLine = preferences.addComposerLine();
            boolean addConductorLine = preferences.addConductorLine();
            boolean classicalMusicTags = preferences.displayClassicalMusicTags();

            trackText.setText(addComposerLine && !mFullHeightLayout ? Util.joinSkipEmpty(": ", song.songInfo.getComposer(), song.getName()) : song.getName());

            if (mFullHeightLayout) {
                btnContextMenu.setVisibility(View.VISIBLE);

                composerText.setText(song.songInfo.getComposer());
                composerText.setVisibility(addComposerLine && !TextUtils.isEmpty(song.songInfo.getComposer()) ? View.VISIBLE : View.GONE);

                if (classicalMusicTags) {
                    if (TextUtils.isEmpty(song.songInfo.getArtist())) {
                        artistText.setVisibility(View.GONE);
                    }
                    // if there is no band, no need to describe that the
                    // artists are soloists
                    else if (!TextUtils.isEmpty(song.songInfo.getBand()))  {
                        // Show description of soloists, depending on whether there is
                        // one or more of them
                        if (song.songInfo.artists.length>1) {
                            artistText.setText(getString(R.string.soloists, song.songInfo.getArtist()));
                        }
                        else {
                            artistText.setText(getString(R.string.soloist, song.songInfo.getArtist()));
                        }
                        artistText.setVisibility(View.VISIBLE);
                    }
                    else {
                        artistText.setText(song.songInfo.getArtist());
                        artistText.setVisibility(View.VISIBLE);
                    }
                }
                else {
                    artistText.setText(song.songInfo.getArtist());
                    artistText.setVisibility(View.VISIBLE);
                }

                if (addConductorLine) {
                    // show band instead of album
                    albumText.setText(song.songInfo.getBand());

                    // remove album line if there is no band
                    albumText.setVisibility((classicalMusicTags && TextUtils.isEmpty(song.songInfo.getBand())) ? View.GONE : View.VISIBLE);

                    if (!classicalMusicTags && TextUtils.isEmpty(song.songInfo.getBand())) {
                        // don't show "Unknown album" if line is intended
                        // for showing the band and there is no band
                        albumText.setText(" ");
                    }
                }
                else {
                    // standard view
                    albumText.setText(song.songInfo.album);
                    albumText.setVisibility(View.VISIBLE);
                }

                 if (addConductorLine && !TextUtils.isEmpty(song.songInfo.getConductor())) {
                    if (classicalMusicTags) {
                        // show description of conductor
                        conductorText.setText(getString(R.string.conductor, song.songInfo.getConductor()));
                    }
                    else {
                        // just show conductor's name
                        conductorText.setText(song.songInfo.getConductor());
                    }
                    conductorText.setVisibility(View.VISIBLE);
                }
                else {
                    // remove line if it should not be shown
                    conductorText.setVisibility(View.GONE);
                }
                artistText.setSelected(true);
                albumText.setSelected(true);

                String trackInfoText = formatTrackInfo(preferences, playerState, song);
                trackInfo.setText(trackInfoText);
                trackInfo.setVisibility(!TextUtils.isEmpty(trackInfoText) ? View.VISIBLE : View.GONE);

                requireService().pluginItems(song.moreAction, new IServiceItemListCallback<>() {
                    @Override
                    public void onItemsReceived(int count, int start, Map<String, Object> parameters, List<JiveItem> items, Class<JiveItem> dataType) {
                        albumItem = findBrowseAction(items, "album_id");
                        artistItem = findBrowseAction(items, "artist_ids");
                        composerItem = findBrowseAction(items, "composer_ids");
                        conductorItem = findBrowseAction(items, "conductor_ids");
                    }

                    @Override
                    public Object getClient() {
                        return mActivity;
                    }
                });
            } else {
                if (addConductorLine) {
                    artistAlbumText.setText(Util.joinSkipEmpty(" - ", song.songInfo.getArtist(), song.songInfo.getBand(),song.songInfo.getConductor()));
                }
                else {
                    artistAlbumText.setText(song.artistAlbum());
                }
                artistAlbumText.setSelected(true);
            }
        } else {
            trackText.setText("");
            if (mFullHeightLayout) {
                artistText.setText("");
                albumText.setText("");
                btnContextMenu.setVisibility(View.GONE);
            } else {
                artistAlbumText.setText("");
            }
        }

        if (!song.useIcon()) {
            albumArt.setImageDrawable(song.getIconDrawable(mActivity, R.drawable.icon_album));
        } else {
            ImageFetcher.getInstance(mActivity).loadImage(song.getIcon(), albumArt);
        }
    }

    private static String formatTrackInfo(Preferences preferences, PlayerState playerState, CurrentPlaylistItem song) {
        return Util.joinSkipEmpty(" - ", formatTechnicalInfo(preferences, song), formatTrackCount(preferences, playerState));
    }

    private static String formatTechnicalInfo(Preferences preferences, CurrentPlaylistItem song) {
        return preferences.showTechnicalInfo() ? Util.joinSkipEmpty(" ", song.songInfo.getBitRate(), song.songInfo.getSampleRate()) : "";
    }

    private static String formatTrackCount(Preferences preferences, PlayerState playerState) {
        return (preferences.showTrackCount() && playerState.getCurrentPlaylistTracksNum() > 1)
                ? String.format("%s/%s", playerState.getCurrentPlaylistIndex() + 1, playerState.getCurrentPlaylistTracksNum())
                : "";
    }

    private void updateVolumeInfo() {
        if (mFullHeightLayout) {
            ISqueezeService.VolumeInfo volumeInfo = requireService().getVolume();
            if (Squeezer.getPreferences().isLargeArtwork()) {
                muteButton.setIconResource(volumeInfo.muted ? R.drawable.ic_volume_off : R.drawable.ic_volume_down);
                volumeBar.setEnabled(!volumeInfo.muted);
                volumeBar.setProgress(volumeInfo.volume);
            } else {
                muteToggle.setChecked(volumeInfo.muted);
                currentProgress = volumeInfo.volume;
                volumeWheel.setProgress(volumeInfo.volume);
                volumeWheel.setLabel(String.valueOf(volumeInfo.volume));
                // label.setText(volumeInfo.name);

                volumeWheel.setIndicatorColor(ColorUtils.setAlphaComponent(volumeWheel.getIndicatorColor(), volumeInfo.muted ? 63 : 255));
                volumeWheel.setProgressPrimaryColor(ColorUtils.setAlphaComponent(volumeWheel.getProgressPrimaryColor(), volumeInfo.muted ? 63 : 255));
                volumeWheel.setProgressSecondaryColor(ColorUtils.setAlphaComponent(volumeWheel.getProgressSecondaryColor(), volumeInfo.muted ? 63 : 255));
                volumeWheel.setOnCrollerChangeListener(volumeInfo.muted ? null : this);
                volumeWheel.setOnTouchListener(volumeInfo.muted ? (view, motionEvent) -> true : null);
            }
        }
    }

    private JiveItem findBrowseAction(List<JiveItem> items, String idParam) {
        for (JiveItem item : items) {
            if (item.goAction != null && item.goAction.action != null &&
                    item.goAction.action.cmd.equals(Arrays.asList("browselibrary", "items")) &&
                    item.goAction.action.params.containsKey(idParam)) {
                return item;
            }
        }
        return null;
    }

    private void disconnect() {
        if (mService != null) mService.disconnect();
    }

    private void setSecondsElapsed(int seconds) {
        if (mService != null) mService.setSecondsElapsed(seconds);
    }

    private PlayerState getPlayerState() {
        if (mService == null) {
            return null;
        }
        return mService.getActivePlayerState();
    }

    private Player getActivePlayer() {
        if (mService == null) {
            return null;
        }
        return mService.getActivePlayer();
    }

    private CurrentPlaylistItem getCurrentSong() {
        PlayerState playerState = getPlayerState();
        return playerState != null ? playerState.getCurrentSong() : null;
    }

    private boolean isConnected() {
        return mService != null && mService.isConnected();
    }

    private boolean isConnectInProgress() {
        return mService != null && mService.isConnectInProgress();
    }

    private boolean canAutoConnect() {
        return mService != null && mService.canAutoConnect();
    }

    @Override
    public void onPause() {
        Log.d(TAG, "onPause...");

        dismissConnectingDialog();

        mActivity.unregisterReceiver(broadcastReceiver);

        if (mRegisteredCallbacks) {
            requireService().getEventBus().unregister(this);
            mRegisteredCallbacks = false;
        }

        super.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mService != null) {
            mActivity.unbindService(serviceConnection);
        }
    }

    /**
     * @see Fragment#onCreateOptionsMenu(android.view.Menu,
     * android.view.MenuInflater)
     */
    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        // I confess that I don't understand why using the inflater passed as
        // an argument here doesn't work -- but if you do it crashes without
        // a stracktrace on API 7.
        MenuInflater i = mActivity.getMenuInflater();
        i.inflate(R.menu.now_playing_fragment_menu, menu);
        PlayerViewLogic.inflatePlayerActions(mActivity, i, menu);

        menuItemSearch = menu.findItem(R.id.menu_item_search);
        menuItemPlaylist = menu.findItem(R.id.menu_item_playlist);
        menuItemDisconnect = menu.findItem(R.id.menu_item_disconnect);

        menuItemTogglePower = menu.findItem(R.id.toggle_power);
        menuItemSleep = menu.findItem(R.id.sleep);
        menuItemSleepAtEndOfSong = menu.findItem(R.id.end_of_song);
        menuItemCancelSleep = menu.findItem(R.id.cancel_sleep);

        menuItemPlayers = menu.findItem(R.id.menu_item_players);
        menuItemAlarm = menu.findItem(R.id.menu_item_alarm);
    }

    /**
     * Sets the state of assorted option menu items based on whether or not there is a connection to
     * the server, and if so, whether any players are connected.
     */
    @Override
    public void onPrepareOptionsMenu(@NonNull Menu menu) {
        boolean connected = isConnected();

        // These are all set at the same time, so one check is sufficient
        if (menuItemDisconnect != null) {
            // Set visibility and enabled state of menu items that are not player-specific.
            menuItemSearch.setVisible(topBarSearch != null);
            menuItemDisconnect.setVisible(connected);

            // Set visibility and enabled state of menu items that are player-specific and
            // require a connection to the server.
            boolean haveConnectedPlayers = connected && mService != null
                    && !mService.getPlayers().isEmpty();

            menuItemPlaylist.setVisible(haveConnectedPlayers);
            menuItemPlayers.setVisible(haveConnectedPlayers);
            menuItemAlarm.setVisible(haveConnectedPlayers);
            menuItemSleep.setVisible(haveConnectedPlayers);

            // Don't show the item to go to current playlist if in CurrentPlaylistActivity.
            if (mActivity instanceof CurrentPlaylistActivity) {
                menuItemPlaylist.setVisible(false);
            }

            // Don't show the item to go to players if in PlayersActivity.
            if (mActivity instanceof PlayerListActivity) {
                menuItemPlayers.setVisible(false);
            }

            // Don't show the item to go to alarms if in AlarmsActivity.
            if (mActivity instanceof AlarmsActivity) {
                menuItemAlarm.setVisible(false);
            }
        }

        updatePlayerMenuItems();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (PlayerViewLogic.doPlayerAction(getParentFragmentManager(), mService, item, getActivePlayer())) {
            return true;
        }

        int itemId = item.getItemId();
        if (itemId == R.id.menu_item_search) {
            topBarSearch.input.initialText = "";
            JiveItemListActivity.show(mActivity, topBarSearch, topBarSearch.goAction);
            return true;
        } else if (itemId == R.id.menu_item_playlist) {
            CurrentPlaylistActivity.show(mActivity);
            return true;
        } else if (itemId == R.id.menu_item_settings) {
            SettingsActivity.show(mActivity);
            return true;
        } else if (itemId == R.id.menu_item_disconnect) {
            disconnect();
            return true;
        } else if (itemId == R.id.menu_item_players) {
            PlayerListActivity.show(mActivity);
            return true;
        } else if (itemId == R.id.menu_item_alarm) {
            AlarmsActivity.show(mActivity);
            return true;
        } else if (itemId == R.id.menu_item_about) {
            new AboutDialog().show(getParentFragmentManager(), "AboutDialog");
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void startVisibleConnection(boolean autoConnect) {
        Log.v(TAG, "startVisibleConnection");

        // If were not connected to service or not attached to activity do nothing.
        if (mService == null || !isAdded()) {
            return;
        }

        Squeezer.getPreferences(preferences -> {
            if (!preferences.hasServerConfig()) {
                // Set up a server connection, if it is not present
                ConnectActivity.show(mActivity);
                return;
            }

            if (isConnectInProgress()) {
                Log.v(TAG, "Connection is already in progress, connecting aborted");
                return;
            }
            requireService().startConnect(autoConnect);
        });
    }

    private final CallStatePermissionLauncher requestCallStateLauncher = new CallStatePermissionLauncher(this);

    @Override
    public void requestCallStatePermission() {
        requestCallStateLauncher.requestCallStatePermission();
    }


    @MainThread
    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onEventMainThread(ConnectionChanged event) {
        Log.d(TAG, "ConnectionChanged: " + event);

        // The fragment may no longer be attached to the parent activity.  If so, do nothing.
        if (!isAdded()) {
            return;
        }

        switch (event.connectionState) {
            case MANUAL_DISCONNECT:
            case DISCONNECTED:
                dismissConnectingDialog();
                ConnectActivity.show(mActivity);
                break;
            case CONNECTION_STARTED:
                showConnectingDialog();
                break;
            case CONNECTION_FAILED:
                dismissConnectingDialog();
                switch (event.connectionError) {
                    case LOGIN_FALIED:
                        ConnectActivity.showLoginFailed(mActivity);
                        break;
                    case INVALID_URL:
                        ConnectActivity.showInvalidUrl(mActivity);
                        break;
                    case START_CLIENT_ERROR:
                    case CONNECTION_ERROR:
                        ConnectActivity.showConnectionFailed(mActivity);
                        break;
                }
                break;
            case CONNECTION_COMPLETED:
            case REHANDSHAKING:
                break;
        }
     }

    @MainThread
    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onEventMainThread(HandshakeComplete event) {
        // Event might arrive before this fragment has connected to the service (e.g.,
        // the activity connected before this fragment did).
        // XXX: Verify that this is possible, since the fragment can't register for events
        // until it's connected to the service.
        if (mService == null) {
            return;
        }

        Log.d(TAG, "Handshake complete");

        dismissConnectingDialog();

        PlayerState playerState = getPlayerState();

        // May be no players connected.
        // TODO: These views should be cleared if there's no player connected.
        if (playerState == null)
            return;

        updateUiFromPlayerState(playerState);

        requestCallStateLauncher.trySetAction(Squeezer.getPreferences().getActionOnIncomingCall());
    }

    @MainThread
    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onEventMainThread(@SuppressWarnings("unused") RegisterSqueezeNetwork event) {
        // We're connected but the controller needs to register with the server
        JiveItemListActivity.register(mActivity);
    }

    @MainThread
    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onEventMainThread(ActivePlayerChanged event) {
        updateUiFromPlayerState(event.player != null ? event.player.getPlayerState() : new PlayerState());
    }

    @MainThread
    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onEventMainThread(MusicChanged event) {
        if (event.player.equals(requireService().getActivePlayer())) {
            updateSongInfo(event.playerState);
        }
    }

    @MainThread
    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onEventMainThread(PlayersChanged event) {
        updatePlayerDropDown(requireService().getPlayers(), requireService().getActivePlayer());
    }

    @MainThread
    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onEventMainThread(PlayStatusChanged event) {
        if (event.player.equals(requireService().getActivePlayer())) {
            updatePlayPauseIcon(event.playStatus);
        }
    }

    @MainThread
    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onEventMainThread(PowerStatusChanged event) {
        if (event.player.equals(requireService().getActivePlayer())) {
            updatePlayerMenuItems();
        }
    }

    @MainThread
    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onEventMainThread(HomeMenuEvent event) {
        boolean myMusicSearch = Squeezer.getPreferences().getTopBarSearch() == Preferences.TopBarSearch.MY_MUSIC;
        String searchKey = myMusicSearch ? "myMusicSearch" : "globalSearch";
        topBarSearch = null;
        for (JiveItem menuItem : event.menuItems) if (menuItem.goAction != null) {
            if (searchKey.equals(menuItem.getId())) topBarSearch = menuItem;
            if ("myMusicSearch".equals(menuItem.getId())) menuItem.input = new Input();
        }
        if (menuItemSearch != null) menuItemSearch.setVisible(topBarSearch != null);
    }

    @MainThread
    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onEventMainThread(RepeatStatusChanged event) {
        if (event.player.equals(requireService().getActivePlayer())) {
            updateRepeatStatus(event.repeatStatus);
        }
    }

    @MainThread
    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onEventMainThread(ShuffleStatusChanged event) {
        if (event.player.equals(requireService().getActivePlayer())) {
            updateShuffleStatus(event.shuffleStatus);
        }
    }

    @MainThread
    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onEventMainThread(SongTimeChanged event) {
        if (event.player.equals(requireService().getActivePlayer())) {
            updateTimeDisplayTo(event.currentPosition, event.duration);
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onEvent(PlayerVolume event) {
        if (!trackingTouch) {
            if (event.player == requireService().getActivePlayer()) {
                updateVolumeInfo();
            }
        }
    }

    @Override
    public void onProgressChanged(Croller croller, int progress) {
        if (currentProgress != progress) {
            currentProgress = progress;
            volumeWheel.setLabel(String.valueOf(progress));
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

}
