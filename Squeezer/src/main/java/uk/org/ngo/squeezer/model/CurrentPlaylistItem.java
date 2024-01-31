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

import uk.org.ngo.squeezer.Util;


public class CurrentPlaylistItem extends JiveItem {
    @NonNull public Song songInfo;

    public CurrentPlaylistItem(Map<String, Object> record) {
        super(record);
        songInfo = new Song(record);
        songInfo.title = getStringOrEmpty(record, "track");
    }

    public static final Creator<CurrentPlaylistItem> CREATOR = new Creator<CurrentPlaylistItem>() {
        @Override
        public CurrentPlaylistItem[] newArray(int size) {
            return new CurrentPlaylistItem[size];
        }

        @Override
        public CurrentPlaylistItem createFromParcel(Parcel source) {
            return new CurrentPlaylistItem(source);
        }
    };

    private CurrentPlaylistItem(Parcel source) {
        super(source);
        songInfo = source.readParcelable(getClass().getClassLoader());
    }

    public String artistAlbum() {
        return Util.joinSkipEmpty(" - ", songInfo.getArtist(), songInfo.album);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeParcelable(songInfo, flags);

    }

    @Override
    public String toString() {
        return "CurrentPlaylistItem{" +
                "song=" + songInfo +
                "} " + super.toString();
    }
}
