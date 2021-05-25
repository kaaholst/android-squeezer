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

import android.text.TextUtils;

import androidx.annotation.NonNull;

import java.util.Map;

import uk.org.ngo.squeezer.Util;

/**
 * The alertWindow is meant for messages that should not leave the screen without the user
 * dismissing it explicitly, and as a full window has the ability of passing much larger text
 * messages than in a small popup.
 */
public class AlertWindow {

    /** text to display in the title bar of the window */
    public final String title;

    /** The text to display in the body of the window as a text-area widget. */
    public final String text;

    public AlertWindow(Map<String, Object> display) {
        title = Util.getString(display, "title");
        Object[] texts = (Object[]) display.get("text");
        String text = TextUtils.join("\n", texts).replaceAll("\\\\n", "\n");
        this.text = (text.startsWith("\n") ? text.substring(1) : text);
    }

    @NonNull
    @Override
    public String toString() {
        return "AlertWindow{" +
                "title='" + title + '\'' +
                ", text='" + text + '\'' +
                '}';
    }
}
