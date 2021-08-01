/*
 * Copyright 2012 Google Inc.
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

import android.content.Context;

import androidx.annotation.NonNull;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.Util;

/**
 * A subclass of {@link ImageWorker} that fetches images from a URL.
 */
public class ImageFetcher extends ImageWorker {
    private static final String TAG = "ImageFetcher";

    private volatile static ImageFetcher sImageFetcher;

    private ImageFetcher(Context context) {
        super(context);
    }

    /**
     * @return an imagefetcher globally useful for the application, with a cache that
     *     is maintained across activities.
     *
     * @param context Anything that provides a context.
     */
    @NonNull
    public static ImageFetcher getInstance(Context context) {
        ImageFetcher result = sImageFetcher;
        if (result == null) {
            synchronized (ImageFetcher.class) {
                result = sImageFetcher;
                if (result == null) {
                    sImageFetcher = new ImageFetcher(context);
                    sImageFetcher.setLoadingImage(R.drawable.pending);
                    ImageCache.ImageCacheParams imageCacheParams = new ImageCache.ImageCacheParams(context, "artwork");
                    imageCacheParams.setMemCacheSizePercent(context, 0.12f);
                    sImageFetcher.addImageCache(imageCacheParams);
                }
            }
        }
        return sImageFetcher;
    }

    /**
     * Call this in low memory situations. Clears the memory cache.
     */
    public static void onLowMemory() {
        if (sImageFetcher == null) {
            return;
        }

        sImageFetcher.clearMemoryCache();
    }

    /**
     * The main process method, which will be called by the ImageWorker in the AsyncTask background
     * thread.
     *
     * @param params The parameters for this request.
     *
     * @return Undecoded bytes for the requested bitmap, null if downloading failed.
     */
    protected byte[] processBitmap(BitmapWorkerTaskParams params) {
        if (params == null || params.data == null) {
            Log.w(TAG, "processBitmap was called without any data " + params + " abandon");
            return null;
        }

        String data = params.data.toString();
        Log.d(TAG, "processBitmap: " + data);

        HttpURLConnection urlConnection = null;
        InputStream in = null;
        byte[] bytes = null;

        try {
            URL resourceUrl, base, next;
            Map<String, Integer> visited;
            String location;

            visited = new HashMap<>();

            while (true)
            {
                Integer times = visited.get(data);
                if (times == null) times = 0;
                visited.put(data, ++times);

                if (times > 3)
                    throw new IOException("Stuck in redirect loop");

                resourceUrl = new URL(data);
                urlConnection = (HttpURLConnection) resourceUrl.openConnection();

                urlConnection.setConnectTimeout(15000);
                urlConnection.setReadTimeout(15000);
                urlConnection.setInstanceFollowRedirects(false);   // Make the logic below easier to detect redirections

                switch (urlConnection.getResponseCode())
                {
                    case HttpURLConnection.HTTP_MOVED_PERM:
                    case HttpURLConnection.HTTP_MOVED_TEMP:
                        location = urlConnection.getHeaderField("Location");
                        location = URLDecoder.decode(location, "UTF-8");
                        base     = new URL(data);
                        next     = new URL(base, location);  // Deal with relative URLs
                        data      = next.toExternalForm();
                        continue;
                }

                break;
            }

            in = urlConnection.getInputStream();
            bytes = Util.toByteArray(in);
        } catch (final IOException e) {
            Log.e(TAG, "Error in downloadUrlToStream - " + data + e);
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
            try {
                if (in != null) {
                    in.close();
                }
            } catch (final IOException e) {
                Log.e(TAG, "Closing input stream failed");
            }
        }

        return bytes;
    }
}
