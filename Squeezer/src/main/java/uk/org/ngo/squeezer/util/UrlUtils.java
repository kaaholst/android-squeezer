package uk.org.ngo.squeezer.util;

import androidx.core.util.Pair;

import java.net.URI;
import java.net.URISyntaxException;

public class UrlUtils {

    public static boolean isValid(String url) {
        if (isEmpty(url)) return false;

        try {
            URI uri = new URI(url);

            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) return false;

            if (uri.getHost() == null) return false;

            if (uri.getPort() > 65535) return false;

            return true;

        } catch (URISyntaxException e) {
            return false;
        }
    }

    public static String addDefaults(String originalUrl, int defaultPort) {
        if (isEmpty(originalUrl)) return originalUrl;

        String url = originalUrl;
        try {
            int authorityPosition = url.indexOf("//");
            int port = parsePort(url);
            if (authorityPosition >= 0 && port >= 0) return originalUrl;

            if (authorityPosition < 0) url = "http://" + url;
            StringBuilder modifiedUrl = new StringBuilder(url);
            URI uri = new URI(modifiedUrl.toString());
            if (port < 0 && isEmpty(uri.getPath()) && isEmpty(uri.getQuery())) modifiedUrl.append(':').append(defaultPort);
            return modifiedUrl.toString();
        } catch (URISyntaxException e) {
            return originalUrl;
        }
    }

    private static boolean isEmpty(String originalUrl) {
        return originalUrl == null || originalUrl.isEmpty();
    }

    private static int parsePort(String address) {
        int colonPosition = address.lastIndexOf(':');
        if (colonPosition == -1) return -1;
        try {
            return Integer.parseInt(address.substring(colonPosition + 1));
        } catch (NumberFormatException unused) {
            return -1;
        }
    }

}