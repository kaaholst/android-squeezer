package uk.org.ngo.squeezer.homescreenwidgets;

import uk.org.ngo.squeezer.service.LyrionController;

@FunctionalInterface
public interface ServiceHandler {
    void run(LyrionController lyrionController) throws Exception;
}
