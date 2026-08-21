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
import androidx.annotation.NonNull;

import java.util.Map;
import java.util.Objects;

import uk.org.ngo.squeezer.Util;


public class CurrentTrack extends JiveItem {
    @NonNull public Song songInfo;

    public CurrentTrack(Map<String, Object> record) {
        super(record);
        songInfo = new Song(record);
        if (record.containsKey("track")) {
            songInfo.title = getStringOrEmpty(record, "track");
        }
    }

    @NonNull
    @Override
    public String getName() {
        String name = super.getName();
        return !name.isEmpty() ? name : songInfo.title;
    }

    public static final Creator<CurrentTrack> CREATOR = new Creator<CurrentTrack>() {
        @Override
        public CurrentTrack[] newArray(int size) {
            return new CurrentTrack[size];
        }

        @Override
        public CurrentTrack createFromParcel(Parcel source) {
            return new CurrentTrack(source);
        }
    };

    private CurrentTrack(Parcel source) {
        super(source);
        songInfo = source.readParcelable(getClass().getClassLoader());
    }

    @Override
    public String text2() {
        return songInfo.album.isEmpty() ? super.text2() : Util.joinSkipEmpty(" - ", songInfo.getArtist(), songInfo.album);
    }

    public String album() {
        return songInfo.album.isEmpty() ? super.text2() : songInfo.album;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeParcelable(songInfo, flags);

    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        CurrentTrack that = (CurrentTrack) o;
        return Objects.equals(songInfo, that.songInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), songInfo);
    }

    @Override
    public String toString() {
        return "CurrentPlaylistItem{" +
                "song=" + songInfo +
                "} " + super.toString();
    }
}
