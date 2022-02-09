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

package uk.org.ngo.squeezer.service.event;

import android.util.Log;

import androidx.annotation.NonNull;

import java.util.List;

import uk.org.ngo.squeezer.model.JiveItem;

/** Event sent when the home menu has changed. */
public class HomeMenuEvent {

    @NonNull
    public List<JiveItem> menuItems;

    public HomeMenuEvent(@NonNull List<JiveItem> menuItems) {
        this.menuItems = menuItems;
    }

    @NonNull
    @Override
    public String toString() {
        return "HomeMenuEvent{" +
                "menuItems=" + menuItems +
                '}';
    }
}
