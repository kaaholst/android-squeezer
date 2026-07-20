/*
 * Copyright (c) 2026 Thomas Malcher
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

import androidx.annotation.NonNull;

import java.util.Map;
import java.util.Objects;

import uk.org.ngo.squeezer.Util;

/**
 * A player preset, i.e. a favorite assigned to one of the preset buttons of the player.
 * <p>
 * Presets are received in the "preset_data" field of the status response, which the server
 * includes when the status request has the "menu:menu" parameter. An unassigned preset slot
 * is an empty record.
 */
public class Preset {

    /** The URL of the assigned item, empty if the preset slot is unassigned. */
    @NonNull
    public final String url;

    /** The display text of the assigned item. */
    @NonNull
    public final String text;

    /** The type of the assigned item, e.g. "audio" or "playlist". */
    @NonNull
    public final String type;

    public Preset(Map<String, Object> record) {
        this(Util.getStringOrEmpty(record, "URL"),
                Util.getStringOrEmpty(record, "text"),
                Util.getStringOrEmpty(record, "type"));
    }

    public Preset(@NonNull String url, @NonNull String text, @NonNull String type) {
        this.url = url;
        this.text = text;
        this.type = type;
    }

    /** @return Whether an item is assigned to this preset slot. */
    public boolean isSet() {
        return !url.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Preset)) return false;
        Preset preset = (Preset) o;
        return url.equals(preset.url) && text.equals(preset.text) && type.equals(preset.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, text, type);
    }

    @NonNull
    @Override
    public String toString() {
        return "Preset{url='" + url + "', text='" + text + "', type='" + type + "'}";
    }
}
