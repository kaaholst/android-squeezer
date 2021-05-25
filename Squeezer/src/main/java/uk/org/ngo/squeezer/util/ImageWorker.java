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

import android.app.Activity;
import android.app.Notification;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import androidx.annotation.IdRes;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationManagerCompat;

import android.text.TextUtils;
import android.util.Log;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.RemoteViews;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;
import java.util.Arrays;

import uk.org.ngo.squeezer.BuildConfig;

/**
 * This class wraps up completing some arbitrary long running work when loading a bitmap to an
 * ImageView. It handles things like using a memory and disk cache, running the work in a background
 * thread and setting a placeholder image.
 */
public abstract class ImageWorker {

    private static final String TAG = "ImageWorker";

    private static final int FADE_IN_TIME = 200;

    private ImageCache mImageCache;

    private Bitmap mLoadingBitmap;

    private boolean mFadeInBitmap = true;

    private boolean mExitTasksEarly = false;

    protected boolean mPauseWork = false;

    private final Object mPauseWorkLock = new Object();

    protected final Resources mResources;

    @IntDef({MESSAGE_CLEAR, MESSAGE_INIT_DISK_CACHE, MESSAGE_FLUSH, MESSAGE_CLOSE,
            MESSAGE_CLEAR_MEMORY_CACHE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface CacheMessages {}
    private static final int MESSAGE_CLEAR = 0;
    private static final int MESSAGE_INIT_DISK_CACHE = 1;
    private static final int MESSAGE_FLUSH = 2;
    private static final int MESSAGE_CLOSE = 3;
    private static final int MESSAGE_CLEAR_MEMORY_CACHE = 4;

    /** Paint to use when colouring debug swatches on images. */
    private static final Paint mCacheDebugPaint = new Paint();

    /** Colour of debug swatch for images loaded from memory cache. */
    private static final int mCacheDebugColorMemory = Color.GREEN;

    /** Colour of debug swatch for images loaded from disk cache. */
    private static final int mCacheDebugColorDisk = Color.BLUE;

    /** Colour of debug swatch for images loaded from network (no caching). */
    private static final int mCacheDebugColorNetwork = Color.RED;

    protected ImageWorker(Context context) {
        mResources = context.getResources();
    }

    /**
     * Load an image specified by the data parameter into an ImageView (override {@link
     * ImageWorker#processBitmap(BitmapWorkerTaskParams)} to define the processing logic). A memory and disk cache
     * will be used if an {@link ImageCache} has been set using {@link
     * ImageWorker#setImageCache(ImageCache)}. If the image is found in the memory cache, it is set
     * immediately, otherwise an {@link AsyncTask} will be created to asynchronously load the
     * bitmap.
     *
     * @param data The URL of the image to download
     * @param imageView The ImageView to bind the downloaded image to
     */
    public void loadImage(final Object data, final ImageView imageView) {
        if (data == null) {
            return;
        }

        int width = imageView.getWidth();
        int height = imageView.getHeight();

        // If the dimensions aren't known yet then the view hasn't been measured. Get a
        // ViewTreeObserver and listen for the PreDraw message. Using a GlobalLayoutListener
        // does not work for views that are in the list but drawn off-screen, possibly due
        // to the convertview. See http://stackoverflow.com/a/14325365 for some discussion.
        // The solution there, of posting a runnable, does not appear to reliably work on
        // devices running (at least) API 7. An OnPreDrawListener appears to work, and will
        // be called after measurement is complete.
        if (width == 0 || height == 0) {
            // Store the URL in the imageView's tag, in case the URL assigned to is changed.
            imageView.setTag(data);

            imageView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    imageView.getViewTreeObserver().removeOnPreDrawListener(this);
                    // If the imageView is still assigned to the URL then we can load in to it.
                    if (data.equals(imageView.getTag())) {
                        loadImage(data, imageView);
                    }
                    return true;
                }
            });
            return;
        }

