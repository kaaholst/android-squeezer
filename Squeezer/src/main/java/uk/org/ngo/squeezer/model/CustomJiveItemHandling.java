package uk.org.ngo.squeezer.model;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import uk.org.ngo.squeezer.itemlist.IServiceItemListCallback;
import uk.org.ngo.squeezer.service.SqueezeService;

public class CustomJiveItemHandling {
    private static final String TAG = CustomJiveItemHandling.class.getSimpleName();
    public static final int CUSTOM_SHORTCUT_WEIGHT_NOT_ALLOWED = -1;
    public static final int CUSTOM_SHORTCUT_WEIGHT_MY_MUSIC = 2000;
    private static final int CUSTOM_SHORTCUT_WEIGHT_APPS = 2010;
    private static final int CUSTOM_SHORTCUT_WEIGHT_RADIO = 2020;

    public static int shortcutWeight(JiveItem item) {
        //  TODO add better check for fitting items
        //  TODO "All titles" is a name that comes up in several occations and will then not be updated
        if ((item.nextWindow != null) || (item.goAction == null)) {
            return CUSTOM_SHORTCUT_WEIGHT_NOT_ALLOWED;
        }
        return shortcutWeight(item.goAction.action);
    }

    public static void recoverShortcuts(SqueezeService service, List<JiveItem> shortcuts) {
        List<JiveItem> albumShortcuts = new ArrayList<>();
        List<JiveItem> artistShortcuts = new ArrayList<>();
        List<JiveItem> genreShortcuts = new ArrayList<>();
        List<JiveItem> trackShortcuts = new ArrayList<>();
        List<JiveItem> folderShortcuts = new ArrayList<>();
        shortcuts.stream()
                .filter(shortcut -> shortcutWeight(shortcut) == CUSTOM_SHORTCUT_WEIGHT_MY_MUSIC)
                .forEach(shortcut -> {
            Action itemAction = (shortcut.moreAction != null && shortcut.moreAction.action != null ? shortcut.moreAction : shortcut.goAction);
            Action.JsonAction action = itemAction.action;
            if (action.params.containsKey("folder_id") || "bmf".equals(shortcut.goAction.action.params.get("mode"))) folderShortcuts.add(shortcut);
            else if (action.params.containsKey("track_id")) trackShortcuts.add(shortcut);
            else if (action.params.containsKey("album_id")) albumShortcuts.add(shortcut);
            else if (action.params.containsKey("artist_id")) artistShortcuts.add(shortcut);
            else if (action.params.containsKey("genre_id")) genreShortcuts.add(shortcut);
        });
        if (!(albumShortcuts.isEmpty() && trackShortcuts.isEmpty())) recoverItems(service, browseLibraryCommand("albums"), albumShortcuts, trackShortcuts);
        if (!artistShortcuts.isEmpty()) recoverItems(service, browseLibraryCommand("artists"), artistShortcuts, List.of());
        if (!genreShortcuts.isEmpty()) recoverItems(service, browseLibraryCommand("genres"), genreShortcuts, List.of());
        if (!folderShortcuts.isEmpty()) recoverItems(service, browseLibraryCommand("bmf"), folderShortcuts, folderShortcuts);
    }

    private static void recoverItems(SqueezeService service, SlimCommand command, List<JiveItem> mainShortcuts, List<JiveItem> subShortCuts) {
        service.requestItems(command, new RecoverReceiver(service, mainShortcuts, subShortCuts));
    }

    private static SlimCommand browseLibraryCommand(String mode) {
        return new SlimCommand()
                .cmd("browselibrary", "items")
                .param("mode", mode)
                .param("menu", "1")
                .param("useContextMenu", "1");
    }

    private static int shortcutWeight(Action.JsonAction action) {
        for (String s : action.cmd) {
            if (allowMyMusic(s)) return CUSTOM_SHORTCUT_WEIGHT_MY_MUSIC;
            if (allowApps(s)) return CUSTOM_SHORTCUT_WEIGHT_APPS;
            if (allowRadio(s)) return CUSTOM_SHORTCUT_WEIGHT_RADIO;
        }
        return CUSTOM_SHORTCUT_WEIGHT_NOT_ALLOWED;
    }

    private static boolean allowMyMusic(String s) {
        return s.equals("browselibrary");
    }

    private static boolean allowApps(String s) {
        return s.equals("items");
    }

    private static boolean allowRadio(String s) {
        return s.equals("play");
    }

    private static class RecoverReceiver implements IServiceItemListCallback<JiveItem> {
        private final SqueezeService service;
        private final List<JiveItem> mainShortcuts;
        private final List<JiveItem> subShortCuts;

        public RecoverReceiver(SqueezeService service, List<JiveItem> mainShortcuts, List<JiveItem> subShortCuts) {
            this.service = service;
            this.mainShortcuts = mainShortcuts;
            this.subShortCuts = subShortCuts;
        }

        @Override
        public void onItemsReceived(int count, int start, Map<String, Object> parameters, List<JiveItem> items, Class<JiveItem> dataType) {
            if (!subShortCuts.isEmpty()) {
                for (JiveItem item : items) {
                    if (item.goAction != null && item.goAction.action != null) {
                        String albumId = (String) item.goAction.action.params.get("album_id");
                        if (albumId != null) recoverItems(service, browseLibraryCommand("tracks").param("album_id", albumId), subShortCuts, List.of());
                    }
                    if (item.moreAction != null && item.moreAction.action != null) {
                        String folderId = (String) item.moreAction.action.params.get("folder_id");
                        if (folderId != null) recoverItems(service, browseLibraryCommand("bmf").param("folder_id", folderId), subShortCuts, subShortCuts);
                    }
                }
            }
            for (JiveItem shortcut : mainShortcuts) {
                Optional<JiveItem> found = items.stream().filter(item -> shortcut.getName().equals(item.getName())).findFirst();
                if (found.isPresent()) {
                    Log.i(TAG, "shortcut '" + shortcut.getName() + "': HIT, item=" + found.get());
                    service.updateShortCut(shortcut, found.get().getRecord());
                }
            }
        }

        @Override
        public Object getClient() {
            return service;
        }
    }
}
