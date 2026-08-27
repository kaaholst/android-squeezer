package uk.org.ngo.squeezer.service;

import androidx.annotation.NonNull;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import uk.org.ngo.squeezer.SqueezerRepository;
import uk.org.ngo.squeezer.model.JiveItem;
import uk.org.ngo.squeezer.model.MenuStatusMessage;
import uk.org.ngo.squeezer.service.event.HomeMenuEvent;

public class HomeMenuHandling {
    private static final List<JiveItem> SPECIAL_NODES = List.of(JiveItem.EXTRAS, JiveItem.ARCHIVE, JiveItem.SHORTCUTS, JiveItem.SETTINGS, JiveItem.ADVANCED_SETTINGS);

    /**
     * Home menu tree as received from slimserver
     */
    private final List<JiveItem> homeMenu = new CopyOnWriteArrayList<>();

    private final Set<String> archivedItems = new HashSet<>();
    private final List<JiveItem> customShortcuts = new CopyOnWriteArrayList<>();
    private boolean grouped;

    public HomeMenuHandling(@NonNull SqueezerRepository repository) {
        this.repository = repository;
    }

    private final SqueezerRepository repository;

    private JiveItem customShortcutNode(boolean grouped) {
        return grouped ? JiveItem.SHORTCUTS : JiveItem.HOME;
    }

    private void setExtraNode(boolean grouped, JiveItem item) {
        item.setNode(grouped ? JiveItem.EXTRAS.getId() : item.getOriginalNode());
    }

    boolean isInArchive(JiveItem toggledItem) {
        return getParents(toggledItem.getNode()).contains(JiveItem.ARCHIVE) ? Boolean.TRUE : Boolean.FALSE;
    }

    private void cleanupArchive(JiveItem toggledItem) {
        for (JiveItem archiveItem : homeMenu) {
            if (archiveItem.getNode().equals(JiveItem.ARCHIVE.getId())) {
                Set<JiveItem> parents = getOriginalParents(archiveItem.getOriginalNode());
                if (parents.contains(toggledItem)) {
                    archivedItems.remove(archiveItem.getId());
                    archiveItem.setNode(archiveItem.getOriginalNode());
                }
            }
        }
    }

    public void handleMenuStatusEvent(MenuStatusMessage event) {
        for (JiveItem serverItem : event.menuItems) {
            Optional<JiveItem> item = homeMenu.stream().filter(clientItem -> serverItem.getId().equals(clientItem.getId())).findFirst();
            if (item.isPresent()) {
                homeMenu.remove(item.get());
                serverItem.setNode(item.get().getNode());  // for Archive
            }
            if (MenuStatusMessage.ADD.equals(event.menuDirective)) {
                homeMenu.add(serverItem);
            }
        }
        triggerHomeMenuEvent();
    }

    private void triggerHomeMenuEvent() {
        repository.post(new HomeMenuEvent(homeMenu));
    }

