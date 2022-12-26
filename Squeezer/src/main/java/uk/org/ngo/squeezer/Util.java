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

package uk.org.ngo.squeezer;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;

import android.text.TextUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

public class Util {

    /**
     * {@link java.util.regex.Pattern} that splits strings on colon.
     */
    private static final Pattern mColonSplitPattern = Pattern.compile(":");

    private Util() {
    }


    /**
     * Update target, if it's different from newValue.
     *
     * @return true if target is updated. Otherwise return false.
     */
    public static <T> boolean atomicReferenceUpdated(AtomicReference<T> target, T newValue) {
        T currentValue = target.get();
        if (currentValue == null && newValue == null) {
            return false;
        }
        if (currentValue == null || !currentValue.equals(newValue)) {
            target.set(newValue);
            return true;
        }
        return false;
    }

    public static String joinSkipEmpty(String separator, String ... parts) {
        return joinSkipEmpty(separator, Arrays.asList(parts));
    }

    public static String joinSkipEmpty(String separator, Iterable<String> parts) {
        if (parts == null) return "";

        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part == null) continue;

            if (sb.length() > 0) sb.append(separator);
            sb.append(part);
        }

        return sb.toString();
    }


    public static double parseDouble(String value, double defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value.length() == 0) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static long parseDecimalInt(String value, long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        int decimalPoint = value.indexOf('.');
        if (decimalPoint != -1) {
            value = value.substring(0, decimalPoint);
        }
        if (value.length() == 0) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> getRecord(Map<String, Object> record, String recordName) {
        return (Map<String, Object>) record.get(recordName);
    }

    public static double getDouble(Map<String, Object> record, String fieldName) {
        return getDouble(record, fieldName, 0);
    }

    public static double getDouble(Map<String, Object> record, String fieldName, double defaultValue) {
        return getDouble(record.get(fieldName), defaultValue);
    }

    public static double getDouble(Object value, double defaultValue) {
        return (value instanceof Number) ? ((Number) value).doubleValue() : parseDouble((String) value, defaultValue);
    }

    public static long getLong(Map<String, Object> record, String fieldName) {
        return getLong(record, fieldName, 0);
    }

    public static long getLong(Map<String, Object> record, String fieldName, long defaultValue) {
        return getLong(record.get(fieldName), defaultValue);
    }

    public static long getLong(Object value, long defaultValue) {
        return (value instanceof Number) ? ((Number) value).intValue() : parseDecimalInt((String) value, defaultValue);
    }

    public static int getInt(Map<String, Object> record, String fieldName) {
        return getInt(record, fieldName, 0);
    }

    public static int getInt(Map<String, Object> record, String fieldName, int defaultValue) {
        return getInt(record.get(fieldName), defaultValue);
    }

    public static int getInt(Object value, int defaultValue) {
        return (value instanceof Number) ? ((Number) value).intValue() : (int) parseDecimalInt((String) value, defaultValue);
    }

    public static int getInt(Object value) {
        return getInt(value, 0);
    }

    public static String getString(Map<String, Object> record, String fieldName) {
        return getString(record.get(fieldName), null);
    }

    public static String getString(Map<String, Object> record, String fieldName, String defaultValue) {
        return getString(record.get(fieldName), defaultValue);
    }

    @NonNull
    public static String getStringOrEmpty(Map<String, Object> record, String fieldName) {
        return getStringOrEmpty(record.get(fieldName));
    }

    @NonNull
    public static String getStringOrEmpty(Object value) {
        return getString(value, "");
    }

    public static String getString(Object value, String defaultValue) {
        if (value == null) return defaultValue;
        return (value instanceof String) ? (String) value : value.toString();
    }

    public static String[] getStringArray(Map<String, Object> record, String fieldName) {
        return getStringArray(record.get(fieldName));
    }

    private static String[] getStringArray(Object object) {
        if (object != null  && !object.getClass().isArray()) {
            return new String[]{ getString(object, null) };
        }

        Object[] objects = (Object[])object;
        String[] result = new String[object == null ? 0 : objects.length];
        if (objects != null) {
            for (int i = 0; i < objects.length; i++) {
                result[i] = getString(objects[i], null);
            }
        }
        return result;
    }

    public static Map<String, Object> mapify(String[] tokens) {
        Map<String, Object> tokenMap = new HashMap<>();
        for (String token : tokens) {
            String[] split = mColonSplitPattern.split(token, 2);
            tokenMap.put(split[0], split.length > 1 ? split[1] : null);
        }
        return tokenMap;
    }

    /**
     * Make sure the icon/image tag is an absolute URL.
     */
    private static final Pattern HEX_PATTERN = Pattern.compile("^\\p{XDigit}+$");

    @NonNull
    public static Uri getImageUrl(String urlPrefix, String imageId) {
        if (imageId != null) {
            if (HEX_PATTERN.matcher(imageId).matches()) {
                // if the iconId is a hex digit, this is a coverid or remote track id(a negative id)
                imageId = "/music/" + imageId + "/cover";
            }

            // Make sure the url is absolute
            if (!Uri.parse(imageId).isAbsolute()) {
                imageId = urlPrefix + (imageId.startsWith("/") ? imageId : "/" + imageId);
            }
        }
        return Uri.parse(imageId != null ? imageId : "");
    }

    @NonNull
    public static Uri getImageUrl(Map<String, Object> record, String fieldName) {
        return getImageUrl(getString(record, "urlPrefix"), getString(record, fieldName));
    }

    /**
     * Make sure the icon/image tag is an absolute URL.
     */
    @NonNull
    public static Uri getDownloadUrl(String urlPrefix, String trackId) {
        return Uri.parse(urlPrefix + "/music/" + trackId + "/download");
    }

    private static final StringBuilder sFormatBuilder = new StringBuilder();

    private static final Formatter sFormatter = new Formatter(sFormatBuilder, Locale.getDefault());

    private static final Object[] sTimeArgs = new Object[5];

    /**
     * Formats an elapsed time in the form "M:SS" or "H:MM:SS" for display.
     * <p>
     * Like {@link android.text.format.DateUtils#formatElapsedTime(long)} but without the leading
     * zeroes if the number of minutes is < 10.
     *
     * @param elapsedSeconds the elapsed time, in seconds.
     */
    public synchronized static String formatElapsedTime(long elapsedSeconds) {
        calculateTimeArgs(elapsedSeconds);
        sFormatBuilder.setLength(0);
        return sFormatter.format("%2$d:%5$02d", sTimeArgs).toString();
    }

    private static void calculateTimeArgs(long elapsedSeconds) {
        sTimeArgs[0] = elapsedSeconds / 3600;
        sTimeArgs[1] = elapsedSeconds / 60;
        sTimeArgs[2] = (elapsedSeconds / 60) % 60;
        sTimeArgs[3] = elapsedSeconds;
        sTimeArgs[4] = elapsedSeconds % 60;
    }

    public static  @NonNull  String formatMac(byte[] mac) {
        if (mac == null || mac.length != 6) {
            return "";
        }
        String[] parts = new String[6];
        for (int i = 0; i < 6; i++) {
            parts[i] = String.format("%02X", mac[i]);
        }
        return TextUtils.join(":", parts);
    }

    public static @NonNull byte[] parseMac(String s) {
        if (!validateMac(s)) {
            return null;
        }

        s = s.toLowerCase().replaceAll("-", ":").replaceAll("\\.", ":");
        String[] parts = s.split(":");

        if (parts.length == 3) {
            List<String> newParts = new ArrayList<>();
            for (String part : parts) {
                newParts.add(part.substring(0, 2));
                newParts.add(part.substring(2));
            }
            parts = newParts.toArray(new String[0]);
        }

        byte[] mac = new byte[6];
        for (int i = 0; i < 6; i++) {
            mac[i] = (byte)Integer.parseInt(parts[i], 16);
        }
        return mac;
    }

    private static final String MAC_REGEX_STRING = "^((([a-fA-F0-9][a-fA-F0-9]+[-]){5}|([a-fA-F0-9][a-fA-F0-9]+[:]){5})([a-fA-F0-9][a-fA-F0-9])$)|(^([a-fA-F0-9][a-fA-F0-9][a-fA-F0-9][a-fA-F0-9]+[.]){2}([a-fA-F0-9][a-fA-F0-9][a-fA-F0-9][a-fA-F0-9]))$";
    private static final Pattern MAC_REGEX = Pattern.compile(MAC_REGEX_STRING);
    public static boolean validateMac(CharSequence mac) {
        return (mac != null && MAC_REGEX.matcher(mac).matches());
    }

    @NonNull
    public static String getBaseName(String fileName) {
        String name = new File(fileName).getName();
        int pos = name.lastIndexOf(".");
        return (pos > 0) ? name.substring(0, pos) : name;
    }

    public static String getFileExtension(String fileName) {
        String name = new File(fileName).getName();
        int pos = name.lastIndexOf(".");
        return  (pos > 0) ? fileName.substring(pos+1) : "";
    }

    public static void moveFile(ContentResolver resolver, Uri source, Uri destination) throws IOException {
        try (InputStream inputStream = resolver.openInputStream(source);
             OutputStream outputStream = resolver.openOutputStream(destination)) {
            if (inputStream == null) {
                throw new IOException("moveFile: could not open '" + source + "'");
            }
            if (outputStream == null) {
                throw new IOException("moveFile: could not open '" + destination + "'");
            }
            copyStream(inputStream, outputStream);
        }
        int deleted = resolver.delete(source, null, null);
        if (deleted != 1) {
            throw new IOException("moveFile: try to delete '" + source + "' after copy, expected 1 deleted file but was " + deleted);
        }

    }

    public static byte[] toByteArray(InputStream inputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        copyStream(inputStream, outputStream);

        return outputStream.toByteArray();
    }

    private static void copyStream(InputStream inputStream, OutputStream outputStream) throws IOException {
        byte[] b = new byte[16384];
        int bytes;
        while ((bytes = inputStream.read(b)) > 0) {
            outputStream.write(b, 0, bytes);
        }
    }

    public static Bitmap vectorToBitmap(Context context, @DrawableRes int vectorResource) {
        return drawableToBitmap(AppCompatResources.getDrawable(context, vectorResource));
    }

    public static Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
            if(bitmapDrawable.getBitmap() != null) {
                return bitmapDrawable.getBitmap();
            }
        }

        return drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0
                ? getBitmap(drawable, 1, 1) // Single color bitmap will be created of 1x1 pixel
                : getBitmap(drawable, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());

    }

    public static Bitmap getBitmap(Drawable drawable, int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }
}
