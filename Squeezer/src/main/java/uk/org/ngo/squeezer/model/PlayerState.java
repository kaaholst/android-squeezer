/*
 * Copyright (c) 2009 Google Inc.  All Rights Reserved.
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

package uk.org.ngo.squeezer.model;


import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.Util;
import uk.org.ngo.squeezer.framework.EnumIdLookup;
import uk.org.ngo.squeezer.framework.EnumWithId;


public class PlayerState implements Parcelable {

    public PlayerState() {
    }

    public static final Creator<PlayerState> CREATOR = new Creator<PlayerState>() {
        @Override
        public PlayerState[] newArray(int size) {
            return new PlayerState[size];
        }

        @Override
        public PlayerState createFromParcel(Parcel source) {
            return new PlayerState(source);
        }
    };

    private PlayerState(Parcel source) {
        playStatus = source.readString();
        poweredOn = (source.readByte() == 1);
        shuffleStatus = ShuffleStatus.valueOf(source.readInt());
        repeatStatus = RepeatStatus.valueOf(source.readInt());
        currentSong = source.readParcelable(getClass().getClassLoader());
        currentPlaylist = source.readString();
        currentPlaylistTimestamp = source.readLong();
        currentPlaylistIndex = source.readInt();
        currentTimeSecond = source.readDouble();
        currentSongDuration = source.readInt();
        currentVolume = source.readInt();
        sleepDuration = source.readInt();
        sleep = source.readInt();
        mSyncMaster = source.readString();
        source.readStringList(mSyncSlaves);
        mPlayerSubscriptionType = PlayerSubscriptionType.valueOf(source.readString());
        prefs = source.readHashMap(getClass().getClassLoader());
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(playStatus);
        dest.writeByte(poweredOn ? (byte) 1 : (byte) 0);
        dest.writeInt(shuffleStatus.getId());
        dest.writeInt(repeatStatus.getId());
        dest.writeParcelable(currentSong, flags);
        dest.writeString(currentPlaylist);
        dest.writeLong(currentPlaylistTimestamp);
        dest.writeInt(currentPlaylistIndex);
        dest.writeDouble(currentTimeSecond);
        dest.writeInt(currentSongDuration);
        dest.writeInt(currentVolume);
        dest.writeInt(sleepDuration);
        dest.writeDouble(sleep);
        dest.writeString(mSyncMaster);
        dest.writeStringList(mSyncSlaves);
        dest.writeString(mPlayerSubscriptionType.name());
        dest.writeMap(prefs);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    private boolean poweredOn;

    private @PlayState String playStatus;

    private ShuffleStatus shuffleStatus;

    private RepeatStatus repeatStatus;

    private CurrentPlaylistItem currentSong;

    /** The name of the current playlist, which may be the empty string. */
    @NonNull
    private String currentPlaylist;

    private long currentPlaylistTimestamp;

    private int currentPlaylistTracksNum;

    private int currentPlaylistIndex;

    private boolean remote;

    public boolean waitingToPlay;

    public double rate;

    private double currentTimeSecond;

    private int currentSongDuration;

    public double statusSeen;

    private int currentVolume = 101;

    private int sleepDuration;

    private double sleep;

    /** The player this player is synced to (null if none). */
    @Nullable
    private String mSyncMaster;

    /** The players synced to this player. */
    private List<String> mSyncSlaves = Collections.emptyList();

    /** How the server is subscribed to the player's status changes. */
    @NonNull
    private PlayerSubscriptionType mPlayerSubscriptionType = PlayerSubscriptionType.NOTIFY_NONE;

    /** Map of current values of our the playerprefs we track. See the specific SlimClient */
    @NonNull
    public Map<Player.Pref, String> prefs = new HashMap<>();

    public boolean isPlaying() {
        return PLAY_STATE_PLAY.equals(playStatus);
    }

    /**
     * @return the player's state. May be null, which indicates that Squeezer has received
     *     a "players" response for this player, but has not yet received a status message
     *     for it.
     */
    @Nullable
    @PlayState
    public String getPlayStatus() {
        return playStatus;
    }

    public boolean setPlayStatus(@NonNull @PlayState String s) {
        if (s.equals(playStatus)) {
            return false;
        }

        playStatus = s;

        return true;
    }

    public boolean isPoweredOn() {
        return poweredOn;
    }

    public boolean setPoweredOn(boolean state) {
        if (state == poweredOn)
            return false;

        poweredOn = state;
        return true;
    }

    public ShuffleStatus getShuffleStatus() {
        return shuffleStatus;
    }

    public boolean setShuffleStatus(ShuffleStatus status) {
        if (status == shuffleStatus)
            return false;

        shuffleStatus = status;
        return true;
    }

    public boolean setShuffleStatus(String s) {
        return setShuffleStatus(s != null ? ShuffleStatus.valueOf(Util.getInt(s)) : null);
    }

    public RepeatStatus getRepeatStatus() {
        return repeatStatus;
    }

    public boolean setRepeatStatus(RepeatStatus status) {
        if (status == repeatStatus)
            return false;

        repeatStatus = status;
        return true;
    }

    public boolean setRepeatStatus(String s) {
        return setRepeatStatus(s != null ? RepeatStatus.valueOf(Util.getInt(s)) : null);
    }

    public CurrentPlaylistItem getCurrentSong() {
        return currentSong;
    }

    public boolean setCurrentSong(CurrentPlaylistItem song) {
        if (song.equals(currentSong))
            return false;

        currentSong = song;
        return true;
    }

    /** @return the name of the current playlist, may be the empty string. */
    @NonNull
    public String getCurrentPlaylist() {
        return currentPlaylist;
    }

    public long getCurrentPlaylistTimestamp() {
        return currentPlaylistTimestamp;
    }

    public boolean setCurrentPlaylistTimestamp(long value) {
        if (value == currentPlaylistTimestamp)
            return false;

        currentPlaylistTimestamp = value;
        return true;
    }

    /** @return the number of tracks in the current playlist */
    public int getCurrentPlaylistTracksNum() {
        return currentPlaylistTracksNum;
    }

    public int getCurrentPlaylistIndex() {
        return currentPlaylistIndex;
    }

    public void setCurrentPlaylist(@Nullable String playlist) {
        if (playlist == null)
            playlist = "";
        currentPlaylist = playlist;
    }

    // set the number of tracks in the current playlist
    public void setCurrentPlaylistTracksNum(int value) {
        currentPlaylistTracksNum = value;
    }

    public void setCurrentPlaylistIndex(int value) {
        currentPlaylistIndex = value;
    }

    public boolean isRemote() {
        return remote;
    }

    public void setRemote(boolean remote) {
        this.remote = remote;
    }

    public double getCurrentTimeSecond() {
        return currentTimeSecond;
    }

    public boolean setCurrentTimeSecond(double value) {
        if (value == currentTimeSecond)
            return false;

        currentTimeSecond = value;
        return true;
    }

    public int getCurrentSongDuration() {
        return currentSongDuration;
    }

    public boolean setCurrentSongDuration(int value) {
        if (value == currentSongDuration)
            return false;

        currentSongDuration = value;
        return true;
    }

    public boolean isMuted() {
        return currentVolume < 0;
    }

    public int getCurrentVolume() {
        return (currentVolume == 101 ? 0: Math.abs(currentVolume));
    }

    public boolean setCurrentVolume(int value) {
        if (value == currentVolume)
            return false;

        int current = currentVolume;
        currentVolume = value;
        return (current != 101); // Do not report a change if previous volume was unknown
    }

    public int getSleepDuration() {
        return sleepDuration;
    }

    public boolean setSleepDuration(int sleepDuration) {
        if (sleepDuration == this.sleepDuration)
            return false;

        this.sleepDuration = sleepDuration;
        return true;
    }

    /** @return seconds left until the player sleeps. */
    public double getSleep() {
        return sleep;
    }

    /**
     *
     * @param sleep seconds left until the player sleeps.
     * @return True if the sleep value was changed, false otherwise.
     */
    public boolean setSleep(double sleep) {
        if (sleep == this.sleep)
            return false;

        this.sleep = sleep;
        return true;
    }

    public boolean setSyncMaster(@Nullable String syncMaster) {
        if (syncMaster == null && mSyncMaster == null)
            return false;

        if (syncMaster != null) {
            if (syncMaster.equals(mSyncMaster))
                return false;
        }

        mSyncMaster = syncMaster;
        return true;
    }

    @Nullable
    public String getSyncMaster() {
        return mSyncMaster;
    }

    public boolean setSyncSlaves(@NonNull List<String> syncSlaves) {
        if (syncSlaves.equals(mSyncSlaves))
            return false;

        mSyncSlaves = Collections.unmodifiableList(syncSlaves);
        return true;
    }

    public List<String> getSyncSlaves() {
        return mSyncSlaves;
    }

    public PlayerSubscriptionType getSubscriptionType() {
        return mPlayerSubscriptionType;
    }

    public void setSubscriptionType(PlayerSubscriptionType type) {
        mPlayerSubscriptionType = type;
    }

    @StringDef({PLAY_STATE_PLAY, PLAY_STATE_PAUSE, PLAY_STATE_STOP})
    @Retention(RetentionPolicy.SOURCE)
    public @interface PlayState {}
    public static final String PLAY_STATE_PLAY = "play";
    public static final String PLAY_STATE_PAUSE = "pause";
    public static final String PLAY_STATE_STOP = "stop";

    @Override
    public String toString() {
        return "PlayerState{" +
                "poweredOn=" + poweredOn +
                ", playStatus='" + playStatus + '\'' +
                ", shuffleStatus=" + shuffleStatus +
                ", repeatStatus=" + repeatStatus +
                ", currentSong=" + currentSong +
                ", currentPlaylist='" + currentPlaylist + '\'' +
                ", currentPlaylistIndex=" + currentPlaylistIndex +
                ", currentTimeSecond=" + currentTimeSecond +
                ", currentSongDuration=" + currentSongDuration +
                ", currentVolume=" + currentVolume +
                ", sleepDuration=" + sleepDuration +
                ", sleep=" + sleep +
                ", mSyncMaster='" + mSyncMaster + '\'' +
                ", mSyncSlaves=" + mSyncSlaves +
                ", mPlayerSubscriptionType='" + mPlayerSubscriptionType + '\'' +
                '}';
    }

    public enum PlayerSubscriptionType {
        NOTIFY_NONE("-"),
        NOTIFY_ON_CHANGE("0");

        private final String status;

        PlayerSubscriptionType(String status) {
            this.status = status;
        }

        public String getStatus() {
            return status;
        }
    }

    public enum ShuffleStatus implements EnumWithId {
        SHUFFLE_OFF(0, R.drawable.btn_shuffle),
        SHUFFLE_SONG(1, R.drawable.btn_shuffle_song),
        SHUFFLE_ALBUM(2, R.drawable.btn_shuffle_album);

        private final int id;

        private final int icon;

        private static final EnumIdLookup<ShuffleStatus> lookup = new EnumIdLookup<>(
                ShuffleStatus.class);

        ShuffleStatus(int id, int icon) {
            this.id = id;
            this.icon = icon;
        }

        @Override
        public int getId() {
            return id;
        }

        @DrawableRes
        public int getIcon() {
            return icon;
        }

        public static ShuffleStatus valueOf(int id) {
            return lookup.get(id);
        }
    }

    public enum RepeatStatus implements EnumWithId {
        REPEAT_OFF(0, R.drawable.btn_repeat),
        REPEAT_ONE(1, R.drawable.btn_repeat_one),
        REPEAT_ALL(2, R.drawable.btn_repeat_all);

        private final int id;

        private final int icon;

        private static final EnumIdLookup<RepeatStatus> lookup = new EnumIdLookup<>(
                RepeatStatus.class);

        RepeatStatus(int id, int icon) {
            this.id = id;
            this.icon = icon;
        }

        @Override
        public int getId() {
            return id;
        }

        public int getIcon() {
            return icon;
        }

        public static RepeatStatus valueOf(int id) {
            return lookup.get(id);
        }
    }

}
