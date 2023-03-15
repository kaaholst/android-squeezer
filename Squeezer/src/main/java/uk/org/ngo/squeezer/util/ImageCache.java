/*
 * Copyright (C) 2012 The Android Open Source Project
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

package uk.org.ngo.squeezer.util;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.os.Environment;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.collection.LruCache;

import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import uk.org.ngo.squeezer.BuildConfig;
import uk.org.ngo.squeezer.Util;

/**
 * This class holds our bitmap caches (memory and disk).
 */
public class ImageCache {

    private static final String TAG = "ImageCache";

    // Default memory cache size
    private static final int DEFAULT_MEM_CACHE_SIZE = 1024 * 1024 * 5; // 5MB

    // Default disk cache size
    private static final int MAX_DISK_CACHE_SIZE = 1024 * 1024 * 100; // 100MB

    // Default disk cache percent
    private static final float DEFAULT_DISK_CACHE_SIZE_PERCENT = 0.2f;

    // Compression settings when writing images to disk cache
    private static final CompressFormat DEFAULT_COMPRESS_FORMAT = CompressFormat.JPEG;

    private static final int DEFAULT_COMPRESS_QUALITY = 70;

    private static final int DISK_CACHE_INDEX = 0;

    // Constants to easily toggle various caches
    private static final boolean DEFAULT_MEM_CACHE_ENABLED = true;

    private static final boolean DEFAULT_DISK_CACHE_ENABLED = true;

    private static final boolean DEFAULT_CLEAR_DISK_CACHE_ON_START = false;

    private static final boolean DEFAULT_INIT_DISK_CACHE_ON_CREATE = false;

    private DiskLruCache mDiskLruCache;

    private LruCache<String, Bitmap> mMemoryCache;

    private ImageCacheParams mCacheParams;

    private final Object mDiskCacheLock = new Object();

    private boolean mDiskCacheStarting = true;

    /**
     * Creating a new ImageCache object using the specified parameters.
     *
     * @param cacheParams The cache parameters to use to initialize the cache
     */
    public ImageCache(ImageCacheParams cacheParams) {
        init(cacheParams);
    }

    /**
     * Creating a new ImageCache object using the default parameters.
     *
     * @param context The context to use
     * @param uniqueName A unique name that will be appended to the cache directory
     */
    public ImageCache(Context context, String uniqueName) {
        init(new ImageCacheParams(context, uniqueName));
    }

    /**
     * Find and return an existing ImageCache stored in a {@link RetainFragment}, if not found a new
     * one is created using the supplied params and saved to a {@link RetainFragment}.
     *
     * @param fragmentManager The fragment manager to use when dealing with the retained fragment.
     * @param cacheParams The cache parameters to use if creating the ImageCache
     *
     * @return An existing retained ImageCache object or a new one if one did not exist
     */
    public static ImageCache findOrCreateCache(
            FragmentManager fragmentManager, ImageCacheParams cacheParams) {

        // Search for, or create an instance of the non-UI RetainFragment
        final RetainFragment mRetainFragment = RetainFragment.getInstance(TAG, fragmentManager);

        // See if we already have an ImageCache stored in RetainFragment
        ImageCache imageCache = (ImageCache) mRetainFragment.get(TAG);

        // No existing ImageCache, create one and store it in RetainFragment
        if (imageCache == null) {
            imageCache = new ImageCache(cacheParams);
            mRetainFragment.put(TAG, imageCache);
        }

        return imageCache;
    }

