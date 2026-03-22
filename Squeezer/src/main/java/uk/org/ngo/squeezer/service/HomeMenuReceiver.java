package uk.org.ngo.squeezer.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import uk.org.ngo.squeezer.Preferences;
import uk.org.ngo.squeezer.Squeezer;
import uk.org.ngo.squeezer.itemlist.IServiceItemListCallback;
import uk.org.ngo.squeezer.model.JiveItem;

class HomeMenuReceiver implements IServiceItemListCallback<JiveItem> {
    private final LyrionController lyrionController;
    private final HomeMenuHandling homeMenuHandling;
    private final List<JiveItem> homeMenu = new ArrayList<>();

    HomeMenuReceiver(LyrionController lyrionController, HomeMenuHandling homeMenuHandling) {
        this.lyrionController = lyrionController;
        this.homeMenuHandling = homeMenuHandling;
    }

    @Override
    public void onItemsReceived(int count, int start, Map<String, Object> parameters, List<JiveItem> items, Class<JiveItem> dataType) {
        homeMenu.addAll(items);
        if (homeMenu.size() == count) {
            Preferences preferences = Squeezer.getPreferences();
            boolean useArchive = preferences.getCustomizeHomeMenuMode() != Preferences.CustomizeHomeMenuMode.DISABLED;
            Set<String> archivedMenuItems = Collections.emptySet();
            if ((useArchive) && (lyrionController.getActivePlayer() != null)) {
                archivedMenuItems = preferences.getArchivedMenuItems(lyrionController.getActivePlayer());
            }
            homeMenuHandling.setHomeMenu(homeMenu, archivedMenuItems, preferences.homeGroups());
        }
    }

    @Override
    public Object getClient() {
        return lyrionController;
    }
}