    Set<String> toggleArchiveItem(JiveItem toggledItem) {
        if (toggledItem.getNode().equals(JiveItem.ARCHIVE.getId())) {
            toggledItem.setNode(toggledItem.getOriginalNode());
            archivedItems.remove(toggledItem.getId());
        } else {
            cleanupArchive(toggledItem);
            toggledItem.setNode(JiveItem.ARCHIVE.getId());
            archivedItems.add(toggledItem.getId());
        }
        customizeHomeMenu();
        return archivedItems;
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

    private boolean hasNode(String node) {
        return homeMenu.stream().anyMatch(item -> item.getNode().equals(node));
    }

    private void setArchivedItems(Set<String> archivedItems) {
        this.archivedItems.clear();
        this.archivedItems.addAll(archivedItems);
    }

    public void updateArchivedItems(Set<String> archivedItems) {
        homeMenu.stream().filter(item -> archivedItems.contains(item.getId())).forEach(item -> item.setNode(item.getOriginalNode()));
        setArchivedItems(archivedItems);
        customizeHomeMenu();
    }

    private void setExtraItems() {
        homeMenu.stream()
                .filter(item -> (JiveItem.HOME.getId().equals(item.getOriginalNode()) && (item.doAction || item.hasInput())))
                .forEach(item -> setExtraNode(grouped, item));
    }

    public void setHomeMenu(List<JiveItem> items, Set<String> archivedItems, boolean grouped) {
        this.grouped = grouped;
        setArchivedItems(archivedItems);
        homeMenu.clear();
        homeMenu.addAll(items);
        homeMenu.addAll(customShortcuts);
        homeMenu.add(JiveItem.SETTINGS_PRESETS);
        customizeHomeMenu();
    }

    private void customizeHomeMenu() {
        setExtraItems();
        homeMenu.stream().filter(item -> archivedItems.contains(item.getId())).forEach(item -> item.setNode(JiveItem.ARCHIVE.getId()));
        SPECIAL_NODES.stream().forEach(item -> item.setNode(archivedItems.contains(item.getId()) ? JiveItem.ARCHIVE.getId() : item.getOriginalNode()));
        SPECIAL_NODES.stream().forEach(this::optionalNode);
        triggerHomeMenuEvent();
    }

    private void optionalNode(JiveItem jiveItem) {
        if (hasNode(jiveItem.getId())) {
            if (!homeMenu.contains(jiveItem)) homeMenu.add(jiveItem);
        } else {
            homeMenu.remove(jiveItem);
        }
    }

    public void setCustomShortcuts(boolean grouped, List<Map<String, Object>> shortcuts) {
        this.grouped = grouped;
        customShortcuts.clear();
        shortcuts.stream().forEach(shortcut -> customShortcuts.add(shortcut(shortcut)));
    }

    public List<JiveItem> getCustomShortcuts() {
        return customShortcuts;
    }

    public boolean isCustomShortcut(JiveItem item) {
        return customShortcuts.contains(item);
    }

    public void updateShortcuts(boolean grouped, List<Map<String, Object>> shortcuts) {
        customShortcuts.stream().forEach(homeMenu::remove);
        setCustomShortcuts(grouped, shortcuts);
        homeMenu.addAll(customShortcuts);
        customizeHomeMenu();
    }

    public boolean addShortcut(JiveItem item, JiveItem parent, int shortcutWeight) {
        if (shortcutAlreadyAdded(item)) return false;
        addShortcut(item.getRecord(), parent, shortcutWeight);
        triggerHomeMenuEvent();
        return true;
    }

    public List<JiveItem> updateShortcut(JiveItem item, Map<String, Object> record) {
        removeCustomShortcut(item);
        addShortcut(record, item, item.getWeight());
        triggerHomeMenuEvent();
        return customShortcuts;
    }

    public void removeShortcut(JiveItem item) {
        removeCustomShortcut(item);
        triggerHomeMenuEvent();
    }

    public void removeAllShortcuts() {
        customShortcuts.stream().forEach(homeMenu::remove);
        customizeHomeMenu();
    }

    private boolean shortcutAlreadyAdded(JiveItem itemToShortcut) {
        return customShortcuts.stream()
                .filter(item -> item.getName().equals(itemToShortcut.getName()))
                .findFirst()
                .isPresent();
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
        optionalNode(JiveItem.SHORTCUTS);
    }

    private JiveItem shortcut(Map<String, Object> shortcut) {
        JiveItem item = new JiveItem(shortcut);
        item.setNode(customShortcutNode(grouped).getId());
        if (item.getId() == null) item.setId("customShortcut_" + customShortcuts.size());
        return item;
    }

    private void removeCustomShortcut(JiveItem item) {
        customShortcuts.remove(item);
        homeMenu.remove(item);
        optionalNode(JiveItem.SHORTCUTS);
    }
}
