package uk.org.ngo.squeezer.model;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import java.io.File;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

import uk.org.ngo.squeezer.Util;
import uk.org.ngo.squeezer.download.DownloadFilenameStructure;
import uk.org.ngo.squeezer.download.DownloadPathStructure;

public class Song  implements Parcelable {
    public String id;
    @NonNull public String title;
    public int trackNum;
    @NonNull public final String[] artists;
    @NonNull public final String[] artistIds;
    @NonNull public final String album;
    @NonNull public final String albumId;
    @NonNull public final String[] composers;
    @NonNull public final String[] composerIds;
    @NonNull public final String[] conductors;
    @NonNull public final String[] conductorIds;
    @NonNull public final String[] bands;
    @NonNull public final String[] bandIds;
    @NonNull public final String[] albumArtists;
    @NonNull public final String[] albumArtistIds;
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
        artists = Util.getCommaSeparatedStringArray(record, record.containsKey("trackartist") ? "trackartist" : "artist");
        artistIds = Util.getCommaSeparatedStringArray(record, record.containsKey("trackartist_ids") ? "trackartist_ids" : "artist_ids");
        composers = Util.getCommaSeparatedStringArray(record, "composer");
        composerIds = Util.getCommaSeparatedStringArray(record, "composer_ids");
        conductors = Util.getCommaSeparatedStringArray(record, "conductor");
        conductorIds = Util.getCommaSeparatedStringArray(record, "conductor_ids");
        bands = Util.getCommaSeparatedStringArray(record, "band");
        bandIds = Util.getCommaSeparatedStringArray(record, "band_ids");
        albumArtists = Util.getCommaSeparatedStringArray(record, "albumartist");
        albumArtistIds = Util.getCommaSeparatedStringArray(record, "albumartist_ids");
        album = Util.getStringOrEmpty(record, "album");
        albumId = Util.getStringOrEmpty(record, "album_id");
        bitRate = Util.getStringOrEmpty(record, "bitrate");
        sampleRate = Util.getStringOrEmpty(record, "samplerate");
        duration = Util.getInt(record, "duration");

        url = Uri.parse(Util.getStringOrEmpty(record, "url"));
    }

    private Song(Parcel source) {
        id = source.readString();
        title = source.readString();
        trackNum = source.readInt();
        artists = source.createStringArray();
        artistIds = source.createStringArray();
        album = source.readString();
        albumId = source.readString();
        composers = source.createStringArray();
        composerIds = source.createStringArray();
        conductors = source.createStringArray();
        conductorIds = source.createStringArray();
        bands = source.createStringArray();
        bandIds = source.createStringArray();
        albumArtists = source.createStringArray();
        albumArtistIds = source.createStringArray();
        bitRate = source.readString();
        sampleRate = source.readString();
        duration = source.readInt();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeInt(trackNum);
        dest.writeString(title);
        dest.writeStringArray(artists);
        dest.writeStringArray(artistIds);
        dest.writeString(album);
        dest.writeString(albumId);
        dest.writeStringArray(composers);
        dest.writeStringArray(composerIds);
        dest.writeStringArray(conductors);
        dest.writeStringArray(conductorIds);
        dest.writeStringArray(bands);
        dest.writeStringArray(bandIds);
        dest.writeStringArray(albumArtists);
        dest.writeStringArray(albumArtistIds);
        dest.writeString(bitRate);
        dest.writeString(sampleRate);
        dest.writeInt(duration);
    }

    public String getArtist() {
        return TextUtils.join(", ", artists);
    }

    public String getBand() {
        return TextUtils.join(", ", bands);
    }

    public String getConductor() {
        return TextUtils.join(", ", conductors);
    }

    public String getComposer() {
        return TextUtils.join(", ", composers);
    }
    public String getAlbumArtists() {
        return TextUtils.join(", ", albumArtists);
    }

    public String getLocalPath(DownloadPathStructure downloadPathStructure, DownloadFilenameStructure downloadFilenameStructure) {
        return new File(downloadPathStructure.get(this), downloadFilenameStructure.get(this)).getPath();
    }

    public String getSampleRate() {
        try  {
            int sampleRateInteger = Integer.parseInt(sampleRate);
            return (sampleRateInteger > 0) ? new DecimalFormat("#.#").format(sampleRateInteger / 1000.0) + " kHz" : "";
        } catch (NumberFormatException ignored) {}
        return sampleRate;
    }

    public String getBitRate() {
        return "0".equals(bitRate) ? "" : bitRate;
    }

    @Override
    public String toString() {
        return "Song{" +
                "id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", trackNum=" + trackNum +
                ", artists=" + Arrays.toString(artists) +
                ", album='" + album + '\'' +
                ", composers=" + Arrays.toString(composers) +
                ", conductors=" + Arrays.toString(conductors) +
                ", bands=" + Arrays.toString(bands) +
                ", albumArtists=" + Arrays.toString(albumArtists) +
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
        return trackNum == song.trackNum && duration == song.duration && Objects.equals(id, song.id) && title.equals(song.title) && Arrays.equals(artists, song.artists) && album.equals(song.album) && Arrays.equals(composers, song.composers) && Arrays.equals(conductors, song.conductors) && Arrays.equals(bands, song.bands) && Arrays.equals(albumArtists, song.albumArtists) && bitRate.equals(song.bitRate) && sampleRate.equals(song.sampleRate) && url.equals(song.url);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(id, title, trackNum, album, bitRate, sampleRate, duration, url);
        result = 31 * result + Arrays.hashCode(artists);
        result = 31 * result + Arrays.hashCode(composers);
        result = 31 * result + Arrays.hashCode(conductors);
        result = 31 * result + Arrays.hashCode(bands);
        result = 31 * result + Arrays.hashCode(albumArtists);
        return result;
    }
}