        loadImage(data, imageView, width, height);
    }

    /**
     * Like {@link #loadImage(Object, ImageView)} but with explicit width and height parameters.
     *
     * @param data The URL of the image to download
     * @param imageView The ImageView to bind the downloaded image to
     * @param width Resize the image to this width (and save it in the memory cache as such)
     * @param height Resize the image to this height (and save it in the memory cache as such)
     * @param callback Will be called once an image is set on the view.
     */
    public void loadImage(final Object data, final ImageView imageView, int width, int height, LoadImageCallback callback) {
        Bitmap bitmap = null;
        String memCacheKey = hashKeyForMemory(String.valueOf(data), width, height);

        if (mImageCache != null) {
            bitmap = mImageCache.getBitmapFromMemCache(memCacheKey);
        }

        if (bitmap != null) {
            // Bitmap found in memory cache
            if (BuildConfig.DEBUG) {
                addDebugSwatch(new Canvas(bitmap), mCacheDebugColorMemory);
            }
            imageView.setImageBitmap(bitmap);
            if (callback != null) {
                callback.onDone();
            }
        } else if (cancelPotentialWork(data, imageView)) {
            final ImageViewBitmapWorkerTask task = new ImageViewBitmapWorkerTask(imageView, callback);
            final AsyncDrawable asyncDrawable =
                    new AsyncDrawable(mResources, mLoadingBitmap, task);
            imageView.setImageDrawable(asyncDrawable);

            // NOTE: This uses a custom version of AsyncTask that has been pulled from the
            // framework and slightly modified. Refer to the docs at the top of the class
            // for more info on what was changed.
            task.executeOnExecutor(AsyncTask.DUAL_THREAD_EXECUTOR,
                    new BitmapWorkerTaskParams(width, height, data, memCacheKey));
        }
    }

    /**
     * Like {@link #loadImage(Object, ImageView)} but with explicit width and height parameters.
     *
     * @param data The URL of the image to download
     * @param imageView The ImageView to bind the downloaded image to
     * @param width Resize the image to this width (and save it in the memory cache as such)
     * @param height Resize the image to this height (and save it in the memory cache as such)
     */
    public void loadImage(final Object data, final ImageView imageView, int width, int height) {
        loadImage(data, imageView, width, height, null);
    }

    /**
     * Interface for callbacks passed to {@link #loadImage(Object, ImageView, int, int, LoadImageCallback)}
     */
    public interface LoadImageCallback {
        /**
         * Called after the bitmap has been set on the view.
         */
        void onDone();
    }

    /**
     * Interface for callbacks passed to {@link #loadImage(Object, int, int, ImageWorkerCallback)}
     */
    public interface ImageWorkerCallback {
        /**
         * Called after the bitmap has been loaded.
         *
         * @param data The same value passed as the data parameter to
         *     {@link #loadImage(Object, int, int, ImageWorkerCallback)}
         * @param bitmap The bitmap, may be null if loading if it failed to load
         */
        void process(Object data, @Nullable Bitmap bitmap);
    }

    /**
     * Like {@link #loadImage(Object, ImageView, int, int)} but calls the provided callback after
     * the image has been loaded instead of saving it in to an imageview.
     *
     * @param data The URL of the image to download
     * @param width Resize the image to this width (and save it in the memory cache as such)
     * @param height Resize the image to this height (and save it in the memory cache as such)
     * @param callback The callback
     */
    public void loadImage(final Object data, int width, int height, ImageWorkerCallback callback) {
        Bitmap bitmap = null;
        String memCacheKey = hashKeyForMemory(String.valueOf(data), width, height);
        if (mImageCache != null) {
            bitmap = mImageCache.getBitmapFromMemCache(memCacheKey);
        }

        if (bitmap != null) {
            // Bitmap found in memory cache
            if (BuildConfig.DEBUG) {
                addDebugSwatch(new Canvas(bitmap), mCacheDebugColorMemory);
            }
            callback.process(data, bitmap);
        } else {
            final CallbackBitmapWorkerTask task = new CallbackBitmapWorkerTask(callback);

            task.executeOnExecutor(AsyncTask.DUAL_THREAD_EXECUTOR,
                    new BitmapWorkerTaskParams(width, height, data, memCacheKey));
        }
    }

    /**
     * Loads the requested image in to an {@link ImageView} in the given {@link RemoteViews}
     * and updates the notification when done.
     *
     * @param context system context
     * @param data The URL of the image to download
     * @param remoteViews The {@link RemoteViews} that contains the {@link ImageView}
     * @param viewId The identifier for the {@link ImageView}
     * @param width Resize the image to this width (and save it in the memory cache as such)
     * @param height Resize the image to this height (and save it in the memory cache as such)
     * @param nm The notification manager
     * @param notificationId Identifier of the notification to update
     * @param notification The notification to post
     */
    public void loadImage(Context context, final Object data, final RemoteViews remoteViews,
                          @IdRes int viewId, int width, int height,
                          NotificationManagerCompat nm, int notificationId, Notification notification) {
        Bitmap bitmap = null;
        String memCacheKey = hashKeyForMemory(String.valueOf(data), width, height);
        if (mImageCache != null) {
            bitmap = mImageCache.getBitmapFromMemCache(memCacheKey);
        }

        if (bitmap != null) {
            // Bitmap found in memory cache
            if (BuildConfig.DEBUG) {
                addDebugSwatch(new Canvas(bitmap), mCacheDebugColorMemory);
            }
            remoteViews.setImageViewBitmap(viewId, bitmap);
            nm.notify(notificationId, notification);
        } else {
            final RemoteViewBitmapWorkerTask task = new RemoteViewBitmapWorkerTask(
                    remoteViews, viewId, nm, notificationId, notification);
            final AsyncDrawable asyncDrawable = new AsyncDrawable(mResources, mLoadingBitmap, task);
            remoteViews.setImageViewBitmap(viewId, asyncDrawable.getBitmap());

            // NOTE: This uses a custom version of AsyncTask that has been pulled from the
            // framework and slightly modified. Refer to the docs at the top of the class
            // for more info on what was changed.
            task.executeOnExecutor(AsyncTask.DUAL_THREAD_EXECUTOR,
                    new BitmapWorkerTaskParams(width, height, data, memCacheKey));
        }
    }

    /**
     * Generates a hash key for the memory cache. The key includes the target width and height,
     * so that multiple copies of the image may exist in the cache at different sizes.
     *
     * @param data The identifier for the image (e.g., URL).
     * @param width Target width for the bitmap.
     * @param height Target height for the bitmap.
     * @return Cache key to use.
     */
    @NonNull
    private static String hashKeyForMemory(@NonNull String data, int width, int height) {
        return TextUtils.join(":", Arrays.asList(width, height, data));
    }

    /**
     * Set placeholder bitmap that shows when the the background thread is running.
     *
     * @param bitmap
     */
    public void setLoadingImage(Bitmap bitmap) {
        mLoadingBitmap = bitmap;
    }

    /**
     * Set placeholder bitmap that shows when the the background thread is running.
     *
     * @param resId
     */
    public void setLoadingImage(int resId) {
        mLoadingBitmap = BitmapFactory.decodeResource(mResources, resId);
    }

    /**
     * Adds a debug swatch to a canvas. The marker is a triangle pointing north-west
     * on the top left corner, the edges are 25% of the canvas' width and height.
     *
     * @param canvas The canvas to draw on.
     * @param color The colour to use for the swatch.
     */
    public static void addDebugSwatch(Canvas canvas, int color) {
        float width = canvas.getWidth();
        float height = canvas.getHeight();

        Path path = new Path();
        path.lineTo(width / 4, 0);
        path.lineTo(0, height / 4);
        path.lineTo(0, 0);

        // Draw the swatch.
        mCacheDebugPaint.setColor(color);
        mCacheDebugPaint.setStyle(Paint.Style.FILL);
        canvas.drawPath(path, mCacheDebugPaint);

        // Stroke the swatch with a white hairline.
        mCacheDebugPaint.setColor(Color.WHITE);
        mCacheDebugPaint.setStyle(Paint.Style.STROKE);
        mCacheDebugPaint.setStrokeWidth(0);
        canvas.drawPath(path, mCacheDebugPaint);
    }

    /**
     * Adds an {@link ImageCache} to this worker in the background, to prevent disk access on
     * the UI thread.
     *
     * @param imageCacheParams A description of the cache.
     */
    public void addImageCache(ImageCache.ImageCacheParams imageCacheParams) {
        setImageCache(new ImageCache(imageCacheParams));
        
        // AsyncTask instantiates an InternalHandler (which extends Handler but doesn't pass a
        // Looper so it grabs the current one, sometimes it's not the main thread and an exception
        // is thrown. This makes the Looper explicit.
        
        Handler handler = new Handler(Looper.getMainLooper()) {
            public void handleMessage(@NonNull Message msg) {
                    new CacheAsyncTask().execute(msg.what);
            }
        };
        handler.obtainMessage(MESSAGE_INIT_DISK_CACHE).sendToTarget();
    }

    /**
     * Sets the {@link ImageCache} object to use with this ImageWorker. Usually you will not need to
     * call this directly, instead use {@link ImageWorker#addImageCache} which will create and add
     * the {@link ImageCache} object in a background thread (to ensure no disk access on the main/UI
     * thread).
     *
     * @param imageCache
     */
    public void setImageCache(ImageCache imageCache) {
        mImageCache = imageCache;
    }

    /**
     * If set to true, the image will fade-in once it has been loaded by the background thread.
     */
    public void setImageFadeIn(boolean fadeIn) {
        mFadeInBitmap = fadeIn;
    }

    /**
     * Set the flag that determines whether tasks should exit early. Set to true if any pending
     * work should be abandoned (e.g., in {@link Activity#onPause()} ).
     *
     * @param exitTasksEarly
     */
    public void setExitTasksEarly(boolean exitTasksEarly) {
        mExitTasksEarly = exitTasksEarly;
    }

    /**
     * Subclasses should override this to define any processing or work that must happen to produce
     * the final bitmap. This will be executed in a background thread and be long running. For
     * example, you could resize a large bitmap here, or pull down an image from the network.
     *
     * @param params The parameters to identify which image to process, as provided by {@link
     * ImageWorker#loadImage(Object, ImageView)}
     *
     * @return The processed bitmap, or null if processing failed.
     */
    protected abstract byte[] processBitmap(BitmapWorkerTaskParams params);

    /**
     * Cancels any pending work attached to the provided ImageView.
     *
     * @param imageView
     */
    public static void cancelWork(ImageView imageView) {
        final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);
        if (bitmapWorkerTask != null) {
            bitmapWorkerTask.cancel(true);
            if (BuildConfig.DEBUG) {
                final Object bitmapData = bitmapWorkerTask.data;
                Log.d(TAG, "cancelWork - cancelled work for " + bitmapData);
            }
        }
    }

    /**
     * Returns true if the current work has been cancelled or if there was no work in progress on
     * this image view. Returns false if the work in progress deals with the same data. The work is
     * not stopped in that case.
     */
    public static boolean cancelPotentialWork(Object data, ImageView imageView) {
        final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);

        if (bitmapWorkerTask != null) {
            final Object bitmapData = bitmapWorkerTask.data;
            if (bitmapData == null || !bitmapData.equals(data)) {
                bitmapWorkerTask.cancel(true);
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "cancelPotentialWork - cancelled work for " + data);
                }
            } else {
                // The same work is already in progress.
                return false;
            }
        }
        return true;
    }

    /**
     * @param imageView Any imageView
     *
     * @return Retrieve the currently active work task (if any) associated with this imageView. null
     * if there is no such task.
     */
    private static BitmapWorkerTask getBitmapWorkerTask(ImageView imageView) {
        if (imageView != null) {
            final Drawable drawable = imageView.getDrawable();
            if (drawable instanceof AsyncDrawable) {
                final AsyncDrawable asyncDrawable = (AsyncDrawable) drawable;
                return asyncDrawable.getBitmapWorkerTask();
            }
        }
        return null;
    }

    protected class BitmapWorkerTaskParams {
        /** Desired bitmap width. */
        public final int width;

        /** Desired bitmap height. */
        public final int height;

        /** Identifier for the bitmap to fetch (e.g., a URL). */
        @NonNull
        public final Object data;

        /** Cache key to use when saving the bitmap in the memory cache. */
        @NonNull
        public final String memCacheKey;

        public BitmapWorkerTaskParams(int width, int height,
                                      @NonNull Object data, @NonNull String memCacheKey) {
            this.width = width;
            this.height = height;
            this.data = data;
            this.memCacheKey = memCacheKey;
        }

        @Override
        public String toString() {
            return "BitmapWorkerTaskParams{" +
                    "width=" + width +
                    ", height=" + height +
                    ", data=" + data +
                    ", memCacheKey='" + memCacheKey + '\'' +
                    '}';
        }
    }

    protected class RemoteViewBitmapWorkerTaskParams extends BitmapWorkerTaskParams {
        NotificationManagerCompat mNotificationManagerCompat;
        int mNotificationId;
        Notification mNotification;

        public RemoteViewBitmapWorkerTaskParams(int width, int height,
                                                @NonNull Object data, @NonNull String memCacheKey,
                                                @NonNull NotificationManagerCompat notificationManagerCompat,
                                                int notificationId,
                                                @NonNull Notification notification) {
            super(width, height, data, memCacheKey);
            mNotificationManagerCompat = notificationManagerCompat;
            mNotificationId = notificationId;
            mNotification = notification;
        }
    }

    /**
     * The actual AsyncTask that will asynchronously process the image.
     */
    private class BitmapWorkerTask extends AsyncTask<BitmapWorkerTaskParams, Void, Bitmap> {
        protected static final String TAG = "BitmapWorkerTask";
        protected Object data;

        /**
         * Background processing.
         */
        @Override
        protected Bitmap doInBackground(BitmapWorkerTaskParams... params) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "doInBackground - starting work");
            }

            boolean loadedFromNetwork = false;

            data = params[0].data;
            final String dataString = String.valueOf(data);
            byte[] bytes = null;
            Bitmap scaledBitmap = null;

            // Wait here if work is paused and the task is not cancelled
            synchronized (mPauseWorkLock) {
                while (mPauseWork && !isCancelled()) {
                    try {
                        mPauseWorkLock.wait();
                    } catch (InterruptedException e) {
                    }
                }
            }

            // If the image cache is available and this task has not been cancelled by another
            // thread and there's nothing to indicate this task should cancel then try and fetch
            // the bitmap bytes from the cache.
            if (mImageCache != null && !isCancelled() && !shouldCancel()) {
                bytes = mImageCache.getBytesFromDiskCache(dataString);
            }

            // If the bitmap was not found in the cache and this task has not been cancelled by
            // another thread and there's nothing to indicate that this task should cancel, then
            // call the main process method (as implemented by a subclass)
            if ((bytes == null || bytes.length == 0) && !isCancelled() && !shouldCancel()) {
                bytes = processBitmap(params[0]);
                loadedFromNetwork = true;

                // If the bitmap bytes were loaded then add them to the disk cache.
                if (bytes != null && bytes.length != 0 && mImageCache != null) {
                    mImageCache.addBytesToDiskCache(dataString, bytes);
                }
            }

            // Create a bitmap from the bytes, scaled to the appropriate size.
            if (bytes != null && bytes.length != 0 && params[0].width > 0 && params[0].height > 0) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);

                options.inSampleSize = calculateInSampleSize(
                        options, params[0].width, params[0].height);

                options.inJustDecodeBounds = false;

                if (! BuildConfig.DEBUG) {
                    // Not a debug build, just need the scaled bitmap.
                    scaledBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
                } else {
                    // Debug build, need a mutable bitmap to add the debug swatch later.
                    options.inMutable = true;
                    scaledBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
                }
            }

            // If the bitmap was processed and the image cache is available, then add the processed
            // bitmap to the memory cache for future use. Note we don't check if the task was
            // cancelled here, if it was, and the thread is still running, we may as well add the
            // processed bitmap to our cache as it might be used again in the future.
            if (scaledBitmap != null && mImageCache != null) {
                mImageCache.addBitmapToMemoryCache(params[0].memCacheKey, scaledBitmap);
            }

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "doInBackground - finished work");
            }

            if (BuildConfig.DEBUG && scaledBitmap != null) {
                if (loadedFromNetwork) {
                    addDebugSwatch(new Canvas(scaledBitmap), mCacheDebugColorNetwork);
                } else {
                    addDebugSwatch(new Canvas(scaledBitmap), mCacheDebugColorDisk);
                }

            }
            return scaledBitmap;
        }

        /**
         * Calculate an inSampleSize for use in a {@link BitmapFactory.Options} object when decoding
         * bitmaps using the decode* methods from {@link BitmapFactory}. This implementation calculates
         * the closest inSampleSize that will result in the final decoded bitmap having a width and
         * height equal to or larger than the requested width and height. This implementation does not
         * ensure a power of 2 is returned for inSampleSize which can be faster when decoding but
         * results in a larger bitmap which isn't as useful for caching purposes.
         *
         * @param options An options object with out* params already populated (run through a decode*
         * method with inJustDecodeBounds==true
         * @param reqWidth The requested width of the resulting bitmap
         * @param reqHeight The requested height of the resulting bitmap
         *
         * @return The value to be used for inSampleSize
         */
        public int calculateInSampleSize(BitmapFactory.Options options,
                                                int reqWidth, int reqHeight) {
            // Raw height and width of image
            final int height = options.outHeight;
            final int width = options.outWidth;
            int inSampleSize = 1;

            if (height > reqHeight || width > reqWidth) {
                if (width > height) {
                    inSampleSize = Math.round((float) height / (float) reqHeight);
                } else {
                    inSampleSize = Math.round((float) width / (float) reqWidth);
                }

                // This offers some additional logic in case the image has a strange
                // aspect ratio. For example, a panorama may have a much larger
                // width than height. In these cases the total pixels might still
                // end up being too large to fit comfortably in memory, so we should
                // be more aggressive with sample down the image (=larger
                // inSampleSize).

                final float totalPixels = width * height;

                // Anything more than 2x the requested pixels we'll sample down
                // further.
                final float totalReqPixelsCap = reqWidth * reqHeight * 2;

                while (totalPixels / (inSampleSize * inSampleSize) > totalReqPixelsCap) {
                    inSampleSize++;
                }
            }
            return inSampleSize;
        }

        @Override
        protected void onCancelled(Bitmap bitmap) {
            super.onCancelled(bitmap);
            synchronized (mPauseWorkLock) {
                mPauseWorkLock.notifyAll();
            }
        }

        /**
         * Determines whether bitmap processing should abort early.
         *
         * @return The value of {@code mExitTasksEarly}.
         */
        protected boolean shouldCancel() {
            return mExitTasksEarly;
        }
    }

    /**
     * A specialisation of {@link BitmapWorkerTask} that sets the loaded bitmap in to an
     * {@link ImageView}.
     */
    private class ImageViewBitmapWorkerTask extends BitmapWorkerTask {
        protected final WeakReference<ImageView> imageViewReference;
        private LoadImageCallback callback;

        public ImageViewBitmapWorkerTask(ImageView imageView, LoadImageCallback callback) {
            super();
            imageViewReference = new WeakReference<>(imageView);
            this.callback = callback;
        }

        /**
         * Once the image is processed, associates it to the imageView
         */
        @Override
        protected void onPostExecute(Bitmap bitmap) {
            // if cancel was called on this task or the "exit early" flag is set then we're done
            if (isCancelled() || mExitTasksEarly) {
                bitmap = null;
            }

            final ImageView imageView = getAttachedImageView();
            if (bitmap != null && imageView != null) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "onPostExecute - setting bitmap");
                }
                setImageBitmap(imageView, bitmap);
                if (callback != null) {
                    callback.onDone();
                }
            }
        }

        protected boolean shouldCancel() {
            return super.shouldCancel() && getAttachedImageView() == null;
        }

        /**
         * Returns the ImageView associated with this task as long as the ImageView's task still
         * points to this task as well. Returns null otherwise.
         */
        protected ImageView getAttachedImageView() {
            final ImageView imageView = imageViewReference.get();
            final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);

            if (this == bitmapWorkerTask) {
                return imageView;
            }

            return null;
        }

    }

    /**
     * A specialisation of {@link BitmapWorkerTask} that passed the loaded bitmap to a callback
     * for further processing.
     */
    private class CallbackBitmapWorkerTask extends BitmapWorkerTask {
        protected static final String TAG = "CallbackBitmapWorkerTas";

        private ImageWorkerCallback mCallback;

        public CallbackBitmapWorkerTask(ImageWorkerCallback callback) {
            mCallback = callback;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            Log.d(TAG, "callback: onPostExecute()");
            if (isCancelled() || shouldCancel()) {
                bitmap = null;
            }

            Log.d(TAG, "onPostExecute - setting bitmap");
            mCallback.process(data, bitmap);
        }

        /**
         * @return Always returns false, the processing is not aborted before the callback is called.
         */
        @Override
        protected boolean shouldCancel() {
            return false;
        }
    }

    /**
     * A specialisation of {@link BitmapWorkerTask} that sets the loaded bitmap in to an
     * {@link ImageView} in a {@link RemoteViews} and posts a {@link Notification}.
     */
    private class RemoteViewBitmapWorkerTask extends BitmapWorkerTask {
        private RemoteViews mRemoteViews;
        private int mViewId;
        NotificationManagerCompat mNotificationManagerCompat;
        int mNotificationId;
        Notification mNotification;

        public RemoteViewBitmapWorkerTask(RemoteViews remoteViews, int viewId,
                                          NotificationManagerCompat notificationManagerCompat,
                                          int notificationId, Notification notification) {
            super();
            mRemoteViews = remoteViews;
            mViewId = viewId;
            mNotificationManagerCompat = notificationManagerCompat;
            mNotificationId = notificationId;
            mNotification = notification;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (bitmap != null) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "onPostExecute - setting bitmap");
                }
                Log.d(TAG, "Setting notification bitmap");
                mRemoteViews.setImageViewBitmap(mViewId, bitmap);
            }

            // Always post the notification.
            mNotificationManagerCompat.notify(mNotificationId, mNotification);
        }

        /**
         * @return Always returns false, the processing is not to ensure the notification is posted.
         */
        @Override
        protected boolean shouldCancel() {
            return false;
        }
    }

    /**
     * A custom Drawable that will be attached to the imageView while the work is in progress.
     * Contains a reference to the actual worker task, so that it can be stopped if a new binding is
     * required, and makes sure that only the last started worker process can bind its result,
     * independently of the finish order.
     */
    private static class AsyncDrawable extends BitmapDrawable {

        private final WeakReference<BitmapWorkerTask> bitmapWorkerTaskReference;

        public AsyncDrawable(Resources res, Bitmap bitmap, BitmapWorkerTask bitmapWorkerTask) {
            super(res, bitmap);
            bitmapWorkerTaskReference =
                    new WeakReference<>(bitmapWorkerTask);
        }

        public BitmapWorkerTask getBitmapWorkerTask() {
            return bitmapWorkerTaskReference.get();
        }
    }

    /**
     * Called when the processing is complete and the final bitmap should be set on the ImageView.
     *
     * @param imageView
     * @param bitmap
     */
    private void setImageBitmap(ImageView imageView, Bitmap bitmap) {
        if (mFadeInBitmap) {
            // Transition drawable between the pending image and the final bitmap.
            final TransitionDrawable td =
                    new TransitionDrawable(new Drawable[]{
                            imageView.getDrawable(),
                            new BitmapDrawable(mResources, bitmap)
                    });

            imageView.setImageDrawable(td);
            td.setCrossFadeEnabled(true);
            td.startTransition(FADE_IN_TIME);
        } else {
            imageView.setImageDrawable(new BitmapDrawable(mResources, bitmap));
        }
    }

    public void setPauseWork(boolean pauseWork) {
        synchronized (mPauseWorkLock) {
            mPauseWork = pauseWork;
            if (!mPauseWork) {
                mPauseWorkLock.notifyAll();
            }
        }
    }

    protected class CacheAsyncTask extends AsyncTask<Object, Void, Void> {

        @Override
        protected Void doInBackground(Object... params) {
            @CacheMessages int message = (Integer) params[0];
            switch (message) {
                case MESSAGE_CLEAR:
                    clearCacheInternal();
                    break;
                case MESSAGE_INIT_DISK_CACHE:
                    initDiskCacheInternal();
                    break;
                case MESSAGE_FLUSH:
                    flushCacheInternal();
                    break;
                case MESSAGE_CLOSE:
                    closeCacheInternal();
                    break;
                case MESSAGE_CLEAR_MEMORY_CACHE:
                    clearMemoryCacheInternal();
                    break;
            }
            return null;
        }
    }

    protected void initDiskCacheInternal() {
        if (mImageCache != null) {
            mImageCache.initDiskCache();
        }
    }

    protected void clearCacheInternal() {
        if (mImageCache != null) {
            mImageCache.clearCache();
        }
    }

    protected void clearMemoryCacheInternal() {
        if (mImageCache != null) {
            mImageCache.clearMemoryCache();
        }
    }

    protected void flushCacheInternal() {
        if (mImageCache != null) {
            mImageCache.flush();
        }
    }

    protected void closeCacheInternal() {
        if (mImageCache != null) {
            mImageCache.close();
            mImageCache = null;
        }
    }

    public void clearCache() {
        new CacheAsyncTask().execute(MESSAGE_CLEAR);
    }

    public void clearMemoryCache() { new CacheAsyncTask().execute(MESSAGE_CLEAR_MEMORY_CACHE); }

    public void flushCache() {
        new CacheAsyncTask().execute(MESSAGE_FLUSH);
    }

    public void closeCache() {
        new CacheAsyncTask().execute(MESSAGE_CLOSE);
    }
}
