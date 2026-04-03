package com.zenith.browser.browser;

import android.content.Context;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

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

        // Pause previous tab
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

        // Select adjacent tab
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
    }

    public void closeAllTabs() {
        Iterator<BrowserTab> iterator = tabs.iterator();
        while (iterator.hasNext()) {
            BrowserTab tab = iterator.next();
            tab.destroy();
            iterator.remove();
        }
        activeTab = null;
        if (listener != null) listener.onAllTabsClosed();
    }

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
        }
        if (activeTab != null) {
            outState.putString("active_tab_id", activeTab.getId());
        }
    }

    public void restoreState(Bundle savedInstanceState, BrowserTab.TabListener tabListener) {
        int count = savedInstanceState.getInt("tab_count", 0);
        String activeId = savedInstanceState.getString("active_tab_id");

        BrowserTab firstTab = null;
        for (int i = 0; i < count; i++) {
            String url = savedInstanceState.getString("tab_" + i + "_url", "zenith://newtab");
            String title = savedInstanceState.getString("tab_" + i + "_title", "");
            String typeName = savedInstanceState.getString("tab_" + i + "_type", "NORMAL");

            BrowserTab.TabType type = BrowserTab.TabType.NORMAL;
            try { type = BrowserTab.TabType.valueOf(typeName); } catch (Exception ignored) {}

            BrowserTab tab = createTab(type, tabListener);
            if (i == 0) firstTab = tab;
            tab.loadUrl(url);
        }

        // Select the active tab
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
