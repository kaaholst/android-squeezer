package uk.org.ngo.squeezer.service;

import androidx.annotation.NonNull;

/**
 * @param muted  True if the volume is muted
 * @param volume The player's new volume.
 * @param name   Name of player or group.
 */
public record VolumeInfo(boolean muted, int volume, @NonNull String name) {
}
