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

package uk.org.ngo.squeezer.service;

import android.net.Uri;

import java.util.Arrays;

import uk.org.ngo.squeezer.Util;

public class NotificationState {
    public boolean hasPlayer;
    public String playerName;

    public boolean hasSong;
    public String songName;
    public String albumName;
    public String artistName;
    public Uri artworkUrl;
    public boolean playing;

    public String artistAlbum() {
        return Util.joinSkipEmpty(" - ", artistName, albumName);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        NotificationState that = (NotificationState) o;

        if (hasPlayer != that.hasPlayer) return false;
        if (hasSong != that.hasSong) return false;
        if (playing != that.playing) return false;
        if (playerName != null ? !playerName.equals(that.playerName) : that.playerName != null)
            return false;
        if (songName != null ? !songName.equals(that.songName) : that.songName != null)
            return false;
        if (albumName != null ? !albumName.equals(that.albumName) : that.albumName != null)
            return false;
        if (artistName != null ? !artistName.equals(that.artistName) : that.artistName != null)
            return false;
        return artworkUrl != null ? artworkUrl.equals(that.artworkUrl) : that.artworkUrl == null;
    }

    @Override
    public int hashCode() {
        int result = (hasPlayer ? 1 : 0);
        result = 31 * result + (playerName != null ? playerName.hashCode() : 0);
        result = 31 * result + (hasSong ? 1 : 0);
        result = 31 * result + (songName != null ? songName.hashCode() : 0);
        result = 31 * result + (albumName != null ? albumName.hashCode() : 0);
        result = 31 * result + (artistName != null ? artistName.hashCode() : 0);
        result = 31 * result + (artworkUrl != null ? artworkUrl.hashCode() : 0);
        result = 31 * result + (playing ? 1 : 0);
        return result;
    }
}
