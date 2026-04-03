package com.zenith.browser.extensions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ChromeExtension {

    private final File extensionDir;
    private JSONObject manifest;
    private boolean enabled = true;
    private long installedAt;

    // Manifest fields
    private String name = "";
    private String version = "1.0.0";
    private String description = "";
    private String author = "";
    private String defaultLocale = "";
    private int manifestVersion = 2;
    private String permissions = "";
    private String contentScripts = "";
    private String backgroundScript = "";
    private String popupHtml = "";
    private String popupJs = "";
    private String iconPath = "";
    private List<ContentScript> contentScriptList = new ArrayList<>();

    public ChromeExtension(File extensionDir) {
        this.extensionDir = extensionDir;
        this.installedAt = System.currentTimeMillis();
    }

    public boolean loadManifest() {
        File manifestFile = new File(extensionDir, "manifest.json");
        if (!manifestFile.exists()) return false;
        try {
            String content = FileUtils.readFileToString(manifestFile);
            manifest = new JSONObject(content);
            parseManifest();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private void parseManifest() throws Exception {
        name = manifest.optString("name", extensionDir.getName());
        version = manifest.optString("version", "1.0.0");
        description = manifest.optString("description", "");
        author = manifest.optString("author", "");
        manifestVersion = manifest.optInt("manifest_version", 2);

        // Permissions
        JSONArray permsArray = manifest.optJSONArray("permissions");
        if (permsArray != null) {
            permissions = permsArray.join(",");
        }

        // Content scripts
        JSONArray csArray = manifest.optJSONArray("content_scripts");
        if (csArray != null) {
            for (int i = 0; i < csArray.length(); i++) {
                JSONObject csObj = csArray.getJSONObject(i);
                ContentScript cs = new ContentScript();
                JSONArray matches = csObj.optJSONArray("matches");
                if (matches != null) {
                    cs.matches = new String[matches.length()];
                    for (int j = 0; j < matches.length(); j++) {
                        cs.matches[j] = matches.getString(j);
                    }
                }
                JSONArray jsFiles = csObj.optJSONArray("js");
                if (jsFiles != null) {
                    cs.jsFiles = new String[jsFiles.length()];
                    for (int j = 0; j < jsFiles.length(); j++) {
                        cs.jsFiles[j] = jsFiles.getString(j);
                    }
                }
                JSONArray cssFiles = csObj.optJSONArray("css");
                if (cssFiles != null) {
                    cs.cssFiles = new String[cssFiles.length()];
                    for (int j = 0; j < cssFiles.length(); j++) {
                        cs.cssFiles[j] = cssFiles.getString(j);
                    }
                }
                cs.runAt = csObj.optString("run_at", "document_idle");
                contentScriptList.add(cs);
            }
        }

        // Background script
        JSONObject background = manifest.optJSONObject("background");
        if (background != null) {
            if (manifestVersion == 3) {
                JSONArray serviceWorkers = background.optJSONArray("service_worker");
                if (serviceWorkers != null && serviceWorkers.length() > 0) {
                    backgroundScript = serviceWorkers.getString(0);
                } else {
                    backgroundScript = background.optString("service_worker", "");
                }
            } else {
                JSONArray bgScripts = background.optJSONArray("scripts");
                if (bgScripts != null && bgScripts.length() > 0) {
                    backgroundScript = bgScripts.getString(0);
                } else {
                    backgroundScript = background.optString("scripts", "");
                }
            }
        }

        // Browser action / Action (popup)
        JSONObject action = manifest.optJSONObject("action");
        if (action == null) action = manifest.optJSONObject("browser_action");
        if (action != null) {
            popupHtml = action.optString("default_popup", "");
            JSONObject iconObj = action.optJSONObject("default_icon");
            if (iconObj != null) {
                iconPath = iconObj.optString("128", iconObj.optString("48", ""));
            }
        }

        // Icons
        JSONObject icons = manifest.optJSONObject("icons");
        if (icons != null && iconPath.isEmpty()) {
            iconPath = icons.optString("128", icons.optString("48", icons.optString("32", "")));
        }
    }

    public String getScriptForInjection(String url) {
        StringBuilder sb = new StringBuilder();
        for (ContentScript cs : contentScriptList) {
            if (cs.matchesUrl(url)) {
                // Inject CSS
                for (String css : cs.cssFiles) {
                    String cssContent = readFile(css);
                    if (cssContent != null) {
                        sb.append("(function(){var s=document.createElement('style');s.textContent=`").append(escapeJs(cssContent)).append("`;document.head.appendChild(s);})();\n");
                    }
                }
                // Inject JS
                for (String js : cs.jsFiles) {
                    String jsContent = readFile(js);
                    if (jsContent != null) {
                        sb.append(jsContent).append("\n");
                    }
                }
            }
        }
        return sb.toString();
    }

    private String readFile(String relativePath) {
        File file = new File(extensionDir, relativePath);
        return FileUtils.readFileToString(file);
    }

    private String escapeJs(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("`", "\\`").replace("$", "\\$");
    }

    public String getPopupHtmlContent() {
        if (popupHtml.isEmpty()) return null;
        return readFile(popupHtml);
    }

    public File getIconFile() {
        if (iconPath.isEmpty()) return null;
        File icon = new File(extensionDir, iconPath);
        return icon.exists() ? icon : null;
    }

    public String getBackgroundScriptContent() {
        if (backgroundScript.isEmpty()) return null;
        return readFile(backgroundScript);
    }

    // Getters
    public File getExtensionDir() { return extensionDir; }
    public String getName() { return name; }
    public String getVersion() { return version; }
    public String getDescription() { return description; }
    public String getAuthor() { return author; }
    public int getManifestVersion() { return manifestVersion; }
    public String getPermissions() { return permissions; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public long getInstalledAt() { return installedAt; }
    public List<ContentScript> getContentScriptList() { return contentScriptList; }
    public JSONObject getManifest() { return manifest; }

    public static class ContentScript {
        public String[] matches = {};
        public String[] jsFiles = {};
        public String[] cssFiles = {};
        public String runAt = "document_idle";

        public boolean matchesUrl(String url) {
            if (matches == null || matches.length == 0) return false;
            for (String pattern : matches) {
                if (matchPattern(pattern, url)) return true;
            }
            return false;
        }

        private boolean matchPattern(String pattern, String url) {
            // Simple pattern matching: <scheme>://<host><path>
            // Supports wildcards: * in scheme, host, path
            pattern = pattern.replace("*", ".*");
            try {
                return url.matches(pattern);
            } catch (Exception e) {
                return false;
            }
        }
    }
}
