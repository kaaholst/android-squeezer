package uk.org.ngo.squeezer.homescreenwidgets;

import uk.org.ngo.squeezer.model.LyrionPlayer;
import uk.org.ngo.squeezer.service.LyrionController;

@FunctionalInterface
interface ServicePlayerHandler {
    void run(LyrionController lyrionController, LyrionPlayer player) throws Exception;
}
