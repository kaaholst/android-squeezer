/*
 * Copyright (c) 2019 Kurt Aaholst <kaaholst@gmail.com>
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
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import android.text.TextUtils;

import java.util.HashMap;
import java.util.Map;

import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.Util;

/**
 * The purpose of the showBriefly is (typically) to show a brief popup message on the display to
 * convey something to the user.
 */
public class DisplayMessage {
    private static final String TYPE_ICON = "icon";
    private static final String TYPE_TEXT = "text";
    private static final String TYPE_MIXED = "mixed";
    private static final String TYPE_SONG = "song";
    private static final String TYPE_POPUPALBUM = "popupalbum";

    /** tells SP what style of popup to use. Valid types are 'popupplay', 'icon', 'song', 'mixed', and 'popupalbum'. In 7.6, 'alertWindow' has been added (see next section) */
    public final String type;

    /** duration in milliseconds to display the showBriefly popup. Defaults to 3 seconds. In 7.6, a duration of -1 will create a popup that doesn't go away until dismissed. */
    public final int duration;

    /** used for specific styles to be used in popup windows, e.g. adding a + badge when adding a favorite. */
    public final String style;

    /** The message to show. */
    public final String text;

    /** Remote icon or {@link Uri#EMPTY} */
    @NonNull public Uri icon;

    public DisplayMessage(Map<String, Object> display) {
        type = Util.getString(display, "type", TYPE_TEXT);
        duration = Util.getInt(display, "duration", 3000);
        style = Util.getString(display, "style");
        Object[] texts = (Object[]) display.get("text");
        String text = TextUtils.join("\n",texts).replaceAll("\\\\n", "\n");
        this.text = (text.startsWith("\n") ? text.substring(1) : text);
        icon = Util.getImageUrl(display, display.containsKey("icon-id") ? "icon-id" : "icon");
    }

    public boolean isIcon() {
        return (TYPE_ICON.equals(type) && !TextUtils.isEmpty(style));
    }

    public boolean isMixed() {
        return (TYPE_MIXED.equals(type));
    }

    public boolean isPopupAlbum() {
        return (TYPE_POPUPALBUM.equals(type));
    }

    public boolean isSong() {
        return (TYPE_SONG.equals(type));
    }

    /** @return Whether this message has a remote icon associated with it. */
    public boolean hasIcon() {
        return !(icon.equals(Uri.EMPTY));
    }

    @NonNull
    @Override
    public String toString() {
        return "DisplayMessage{" +
                "type='" + type + '\'' +
                ", duration=" + duration +
                ", style='" + style + '\'' +
                ", icon=" + icon +
                ", text='" + text + '\'' +
                '}';
    }

    @DrawableRes
    public int getIconResource() {
        @DrawableRes Integer iconResource = displayMessageIcons.get(style);
        return iconResource == null ? 0 : iconResource;
    }

    private static final Map<String, Integer> displayMessageIcons = initializeDisplayMessageIcons();

    private static Map<String, Integer> initializeDisplayMessageIcons() {
        Map<String, Integer> result = new HashMap<>();

        result.put("volume", R.drawable.ic_volume_up_white);
        result.put("mute", R.drawable.ic_volume_off_white);

        result.put("sleep_15", R.drawable.ic_sleep_15);
        result.put("sleep_30", R.drawable.ic_sleep_30);
        result.put("sleep_45", R.drawable.ic_sleep_45);
        result.put("sleep_60", R.drawable.ic_sleep_60);
        result.put("sleep_90", R.drawable.ic_sleep_90);
        result.put("sleep_cancel", R.drawable.ic_sleep_off);

        result.put("shuffle0", R.drawable.ic_shuffle_off);
        result.put("shuffle1", R.drawable.ic_shuffle_song);
        result.put("shuffle2", R.drawable.ic_shuffle_album);

        result.put("repeat0", R.drawable.ic_repeat_off);
        result.put("repeat1", R.drawable.ic_repeat_song);
        result.put("repeat2", R.drawable.ic_repeat_white);

        result.put("pause", R.drawable.ic_action_pause);
        result.put("play", R.drawable.ic_action_play);
        result.put("fwd", R.drawable.ic_action_next);
        result.put("rew", R.drawable.ic_action_previous);
        result.put("stop", R.drawable.ic_action_stop);

        result.put("add", R.drawable.ic_add);
        result.put("favorite", R.drawable.icon_popup_favorite);
        result.put("lineIn", R.drawable.icon_line_in_white);

        return result;
    }

}
