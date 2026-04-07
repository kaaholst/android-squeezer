package uk.org.ngo.squeezer.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import uk.org.ngo.squeezer.Preferences;
import uk.org.ngo.squeezer.Squeezer;
import uk.org.ngo.squeezer.itemlist.ItemListCallback;
import uk.org.ngo.squeezer.model.JiveItem;
import uk.org.ngo.squeezer.model.LyrionPlayer;

class HomeMenuReceiver implements ItemListCallback<JiveItem> {
    private final LyrionPlayer player;
    private final LyrionController lyrionController;
    private final HomeMenuHandling homeMenuHandling;
    private final List<JiveItem> homeMenu = new ArrayList<>();

    HomeMenuReceiver(LyrionPlayer player, LyrionController lyrionController, HomeMenuHandling homeMenuHandling) {
        this.player = player;
        this.lyrionController = lyrionController;
        this.homeMenuHandling = homeMenuHandling;
        lyrionController.requestItems(player, 0, this).cmd("menu").param("direct", "1").exec();
    }

    @Override
    public void onItemsReceived(int count, int start, Map<String, Object> parameters, List<JiveItem> items, Class<JiveItem> dataType) {
        homeMenu.addAll(items);
        if (homeMenu.size() == count) {
            Preferences preferences = Squeezer.instance().preferences();
            boolean useArchive = preferences.getCustomizeHomeMenuMode() != Preferences.CustomizeHomeMenuMode.DISABLED;
            Set<String> archivedMenuItems = Collections.emptySet();
            if (useArchive) {
                archivedMenuItems = preferences.getArchivedMenuItems(player);
            }
            homeMenuHandling.setHomeMenu(homeMenu, archivedMenuItems, preferences.homeGroups());
        }
    }

    @Override
    public Object getClient() {
        return lyrionController;
    }
}
