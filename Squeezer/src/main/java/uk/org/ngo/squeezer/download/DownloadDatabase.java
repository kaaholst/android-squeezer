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
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Maintain a queue of download requests.
 * <p>
 * Enqueue a new download request via {@link #enqueueDownload(DownloadManager, String, Uri, String)} and
 * call {@link #popDownloadEntry(Context, long)} when a download is completed.
 */
public class DownloadDatabase {
    public static final String TAG = DownloadDatabase.class.getSimpleName();

    private static class DOWNLOAD_DATABASE {
        private static final String NAME = "download";
        private static final int VERSION = 5;

        private static class SONG {
            private static final String TABLE = "download";

            private static class COLUMNS {
                private static final String DOWNLOAD_ID = "download_id";
                private static final String URL = "url";
                private static final String FILE_NAME = "file_name";
                private static final String CREDENTIALS = "credentials";
                private static final String TITLE = "title";
                private static final String ALBUM = "album";
                private static final String ARTIST = "artist";
            }
        }
    }

    private final SQLiteDatabase db;

    public DownloadDatabase(Context context) {
        db = OpenHelper.getInstance(context).getWritableDatabase();
    }

    private static class OpenHelper extends SQLiteOpenHelper {

        private static final Object mInstanceLock = new Object();
        private static OpenHelper mInstance;

        private OpenHelper(Context context) {
            // calls the super constructor, requesting the default cursor
            // factory.
            super(context, DOWNLOAD_DATABASE.NAME, null, DOWNLOAD_DATABASE.VERSION);
        }

        public static OpenHelper getInstance(Context context) {
            if (mInstance == null) {
                synchronized (mInstanceLock) {
                    if (mInstance == null) {
                        mInstance = new OpenHelper(context);
                    }
                }
            }
            return mInstance;
        }

        @Override
        public void onCreate(SQLiteDatabase sqLiteDatabase) {
            sqLiteDatabase.execSQL("CREATE TABLE " + DOWNLOAD_DATABASE.SONG.TABLE + "(" +
                    DOWNLOAD_DATABASE.SONG.COLUMNS.DOWNLOAD_ID + " INTEGER, " +
                    DOWNLOAD_DATABASE.SONG.COLUMNS.URL + " TEXT, " +
                    DOWNLOAD_DATABASE.SONG.COLUMNS.FILE_NAME + " TEXT, " +
                    DOWNLOAD_DATABASE.SONG.COLUMNS.CREDENTIALS + " TEXT, " +
                    DOWNLOAD_DATABASE.SONG.COLUMNS.TITLE + " TEXT, " +
                    DOWNLOAD_DATABASE.SONG.COLUMNS.ALBUM + " TEXT, " +
                    DOWNLOAD_DATABASE.SONG.COLUMNS.ARTIST + " TEXT)");
        }

        @Override
        public void onUpgrade(SQLiteDatabase sqLiteDatabase, int oldVersion, int newVersion) {
            sqLiteDatabase.execSQL("DROP TABLE IF EXISTS " + DOWNLOAD_DATABASE.SONG.TABLE);
            // Upgrades just creates a new database. The database keeps track of
            // active downloads, so it holds only temporary information.
            onCreate(sqLiteDatabase);
        }

    }

    /**
     * Register a download request.
     */
    public void registerDownload(Context context, String credentials, Uri url, @NonNull String fileName, @NonNull String title, String album, String artist) {
        // To avoid download manager stops processing our requests due to exceeding the rate
        // limit for notifications (because download manager shows a notification), we delay
        // enqueuing further download requests until any current enqueued requests is completed.
        DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        long downloadId = (activeRequests() < 4) ? enqueueDownload(downloadManager, credentials, url, title) : -1;

        ContentValues contentValues = new ContentValues();
        contentValues.put(DOWNLOAD_DATABASE.SONG.COLUMNS.DOWNLOAD_ID, downloadId);
        contentValues.put(DOWNLOAD_DATABASE.SONG.COLUMNS.URL, url.toString());
        contentValues.put(DOWNLOAD_DATABASE.SONG.COLUMNS.FILE_NAME, fileName);
        contentValues.put(DOWNLOAD_DATABASE.SONG.COLUMNS.CREDENTIALS, credentials);
        contentValues.put(DOWNLOAD_DATABASE.SONG.COLUMNS.TITLE, title);
        contentValues.put(DOWNLOAD_DATABASE.SONG.COLUMNS.ALBUM, album);
        contentValues.put(DOWNLOAD_DATABASE.SONG.COLUMNS.ARTIST, artist);
        if (db.insert(DOWNLOAD_DATABASE.SONG.TABLE, null, contentValues) == -1) {
            Log.w(TAG, "Could not register download entry for: " + title);
            if (downloadId != -1) {
                downloadManager.remove(downloadId);
            }
        }
    }

    /**
     * Enqueue a download if any pending
     */
    private void maybeEnqueueDownload(Context context) {
        DownloadEntry entry = null;

        try (Cursor cursor = db.rawQuery("select * from " + DOWNLOAD_DATABASE.SONG.TABLE +
                        " where " + DOWNLOAD_DATABASE.SONG.COLUMNS.DOWNLOAD_ID + "=?", new String[]{String.valueOf(-1)})) {
            if (cursor.moveToNext()) {
                entry = getDownloadEntry(cursor);
            }
        }
        if (entry != null) {
            DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            long downloadId = enqueueDownload(downloadManager, entry.credentials, entry.url, entry.title);
            ContentValues contentValues = new ContentValues();
            contentValues.put(DOWNLOAD_DATABASE.SONG.COLUMNS.DOWNLOAD_ID, downloadId);
            db.update(DOWNLOAD_DATABASE.SONG.TABLE, contentValues, DOWNLOAD_DATABASE.SONG.COLUMNS.URL + "=?",
                    new String[]{String.valueOf(entry.url)});
        }
    }

    private long activeRequests() {
        try (Cursor cursor = db.rawQuery("select count(*) from " + DOWNLOAD_DATABASE.SONG.TABLE +
                " where " + DOWNLOAD_DATABASE.SONG.COLUMNS.DOWNLOAD_ID + " <>?", new String[]{String.valueOf(-1)})) {
            if (cursor.moveToNext()) {
                return cursor.getLong(0);
            }
        }
        return 0;
    }

    private long enqueueDownload(DownloadManager downloadManager, String credentials, Uri url, @NonNull String title) {
        String base64EncodedCredentials = Base64.encodeToString(credentials.getBytes(), Base64.NO_WRAP);
        DownloadManager.Request request = new DownloadManager.Request(url)
                .setTitle(title)
                .setVisibleInDownloadsUi(false)
                .addRequestHeader("Authorization", "Basic " + base64EncodedCredentials);
        long downloadId = downloadManager.enqueue(request);
        Log.i(TAG, "download enqueued[" + title + "]: " + downloadId);
        return downloadId;
    }

    /**
     * Search for a previously registered download entry with the supplied id.
     * If an entry is found it is returned, the download is unregistered and if any pending
     * downloads a new one is enqueued.
     *
     * @param downloadId Download id
     * @return The registered download entry or null if not found
     */
    @Nullable
    public DownloadEntry popDownloadEntry(Context context, long downloadId) {
        DownloadEntry entry = null;

        try (Cursor cursor = db.rawQuery("select * from " + DOWNLOAD_DATABASE.SONG.TABLE +
                        " where " + DOWNLOAD_DATABASE.SONG.COLUMNS.DOWNLOAD_ID + "=?",
                new String[]{String.valueOf(downloadId)})) {
            if (cursor.moveToNext()) {
                entry = getDownloadEntry(cursor);
            }
        }
        if (entry != null) {
            db.delete(DOWNLOAD_DATABASE.SONG.TABLE, DOWNLOAD_DATABASE.SONG.COLUMNS.DOWNLOAD_ID + "=?",
                    new String[]{String.valueOf(downloadId)});
            maybeEnqueueDownload(context);
        }

        return entry;
    }

    public void iterateDownloadEntries(DownloadHandler callback) {
        try (Cursor cursor = db.rawQuery("select * from " + DOWNLOAD_DATABASE.SONG.TABLE, null)) {
            while (cursor.moveToNext()) {
                callback.handle(getDownloadEntry(cursor));
            }
        }
    }

    private DownloadEntry getDownloadEntry(Cursor cursor) {
        DownloadEntry entry = new DownloadEntry();
        entry.downloadId = cursor.getLong(cursor.getColumnIndexOrThrow(DOWNLOAD_DATABASE.SONG.COLUMNS.DOWNLOAD_ID));
        entry.url = Uri.parse(cursor.getString(cursor.getColumnIndexOrThrow(DOWNLOAD_DATABASE.SONG.COLUMNS.URL)));
        entry.fileName = cursor.getString(cursor.getColumnIndexOrThrow(DOWNLOAD_DATABASE.SONG.COLUMNS.FILE_NAME));
        entry.credentials = cursor.getString(cursor.getColumnIndexOrThrow(DOWNLOAD_DATABASE.SONG.COLUMNS.CREDENTIALS));
        entry.title = cursor.getString(cursor.getColumnIndexOrThrow(DOWNLOAD_DATABASE.SONG.COLUMNS.TITLE));
        entry.album = cursor.getString(cursor.getColumnIndexOrThrow(DOWNLOAD_DATABASE.SONG.COLUMNS.ALBUM));
        entry.artist = cursor.getString(cursor.getColumnIndexOrThrow(DOWNLOAD_DATABASE.SONG.COLUMNS.ARTIST));
        return entry;
    }

    public void remove(Uri url) {
        db.delete(DOWNLOAD_DATABASE.SONG.TABLE, DOWNLOAD_DATABASE.SONG.COLUMNS.URL + "=?", new String[]{url.toString()});
    }

    public static class DownloadEntry {
        public long downloadId;
        public Uri url;
        public String fileName;
        public String credentials;
        public String title;
        public String album;
        public String artist;
    }

    public interface DownloadHandler {
        void handle(DownloadEntry entry);
    }
}