    /**
     * Initialize the cache, providing all parameters.
     *
     * @param cacheParams The cache parameters to initialize the cache
     */
    private void init(ImageCacheParams cacheParams) {
        mCacheParams = cacheParams;

        // Set up memory cache
        if (mCacheParams.memoryCacheEnabled) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Memory cache created (size = " + mCacheParams.memCacheSize + ")");
            }
            mMemoryCache = new LruCache<String, Bitmap>(mCacheParams.memCacheSize) {
                @Override
                protected int sizeOf(String key, Bitmap bitmap) {
                    return (bitmap.getRowBytes() * bitmap.getHeight());
                }
            };
        }

        // By default the disk cache is not initialized here as it should be initialized
        // on a separate thread due to disk access.
        if (cacheParams.initDiskCacheOnCreate) {
            // Set up disk cache
            initDiskCache();
        }
    }

    /**
     * Initializes the disk cache.  Note that this includes disk access so this should not be
     * executed on the main/UI thread. By default an ImageCache does not initialize the disk cache
     * when it is created, instead you should call initDiskCache() to initialize it on a background
     * thread.
     */
    public void initDiskCache() {
        // Set up disk cache
        synchronized (mDiskCacheLock) {
            if (mDiskLruCache == null || mDiskLruCache.isClosed()) {
                File diskCacheDir = mCacheParams.diskCacheDir;
                if (mCacheParams.diskCacheEnabled && diskCacheDir != null) {
                    if (!diskCacheDir.exists()) {
                        diskCacheDir.mkdirs();
                    }
                    long usableSpace = getUsableSpace(diskCacheDir);
                    long diskCacheSize = Math.round(Math.min(
                            usableSpace * mCacheParams.diskCacheSizePercent,
                            MAX_DISK_CACHE_SIZE));
                    Log.d(TAG, "Usable space: " + usableSpace);
                    Log.d(TAG, "  Cache size: " + diskCacheSize);

                    if (usableSpace > diskCacheSize) {
                        try {
                            mDiskLruCache = DiskLruCache.open(diskCacheDir, 1, 1, diskCacheSize);
                            if (BuildConfig.DEBUG) {
                                Log.d(TAG, "Disk cache initialized in " + diskCacheDir);
                            }
                        } catch (final IOException e) {
                            mCacheParams.diskCacheDir = null;
                            Log.e(TAG, "initDiskCache - " + e);
                        }
                    }
                }
            }
            mDiskCacheStarting = false;
            mDiskCacheLock.notifyAll();
        }
    }

    /**
     * Adds a bitmap to both memory and disk cache.
     *
     * @param data Unique identifier for the bitmap to store
     * @param bitmap The bitmap to store
     */
    public void addBitmapToCache(String data, Bitmap bitmap) {
        if (data == null || bitmap == null) {
            return;
        }

        addBitmapToMemoryCache(data, bitmap);
        addBitmapToDiskCache(data, bitmap);
    }

    /**
     * Adds a bitmap to the memory cache.
     *
     * @param data Unique identifier for the bitmap to store
     * @param bitmap The bitmap to store
     */
    public void addBitmapToMemoryCache(String data, Bitmap bitmap) {
        if (data == null || bitmap == null) {
            return;
        }

        // Add to memory cache
        if (mMemoryCache != null && mMemoryCache.get(data) == null) {
            mMemoryCache.put(data, bitmap);
        }
    }

    /**
     * Adds a bitmap to the disk cache.
     *
     * @param data Unique identifier for the bitmap to store
     * @param bitmap The bitmap to store
     */
    public void addBitmapToDiskCache(String data, Bitmap bitmap) {
        if (data == null || bitmap == null) {
            return;
        }

        synchronized (mDiskCacheLock) {
            // Add to disk cache
            if (mDiskLruCache != null) {
                final String key = hashKeyForDisk(data);
                try (DiskLruCache.Snapshot snapshot = mDiskLruCache.get(key)) {
                    if (snapshot == null) {
                        final DiskLruCache.Editor editor = mDiskLruCache.edit(key);
                        if (editor != null) {
                            try (OutputStream out = editor.newOutputStream(DISK_CACHE_INDEX)) {
                                bitmap.compress(mCacheParams.compressFormat, mCacheParams.compressQuality, out);
                                editor.commit();
                            }
                        }
                    }
                } catch (final IOException e) {
                    Log.e(TAG, "addBitmapToCache - " + e);
                } catch (Exception e) {
                    Log.e(TAG, "addBitmapToCache - " + e);
                }
            }
        }
    }

    /**
     * Adds a byte[] to the disk cache.
     *
     * @param data Unique identifier for the bitmap to store
     * @param bytes The bytes to store
     */
    public void addBytesToDiskCache(String data, byte[] bytes) {
        if (data == null || bytes.length == 0) {
            return;
        }

        synchronized (mDiskCacheLock) {
            // Add to disk cache
            if (mDiskLruCache != null) {
                final String key = hashKeyForDisk(data);
                try (DiskLruCache.Snapshot snapshot = mDiskLruCache.get(key)) {
                    if (snapshot == null) {
                        final DiskLruCache.Editor editor = mDiskLruCache.edit(key);
                        if (editor != null) {
                            try (OutputStream out = editor.newOutputStream(DISK_CACHE_INDEX)) {
                                out.write(bytes);
                                editor.commit();
                            }
                        }
                    }
                } catch (final IOException e) {
                    Log.e(TAG, "addBitmapToCache - " + e);
                } catch (Exception e) {
                    Log.e(TAG, "addBitmapToCache - " + e);
                }
            }
        }
    }

    /**
     * Get from memory cache.
     *
     * @param data Unique identifier for which item to get
     *
     * @return The bitmap if found in cache, null otherwise
     */
    public Bitmap getBitmapFromMemCache(String data) {
        Bitmap bitmap = null;

        if (mMemoryCache != null) {
            bitmap = mMemoryCache.get(data);

            /*
            Log.d(TAG, String.format(
                    "Cache stats: h: %d m: %d (%.2f%%) p: %d e: %d, size: %d of %d (%.2f%%)",
                    mMemoryCache.hitCount(),
                    mMemoryCache.missCount(),
                    ((float) mMemoryCache.hitCount()
                            / (mMemoryCache.hitCount() + mMemoryCache.missCount()) * 100),
                    mMemoryCache.putCount(),
                    mMemoryCache.evictionCount(), mMemoryCache.size(), mMemoryCache.maxSize(),
                    ((float) mMemoryCache.size() / mMemoryCache.maxSize()) * 100));
            */
        }

        return bitmap;
    }

    /**
     * Get from disk cache.
     *
     * @param data Unique identifier for which item to get
     *
     * @return The bitmap if found in cache, null otherwise
     */
    @Nullable
    public Bitmap getBitmapFromDiskCache(String data) {
        final String key = hashKeyForDisk(data);
        synchronized (mDiskCacheLock) {
            while (mDiskCacheStarting) {
                try {
                    mDiskCacheLock.wait();
                } catch (InterruptedException e) {
                }
            }
            if (mDiskLruCache != null) {
                try (DiskLruCache.Snapshot snapshot = mDiskLruCache.get(key)) {
                    if (snapshot != null) {
                        if (BuildConfig.DEBUG) {
                            Log.d(TAG, "Disk cache hit");
                        }
                        final InputStream inputStream = snapshot.getInputStream(DISK_CACHE_INDEX);
                        if (inputStream != null) {
                            return BitmapFactory.decodeStream(inputStream);
                        }
                    }
                } catch (final IOException e) {
                    Log.e(TAG, "getBitmapFromDiskCache - " + e);
                }
            }
            return null;
        }
    }

    /**
     * Get byte[] from disk cache.
     *
     * @param data Unique identifier for which item to get
     *
     * @return The bytes at that entry in the cache, null otherwise
     */
    @Nullable
    public byte[] getBytesFromDiskCache(String data) {
        final String key = hashKeyForDisk(data);
        synchronized (mDiskCacheLock) {
            while (mDiskCacheStarting) {
                try {
                    mDiskCacheLock.wait();
                } catch (InterruptedException e) {
                }
            }
            if (mDiskLruCache != null) {
                try (DiskLruCache.Snapshot snapshot = mDiskLruCache.get(key)) {
                    if (snapshot != null) {
                        if (BuildConfig.DEBUG) {
                            Log.d(TAG, "Disk cache hit");
                        }
                        InputStream inputStream = snapshot.getInputStream(DISK_CACHE_INDEX);
                        if (inputStream != null) {
                            return Util.toByteArray(inputStream);
                        }
                    }
                } catch (final IOException e) {
                    Log.e(TAG, "getBitmapFromDiskCache - " + e);
                }
            }
            return null;
        }
    }

    /**
     * Clears both the memory and disk cache associated with this ImageCache object. Note that this
     * includes disk access so this should not be executed on the main/UI thread.
     */
    public void clearCache() {
        clearMemoryCache();

        synchronized (mDiskCacheLock) {
            mDiskCacheStarting = true;
            if (mDiskLruCache != null && !mDiskLruCache.isClosed()) {
                try {
                    mDiskLruCache.delete();
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Disk cache cleared");
                    }
                } catch (IOException e) {
                    Log.e(TAG, "clearCache - " + e);
                }
                mDiskLruCache = null;
                initDiskCache();
            }
        }
    }

    public void clearMemoryCache() {
        if (mMemoryCache != null) {
            mMemoryCache.evictAll();
            Log.d(TAG, "Memory cache cleared");
        }
    }

    /**
     * Flushes the disk cache associated with this ImageCache object. Note that this includes disk
     * access so this should not be executed on the main/UI thread.
     */
    public void flush() {
        synchronized (mDiskCacheLock) {
            if (mDiskLruCache != null) {
                try {
                    mDiskLruCache.flush();
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Disk cache flushed");
                    }
                } catch (IOException e) {
                    Log.e(TAG, "flush - " + e);
                }
            }
        }
    }

    /**
     * Closes the disk cache associated with this ImageCache object. Note that this includes disk
     * access so this should not be executed on the main/UI thread.
     */
    public void close() {
        synchronized (mDiskCacheLock) {
            if (mDiskLruCache != null) {
                try {
                    if (!mDiskLruCache.isClosed()) {
                        mDiskLruCache.close();
                        mDiskLruCache = null;
                        if (BuildConfig.DEBUG) {
                            Log.d(TAG, "Disk cache closed");
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG, "close - " + e);
                }
            }
        }
    }

    /**
     * A holder class that contains cache parameters.
     */
    public static class ImageCacheParams {

        public int memCacheSize = DEFAULT_MEM_CACHE_SIZE;

        public float diskCacheSizePercent = DEFAULT_DISK_CACHE_SIZE_PERCENT;

        public final int maxDiskCacheSize = MAX_DISK_CACHE_SIZE;

        public File diskCacheDir;

        public final CompressFormat compressFormat = DEFAULT_COMPRESS_FORMAT;

        public final int compressQuality = DEFAULT_COMPRESS_QUALITY;

        public final boolean memoryCacheEnabled = DEFAULT_MEM_CACHE_ENABLED;

        public final boolean diskCacheEnabled = DEFAULT_DISK_CACHE_ENABLED;

        public boolean clearDiskCacheOnStart = DEFAULT_CLEAR_DISK_CACHE_ON_START;

        public final boolean initDiskCacheOnCreate = DEFAULT_INIT_DISK_CACHE_ON_CREATE;

        public ImageCacheParams(Context context, String uniqueName) {
            diskCacheDir = getDiskCacheDir(context, uniqueName);
        }

        public ImageCacheParams(File diskCacheDir) {
            this.diskCacheDir = diskCacheDir;
        }

        /**
         * Sets the memory cache size based on a percentage of the device memory class. Eg. setting
         * percent to 0.2 would set the memory cache to one fifth of the device memory class. Throws
         * {@link IllegalArgumentException} if percent is < 0.05 or > .8.
         * <p>
         * This value should be chosen carefully based on a number of factors Refer to the
         * corresponding Android Training class for more discussion: http://developer.android.com/training/displaying-bitmaps/
         *
         * @param context Context to use to fetch memory class
         * @param percent Percent of memory class to use to size memory cache
         */
        public void setMemCacheSizePercent(Context context, float percent) {
            if (percent < 0.05f || percent > 0.8f) {
                throw new IllegalArgumentException("setMemCacheSizePercent - percent must be "
                        + "between 0.05 and 0.8 (inclusive)");
            }
            memCacheSize = Math.round(percent * getMemoryClass(context) * 1024 * 1024);
        }

        private static int getMemoryClass(Context context) {
            return ((ActivityManager) context.getSystemService(
                    Context.ACTIVITY_SERVICE)).getMemoryClass();
        }
    }

    /**
     * Get a usable cache directory (external if available, internal otherwise).
     *
     * @param context The context to use
     * @param uniqueName A unique directory name to append to the cache dir
     *
     * @return The cache dir
     */
    public static File getDiskCacheDir(Context context, String uniqueName) {
        // Check if media is mounted or storage is built-in, if so, try and use external cache dir
        // otherwise use internal cache dir
        File externalCacheDir = getExternalCacheDir(context);
        final String cachePath = ((Environment.MEDIA_MOUNTED.equals(Environment
                .getExternalStorageState()) || !isExternalStorageRemovable())
                && externalCacheDir != null)
                ? externalCacheDir.getPath()
                : context.getCacheDir().getPath();

        return new File(cachePath + File.separator + uniqueName);
    }

    /**
     * A hashing method that changes a string (like a URL) into a hash suitable for using as a disk
     * filename.  The hashing method is MD5.
     */
    public static String hashKeyForDisk(String key) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        byte[] bytes = digest.digest(key.getBytes());
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));

        }
        return sb.toString();
    }

    /**
     * Get the size in bytes of a bitmap.
     *
     * @param bitmap Bitmap to examine.
     *
     * @return size in bytes
     */
    public static int getBitmapSize(Bitmap bitmap) {
        return bitmap.getByteCount();
    }

    /**
     * Check if external storage is built-in or removable.
     *
     * @return True if external storage is removable (like an SD card), false otherwise.
     */
    public static boolean isExternalStorageRemovable() {
        return Environment.isExternalStorageRemovable();
    }

    /**
     * Get the external app cache directory.
     *
     * @param context The context to use
     *
     * @return The external cache dir
     */
    @Nullable
    public static File getExternalCacheDir(Context context) {
        return context.getExternalCacheDir();
    }

    /**
     * Check how much usable space is available at a given path.
     *
     * @param path The path to check
     *
     * @return The space available in bytes
     */
    public static long getUsableSpace(File path) {
        return path.getUsableSpace();
    }
}
