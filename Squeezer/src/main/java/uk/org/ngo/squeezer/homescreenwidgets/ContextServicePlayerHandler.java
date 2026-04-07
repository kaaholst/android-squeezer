package uk.org.ngo.squeezer.homescreenwidgets;

import android.content.Context;

import uk.org.ngo.squeezer.model.LyrionPlayer;
import uk.org.ngo.squeezer.service.LyrionController;

@FunctionalInterface
public interface ContextServicePlayerHandler {
    void run(Context context, LyrionController service, LyrionPlayer player) throws Exception;
}
