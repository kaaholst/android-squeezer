/*
 * Copyright (c) 2011 Kurt Aaholst <kaaholst@gmail.com>
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

package uk.org.ngo.squeezer.model;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Parcel;
import android.text.TextUtils;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import org.eclipse.jetty.util.ajax.JSON;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.Squeezer;
import uk.org.ngo.squeezer.Util;
import uk.org.ngo.squeezer.util.FluentHashMap;


public class JiveItem extends Item {
    public static final JiveItem HOME = new JiveItem(record("home", null, Squeezer.getInstance().getString(R.string.HOME), 1), Window.WindowStyle.HOME_MENU);
    public static final JiveItem CURRENT_PLAYLIST = new JiveItem(record("status", null, Squeezer.getInstance().getString(R.string.menu_item_playlist), 1), Window.WindowStyle.PLAY_LIST);
    public static final JiveItem EXTRAS = new JiveItem(record("extras", "home", Squeezer.getInstance().getString(R.string.EXTRAS), 50), Window.WindowStyle.HOME_MENU);
    public static final JiveItem SETTINGS = new JiveItem(record("settings", "home", Squeezer.getInstance().getString(R.string.SETTINGS), 1005), Window.WindowStyle.HOME_MENU);
    public static final JiveItem ADVANCED_SETTINGS = new JiveItem(record("advancedSettings", "settings", Squeezer.getInstance().getString(R.string.ADVANCED_SETTINGS), 105), Window.WindowStyle.TEXT_ONLY);
    public static final JiveItem ARCHIVE = new JiveItem(record("archiveNode", "home", Squeezer.getInstance().getString(R.string.ARCHIVE_NODE), 10), Window.WindowStyle.HOME_MENU);
    public static final JiveItem DOWNLOAD = new JiveItem(record("downloadItem", R.string.DOWNLOAD));
    public static final JiveItem RANDOM_PLAY = new JiveItem(record("randomPlay",  R.string.PLAY_RANDOM_FOLDER));
    public static final JiveItem PLAY_NOW = new JiveItem(record("playNow",  R.string.PLAY_NOW));
    public static final JiveItem ADD_TO_END = new JiveItem(record("playAdd",  R.string.ADD_TO_END));
    public static final JiveItem PLAY_NEXT = new JiveItem(record("playNext",  R.string.PLAY_NEXT));
    public static final JiveItem MORE = new JiveItem(record("more",  R.string.MORE));

    /**
     * Information that will be requested about songs.
     * <p>
     A 	<role> 	For every artist role (one of "artist", "composer", "conductor", "band", "albumartist" or "trackartist"), a comma and space (', ') separated list of names.
     AA 	<role> 	This is like "A", but without the space after the comma. This should simplify parsing/splitting when required.
     B 	buttons 	A hash with button definitions. Only available for certain plugins such as Pandora.
     d 	duration 	Song duration in seconds.
     e 	album_id 	Album ID. Only if known.
     j 	coverart 	1 if coverart is available for this song. Not listed otherwise.
     J 	artwork_track_id 	Identifier of the album track used by the server to display the album's artwork. Not listed if artwork is not available for this album.
     K 	artwork_url 	A full URL to remote artwork. Only available for certain online music services.
     l 	album 	Album name. Only if known.
     r 	bitrate 	Song bitrate. Only if known.
     S 	<role>_ids 	For each role as defined above, the list of ids (comma separated).
     t 	tracknum 	Track number. Only if known.
     T 	samplerate 	Song sample rate (in KHz)
     u 	url 	Song file url.
     x 	remote 	If 1, this is a remote track.
     */
    public static final String SONG_TAGS = "ABdejJKlrStTux";

    public static final Creator<JiveItem> CREATOR = new Creator<>() {
        @Override
        public JiveItem[] newArray(int size) {
            return new JiveItem[size];
        }

        @Override
        public JiveItem createFromParcel(Parcel source) {
            return new JiveItem(source);
        }
    };

    private JiveItem(Map<String, Object> record, Window.WindowStyle windowStyle) {
        this(record);
        window = new Window();
        window.windowStyle = windowStyle;
    }

    private static Map<String, Object> record(String id, @StringRes int text) {
        return new FluentHashMap<String, Object>()
                .with("id", id)
                .with("node", id)
                .with("name", Squeezer.getInstance().getString(text));
    }

    private static Map<String, Object> record(String id, String node, String text, int weight) {
        return new FluentHashMap<String, Object>()
                .with("id", id)
                .with("node", node)
                .with("name", text)
                .with("weight", weight);
    }


    private String record;
    @NonNull private String name = "";
    public String text2;
    @NonNull public String textkey = "";
    @NonNull private Uri icon = Uri.EMPTY;
    public String iconStyle;
    private String extid;

    private String node;
    private String originalNode;
    private int weight;
    private String type;

    public Action.NextWindow nextWindow;
    public Input input;
    public String inputValue;
    public Window window;
    public boolean doAction;
    public Action goAction;
    public Action playAction;
    public Action addAction;
    public Action insertAction;
    public Action moreAction;
    public List<JiveItem> subItems;
    public boolean showBigArtwork;
    public int selectedIndex;
    public String[] choiceStrings;
    public Boolean checkbox;
    public Map<Boolean, Action> checkboxActions;
    public Boolean radio;
    public Slider slider;

    @NonNull public Uri webLink = Uri.EMPTY;

    private SlimCommand downloadCommand;
    private SlimCommand randomPlayFolderCommand;

    public JiveItem() {
    }

    @NonNull
    public String getName() {
        return name;
    }

    public void setName(@NonNull String name) {
        this.name = name;
    }

    /** The URL to use to download the icon. */
    @NonNull
    public Uri getIcon() {
        if (icon.equals(Uri.EMPTY) && window != null) {
            return window.icon;
        }
        return icon;
    }

    /**
     * @return Whether the song has downloadable artwork associated with it.
     */
    private boolean hasIconUri() {
        return !getIcon().equals(Uri.EMPTY);
    }

    /**
     * @return Whether we should download icon or use embedded drawable
     */
    public boolean useIcon() {
        return hasIconUri() && (ItemIconUtils.getItemIcon(this) == null);
    }

    /**
     * @return Whether the song has an icon associated with it.
     */
    public boolean hasIcon() {
        return hasIconUri() || (ItemIconUtils.getItemIcon(this) != null);
    }

    /**
     * @return Icon resource for this item if it is embedded in the Squeezer app, or an empty icon.
     */
    public Drawable getIconDrawable(Context context) {
        return getIconDrawable(context, R.drawable.icon_no_artwork);
    }

    /**
     * @return Icon resource for this item if it is embedded in the Squeezer app, or the supplied default icon.
     */
    public Drawable getIconDrawable(Context context, @DrawableRes int defaultIcon) {
        return ItemIconUtils.getIconDrawable(context, this, defaultIcon);
    }

    public Drawable getLogo(Context context) {
        return ServiceLogoUtils.getLogo(context, extid);
    }

    public String getNode() {
        return node;
    }

    public int getWeight() {
        return weight;
    }

    public String getType() {
        return type;
    }


    public boolean isSelectable() {
        return (goAction != null || nextWindow != null || hasSubItems()|| node != null || checkbox != null || !webLink.equals(Uri.EMPTY));
    }

    public boolean hasContextMenu() {
        return (playAction != null || addAction != null || insertAction != null || moreAction != null || checkbox != null || radio != null);
    }

    public Map<String, Object> getRecord() {
        JSON json = new JSON();
        return (Map) json.fromJSON(this.record);
    }

    public void appendWeight(int weight) {
        JSON json = new JSON();
        Map<String, Object> map = (Map) json.fromJSON(this.record);
        map.put("weight", weight);
        this.record = json.toJSON(map);
    }

    public JiveItem(Map<String, Object> record) {
        JSON json = new JSON();
        this.record = json.toJSON(record);
        setId(getString(record, record.containsKey("cmd") ? "cmd" : "id"));
        splitItemText(getStringOrEmpty(record, record.containsKey("name") ? "name" : "text"));
        textkey = getStringOrEmpty(record, "textkey");
        icon = getImageUrl(record, record.containsKey("icon-id") ? "icon-id" : "icon");
        iconStyle = getString(record, "iconStyle");
        extid = getString(record, "extid");
        node = originalNode = getString(record, "node");
        weight = getInt(record, "weight");
        type = getString(record, "type");
        Map<String, Object> baseRecord = getRecord(record, "base");
        Map<String, Object> baseActions = (baseRecord != null ? getRecord(baseRecord, "actions") : null);
        Map<String, Object> baseWindow = (baseRecord != null ? getRecord(baseRecord, "window") : null);
        Map<String, Object> actionsRecord = getRecord(record, "actions");
        nextWindow = Action.NextWindow.fromString(getString(record, "nextWindow"));
        input = extractInput(getRecord(record, "input"));
        window = extractWindow(getRecord(record, "window"), baseWindow);

        // do takes precedence over go
        goAction = extractAction("do", baseActions, actionsRecord, record, baseRecord);
        doAction = (goAction != null);
        if (goAction == null) {
            // check if item instructs us to use a different action
            String goActionName = record.containsKey("goAction") ? getString(record, "goAction") : "go";
            goAction = extractAction(goActionName, baseActions, actionsRecord, record, baseRecord);
        }

        playAction = extractAction("play", baseActions, actionsRecord, record, baseRecord);
        addAction = extractAction("add", baseActions, actionsRecord, record, baseRecord);
        insertAction = extractAction("add-hold", baseActions, actionsRecord, record, baseRecord);
        moreAction = extractAction("more", baseActions, actionsRecord, record, baseRecord);
        if (moreAction != null) {
            moreAction.action.params.put("xmlBrowseInterimCM", 1);
        }

        downloadCommand = extractDownloadAction(record);
        randomPlayFolderCommand = downloadCommand;

        subItems = extractSubItems((Object[]) record.get("item_loop"));
        showBigArtwork = record.containsKey("showBigArtwork");

        selectedIndex = getInt(record, "selectedIndex");
        choiceStrings = Util.getStringArray(record, "choiceStrings");

        if (record.containsKey("checkbox")) {
            checkbox = (getInt(record, "checkbox") != 0);
            checkboxActions = new HashMap<>();
            checkboxActions.put(true, extractAction("on", baseActions, actionsRecord, record, baseRecord));
            checkboxActions.put(false, extractAction("off", baseActions, actionsRecord, record, baseRecord));
        }

        if (record.containsKey("radio")) {
            radio = (getInt(record, "radio") != 0);
        }

        if (record.containsKey("slider")) {
            slider = new Slider();
            slider.min = getInt(record, "min");
            slider.max = getInt(record, "max");
            slider.adjust = getInt(record, "adjust");
            slider.initial = getInt(record, "initial");
            slider.sliderIcons = getString(record, "sliderIcons");
            slider.help = getString(record, "help");
        }

        webLink = Uri.parse(getStringOrEmpty(record, "weblink"));
    }

    public JiveItem(Parcel source) {
        setId(source.readString());
        name = source.readString();
        text2 = source.readString();
        textkey = source.readString();
        icon = Uri.parse(source.readString());
        iconStyle = source.readString();
        extid = source.readString();
        node = source.readString();
        weight = source.readInt();
        type = source.readString();
        nextWindow = Action.NextWindow.fromString(source.readString());
        input = Input.readFromParcel(source);
        window = source.readParcelable(getClass().getClassLoader());
        goAction = source.readParcelable(getClass().getClassLoader());
        playAction = source.readParcelable(getClass().getClassLoader());
        addAction = source.readParcelable(getClass().getClassLoader());
        insertAction = source.readParcelable(getClass().getClassLoader());
        moreAction = source.readParcelable(getClass().getClassLoader());
        subItems = source.createTypedArrayList(JiveItem.CREATOR);
        doAction = (source.readByte() != 0);
        showBigArtwork = (source.readByte() != 0);
        selectedIndex = source.readInt();
        choiceStrings = source.createStringArray();
        checkbox = (Boolean) source.readValue(getClass().getClassLoader());
        if (checkbox != null) {
            checkboxActions = new HashMap<>();
            checkboxActions.put(true, (Action) source.readParcelable(getClass().getClassLoader()));
            checkboxActions.put(false, (Action) source.readParcelable(getClass().getClassLoader()));
        }
        radio = (Boolean) source.readValue(getClass().getClassLoader());
        slider = source.readParcelable(getClass().getClassLoader());
        downloadCommand = source.readParcelable(getClass().getClassLoader());
        webLink = Uri.parse(source.readString());
//      TODO
//        randomPlayFolderCommand = source.readParcelable(getClass().getClassLoader());
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(getId());
        dest.writeString(name);
        dest.writeString(text2);
        dest.writeString(textkey);
        dest.writeString(icon.toString());
        dest.writeString(iconStyle);
        dest.writeString(extid);
        dest.writeString(node);
        dest.writeInt(weight);
        dest.writeString(type);
        dest.writeString(nextWindow == null ? null : nextWindow.toString());
        Input.writeToParcel(dest, input);
        dest.writeParcelable(window, flags);
        dest.writeParcelable(goAction, flags);
        dest.writeParcelable(playAction, flags);
        dest.writeParcelable(addAction, flags);
        dest.writeParcelable(insertAction, flags);
        dest.writeParcelable(moreAction, flags);
        dest.writeTypedList(subItems);
        dest.writeByte((byte) (doAction ? 1 : 0));
        dest.writeByte((byte) (showBigArtwork ? 1 : 0));
        dest.writeInt(selectedIndex);
        dest.writeStringArray(choiceStrings);
        dest.writeValue(checkbox);
        if (checkbox != null) {
            dest.writeParcelable(checkboxActions.get(true), flags);
            dest.writeParcelable(checkboxActions.get(false), flags);
        }
        dest.writeValue(radio);
        dest.writeParcelable(slider, flags);
        dest.writeParcelable(downloadCommand, flags);
        dest.writeString(webLink.toString());
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public boolean hasInput() {
        return hasInputField() || hasChoices();
    }

    public boolean hasInputField() {
        return (input != null);
    }

    public boolean hasChoices() {
        return (choiceStrings.length > 0);
    }

    public boolean hasSlider() {
        return (slider != null);
    }

    public boolean isInputReady() {
        return !TextUtils.isEmpty(inputValue);
    }

    public boolean hasSubItems() {
        return (subItems != null);
    }

    public boolean canDownload() {
        return downloadCommand != null;
    }

    public String selectedChoice() {
        return (selectedIndex > 0 && selectedIndex <= choiceStrings.length) ? choiceStrings[selectedIndex - 1] : "";
    }

    public String text2() {
        return (hasChoices() && TextUtils.isEmpty(text2)) ? selectedChoice() : text2;
    }

    public SlimCommand downloadCommand() {
        return downloadCommand;
    }

    public SlimCommand randomPlayFolderCommand() {
        return randomPlayFolderCommand;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public int hashCode() {
        return (getId() != null ? getId().hashCode() : 0);
    }

    @Override
    public String toString() {
        return "JiveItem{" +
                "name='" + name + '\'' +
                ", text2='" + text2 + '\'' +
                ", textkey='" + textkey + '\'' +
                ", icon=" + icon +
                ", iconStyle='" + iconStyle + '\'' +
                ", extid='" + extid + '\'' +
                ", node='" + node + '\'' +
                ", originalNode='" + originalNode + '\'' +
                ", weight=" + weight +
                ", type='" + type + '\'' +
                ", nextWindow=" + nextWindow +
                ", input=" + input +
                ", inputValue='" + inputValue + '\'' +
                ", window=" + window +
                ", doAction=" + doAction +
                ", goAction=" + goAction +
                ", playAction=" + playAction +
                ", addAction=" + addAction +
                ", insertAction=" + insertAction +
                ", moreAction=" + moreAction +
                ", subItems=" + subItems +
                ", showBigArtwork=" + showBigArtwork +
                ", selectedIndex=" + selectedIndex +
                ", choiceStrings=" + Arrays.toString(choiceStrings) +
                ", checkbox=" + checkbox +
                ", checkboxActions=" + checkboxActions +
                ", radio=" + radio +
                ", slider=" + slider +
                ", webLink=" + webLink +
                ", downloadCommand=" + downloadCommand +
                ", randomPlayFolderCommand=" + randomPlayFolderCommand +
                "} " + super.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        JiveItem jiveItem = (JiveItem) o;
        return name.equals(jiveItem.name) && Objects.equals(text2, jiveItem.text2) && icon.equals(jiveItem.icon);
    }

    private void splitItemText(String text) {
        // This happens enough for regular expressions to be ineffective
        int nameEnd = text.indexOf('\n');
        if (nameEnd > 0) {
            name = text.substring(0, nameEnd);
            text2 = text.substring(nameEnd+1);
        } else {
            name = text;
            text2 = "";
        }
    }

    public static Window extractWindow(Map<String, Object> itemWindow, Map<String, Object> baseWindow) {
        if (itemWindow == null && baseWindow == null) return null;

        Map<String, Object> params = new HashMap<>();
        if (baseWindow != null) params.putAll(baseWindow);
        if (itemWindow != null) params.putAll(itemWindow);

        Window window = new Window();
        window.windowId = getString(params, "windowId");
        window.text = getString(params, "text");
        window.textarea = getStringOrEmpty(params, "textarea").replaceAll("\\\\n", "\n");
        window.textareaToken = getString(params, "textAreaToken");
        window.help = getString(params, "help");
        window.html = getString(params, "html");
        window.icon = getImageUrl(params, params.containsKey("icon-id") ? "icon-id" : "icon");
        window.titleStyle = getString(params, "titleStyle");

        String menuStyle = getString(params, "menuStyle");
        String windowStyle = getString(params, "windowStyle");
        window.windowStyle = Window.WindowStyle.get(windowStyle);
        if (window.windowStyle == null) {
            window.windowStyle = menu2window.get(menuStyle);
            if (window.windowStyle == null) {
                window.windowStyle = Window.WindowStyle.TEXT_ONLY;
            }
        }

        return window;
    }

    /**
     * legacy map of menuStyles to windowStyles
     * <p>
     * make an educated guess at window style when one is not sent but a menu style is
     */
    private static final Map<String, Window.WindowStyle> menu2window = initializeMenu2Window();

    private static Map<String, Window.WindowStyle> initializeMenu2Window() {
        Map<String, Window.WindowStyle> result = new HashMap<>();

        result.put("album", Window.WindowStyle.ICON_LIST);
        result.put("playlist", Window.WindowStyle.PLAY_LIST);

        return result;
    }


    private Input extractInput(Map<String, Object> record) {
        if (record == null) return null;

        Input input = new Input();
        input.len = getInt(record, "len");
        input.softbutton1 = getString(record, "softbutton1");
        input.softbutton2 = getString(record, "softbutton2");
        input.inputStyle = getString(record, "_inputStyle");
        input.title = getString(record, "title");
        input.initialText = getString(record, "initialText");
        input.allowedChars = getString(record, "allowedChars");
        Map<String, Object> helpRecord = getRecord(record, "help");
        if (helpRecord != null) {
            input.help = new HelpText();
            input.help.text = getString(helpRecord, "text");
            input.help.token = getString(helpRecord, "token");
        }
        return input;
    }

    private Action extractAction(String actionName, Map<String, Object> baseActions, Map<String, Object> itemActions, Map<String, Object> record, Map<String, Object> baseRecord) {
        Map<String, Object> actionRecord = null;
        Map<String, Object> itemParams = null;

        Object itemAction = (itemActions != null ? itemActions.get(actionName) : null);
        if (itemAction instanceof Map) {
            actionRecord = (Map<String, Object>) itemAction;
        }
        if (actionRecord == null && baseActions != null) {
            Map<String, Object> baseAction = getRecord(baseActions, actionName);
            if (baseAction != null) {
                String itemsParams = (String) baseAction.get("itemsParams");
                if (itemsParams != null) {
                    itemParams = getRecord(record, itemsParams);
                    if (itemParams != null) {
                        actionRecord = baseAction;
                    }
                }
            }
        }
        if (actionRecord == null) return null;

        Action actionHolder = new Action();

        if (actionRecord.containsKey("choices")) {
            Object[] choices = (Object[]) actionRecord.get("choices");
            actionHolder.choices = new Action.JsonAction[choices.length];
            for (int i = 0; i < choices.length; i++) {
                actionRecord = (Map<String, Object>) choices[i];
                actionHolder.choices[i]= extractJsonAction(baseRecord, actionRecord, itemParams);
            }
        } else {
            actionHolder.action = extractJsonAction(baseRecord, actionRecord, itemParams);
        }

        return actionHolder;
    }

    private Action.JsonAction extractJsonAction(Map<String, Object> baseRecord, Map<String, Object> actionRecord, Map<String, Object> itemParams) {
        Action.JsonAction action = new Action.JsonAction();

        action.players = "0".equals(getString(actionRecord, "player")) ? new String[0] : Util.getStringArray(actionRecord, "player");
        action.nextWindow = Action.NextWindow.fromString(getString(actionRecord, "nextWindow"));
        if (action.nextWindow == null) action.nextWindow = nextWindow;
        if (action.nextWindow == null && baseRecord != null)
            action.nextWindow = Action.NextWindow.fromString(getString(baseRecord, "nextWindow"));

        action.cmd(Util.getStringArray(actionRecord, "cmd"));
        Map<String, Object> params = getRecord(actionRecord, "params");
        if (params != null) {
            action.params(params);
        }
        if (itemParams != null) {
            action.params(itemParams);
        }
        action.param("useContextMenu", "1");

        Map<String, Object> windowRecord = getRecord(actionRecord, "window");
        if (windowRecord != null) {
            action.window = new Action.ActionWindow(getInt(windowRecord, "isContextMenu") != 0);
        }

        // LMS may send isContextMenu in the itemParams, but this is ignored by squeezeplay, so we must do the same.
        action.isContextMenu = (params != null && params.containsKey("isContextMenu")) || (action.window != null && action.window.isContextMenu);

        return action;
    }

    private List<JiveItem> extractSubItems(Object[] item_loop) {
        if (item_loop != null) {
            List<JiveItem> items = new ArrayList<>();
            for (Object item_d : item_loop) {
                Map<String, Object> record = (Map<String, Object>) item_d;
                items.add(new JiveItem(record));
            }
            return items;
        }

        return null;
    }

    private SlimCommand extractDownloadAction(Map<String, Object> record) {
        if ("local".equals(getString(record, "trackType")) && (goAction != null || moreAction != null)) {
            Action action = (moreAction != null ? moreAction : goAction);
            String trackId = getStringOrEmpty(action.action.params, "track_id");
            return new SlimCommand()
                    .cmd("titles")
                    .param("tags", SONG_TAGS)
                    .param("track_id", trackId);
        } else if (playAction != null && Collections.singletonList("playlistcontrol").equals(playAction.action.cmd) && "load".equals(playAction.action.params.get("cmd"))) {
            if (playAction.action.params.containsKey("folder_id")) {
                return new SlimCommand()
                        .cmd("musicfolder")
                        .param("tags", "cu")
                        .param("recursive", "1")
                        .param("folder_id", playAction.action.params.get("folder_id"));
            } else if (playAction.action.params.containsKey("playlist_id")) {
                return new SlimCommand()
                        .cmd("playlists", "tracks")
                        .param("tags", SONG_TAGS)
                        .param("playlist_id", playAction.action.params.get("playlist_id"));
            } else {
                return new SlimCommand()
                        .cmd("titles")
                        .param("tags", SONG_TAGS)
                        .params(getTitlesParams(playAction.action));
            }
        }
        return null;
    }

    public static SlimCommand downloadCommand(String id) {
        return new SlimCommand()
                .cmd("titles")
                .param("tags", SONG_TAGS)
                .param("track_id", id);
    }

    /**
     * Get parameters which can be used in a titles command from the supplied action
     */
    private Map<String,Object> getTitlesParams(SlimCommand action) {
        Map<String, Object> map = new HashMap<>();
        for (Map.Entry<String, Object> e : action.params.entrySet()) {
            if (title_parameters.contains(e.getKey()) && e.getValue() != null) {
                map.put(e.getKey(), e.getValue());
            }
        }
        return map;
    }
    private static final Set<String> title_parameters = new HashSet<>(Arrays.asList("track_id", "album_id", "artist_id", "genre_id", "year"));

    public void setNode(String node) {
        this.node = node;
    }

    public String getOriginalNode() {
        return this.originalNode;
    }
}
