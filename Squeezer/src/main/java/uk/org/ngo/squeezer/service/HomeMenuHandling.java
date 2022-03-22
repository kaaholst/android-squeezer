package uk.org.ngo.squeezer.service;

import android.util.Log;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import org.greenrobot.eventbus.EventBus;
import uk.org.ngo.squeezer.model.JiveItem;
import uk.org.ngo.squeezer.model.MenuStatusMessage;
import uk.org.ngo.squeezer.service.event.HomeMenuEvent;

public class HomeMenuHandling {


    private static final String TAG = "HomeMenuHandling";

    /**
     * Home menu tree as received from slimserver
     */
    public final List<JiveItem> homeMenu = new Vector<>();
    public CopyOnWriteArrayList<JiveItem> customShortcuts = new CopyOnWriteArrayList<>();

    public HomeMenuHandling(@NonNull EventBus eventBus) {
        mEventBus = eventBus;
    }

    private final EventBus mEventBus;

    boolean isInArchive(JiveItem toggledItem) {
        return getParents(toggledItem.getNode()).contains(JiveItem.ARCHIVE) ? Boolean.TRUE : Boolean.FALSE;
    }

    void cleanupArchive(JiveItem toggledItem) {
        for (JiveItem archiveItem : homeMenu) {
            if (archiveItem.getNode().equals(JiveItem.ARCHIVE.getId())) {
                Set<JiveItem> parents = getOriginalParents(archiveItem.getOriginalNode());
                if (parents.contains(toggledItem)) {
                    archiveItem.setNode(archiveItem.getOriginalNode());
                }
            }
        }
    }

    public void handleMenuStatusEvent(MenuStatusMessage event) {
        for (JiveItem serverItem : event.menuItems) {
            JiveItem item = null;
            for (JiveItem clientItem : homeMenu) {
                if (serverItem.getId().equals(clientItem.getId())) {
                    item = clientItem;
                    break;
                }
            }
            if (item != null) {
                homeMenu.remove(item);
                serverItem.setNode(item.getNode());  // for Archive
            }
            if (MenuStatusMessage.ADD.equals(event.menuDirective)) {
                homeMenu.add(serverItem);
            }
        }
        mEventBus.postSticky(new HomeMenuEvent(homeMenu));
    }

    public void triggerHomeMenuEvent() {
        mEventBus.postSticky(new HomeMenuEvent(homeMenu));
    }

    List<String> toggleArchiveItem(JiveItem toggledItem) {
        if (toggledItem.getNode().equals(JiveItem.ARCHIVE.getId())) {
            toggledItem.setNode(toggledItem.getOriginalNode());
            List<String> archivedItems = getArchivedItems();
            if (archivedItems.isEmpty()) {
                homeMenu.remove(JiveItem.ARCHIVE);
            }
            return archivedItems;
        }

        cleanupArchive(toggledItem);
        toggledItem.setNode(JiveItem.ARCHIVE.getId());
        if (!homeMenu.contains(JiveItem.ARCHIVE)) {
            homeMenu.add(JiveItem.ARCHIVE);
            mEventBus.postSticky(new HomeMenuEvent(homeMenu));
        }
        return getArchivedItems();
    }

    public Set<JiveItem> getOriginalParents(String node) {
        Set<JiveItem> parents = new HashSet<>();
        getParents(node, parents, JiveItem::getOriginalNode);
        return parents;
    }

    private Set<JiveItem> getParents(String node) {
        Set<JiveItem> parents = new HashSet<>();
        getParents(node, parents, JiveItem::getNode);
        return parents;
    }

    private void getParents(String node, Set<JiveItem> parents, Function<JiveItem, String> getParent) {
        if (node == null || node.equals(JiveItem.HOME.getId())) {          // if we are done
            return;
        }
        for (JiveItem menuItem : homeMenu) {
            if (menuItem.getId().equals(node)) {
                String parent = getParent.apply(menuItem);
                parents.add(menuItem);
                getParents(parent, parents, getParent);
            }
        }
    }

