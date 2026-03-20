package uk.org.ngo.squeezer.homescreenwidgets;

import android.content.Context;

import uk.org.ngo.squeezer.model.LyrionPlayer;
import uk.org.ngo.squeezer.service.ISqueezeService;

@FunctionalInterface
interface ContextServicePlayerHandler {
    void run(Context context, ISqueezeService service, LyrionPlayer player) throws Exception;
}
