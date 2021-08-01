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

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.android.material.timepicker.MaterialTimePicker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import uk.org.ngo.squeezer.download.DownloadFilenameStructure;
import uk.org.ngo.squeezer.download.DownloadPathStructure;
import uk.org.ngo.squeezer.framework.EnumWithText;
import uk.org.ngo.squeezer.itemlist.dialog.ArtworkListLayout;
import uk.org.ngo.squeezer.model.Player;
import uk.org.ngo.squeezer.util.ThemeManager;

public final class Preferences {
    private static final String TAG = Preferences.class.getSimpleName();

    public static final String NAME = "Squeezer";

    // Old setting for connect via the CLI protocol
    private static final String KEY_CLI_SERVER_ADDRESS = "squeezer.serveraddr";

    // Squeezebox server address (host:port)
    private static final String KEY_SERVER_ADDRESS = "squeezer.server_addr";

    // Do we connect to mysqueezebox.com
    private static final String KEY_SQUEEZE_NETWORK = "squeezer.squeeze_network";

    // Optional Squeezebox Server name
    private static final String KEY_SERVER_NAME = "squeezer.server_name";

    // Optional Squeezebox Server user name
    private static final String KEY_USERNAME = "squeezer.username";

    // Optional Squeezebox Server password
    private static final String KEY_PASSWORD = "squeezer.password";

    // Optional Squeezebox Server password
    private static final String KEY_WOL = "squeezer.wol";

    // Optional Squeezebox Server password
    private static final String KEY_MAC = "squeezer.mac";

    // The playerId that we were last connected to. e.g. "00:04:20:17:04:7f"
    private static final String KEY_LAST_PLAYER = "squeezer.lastplayer";

    // Do we automatically try and connect on WiFi availability?
    public static final String KEY_AUTO_CONNECT = "squeezer.autoconnect";

    // Pause music on incoming call? (OLD setting use to initialize action on incoming call
    private static final String KEY_PAUSE_ON_INCOMING_CALL = "squeezer.pause_on_incoming_call";

    // Action on incoming call
    public static final String KEY_ACTION_ON_INCOMING_CALL = "squeezer.action_on_incoming_call";

    // Are we disconnected via the options menu?
    private static final String KEY_MANUAL_DISCONNECT = "squeezer.manual.disconnect";

    // Type of notification to show. NOT USED ANYMORE
    private static final String KEY_NOTIFICATION_TYPE = "squeezer.notification_type";

    // Do we scrobble track information?
    // Deprecated, retained for compatibility when upgrading. Was an int, of
    // either 0 == No scrobbling, 1 == use ScrobbleDroid API, 2 == use SLS API
    static final String KEY_SCROBBLE = "squeezer.scrobble";

    // Do we scrobble track information (if a scrobble service is available)?
    //
    // Type of underlying preference is bool / CheckBox
    static final String KEY_SCROBBLE_ENABLED = "squeezer.scrobble.enabled";

    // Do we send anonymous usage statistics?
    public static final String KEY_ANALYTICS_ENABLED = "squeezer.analytics.enabled";

    // Background Volume Control
    static final String KEY_BACKGROUND_VOLUME = "squeezer.backgroundVolume";

    // Volume up/down increments
    private static final String KEY_VOLUME_INCREMENTS = "squeezer.volumeIncrements";

    // Fade-in period? (0 = disable fade-in)
    static final String KEY_FADE_IN_SECS = "squeezer.fadeInSecs";

    // What do to when an album is selected in the list view
    private static final String KEY_ON_SELECT_ALBUM_ACTION = "squeezer.action.onselect.album";

    // What do to when a song is selected in the list view
    private static final String KEY_ON_SELECT_SONG_ACTION = "squeezer.action.onselect.song";

    // Preferred album list layout.
    private static final String KEY_ALBUM_LIST_LAYOUT = "squeezer.album.list.layout";

    // Preferred home menu layout.
    private static final String KEY_HOME_MENU_LAYOUT = "squeezer.home.menu.layout";

    // Preferred maximum info per item for a given list layout
    public static final String KEY_MAX_LINES_FORMAT = "squeezer.%s.maxLines";

    // Preferred song list layout.
    private static final String KEY_SONG_LIST_LAYOUT = "squeezer.song.list.layout";

    // Start SqueezePlayer automatically if installed.
    public static final String KEY_SQUEEZEPLAYER_ENABLED = "squeezer.squeezeplayer.enabled";

    // Preferred UI theme.
    static final String KEY_ON_THEME_SELECT_ACTION = "squeezer.theme";

