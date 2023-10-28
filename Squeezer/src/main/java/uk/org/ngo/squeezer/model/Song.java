package uk.org.ngo.squeezer.model;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import java.io.File;
import java.util.Map;
import java.util.Objects;

import uk.org.ngo.squeezer.Util;
import uk.org.ngo.squeezer.download.DownloadFilenameStructure;
import uk.org.ngo.squeezer.download.DownloadPathStructure;

public class Song  implements Parcelable {
    public String id;
    @NonNull public String title;
    public int trackNum;
    @NonNull public final String artist;
    @NonNull public final String album;
    @NonNull public final String composer;
    @NonNull public final String conductor;
    @NonNull public final String band;
    @NonNull public final String albumArtist;
    @NonNull public final String trackArtist;
    @NonNull public final String bitRate;
    @NonNull public final String sampleRate;
    public int duration;

    @NonNull
    public Uri url;

    public static final Creator<Song> CREATOR = new Creator<>() {
        @Override
        public Song[] newArray(int size) {
            return new Song[size];
        }

        @Override
        public Song createFromParcel(Parcel source) {
            return new Song(source);
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    public Song(Map<String, Object> record) {
        id = Util.getString(record, "id");
        title = Util.getStringOrEmpty(record, "title");
        trackNum = Util.getInt(record, "tracknum", 1);
        artist = Util.getStringOrEmpty(record, "artist");
        composer = Util.getStringOrEmpty(record, "composer");
        conductor = Util.getStringOrEmpty(record, "conductor");
        band = Util.getStringOrEmpty(record, "band");
        albumArtist = Util.getStringOrEmpty(record, "albumartist");
        trackArtist = Util.getStringOrEmpty(record, "trackartist");
        album = Util.getStringOrEmpty(record, "album");
        bitRate = Util.getStringOrEmpty(record, "bitrate");
        sampleRate = Util.getStringOrEmpty(record, "samplerate");
        duration = Util.getInt(record, "duration");

        url = Uri.parse(Util.getStringOrEmpty(record, "url"));
    }

    private Song(Parcel source) {
        id = source.readString();
        title = source.readString();
        trackNum = source.readInt();
        artist = source.readString();
        album = source.readString();
        composer = source.readString();
        conductor = source.readString();
        band = source.readString();
        albumArtist = source.readString();
        trackArtist = source.readString();
        bitRate = source.readString();
        sampleRate = source.readString();
        duration = source.readInt();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeInt(trackNum);
        dest.writeString(title);
        dest.writeString(artist);
        dest.writeString(album);
        dest.writeString(composer);
        dest.writeString(conductor);
        dest.writeString(band);
        dest.writeString(albumArtist);
        dest.writeString(trackArtist);
        dest.writeString(bitRate);
        dest.writeString(sampleRate);
        dest.writeInt(duration);
    }

    public String getLocalPath(DownloadPathStructure downloadPathStructure, DownloadFilenameStructure downloadFilenameStructure) {
        return new File(downloadPathStructure.get(this), downloadFilenameStructure.get(this)).getPath();
    }

    @Override
    public String toString() {
        return "Song{" +
                "id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", trackNum=" + trackNum +
                ", artist='" + artist + '\'' +
                ", album='" + album + '\'' +
                ", composer='" + composer + '\'' +
                ", conductor='" + conductor + '\'' +
                ", band='" + band + '\'' +
                ", albumArtist='" + albumArtist + '\'' +
                ", trackArtist='" + trackArtist + '\'' +
                ", bitRate='" + bitRate + '\'' +
                ", sampleRate='" + sampleRate + '\'' +
                ", duration=" + duration +
                ", url=" + url +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Song song = (Song) o;
        return trackNum == song.trackNum && duration == song.duration && Objects.equals(id, song.id) && title.equals(song.title) && artist.equals(song.artist) && album.equals(song.album) && composer.equals(song.composer) && conductor.equals(song.conductor) && band.equals(song.band) && albumArtist.equals(song.albumArtist) && trackArtist.equals(song.trackArtist) && bitRate.equals(song.bitRate) && sampleRate.equals(song.sampleRate) && url.equals(song.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, title, trackNum, artist, album, composer, conductor, band, albumArtist, trackArtist, bitRate, sampleRate, duration, url);
    }
}