    public List<String> getArchivedItems() {
        List<String> archivedItems = new ArrayList<>();
        for (JiveItem item : homeMenu) {
            if (item.getNode().equals(JiveItem.ARCHIVE.getId())) {
                archivedItems.add(item.getId());
            }
        }
        return archivedItems;
    }

    private void addArchivedItems(List<String> archivedItems) {
        if (!(archivedItems.isEmpty()) && (!homeMenu.contains(JiveItem.ARCHIVE))) {
            homeMenu.add(JiveItem.ARCHIVE);
        }
        for (String s : archivedItems) {
            for (JiveItem item : homeMenu) {
                if (item.getId().equals(s)) {
                    item.setNode(JiveItem.ARCHIVE.getId());
                }
            }
        }
    }

    public void setHomeMenu(List<String> archivedItems, Map<String, Map<String, Object>> customShortcuts) {
        homeMenu.remove(JiveItem.ARCHIVE);
        homeMenu.stream().forEach(item -> item.setNode(item.getOriginalNode()));
        addArchivedItems(archivedItems);
        loadShortcutItems(customShortcuts);
        mEventBus.postSticky(new HomeMenuEvent(homeMenu));
    }

    public void setHomeMenu(List<JiveItem> items, List<String> archivedItems, Map<String, Map<String, Object>> customShortcuts) {
        jiveMainNodes(items);
        homeMenu.clear();
        homeMenu.addAll(items);
        addArchivedItems(archivedItems);
        loadShortcutItems(customShortcuts);
        mEventBus.postSticky(new HomeMenuEvent(homeMenu));
    }

    private void jiveMainNodes(List<JiveItem> homeMenu) {
        addNode(JiveItem.EXTRAS, homeMenu);
        addNode(JiveItem.SETTINGS, homeMenu);
        addNode(JiveItem.ADVANCED_SETTINGS, homeMenu);
    }

    void addNode(JiveItem jiveItem, List<JiveItem> homeMenu) {
        if (!homeMenu.contains(jiveItem)) {
            jiveItem.setNode(jiveItem.getOriginalNode());
            homeMenu.add(jiveItem);
        }
    }

    private final static String CUSTOM_SHORTCUT_NODE = "home";

    /**
     * Load complete list of stored items from preferences.
     * Use action the values on the initialized customNodes.
     */
    public void loadShortcutItems(Map<String, Map<String, Object>> map) {
        customShortcuts.clear();
        for (Map.Entry<String, Map<String, Object>> pair : map.entrySet()) {
            Map<String, Object> record = pair.getValue();
            JiveItem shortcut = new JiveItem(record);
            shortcut.setName(pair.getKey());
            customShortcuts.add(setShortcut(shortcut));
        }
        homeMenu.addAll(customShortcuts);
    }

    public boolean triggerCustomShortcut(JiveItem itemToShortcut) {
        return addShortcut(itemToShortcut);
    }

    private boolean addShortcut(JiveItem item) {
        if (!shortcutAlreadyAdded(item)) {
            JiveItem template = new JiveItem(item.getRecord());
//            TODO template.setIcon
            customShortcuts.add(setShortcut(template));
            homeMenu.add(template);
        } else {
            return false;
        }
        return true;
    }

    private boolean shortcutAlreadyAdded(JiveItem itemToShortcut) {
        for (JiveItem item : customShortcuts) {
            if (item.getName().equals(itemToShortcut.getName())) {
                return true;
            }
        }
        return false;
    }

    private JiveItem setShortcut(JiveItem item) {
        item.setNode(CUSTOM_SHORTCUT_NODE);
        item.setId("customShortcut_" + customShortcuts.size());
        return item;
    }

    public void removeCustomShortcut(JiveItem item) {
        customShortcuts.remove(item);
        homeMenu.remove(item);
    }

    public void removeAllShortcuts() {
        for (JiveItem item : customShortcuts) {
            customShortcuts.remove(item);
            homeMenu.remove(item);
        }
    }
}
