package com.zenith.browser.browser;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class SearchEngine {

    public enum Engine {
        GOOGLE("Google", "https://www.google.com/search?q=%s"),
        BING("Bing", "https://www.bing.com/search?q=%s"),
        DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q=%s"),
        YAHOO("Yahoo", "https://search.yahoo.com/search?p=%s"),
        BRAVE("Brave", "https://search.brave.com/search?q=%s"),
        STARTPAGE("Startpage", "https://www.startpage.com/sp/search?query=%s"),
        YANDEX("Yandex", "https://yandex.com/search/?text=%s"),
        BAIDU("Baidu", "https://www.baidu.com/s?wd=%s");

        private final String name;
        private final String searchUrl;

        Engine(String name, String searchUrl) {
            this.name = name;
            this.searchUrl = searchUrl;
        }

        public String getName() { return name; }
        public String getSearchUrl() { return searchUrl; }
    }

    private static Engine currentEngine = Engine.GOOGLE;

    public static void setEngine(Engine engine) {
        currentEngine = engine;
    }

    public static Engine getEngine() {
        return currentEngine;
    }

    public static String search(String query) {
        if (query == null || query.trim().isEmpty()) return getHomepage();
        try {
            return String.format(currentEngine.getSearchUrl(),
                URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name()));
        } catch (UnsupportedEncodingException e) {
            return String.format(currentEngine.getSearchUrl(), query.trim());
        }
    }

    public static String getHomepage() {
        return "zenith://newtab";
    }

    public static String[] getEngineNames() {
        Engine[] engines = Engine.values();
        String[] names = new String[engines.length];
        for (int i = 0; i < engines.length; i++) {
            names[i] = engines[i].getName();
        }
        return names;
    }

    public static Engine getEngineByName(String name) {
        for (Engine engine : Engine.values()) {
            if (engine.getName().equalsIgnoreCase(name)) return engine;
        }
        return Engine.GOOGLE;
    }
}
