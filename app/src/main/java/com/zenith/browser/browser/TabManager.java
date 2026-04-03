package com.zenith.browser.browser;

import android.content.Context;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TabManager {

    public interface TabManagerListener {
        void onTabAdded(BrowserTab tab);
        void onTabRemoved(BrowserTab tab);
        void onTabSelected(BrowserTab tab);
        void onAllTabsClosed();
    }

    private final List<BrowserTab> tabs = new ArrayList<>();
    private BrowserTab activeTab;
    private final Context context;
    private TabManagerListener listener;
    private int tabCounter = 0;

    // Tab groups: name -> color
    private final Map<String, Integer> tabGroups = new HashMap<>();

    public TabManager(Context context) {
        this.context = context;
    }

    public void setListener(TabManagerListener listener) {
        this.listener = listener;
    }

    public BrowserTab createTab(BrowserTab.TabType type, BrowserTab.TabListener tabListener) {
        tabCounter++;
        BrowserTab tab = new BrowserTab(context, type, tabListener);
        tab.getWebView().setTag(tab);
        tabs.add(tab);

        if (type == BrowserTab.TabType.NEW_TAB) {
            tab.loadUrl("zenith://newtab");
        } else if (type == BrowserTab.TabType.INCOGNITO) {
            tab.loadUrl("zenith://newtab");
        }

        if (listener != null) listener.onTabAdded(tab);
        return tab;
    }

    public void selectTab(BrowserTab tab) {
        if (tab == null || !tabs.contains(tab)) return;

        if (activeTab != null && activeTab != tab) {
            activeTab.onPause();
        }

        activeTab = tab;
        activeTab.onResume();

        if (listener != null) listener.onTabSelected(tab);
    }

    public void closeTab(BrowserTab tab) {
        if (tab == null) return;

        int index = tabs.indexOf(tab);
        if (index < 0) return;

        tabs.remove(index);
        tab.destroy();

        if (activeTab == tab) {
            if (tabs.isEmpty()) {
                activeTab = null;
                if (listener != null) listener.onAllTabsClosed();
            } else {
                int newIndex = Math.min(index, tabs.size() - 1);
                selectTab(tabs.get(newIndex));
            }
        }

        if (listener != null) listener.onTabRemoved(tab);

        // Clean up empty groups
        cleanupEmptyGroups();
    }

    public void closeAllTabs() {
        Iterator<BrowserTab> iterator = tabs.iterator();
        while (iterator.hasNext()) {
            BrowserTab tab = iterator.next();
            tab.destroy();
            iterator.remove();
        }
        activeTab = null;
        tabGroups.clear();
        if (listener != null) listener.onAllTabsClosed();
    }

    // ==================== Tab Group Support ====================

    /**
     * Get all group names.
     */
    public Set<String> getGroupNames() {
        return new LinkedHashSet<>(tabGroups.keySet());
    }

    /**
     * Create a new tab group with the given name.
     * Returns the assigned color.
     */
    public int createGroup(String name) {
        int colorIndex = tabGroups.size() % BrowserTab.GROUP_COLORS.length;
        int color = BrowserTab.GROUP_COLORS[colorIndex];
        tabGroups.put(name, color);
        return color;
    }

    /**
     * Remove a tab group (ungroup all its tabs).
     */
    public void removeGroup(String name) {
        for (BrowserTab tab : tabs) {
            if (name.equals(tab.getGroupName())) {
                tab.setGroupName(null);
            }
        }
        tabGroups.remove(name);
    }

    /**
     * Assign a tab to a group.
     */
    public void assignTabToGroup(BrowserTab tab, String groupName) {
        if (groupName == null || groupName.isEmpty()) {
            tab.setGroupName(null);
            return;
        }
        if (!tabGroups.containsKey(groupName)) {
            createGroup(groupName);
        }
        tab.setGroupName(groupName);
        tab.setGroupColor(tabGroups.get(groupName));
    }

    /**
     * Get all tabs in a specific group.
     */
    public List<BrowserTab> getTabsInGroup(String groupName) {
        List<BrowserTab> result = new ArrayList<>();
        for (BrowserTab tab : tabs) {
            if (groupName.equals(tab.getGroupName())) {
                result.add(tab);
            }
        }
        return result;
    }

    /**
     * Get tabs that are not in any group.
     */
    public List<BrowserTab> getUngroupedTabs() {
        List<BrowserTab> result = new ArrayList<>();
        for (BrowserTab tab : tabs) {
            if (!tab.isInGroup()) {
                result.add(tab);
            }
        }
        return result;
    }

    /**
     * Rename a group.
     */
    public void renameGroup(String oldName, String newName) {
        Integer color = tabGroups.get(oldName);
        if (color == null) return;
        tabGroups.remove(oldName);
        tabGroups.put(newName, color);
        for (BrowserTab tab : tabs) {
            if (oldName.equals(tab.getGroupName())) {
                tab.setGroupName(newName);
            }
        }
    }

    /**
     * Get the color for a group.
     */
    public int getGroupColor(String name) {
        Integer color = tabGroups.get(name);
        return color != null ? color : BrowserTab.GROUP_COLORS[0];
    }

    /**
     * Change group color.
     */
    public void setGroupColor(String name, int color) {
        tabGroups.put(name, color);
        for (BrowserTab tab : tabs) {
            if (name.equals(tab.getGroupName())) {
                tab.setGroupColor(color);
            }
        }
    }

    private void cleanupEmptyGroups() {
        Set<String> nonEmptyGroups = new LinkedHashSet<>();
        for (BrowserTab tab : tabs) {
            if (tab.isInGroup()) {
                nonEmptyGroups.add(tab.getGroupName());
            }
        }
        tabGroups.keySet().retainAll(nonEmptyGroups);
    }

    // ==================== Core Methods ====================

    public BrowserTab getActiveTab() { return activeTab; }
    public List<BrowserTab> getTabs() { return new ArrayList<>(tabs); }
    public int getTabCount() { return tabs.size(); }

    public BrowserTab getTabAt(int position) {
        if (position >= 0 && position < tabs.size()) return tabs.get(position);
        return null;
    }

    public void saveState(Bundle outState) {
        outState.putInt("tab_count", tabs.size());
        for (int i = 0; i < tabs.size(); i++) {
            BrowserTab tab = tabs.get(i);
            outState.putString("tab_" + i + "_url", tab.getUrl());
            outState.putString("tab_" + i + "_title", tab.getTitle());
            outState.putString("tab_" + i + "_type", tab.getType().name());
            outState.putString("tab_" + i + "_group", tab.getGroupName() != null ? tab.getGroupName() : "");
        }
        if (activeTab != null) {
            outState.putString("active_tab_id", activeTab.getId());
        }
        // Save groups
        int gIdx = 0;
        for (Map.Entry<String, Integer> entry : tabGroups.entrySet()) {
            outState.putString("group_" + gIdx + "_name", entry.getKey());
            outState.putInt("group_" + gIdx + "_color", entry.getValue());
            gIdx++;
        }
        outState.putInt("group_count", gIdx);
    }

    public void restoreState(Bundle savedInstanceState, BrowserTab.TabListener tabListener) {
        int count = savedInstanceState.getInt("tab_count", 0);
        String activeId = savedInstanceState.getString("active_tab_id");

        // Restore groups first
        int groupCount = savedInstanceState.getInt("group_count", 0);
        for (int i = 0; i < groupCount; i++) {
            String gName = savedInstanceState.getString("group_" + i + "_name", "");
            int gColor = savedInstanceState.getInt("group_" + i + "_color", BrowserTab.GROUP_COLORS[0]);
            if (!gName.isEmpty()) {
                tabGroups.put(gName, gColor);
            }
        }

        BrowserTab firstTab = null;
        for (int i = 0; i < count; i++) {
            String url = savedInstanceState.getString("tab_" + i + "_url", "zenith://newtab");
            String title = savedInstanceState.getString("tab_" + i + "_title", "");
            String typeName = savedInstanceState.getString("tab_" + i + "_type", "NORMAL");
            String group = savedInstanceState.getString("tab_" + i + "_group", "");

            BrowserTab.TabType type = BrowserTab.TabType.NORMAL;
            try { type = BrowserTab.TabType.valueOf(typeName); } catch (Exception ignored) {}

            BrowserTab tab = createTab(type, tabListener);
            if (i == 0) firstTab = tab;
            tab.loadUrl(url);

            // Restore group assignment
            if (!group.isEmpty() && tabGroups.containsKey(group)) {
                tab.setGroupName(group);
                tab.setGroupColor(tabGroups.get(group));
            }
        }

        if (activeId != null) {
            for (BrowserTab tab : tabs) {
                if (tab.getId().equals(activeId)) {
                    selectTab(tab);
                    return;
                }
            }
        }
        if (firstTab != null) selectTab(firstTab);
    }
}
