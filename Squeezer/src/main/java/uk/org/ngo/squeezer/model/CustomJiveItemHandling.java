package uk.org.ngo.squeezer.model;

import java.util.HashMap;
import java.util.Map;

import uk.org.ngo.squeezer.itemlist.JiveItemListActivity;
import uk.org.ngo.squeezer.service.HomeMenuHandling;
import uk.org.ngo.squeezer.service.ISqueezeService;

public class CustomJiveItemHandling {

    public int ADJUSTED_CUSTOM_SHORTCUT_WEIGHT = 2000;
    final JiveItemListActivity mActivity;

    public CustomJiveItemHandling(JiveItemListActivity activity) {
        mActivity = activity;
    }

    private ISqueezeService service() {
        return mActivity.getService();
    }

    private HomeMenuHandling homeMenuHandling() {
        return service().getDelegate().getHomeMenuHandling();
    }
    /**
     * Send long pressed JiveItem
     */
    public boolean triggerCustomShortcut(JiveItem item) {
        item.appendWeight(ADJUSTED_CUSTOM_SHORTCUT_WEIGHT);
        return homeMenuHandling().triggerCustomShortcut(item);
    }

    public boolean isCustomShortcut(JiveItem item) {
        return homeMenuHandling().customShortcuts.contains(item);
    }

    public boolean isShortcutable(JiveItem item) {
        //  TODO add better check for fitting items
        //  TODO "All titles" is a name that comes up in several occations and will then not be updated
        if ((item.nextWindow != null) || (item.goAction == null)) {
            return false;
        }
        for (String s : item.goAction.action.cmd) {
            if (allowMyMusic(s) || allowApps(s) || allowRadio(s)) {
                return true;
            }
        }
    return false;
    }

    private boolean allowMyMusic(String s) {
        if (s.equals("browselibrary")) {
            ADJUSTED_CUSTOM_SHORTCUT_WEIGHT = 2000;
            return true;
        }
        return false;
    }

    private boolean allowApps(String s) {
        if (s.equals("items")) {
            ADJUSTED_CUSTOM_SHORTCUT_WEIGHT = 2010;
            return true;
        }
        return false;
    }

    private boolean allowRadio(String s) {
        if (s.equals("play")) {
            ADJUSTED_CUSTOM_SHORTCUT_WEIGHT = 2020;
            return true;
        }
        return false;
    }

    public Map<String, Object> convertShortcuts() {
        Map<String, Object> map = new HashMap<>();
        for (JiveItem item : homeMenuHandling().customShortcuts) {
            map.put(item.getName(), item.getRecord());
        }
        return map;
    }
}

