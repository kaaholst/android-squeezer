package uk.org.ngo.squeezer.homescreenwidgets;

import uk.org.ngo.squeezer.model.LyrionPlayer;
import uk.org.ngo.squeezer.service.ISqueezeService;

@FunctionalInterface
interface ServicePlayerHandler {
    void run(ISqueezeService service, LyrionPlayer player) throws Exception;
}
