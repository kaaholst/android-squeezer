package uk.org.ngo.squeezer.service;

import android.util.Log;

import org.cometd.common.TransportException;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.util.IO;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ListeningThread extends Thread {

    private static final String TAG = "ListeningThread";

    private final HttpStreamingTransport.Delegate delegate;
    private final BufferedReader reader;

    public ListeningThread(HttpStreamingTransport.Delegate delegate, InputStream inputStream) {
        this.delegate = delegate;
        reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
    }

    int status;
    boolean chunked = false;
    int contentSize = 0;

    @Override
    public void run() {
        while (delegate.isConnected()) {
            try {
                getHeader();
                if (!chunked) {
                    readWhole();
                } else {
                    readChunks();
            }
            if (status != HttpStatus.OK_200) {
                Map<String, Object> failure = new HashMap<>(2);
                failure.put("httpCode", status);
                TransportException x = new TransportException(failure);
                delegate.fail(x, "Unexpected HTTP status code");
            }
        } catch(IOException e){
            if (delegate.isConnected()) {
                delegate.fail(e, "IOException reading socket");
            }
        }
    }
}

    void getHeader() throws IOException {
        status = parseHttpStatus(readLine());
        chunked = false;
        contentSize = 0;
        String headerLine;
        while (!"".equals(headerLine = readLine())) {
            if ("Transfer-Encoding: chunked".equals(headerLine))
                chunked = true;
            int pos = headerLine.indexOf("Content-Length: ");
            if (pos == 0) {
                contentSize = Integer.parseInt(headerLine.substring("Content-Length: ".length()));
            }
        }
    }

    void readWhole() throws IOException {
        String content = read(contentSize);
        if (content.length() > 0) {
            if (status == HttpStatus.OK_200) {
                delegate.onData(content);
            }
        } else {
            Map<String, Object> failure = new HashMap<>(2);
            // Convert the 200 into 204 (no content)
            failure.put("httpCode", HttpStatus.NO_CONTENT_204);
            TransportException x = new TransportException(failure);
            delegate.fail(x, "No content");
        }
    }


    private void readChunks() throws IOException {
        String unprocessed = "";
        while (!"0".equals(readLine())) {
            String data = readLine();
            unprocessed += data;
            if (isValidJson(unprocessed)) {
                Log.v(TAG, "JSON is valid! Sending data to parser.");
                if (status == HttpStatus.OK_200) {
                    delegate.onData(unprocessed);
                }
                unprocessed = "";
            } else {
                Log.v(TAG, "JSON is not valid! Appending to next chunk.");
            }
        }
        readLine();//Read final/empty chunk
        delegate.disconnect("End of chunks");
    }

    private boolean isValidJson(String jsonStr) {
        try {
            JSONTokener tokenizer = new JSONTokener(jsonStr);
            while (tokenizer.more()) {
                Object json = tokenizer.nextValue();
                if (!(json instanceof JSONObject || json instanceof JSONArray)) {
                    return false;
                }
            }
            return true;
        } catch (JSONException e) {
            return false;
        }
    }

    Pattern httpStatusLinePattern = Pattern.compile("HTTP/1.1 (\\d{3}) \\p{all}+");

    private int parseHttpStatus(String statusLine) {
        Matcher m = httpStatusLinePattern.matcher(statusLine);
        try {
            if (m.find()) {
                return Integer.parseInt(m.group(1));
            }
        } catch (NumberFormatException e) {
        }
        return -1;
    }

    private String readLine() throws IOException {
        String inputLine = reader.readLine();
        if (inputLine == null) {
            throw new EOFException();
        }
        return inputLine;
    }

    private String read(int size) throws IOException {
        char[] buffer = new char[size];
        int length = reader.read(buffer);
        if (length != size) {
            throw new EOFException("Expected " + size + " characters, but got " + length);
        }
        return new String(buffer);
    }
}


