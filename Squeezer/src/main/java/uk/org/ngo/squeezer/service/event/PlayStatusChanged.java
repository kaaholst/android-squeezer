/*
 * Copyright (c) 2014 Google Inc.  All Rights Reserved.
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

import uk.org.ngo.squeezer.model.LyrionPlayer;
import uk.org.ngo.squeezer.model.PlayerState;

/** Event sent when a player's play status changes. */
public class PlayStatusChanged {
    /** The new play status. */
    @NonNull
    @PlayerState.PlayState
    public final String playStatus;

    /** The affected player. */
    @NonNull
    public final LyrionPlayer player;

    public PlayStatusChanged(@NonNull @PlayerState.PlayState String playStatus, @NonNull LyrionPlayer player) {
        this.playStatus = playStatus;
        this.player = player;
    }

    @Override
    public String toString() {
        return "PlayStatusChanged{" +
                "playStatus='" + playStatus + '\'' +
                ", player=" + player +
                '}';
    }
}
