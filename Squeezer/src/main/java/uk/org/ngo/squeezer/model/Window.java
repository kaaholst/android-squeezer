/*
 * Copyright (c) 2017 Kurt Aaholst <kaaholst@gmail.com>
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

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Implements <window_fields> of the LMS SqueezePlay interface.
 * http://wiki.slimdevices.com/index.php/SqueezeCenterSqueezePlayInterface#.3Cwindow_fields.3E
 */
public class Window implements Parcelable {
    public String text;
    public String textarea;
    public String textareaToken;
    @NonNull public Uri icon = Uri.EMPTY;
    public String titleStyle;
    public WindowStyle windowStyle;
    public String help;
    public String windowId;

    public Window() {
    }

    protected Window(Parcel in) {
        text = in.readString();
        textarea = in.readString();
        textareaToken = in.readString();
        icon = Uri.parse(in.readString());
        titleStyle = in.readString();
        windowStyle = WindowStyle.valueOf(in.readString());
        help = in.readString();
        windowId = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(text);
        dest.writeString(textarea);
        dest.writeString(textareaToken);
        dest.writeString(icon.toString());
        dest.writeString(titleStyle);
        dest.writeString(windowStyle.name());
        dest.writeString(help);
        dest.writeString(windowId);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<Window> CREATOR = new Creator<Window>() {
        @Override
        public Window createFromParcel(Parcel in) {
            return new Window(in);
        }

        @Override
        public Window[] newArray(int size) {
            return new Window[size];
        }
    };

    @Override
    public String toString() {
        return "Window{" +
                "text='" + text + '\'' +
                ", textarea='" + textarea + '\'' +
                ", textareaToken='" + textareaToken + '\'' +
                ", icon='" + icon + '\'' +
                ", titleStyle='" + titleStyle + '\'' +
                ", windowStyle='" + windowStyle + '\'' +
                ", help=" + help +
                ", windowId='" + windowId + '\'' +
                '}';
    }

    /**
     * Window styles from LMS
     * <p>
     * <b>NOTE:</b><br/>
     * home_menu from LMS just means "hasImage", whereas we use it for Home Menu Items
     * http://wiki.slimdevices.com/index.php/HomeMenuItemsVersusSlimbrowseItems
     */
    public enum WindowStyle {
        HOME_MENU("home_menu"), // Like ICON_LIST
        ICON_LIST("icon_list"), // Item layout have an icon, text1, text2
        PLAY_LIST("play_list"), // Like ICON_LIST but not in a grid.
        TEXT_ONLY("text_list"); // Items have text1, text2,m i.e. no icon.

        private static Map<String, WindowStyle> ENUM_MAP = initEnumMap();
        private final String id;

        WindowStyle(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        private static Map<String, WindowStyle> initEnumMap() {
            Map<String, WindowStyle> map = new HashMap<>();
            for (WindowStyle windowStyle : WindowStyle.values()) {
                map.put(windowStyle.id, windowStyle);
            }
            return Collections.unmodifiableMap(map);
        }

        public static WindowStyle get(String name) {
            return ENUM_MAP.get(name);
        }
    }
}