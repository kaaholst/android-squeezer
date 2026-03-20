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

/** Event sent when the track the player is playing changes. */
public class MusicChanged {
    /** The player with changed state. */
    @NonNull public final LyrionPlayer player;

    /** The active player's new state. */
    @NonNull
    public final PlayerState playerState;

    public MusicChanged(@NonNull LyrionPlayer player, @NonNull PlayerState playerState) {
        this.player = player;
        this.playerState = playerState;
    }

    @Override
    public String toString() {
        return "MusicChanged{" +
                "player=" + player +
                ", playerState=" + playerState +
                '}';
    }
}
