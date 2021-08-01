/*
 * Copyright (c) 2014 Kurt Aaholst <kaaholst@gmail.com>
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

package uk.org.ngo.squeezer.download;

import android.app.DownloadManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.Util;
import uk.org.ngo.squeezer.service.SqueezeService;


/**
 * Handle events from the download manager
 * <p>
 * This class is registered in the manifest.
 */
public class DownloadStatusReceiver extends BroadcastReceiver {
    private static final String TAG = DownloadStatusReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (DownloadManager.ACTION_NOTIFICATION_CLICKED.equals(intent.getAction())) {
            handleUserRequest(context);
        }
        if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) {
            handleDownloadComplete(context, intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0));
        }
    }

    private void handleUserRequest(Context context) {
        Log.i(TAG, "Download notification clicked");
        Intent intent = new Intent(context, CancelDownloadsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    private void handleDownloadComplete(Context context, long id) {
        final DownloadDatabase downloadDatabase = new DownloadDatabase(context);
        final DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        final DownloadManager.Query query = new DownloadManager.Query().setFilterById(id);

        Log.i(TAG, "download complete: " + id);
        try (Cursor cursor = downloadManager.query(query)) {
            if (!cursor.moveToNext()) {
                // Download complete events may still come in, even after DownloadManager.remove is
                // called, so don't log this
                //Logger.logError(TAG, "Download manager does not have an entry for " + id);
                return;
            }

            int downloadId = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID));
            int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            int reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON));
            String title = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE));
            String url = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_URI));
            Uri local_url = Uri.parse(cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI)));
            Log.i(TAG, "download complete(" + title + "): " + id);

            final DownloadDatabase.DownloadEntry downloadEntry = downloadDatabase.popDownloadEntry(context, downloadId);
            if (downloadEntry == null) {
                // TODO remote logging
                Log.e(TAG, "Download database does not have an entry for " + format(status, reason, title, url, local_url));
                return;
            }
            if (status != DownloadManager.STATUS_SUCCESSFUL) {
                // TODO remote logging
                Log.e(TAG, "Unsuccessful download " + format(status, reason, title, url, local_url));
                return;
            }

            try {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    addToMediaStorage(context, downloadEntry, local_url);
                } else {
                    addToMediaLibrary(context, downloadEntry, local_url);
                }
            } catch (IOException e) {
                // TODO remote logging
                Log.e(TAG, "IOException moving downloaded file", e);
            }
        }
    }

    private String format(int status, int reason, String title, String url, Uri local_url) {
        return "{status:" + status + ", reason:" + reason + ", title:'" + title + "', url:'" + url + "', local url:'" + local_url + "'}";
    }

    private void addToMediaStorage(Context context, DownloadDatabase.DownloadEntry downloadEntry, Uri local_url) throws IOException {
        File destinationFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), downloadEntry.fileName);
        File destFolder = destinationFile.getParentFile();

        if (destFolder != null && !destFolder.exists()) {
            if (!destFolder.mkdirs()) {
                throw new IOException("Cant create folder for '" + destinationFile + "'");
            }
        }
        Util.moveFile(context.getContentResolver(), local_url, Uri.fromFile(destinationFile));

        MediaScannerConnection.scanFile(
                context.getApplicationContext(),
                new String[]{destinationFile.getAbsolutePath()},
                null,
                new DownloadOnScanCompletedListener(context, downloadEntry)
        );
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void addToMediaLibrary(Context context, DownloadDatabase.DownloadEntry downloadEntry, Uri local_url) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        Uri audioCollection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        Uri uri = null;

        String[] projection = {MediaStore.Audio.AudioColumns._ID};
        String selection = MediaStore.Audio.AudioColumns.TITLE + "=? and " + MediaStore.Audio.Media.ALBUM + "=? and " + MediaStore.Audio.Media.ARTIST + "=?";
        String[] selectionArgs = new String[]{downloadEntry.title, downloadEntry.album, downloadEntry.artist};
        try (Cursor cursor = resolver.query(audioCollection, projection, selection, selectionArgs, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                uri = ContentUris.withAppendedId(audioCollection, cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns._ID)));
                Log.i(TAG, downloadEntry.title + " found in media library: " + uri);
            }
        }
        if (uri == null) {
            File file = new File(downloadEntry.fileName);
            File parent = file.getParentFile();
            ContentValues songDetails = new ContentValues();
            songDetails.put(MediaStore.Audio.Media.DISPLAY_NAME, file.getName());
            if (parent != null) {
                songDetails.put(MediaStore.Audio.Media.RELATIVE_PATH, new File(Environment.DIRECTORY_MUSIC, parent.getPath()).getPath());
            }

            // Attempt to look up mime type
            String mimeType = resolver.getType(local_url);
            if (mimeType == null) {
                mimeType = Files.probeContentType(file.toPath());
            }
            if (mimeType != null) {
                songDetails.put(MediaStore.Audio.Media.MIME_TYPE, mimeType);
            }

            uri = resolver.insert(audioCollection, songDetails);
            Log.i(TAG, downloadEntry.title + " added to media library: " + uri);
        }
        if (uri == null) {
            throw new IOException("Failed to insert downloaded file: " + downloadEntry.fileName);
        }

        Util.moveFile(resolver, local_url, uri);
    }

    private static class DownloadOnScanCompletedListener implements MediaScannerConnection.OnScanCompletedListener {
        private final Context context;
        private final DownloadDatabase.DownloadEntry downloadEntry;

        DownloadOnScanCompletedListener(Context context, DownloadDatabase.DownloadEntry downloadEntry) {
            this.context = context;
            this.downloadEntry = downloadEntry;
        }

        @Override
        public void onScanCompleted(String path, final Uri uri) {
            Log.i(TAG, "onScanCompleted('" + path + "'): " + uri);
            if (uri == null) {
                // Scanning failed, probably the file format is not supported.
                Log.i(TAG, "'" + path + "' could not be added to the media database");
                if (!new File(path).delete()) {
                    // TODO remote logging
                    Log.e(TAG, "Could not delete '" + path + "', which could not be added to the media database");
                }
                notifyFailedMediaScan(downloadEntry.fileName);
            }
        }

        private void notifyFailedMediaScan(String fileName) {
            String name = Util.getBaseName(fileName);

            // Content intent is required on some API levels even if
            // https://developer.android.com/guide/topics/ui/notifiers/notifications.html
            // says it's optional
            PendingIntent emptyPendingIntent = PendingIntent.getService(
                    context,
                    0,
                    new Intent(),  //Dummy Intent do nothing
                    0);

            final NotificationCompat.Builder builder = new NotificationCompat.Builder(context, SqueezeService.NOTIFICATION_CHANNEL_ID);
            builder.setContentIntent(emptyPendingIntent);
            builder.setOngoing(false);
            builder.setOnlyAlertOnce(true);
            builder.setAutoCancel(true);
            builder.setSmallIcon(R.drawable.squeezer_notification);
            builder.setTicker(name + " " + context.getString(R.string.NOTIFICATION_DOWNLOAD_MEDIA_SCANNER_ERROR));
            builder.setContentTitle(name);
            builder.setContentText(context.getString(R.string.NOTIFICATION_DOWNLOAD_MEDIA_SCANNER_ERROR));

            final NotificationManagerCompat nm = NotificationManagerCompat.from(context);
            nm.notify(SqueezeService.DOWNLOAD_ERROR, builder.build());
        }
    }

}