    // Download confirmation
    static final String KEY_CLEAR_PLAYLIST_CONFIRMATION = "squeezer.clear.current_playlist.confirmation";

    // Download enabled
    static final String KEY_DOWNLOAD_ENABLED = "squeezer.download.enabled";

    // Download confirmation
    static final String KEY_DOWNLOAD_CONFIRMATION = "squeezer.download.confirmation";

    // Download folder
    static final String KEY_DOWNLOAD_USE_SERVER_PATH = "squeezer.download.use_server_path";

    // Download path structure
    static final String KEY_DOWNLOAD_PATH_STRUCTURE = "squeezer.download.path_structure";

    // Download filename structure
    static final String KEY_DOWNLOAD_FILENAME_STRUCTURE = "squeezer.download.filename_structure";

    // Use SD-card (getExternalMediaDirs)
    static final String KEY_DOWNLOAD_USE_SD_CARD_SCREEN = "squeezer.download.use_sd_card.screen";
    static final String KEY_DOWNLOAD_USE_SD_CARD = "squeezer.download.use_sd_card";

    // Store a "mac id" for this app instance.
    private static final String KEY_MAC_ID = "squeezer.mac_id";

    // Store a unique id for this app instance.
    private static final String KEY_UUID = "squeezer.uuid";

    // Archive mode
    public static final String KEY_CUSTOMIZE_HOME_MENU_MODE = "squeezer.customize_home_menu.mode";

    // Map JiveItems to archive
    private static final String KEY_PLAYER_ARCHIVED_ITEMS_FORMAT = "squeezer.archived_menu_items.%s";

    // Preferred time input method.
    private static final String KEY_TIME_INPUT_MODE = "squeezer.time_input_mode";

    // Show total time or remaining time.
    private static final String KEY_SHOW_REMAINING_TIME = "squeezer.show_remaining_Time";

    private final Context context;
    private final SharedPreferences sharedPreferences;
    private final int defaultCliPort;
    private final int defaultHttpPort;

    public Preferences(Context context) {
        this(context, context.getSharedPreferences(Preferences.NAME, Context.MODE_PRIVATE));
    }

    public Preferences(Context context, SharedPreferences sharedPreferences) {
        this.context = context;
        this.sharedPreferences = sharedPreferences;
        defaultCliPort = context.getResources().getInteger(R.integer.DefaultCliPort);
        defaultHttpPort = context.getResources().getInteger(R.integer.DefaultHttpPort);
    }

    private String getStringPreference(String preference) {
        final String pref = sharedPreferences.getString(preference, null);
        if (pref == null || pref.length() == 0) {
            return null;
        }
        return pref;
    }

    public boolean hasServerConfig() {
        String bssId = getBssId();
        return (sharedPreferences.contains(prefixed(bssId, KEY_SERVER_ADDRESS)) ||
                sharedPreferences.contains(KEY_SERVER_ADDRESS));
    }

    public ServerAddress getServerAddress() {
        return getSelectedServerAddress(KEY_SERVER_ADDRESS, defaultHttpPort);
    }

    public ServerAddress getCliServerAddress() {
        return getSelectedServerAddress(KEY_CLI_SERVER_ADDRESS, defaultCliPort);
    }

    private ServerAddress getSelectedServerAddress(String setting, int defaultPort) {
        ServerAddress serverAddress = new ServerAddress(getBssId(), defaultPort);

        String address = null;
        if (serverAddress.bssId != null) {
            address = getStringPreference(setting + "_" + serverAddress.bssId);
        }
        if (address == null) {
            address = getStringPreference(setting);
        }

        readServerAddress(serverAddress, address, defaultPort);
        return serverAddress;
    }

    public ServerAddress getServerAddress(String address) {
        ServerAddress serverAddress = new ServerAddress(getBssId(), defaultHttpPort);
        serverAddress.setAddress(address);
        readServerAddress(serverAddress, address, defaultHttpPort);
        return serverAddress;
    }

    private void readServerAddress(ServerAddress serverAddress, String address, int defaultPort) {
        serverAddress.setAddress(address, defaultPort);

        serverAddress.squeezeNetwork = sharedPreferences.getBoolean(prefixed(serverAddress.bssId, KEY_SQUEEZE_NETWORK), false);
        serverAddress.serverName = getStringPreference(prefix(serverAddress) + KEY_SERVER_NAME);
        serverAddress.userName = getStringPreference(prefix(serverAddress) + KEY_USERNAME);
        serverAddress.password = getStringPreference(prefix(serverAddress) + KEY_PASSWORD);
        serverAddress.wakeOnLan = sharedPreferences.getBoolean(prefix(serverAddress) + KEY_WOL, false);
        serverAddress.mac = Util.parseMac(getStringPreference(prefix(serverAddress) + KEY_MAC));
    }

