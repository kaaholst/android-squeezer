package uk.org.ngo.squeezer.volume;

import uk.org.ngo.squeezer.service.ISqueezeService;

public interface VolumeUpdater {
    void update(ISqueezeService.VolumeInfo volumeInfo);
}
