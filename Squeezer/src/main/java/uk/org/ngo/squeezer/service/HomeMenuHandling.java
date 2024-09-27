package uk.org.ngo.squeezer.service;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import org.greenrobot.eventbus.EventBus;

import uk.org.ngo.squeezer.model.JiveItem;
import uk.org.ngo.squeezer.model.MenuStatusMessage;
import uk.org.ngo.squeezer.service.event.HomeMenuEvent;

public class HomeMenuHandling {
    private static final String CUSTOM_SHORTCUT_NODE = JiveItem.HOME.getId();

    /**
     * Home menu tree as received from slimserver
     */
    private final List<JiveItem> homeMenu = new CopyOnWriteArrayList<>();
    private final List<JiveItem> customShortcuts = new CopyOnWriteArrayList<>();

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
        triggerHomeMenuEvent();
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
            triggerHomeMenuEvent();
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

    public void setHomeMenu(List<String> archivedItems) {
        homeMenu.remove(JiveItem.ARCHIVE);
        homeMenu.stream().forEach(item -> item.setNode(item.getOriginalNode()));
        customizeHomeMenu(archivedItems);
    }

    public void setHomeMenu(List<JiveItem> items, List<String> archivedItems) {
        homeMenu.clear();
        homeMenu.addAll(items);
        jiveMainNodes();
        customizeHomeMenu(archivedItems);
    }

    private void customizeHomeMenu(List<String> archivedItems) {
        addArchivedItems(archivedItems);
        homeMenu.addAll(customShortcuts);
        triggerHomeMenuEvent();
    }

    private void jiveMainNodes() {
        addNode(JiveItem.EXTRAS, homeMenu);
        addNode(JiveItem.SETTINGS, homeMenu);
        addNode(JiveItem.ADVANCED_SETTINGS, homeMenu);
    }

    private void addNode(JiveItem jiveItem, List<JiveItem> homeMenu) {
        if (!homeMenu.contains(jiveItem)) {
            jiveItem.setNode(jiveItem.getOriginalNode());
            homeMenu.add(jiveItem);
        }
    }

    public void setCustomShortcuts(List<Map<String, Object>> shortcuts) {
        customShortcuts.clear();
        shortcuts.stream().forEach(shortcut -> customShortcuts.add(shortcut(shortcut)));
    }

    public List<JiveItem> getCustomShortcuts() {
        return customShortcuts;
    }

    public boolean isCustomShortcut(JiveItem item) {
        return customShortcuts.contains(item);
    }

    public boolean addShortcut(JiveItem item, JiveItem parent, int shortcutWeight) {
        if (shortcutAlreadyAdded(item)) return false;
        addShortcut(item.getRecord(), parent, shortcutWeight);
        return true;
    }

    public List<JiveItem> updateShortcut(JiveItem item, Map<String, Object> record) {
        removeCustomShortcut(item);
        addShortcut(record, item, item.getWeight());
        triggerHomeMenuEvent();
        return customShortcuts;
    }

    private void addShortcut(Map<String, Object> record, JiveItem parent, int shortcutWeight) {
        record.put("weight", shortcutWeight);
        JiveItem template = shortcut(record);
        if (!template.hasIcon() && parent != null && parent.hasIcon()) {
            if (parent.hasIconUri()) {
                record.put("icon", parent.getIcon().toString());
            } else {
                record.put("id", parent.getId());
            }
            template = shortcut(record);
        }
        customShortcuts.add(template);
        homeMenu.add(template);
    }

    private boolean shortcutAlreadyAdded(JiveItem itemToShortcut) {
        for (JiveItem item : customShortcuts) {
            if (item.getName().equals(itemToShortcut.getName())) return true;
        }
        return false;
    }

    private JiveItem shortcut(Map<String, Object> shortcut) {
        JiveItem item = new JiveItem(shortcut);
        item.setNode(CUSTOM_SHORTCUT_NODE);
        if (item.getId() == null) item.setId("customShortcut_" + customShortcuts.size());
        return item;
    }

    public void removeCustomShortcut(JiveItem item) {
        customShortcuts.remove(item);
        homeMenu.remove(item);
    }

    public void removeAllShortcuts() {
        for (JiveItem item : customShortcuts) removeCustomShortcut(item);
    }
}