    private String getBssId() {
        WifiManager mWifiManager = (WifiManager) context
                .getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiInfo connectionInfo = mWifiManager.getConnectionInfo();
        return  (connectionInfo != null ? connectionInfo.getBSSID() : null);
    }

    private String prefixed(String bssId, String setting) {
        return (bssId != null ? setting + "_" + bssId : setting);
    }

    private String prefix(ServerAddress serverAddress) {
        return (serverAddress.bssId != null ? serverAddress.bssId + "_ " : "") + serverAddress.localAddress() + "_";
    }

    public static class ServerAddress {
        private static final String SN = "mysqueezebox.com";

        private final String bssId;
        public boolean squeezeNetwork;
        private String address; // <host name or ip>:<port>
        private String host;
        private int port;
        private final int defaultPort;

        private String serverName;
        public String userName;
        public String password;

        public boolean wakeOnLan;
        public byte[] mac;

        private ServerAddress(String bssId, int defaultPort) {
            this.defaultPort = defaultPort;
            this.bssId = bssId;
        }

        public void setAddress(String hostPort) {
            setAddress(hostPort, defaultPort);
        }

        public void setServerName(String serverName) {
            this.serverName = serverName;
        }

        public String address() {
            return host() + ":" + port();
        }

        public String localAddress() {
            if (address == null) {
                return null;
            }

            return host + ":" + port;
        }

        public String host() {
            return (squeezeNetwork ? SN : host);
        }

        public String localHost() {
            return host;
        }

        public int port() {
            return (squeezeNetwork ? defaultPort : port);
        }

        public String serverName() {
            if (squeezeNetwork) {
                return ServerAddress.SN;
            }
            return serverName != null ? serverName : host;
        }

        private void setAddress(String hostPort, int defaultPort) {
            // Common mistakes, based on crash reports...
            if (hostPort != null) {
                if (hostPort.startsWith("Http://") || hostPort.startsWith("http://")) {
                    hostPort = hostPort.substring(7);
                }

                // Ending in whitespace?  From LatinIME, probably?
                while (hostPort.endsWith(" ")) {
                    hostPort = hostPort.substring(0, hostPort.length() - 1);
                }
            }

            address = hostPort;
            host = parseHost();
            port = parsePort(defaultPort);
        }

        private String parseHost() {
            if (address == null) {
                return "";
            }
            int colonPos = address.indexOf(":");
            if (colonPos == -1) {
                return address;
            }
            return address.substring(0, colonPos);
        }

        private int parsePort(int defaultPort) {
            if (address == null) {
                return defaultPort;
            }
            int colonPos = address.indexOf(":");
            if (colonPos == -1) {
                return defaultPort;
            }
            try {
                return Integer.parseInt(address.substring(colonPos + 1));
            } catch (NumberFormatException unused) {
                Log.d(TAG, "Can't parse port out of " + address);
                return defaultPort;
            }
        }
    }

