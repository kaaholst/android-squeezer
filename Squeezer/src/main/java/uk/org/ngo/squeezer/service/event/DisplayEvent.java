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

import androidx.annotation.NonNull;

import uk.org.ngo.squeezer.model.DisplayMessage;

/** Event sent when a display status message is received. */
public class DisplayEvent {
    /** The message to show. */
    @NonNull
    public final DisplayMessage message;

    public boolean isShown = false;

    public DisplayEvent(@NonNull DisplayMessage message) {
        this.message = message;
    }

    @Override
    public String toString() {
        return "DisplayEvent{" +
                "message=" + message +
                '}';
    }
}
