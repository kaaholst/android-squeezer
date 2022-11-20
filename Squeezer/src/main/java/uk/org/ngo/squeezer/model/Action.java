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

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import uk.org.ngo.squeezer.Util;

/**
 * Implements <code>action_fields</code> of the LMS SqueezePlay interface.
 * http://wiki.slimdevices.com/index.php/SqueezeCenterSqueezePlayInterface#.3Cactions_fields.3E
 */
public class Action implements Parcelable {
    private static final String INPUT_PLACEHOLDER = "__INPUT__";
    private static final String TAGGEDINPUT_PLACEHOLDER = "__TAGGEDINPUT__";

    public String urlCommand;
    public JsonAction action;
    public JsonAction[] choices;

    public Action() {
    }

    public static final Creator<Action> CREATOR = new Creator<>() {
        @Override
        public Action[] newArray(int size) {
            return new Action[size];
        }

        @Override
        public Action createFromParcel(Parcel source) {
            return new Action(source);
        }
    };

    public Action(Parcel source) {
        urlCommand = source.readString();
        if (urlCommand == null) {
            int choiceCount = source.readInt();
            if (choiceCount > 0) {
                choices = new JsonAction[choiceCount];
                for (int i = 0; i < choiceCount; i++) {
                    choices[i] = source.readParcelable(getClass().getClassLoader());
                }
            } else {
                action = source.readParcelable(getClass().getClassLoader());
            }
        }
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(urlCommand);
        if (urlCommand == null) {
            dest.writeInt(choices != null ? choices.length : 0);
            if (choices != null) {
                for (JsonAction action : choices) {
                    dest.writeParcelable(action, flags);
                }
            } else {
                dest.writeParcelable(action, flags);
            }
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }


    public InputType getInputType() {
        if (action != null && action.params.containsValue(TAGGEDINPUT_PLACEHOLDER)) {
            for (Map.Entry<String, Object> entry : action.params.entrySet()) {
                if (TAGGEDINPUT_PLACEHOLDER.equals(entry.getValue())) {
                    switch (entry.getKey()) {
                        case "search": return InputType.SEARCH;
                        case "email": return InputType.EMAIL;
                        case "password": return InputType.PASSWORD;
                        default: return InputType.TEXT;
                    }
                }
            }
        }
        return InputType.TEXT;
    }

    public boolean isContextMenu() {
        return (action != null && action.isContextMenu);
    }

    public boolean isSlideShow() {
        return (action != null && action.params.containsKey("slideshow"));
    }

    public boolean isTypeSlideShow() {
        return (action != null && "slideshow".equals(action.params.get("type")));
    }

    public boolean isPlayerSpecific() {
        return (action != null && action.players.length > 0);
    }

    @NonNull
    @Override
    public String toString() {
        return "Action{" +
                "urlCommand='" + urlCommand + '\'' +
                ", action=" + action +
                '}';
    }


    /**
     * Action which can be sent to the server.
     * <p>
     * It is either received from the server or constructed from the CLI specification
     */
    public static class JsonAction extends SlimCommand {
        /** Player if the command requires it. The actual value is replaced by Jive. */
        public String[] players;

        /** If a nextWindow param is given at the json command level, it takes precedence over a nextWindow param at the item level,
         * which in turn takes precedence over a nextWindow param at the base level.
         * See <item_fields> section for more detail on this parameter. */
        public NextWindow nextWindow;

        public ActionWindow window;
        public boolean isContextMenu;

        public JsonAction() {
        }

        public static final Creator<JsonAction> CREATOR = new Creator<>() {
            @Override
            public JsonAction[] newArray(int size) {
                return new JsonAction[size];
            }

            @Override
            public JsonAction createFromParcel(Parcel source) {
                return new JsonAction(source);
            }
        };

        protected JsonAction(Parcel in) {
            super(in);
            players = in.createStringArray();
            nextWindow = NextWindow.fromString(in.readString());
            window = ActionWindow.fromString(in.readString());
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeStringArray(players);
            dest.writeString(nextWindow == null ? null : nextWindow.toString());
            dest.writeString(window == null ? null : window.isContextMenu ? "1" : "0");
        }

        public Map<String, Object> params(String input) {
            if (input == null) {
                return params;
            }
            Map<String, Object> out = new HashMap<>();
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                String value = entry.getValue().toString();
                if (TAGGEDINPUT_PLACEHOLDER.equals(value)) {
                    out.put(entry.getKey(), input);
                } else if (INPUT_PLACEHOLDER.equals(value)) {
                    out.put(input, null);
                } else
                    out.put(entry.getKey(), value);
            }
            if (out.containsKey("valtag")) {
                out.put(Util.getStringOrEmpty(out.get("valtag")), input);
                out.remove("valtag");
            }
            return out;
        }

        @NonNull
        @Override
        public String toString() {
            return "JsonAction{" +
                    "player=" + Arrays.toString(players) +
                    ", cmd=" + cmd +
                    ", params=" + params +
                    ", nextWindow=" + nextWindow +
                    '}';
        }
    }

    public static class ActionWindow {
        public boolean isContextMenu;

        public ActionWindow(boolean isContextMenu) {
            this.isContextMenu = isContextMenu;
        }

        public static ActionWindow fromString(String s) {
            return (s == null) ? null : new ActionWindow("1".equals(s));
        }
    }

    public static class NextWindow {
        public NextWindowEnum nextWindow;
        public String windowId;

        public NextWindow(NextWindowEnum nextWindow) {
            this.nextWindow = nextWindow;
        }

        public static NextWindow fromString(String s) {
            return (s == null ? null : new NextWindow(s));
        }

        private NextWindow(String nextWindow) {
            try {
                this.nextWindow = NextWindowEnum.valueOf(nextWindow);
            } catch (IllegalArgumentException e) {
                this.nextWindow = NextWindowEnum.windowId;
                windowId = nextWindow;
            }
        }

        @NonNull
        @Override
        public String toString() {
            return (nextWindow == NextWindowEnum.windowId ? windowId : nextWindow.name());
        }
    }

    public enum NextWindowEnum {
        nowPlaying, // push to the Now Playing browse window
        playlist, // push to the current playlist window
        home, // push to the top level "home" window
        parent, // push back to the previous window in the stack and refresh that window with the json that created it
        parentNoRefresh, // same as parent but do not refresh the window
        grandparent, // push back two windows in the stack
        refresh, // stay on this window, but resend the cli command that was used to construct it and refresh the window with the freshly returned data
        refreshOrigin, // push to the previous window in the stack, but resend the cli command that was used to construct it and refresh the window with the freshly returned data
        windowId, // (7.4+)any other value of a window that is present on the window stack and has a "windowId" in it's window fields. Search the window stack backwards until a window with this windowId is found and pop all windows above it.
    }

    public enum InputType {
        TEXT, SEARCH, EMAIL, PASSWORD
    }

}