    public void saveServerAddress(ServerAddress serverAddress) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(prefixed(serverAddress.bssId, KEY_SERVER_ADDRESS), serverAddress.address);
        editor.putBoolean(prefixed(serverAddress.bssId, KEY_SQUEEZE_NETWORK), serverAddress.squeezeNetwork);
        editor.putString(prefix(serverAddress) + KEY_SERVER_NAME, serverAddress.serverName);
        editor.putString(prefix(serverAddress) + KEY_USERNAME, serverAddress.userName);
        editor.putString(prefix(serverAddress) + KEY_PASSWORD, serverAddress.password);
        editor.putBoolean(prefix(serverAddress) + KEY_WOL, serverAddress.wakeOnLan);
        editor.putString(prefix(serverAddress) + KEY_MAC, Util.formatMac(serverAddress.mac));
        editor.apply();
    }

    public String getLastPlayer() {
        return getStringPreference(KEY_LAST_PLAYER);
    }

    public void setLastPlayer(@Nullable Player player) {
        SharedPreferences.Editor editor = sharedPreferences.edit();

        if (player == null) {
            Log.v(TAG, "Clearing " + KEY_LAST_PLAYER);
            editor.remove(KEY_LAST_PLAYER);
        } else {
            Log.v(TAG, "Saving " + KEY_LAST_PLAYER + "=" + player.getId());
            editor.putString(KEY_LAST_PLAYER, player.getId());
        }

        editor.apply();
    }

    public boolean isBackgroundVolume() {
        return sharedPreferences.getBoolean(KEY_BACKGROUND_VOLUME, true);
    }

    public int getVolumeIncrements() {
        return sharedPreferences.getInt(KEY_VOLUME_INCREMENTS, 5);
    }

    public int getFadeInSecs() {
        return sharedPreferences.getInt(KEY_FADE_IN_SECS, 0);
    }

    public String getTheme() {
        return getStringPreference(KEY_ON_THEME_SELECT_ACTION);
    }

    public void setTheme(ThemeManager.Theme theme) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(Preferences.KEY_ON_THEME_SELECT_ACTION, theme.name());
        editor.apply();
    }

    public boolean isScrobbleEnabled() {
        return sharedPreferences.getBoolean(KEY_SCROBBLE_ENABLED, false);
    }

    public boolean isClearPlaylistConfirmation() {
        return sharedPreferences.getBoolean(KEY_CLEAR_PLAYLIST_CONFIRMATION, true);
    }

    public void setClearPlaylistConfirmation(boolean b) {
        sharedPreferences.edit().putBoolean(Preferences.KEY_CLEAR_PLAYLIST_CONFIRMATION, b).apply();
    }

    public boolean isAutoConnect() {
        return sharedPreferences.getBoolean(KEY_AUTO_CONNECT, true);
    }

    public IncomingCallAction getActionOnIncomingCall() {
        String string = sharedPreferences.getString(KEY_ACTION_ON_INCOMING_CALL, null);
        if (string == null) {
            return (sharedPreferences.getBoolean(KEY_PAUSE_ON_INCOMING_CALL, true) ? IncomingCallAction.PAUSE : IncomingCallAction.NONE);
        }
        return IncomingCallAction.valueOf(string);
    }

    public boolean controlSqueezePlayer(ServerAddress serverAddress) {
        return  (!serverAddress.squeezeNetwork && sharedPreferences.getBoolean(KEY_SQUEEZEPLAYER_ENABLED, true));
    }

    /** Get the preferred album list layout. */
    public ArtworkListLayout getAlbumListLayout() {
        return getListLayout(KEY_ALBUM_LIST_LAYOUT);
    }

    public void setAlbumListLayout(ArtworkListLayout artworkListLayout) {
        setListLayout(KEY_ALBUM_LIST_LAYOUT, artworkListLayout);
    }

    /** Get the preferred home menu layout. */
    public ArtworkListLayout getHomeMenuLayout() {
        return getListLayout(KEY_HOME_MENU_LAYOUT);
    }

    public void setHomeMenuLayout(ArtworkListLayout artworkListLayout) {
        setListLayout(KEY_HOME_MENU_LAYOUT, artworkListLayout);
    }

    /**
     * Get the preferred layout for the specified preference
     * <p>
     * If the list layout is not selected, a default one is chosen, based on the current screen
     * size, on the assumption that the artwork grid is preferred on larger screens.
     */
    private ArtworkListLayout getListLayout(String preference) {
        String listLayoutString = sharedPreferences.getString(preference, null);
        if (listLayoutString == null) {
            int screenSize = context.getResources().getConfiguration().screenLayout
                    & Configuration.SCREENLAYOUT_SIZE_MASK;
            return (screenSize >= Configuration.SCREENLAYOUT_SIZE_LARGE)
                    ? ArtworkListLayout.grid : ArtworkListLayout.list;
        } else {
            return ArtworkListLayout.valueOf(listLayoutString);
        }
    }

    private void setListLayout(String preference, ArtworkListLayout artworkListLayout) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(preference, artworkListLayout.name());
        editor.apply();
    }

    /** Get max lines for the supplied list layout. */
    public int getMaxLines(ArtworkListLayout listLayout) {
        return sharedPreferences.getInt(String.format(KEY_MAX_LINES_FORMAT, listLayout.name()), 2);
    }

    public void setMaxLines(ArtworkListLayout listLayout, int maxLines) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(String.format(KEY_MAX_LINES_FORMAT, listLayout.name()), maxLines);
        editor.apply();
    }

    /**
     * Retrieve a "mac id" for this app instance.
     * <p>
     * If a mac id is previously stored, then use it, otherwise create a new mac id
     * store it and return it.
     */
    public String getMacId() {
        String macId = sharedPreferences.getString(KEY_MAC_ID, null);
        if (macId == null) {
            macId = generateMacLikeId();
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString(Preferences.KEY_MAC_ID, macId);
            editor.apply();
        }
        return macId;
    }

    /**
     * As Android (6.0 and above) does not allow acces to the mac id, and mysqueezebox.com requires
     * it, this is the best I can think of.
     */
    private String generateMacLikeId() {
        byte[] mac = new byte[6];
        new Random().nextBytes(mac);
        return Util.formatMac(mac);
    }

    /**
     * Retrieve a unique id (uuid) for this app instance.
     * <p>
     * If a uuid is previously stored, then use it, otherwise create a new uuid,
     * store it and return it.
     */
    public String getUuid() {
        String uuid = sharedPreferences.getString(KEY_UUID, null);
        if (uuid == null) {
            //NOTE mysqueezebox.com doesn't accept dash in the uuid
            uuid = UUID.randomUUID().toString().replaceAll("-", "");
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString(Preferences.KEY_UUID, uuid);
            editor.apply();
        }
        return uuid;
    }

    public CustomizeHomeMenuMode getCustomizeHomeMenuMode() {
        String string = sharedPreferences.getString(KEY_CUSTOMIZE_HOME_MENU_MODE, null);
        return string == null ? CustomizeHomeMenuMode.ARCHIVE : CustomizeHomeMenuMode.valueOf(string);
    }

    public List<String> getArchivedMenuItems(Player player) {
        List<String> list = new ArrayList<>();
        String string = sharedPreferences.getString(String.format(KEY_PLAYER_ARCHIVED_ITEMS_FORMAT, player.getId()), null);
        if ( TextUtils.isEmpty(string)) {
            return list;
        }
        Collections.addAll(list, string.split(";"));
        return list;
    }

    public void setArchivedMenuItems(List<String> list, Player player) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(String.format(KEY_PLAYER_ARCHIVED_ITEMS_FORMAT, player.getId()), TextUtils.join(";", list));
        editor.apply();
        return;
    }

    public boolean isDownloadEnabled() {
        return sharedPreferences.getBoolean(KEY_DOWNLOAD_ENABLED, true);
    }

    public void setDownloadEnabled(boolean b) {
        sharedPreferences.edit().putBoolean(Preferences.KEY_DOWNLOAD_ENABLED, b).apply();
    }

    public boolean isDownloadConfirmation() {
        return sharedPreferences.getBoolean(KEY_DOWNLOAD_CONFIRMATION, true);
    }

    public void setDownloadConfirmation(boolean b) {
        sharedPreferences.edit().putBoolean(Preferences.KEY_DOWNLOAD_CONFIRMATION, b).apply();
    }

    public boolean isDownloadUseServerPath() {
        return sharedPreferences.getBoolean(KEY_DOWNLOAD_USE_SERVER_PATH, true);
    }

    public DownloadPathStructure getDownloadPathStructure() {
        final String string = sharedPreferences.getString(KEY_DOWNLOAD_PATH_STRUCTURE, null);
        return (string == null ? DownloadPathStructure.ARTIST_ALBUM: DownloadPathStructure.valueOf(string));
    }

    public DownloadFilenameStructure getDownloadFilenameStructure() {
        final String string = sharedPreferences.getString(KEY_DOWNLOAD_FILENAME_STRUCTURE, null);
        return (string == null ? DownloadFilenameStructure.NUMBER_TITLE: DownloadFilenameStructure.valueOf(string));
    }

    public int getTimeInputMode() {
        return sharedPreferences.getInt(KEY_TIME_INPUT_MODE, MaterialTimePicker.INPUT_MODE_CLOCK);
    }

    public void setTimeInputMode(int mode) {
        sharedPreferences.edit().putInt(Preferences.KEY_TIME_INPUT_MODE, mode).apply();
    }

    public boolean isShowRemainingTime() {
        return sharedPreferences.getBoolean(KEY_SHOW_REMAINING_TIME, false);
    }

    public void setShowRemainingTime(boolean showRemainingTime) {
        sharedPreferences.edit().putBoolean(Preferences.KEY_SHOW_REMAINING_TIME, showRemainingTime).apply();
    }

    public enum IncomingCallAction implements EnumWithText {
        NONE(R.string.settings_no_action_on_incoming_call),
        PAUSE(R.string.pause),
        MUTE(R.string.mute);

        private final int labelId;

        IncomingCallAction(int labelId) {
            this.labelId = labelId;
        }

        @Override
        public String getText(Context context) {
            return context.getString(labelId);
        }
    }

    public enum CustomizeHomeMenuMode implements EnumWithText {
        ARCHIVE(R.string.settings_customize_home_menu_archive),
        DISABLED(R.string.settings_customize_home_menu_disabled),
        LOCKED(R.string.settings_customize_home_menu_locked);

        private final int labelId;

        CustomizeHomeMenuMode(int labelId) {
            this.labelId = labelId;
        }

        @Override
        public String getText(Context context) {
            return context.getString(labelId);
        }
    }
}
