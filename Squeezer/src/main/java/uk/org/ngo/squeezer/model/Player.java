/*
 * Copyright (c) 2011 Kurt Aaholst <kaaholst@gmail.com>
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
import android.os.SystemClock;
import androidx.annotation.NonNull;

import java.util.Comparator;
import java.util.Map;

import uk.org.ngo.squeezer.Util;
import uk.org.ngo.squeezer.service.event.SongTimeChanged;


public class Player extends Item implements Comparable<Player> {

    private String mName;

    private final String mIp;

    private final String mModel;

    private final boolean mCanPowerOff;

    private final PlayerState mPlayerState = new PlayerState();

    /** Is the player connected? */
    private boolean mConnected;

    @Override
    public int compareTo(@NonNull Player otherPlayer) {
        return this.mName.compareToIgnoreCase((otherPlayer).mName);
    }

    /** The types of player preferences. */
    public enum Pref {
        ALARM_DEFAULT_VOLUME("alarmDefaultVolume"),
        ALARM_FADE_SECONDS("alarmfadeseconds"),
        ALARM_SNOOZE_SECONDS("alarmSnoozeSeconds"),
        ALARM_TIMEOUT_SECONDS("alarmTimeoutSeconds"),
        ALARMS_ENABLED("alarmsEnabled"),
        PLAY_TRACK_ALBUM("playtrackalbum"),
        DEFEAT_DESTRUCTIVE_TTP("defeatDestructiveTouchToPlay"),
        SYNC_VOLUME("syncVolume"),
        SYNC_POWER("syncPower");

        private final String prefName;

        Pref(String prefName) {
            this.prefName = prefName;
        }

        public String prefName() {
            return prefName;
        }
    }

    public Player(Map<String, Object> record) {
        setId(getString(record, "playerid"));
        mIp = getString(record, "ip");
        mName = getString(record, "name");
        mModel = getString(record, "model");
        mCanPowerOff = getInt(record, "canpoweroff") == 1;
        mConnected = getInt(record, "connected") == 1;

        for (Player.Pref pref : Player.Pref.values()) {
            if (record.containsKey(pref.prefName)) {
                mPlayerState.prefs.put(pref, Util.getString(record, pref.prefName));
            }
        }
    }

    private Player(Parcel source) {
        setId(source.readString());
        mIp = source.readString();
        mName = source.readString();
        mModel = source.readString();
        mCanPowerOff = (source.readByte() == 1);
        mConnected = (source.readByte() == 1);
    }

    @NonNull
    @Override
    public String getName() {
        return mName;
    }

    public Player setName(@NonNull String name) {
        this.mName = name;
        return this;
    }

    public String getIp() {
        return mIp;
    }

    public String getModel() {
        return mModel;
    }

    public boolean isCanpoweroff() {
        return mCanPowerOff;
    }

    public void setConnected(boolean connected) {
        mConnected = connected;
    }

    public boolean getConnected() {
        return mConnected;
    }

    @NonNull
    public PlayerState getPlayerState() {
        return mPlayerState;
    }

    public static final Creator<Player> CREATOR = new Creator<Player>() {
        @Override
        public Player[] newArray(int size) {
            return new Player[size];
        }

        @Override
        public Player createFromParcel(Parcel source) {
            return new Player(source);
        }
    };

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(getId());
        dest.writeString(mIp);
        dest.writeString(mName);
        dest.writeString(mModel);
        dest.writeByte(mCanPowerOff ? (byte) 1 : (byte) 0);
        dest.writeByte(mConnected ? (byte) 1 : (byte) 0);
    }

    /**
     * Comparator to compare two players by ID.
     */
    public static final Comparator<Player> compareById = (lhs, rhs) -> lhs.getId().compareTo(rhs.getId());

    @Override
    public boolean equals(Object o) {
        if (!super.equals(o)) {
            return false;
        }

        // super.equals() has already checked that o is not null and is of the same class.
        Player p = (Player) o;

        return getName().equals(p.getName());
    }

    @NonNull
    @Override
    public String toString() {
        return "Player{" +
                "mName='" + mName + '\'' +
                ", mIp='" + mIp + '\'' +
                ", mModel='" + mModel + '\'' +
                ", mCanPowerOff=" + mCanPowerOff +
                ", mPlayerState=" + mPlayerState +
                ", mConnected=" + mConnected +
                '}';
    }

    public SongTimeChanged getTrackElapsed() {
        double now = SystemClock.elapsedRealtime() / 1000.0;
        double trackCorrection = mPlayerState.rate * (now - mPlayerState.statusSeen);
        int trackElapsed = (int) (trackCorrection <= 0 ? mPlayerState.getCurrentTimeSecond() : mPlayerState.getCurrentTimeSecond() + trackCorrection);

        return new SongTimeChanged(this, trackElapsed, mPlayerState.getCurrentSongDuration());
    }

    public int getSleepingIn() {
        double now = SystemClock.elapsedRealtime() / 1000.0;
        double correction = now - mPlayerState.statusSeen;
        double remaining = (correction <= 0 ? mPlayerState.getSleep() : mPlayerState.getSleep() - correction);

        return (int) remaining;
    }
}
