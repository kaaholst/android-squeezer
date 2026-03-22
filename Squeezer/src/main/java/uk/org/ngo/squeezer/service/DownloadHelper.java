package uk.org.ngo.squeezer.service;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Map;

import uk.org.ngo.squeezer.Preferences;
import uk.org.ngo.squeezer.Squeezer;
import uk.org.ngo.squeezer.Util;
import uk.org.ngo.squeezer.download.DownloadDatabase;
import uk.org.ngo.squeezer.itemlist.IServiceItemListCallback;
import uk.org.ngo.squeezer.model.JiveItem;
import uk.org.ngo.squeezer.model.MusicFolderItem;
import uk.org.ngo.squeezer.model.SlimCommand;
import uk.org.ngo.squeezer.model.Song;

class DownloadHelper {
    private static final String TAG = DownloadHelper.class.getSimpleName();
    private final Context context;
    private final LyrionController lyrionController;

    public DownloadHelper(Context context, LyrionController lyrionController) {
        this.context = context;
        this.lyrionController = lyrionController;
    }

    public void downloadItem(JiveItem item) {
        Log.i(TAG, "downloadItem(" + item + ")");
        SlimCommand command = item.downloadCommand();
        IServiceItemListCallback<?> callback = ("musicfolder".equals(command.cmd.get(0))) ? musicFolderDownloadCallback : songDownloadCallback;
        lyrionController.requestAllItems(callback).params(command.params).cmd(command.cmd()).exec();
    }


    /** A download request will be passed to the download manager for each song called back to this */
    private final IServiceItemListCallback<Song> songDownloadCallback = new IServiceItemListCallback<>() {
        @Override
        public void onItemsReceived(int count, int start, Map<String, Object> parameters, List<Song> items, Class<Song> dataType) {
            final Preferences preferences = Squeezer.getPreferences();
            for (Song song : items) {
                Log.i(TAG, "downloadSong(" + song + ")");
                Uri downloadUrl = Util.getDownloadUrl(lyrionController.getUrlPrefix(), song.id);
                if (preferences.isDownloadUseServerPath()) {
                    downloadSong(downloadUrl, song.title, song.album, song.getArtist(), getLocalFile(song.url));
                } else {
                    final String lastPathSegment = song.url.getLastPathSegment();
                    final String fileExtension = Util.getFileExtension(lastPathSegment);
                    final String localPath = song.getLocalPath(preferences.getDownloadPathStructure(), preferences.getDownloadFilenameStructure());
                    downloadSong(downloadUrl, song.title, song.album, song.getArtist(), localPath + "." + fileExtension);
                }
            }
        }

        @Override
        public Object getClient() {
            return lyrionController;
        }
    };

    /**
     * For each item called to this:
     * If it is a folder: recursive lookup items in the folder
     * If is is a track: Enqueue a download request to the download manager
     */
    private final IServiceItemListCallback<MusicFolderItem> musicFolderDownloadCallback = new IServiceItemListCallback<>() {

        @Override
        public void onItemsReceived(int count, int start, Map<String, Object> parameters, List<MusicFolderItem> items, Class<MusicFolderItem> dataType) {
            for (MusicFolderItem item : items) {
                if ("track".equals(item.type)) {
                    Log.i(TAG, "downloadMusicFolderTrack(" + item + ")");
                    SlimCommand command = JiveItem.downloadCommand(item.id);
                    lyrionController.requestAllItems(songDownloadCallback).params(command.params).cmd(command.cmd()).exec();
                }
            }
        }

        @Override
        public Object getClient() {
            return lyrionController;
        }
    };

    private void downloadSong(@NonNull Uri url, String title, String album, String artist, String localPath) {
        Log.i(TAG, "downloadSong(" + title + "): " + url);
        if (url.equals(Uri.EMPTY)) {
            return;
        }

        if (localPath == null) {
            return;
        }

        // Convert VFAT-unfriendly characters to "_".
        localPath =  localPath.replaceAll("[?<>\\\\:*|\"]", "_");
        DownloadDatabase downloadDatabase = new DownloadDatabase(context);
        String credentials = lyrionController.getUsername() + ":" + lyrionController.getPassword();
        downloadDatabase.registerDownload(context, credentials, url, localPath, title, album, artist);
    }

    /**
     * Tries to get the path relative to the server music library.
     * <p>
     * If this is not possible resort to the last path segment of the server path.
     */
    @Nullable
    private String getLocalFile(@NonNull Uri serverUrl) {
        String serverPath = serverUrl.getPath();
        String mediaDir = null;
        String path;
        for (String dir : lyrionController.getMediaDirs()) {
            if (serverPath != null && serverPath.startsWith(dir)) {
                mediaDir = dir;
                break;
            }
        }
        if (mediaDir != null) {
            path = serverPath.substring(mediaDir.length());
        } else {
            // Note: if serverUrl is the empty string this can return null.
            path = serverUrl.getLastPathSegment();
        }

        return path;
    }
}
