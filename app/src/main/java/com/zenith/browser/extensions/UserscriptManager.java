package com.zenith.browser.extensions;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tampermonkey-like userscript manager for Zenith Browser.
 * Supports Greasemonkey/Tampermonkey userscript format with @match, @include, @exclude, @grant, @require etc.
 */
public class UserscriptManager {

    private static final String TAG = "UserscriptManager";
    private static final String SCRIPTS_DIR = "Userscripts";
    private static UserscriptManager instance;

    private final Context context;
    private final File scriptsRoot;
    private final List<Userscript> scripts = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface UserscriptListener {
        void onScriptInstalled(Userscript script);
        void onScriptError(String error);
        void onScriptRemoved(Userscript script);
        void onScriptUpdated(Userscript script);
    }

    private UserscriptListener listener;

    public UserscriptManager(Context context) {
        this.context = context.getApplicationContext();
        this.scriptsRoot = new File(this.context.getFilesDir(), SCRIPTS_DIR);
        scriptsRoot.mkdirs();
        instance = this;
        loadAllScripts();
    }

    public static UserscriptManager getInstance() {
        return instance;
    }

    public static UserscriptManager getInstance(Context context) {
        if (instance == null) {
            instance = new UserscriptManager(context);
        }
        return instance;
    }

    public void setListener(UserscriptListener listener) {
        this.listener = listener;
    }

    private void loadAllScripts() {
        File[] files = scriptsRoot.listFiles((dir, name) -> name.endsWith(".user.js"));
        if (files == null) return;
        for (File file : files) {
            try {
                Userscript script = parseUserscript(file);
                if (script != null) {
                    scripts.add(script);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to load userscript: " + file.getName(), e);
            }
        }
    }

    /**
     * Parse a userscript file into a Userscript object.
     */
    private Userscript parseUserscript(File file) throws Exception {
        String content = FileUtils.readFileToString(file);
        if (content == null || content.isEmpty()) return null;

        Userscript script = new Userscript();
        script.file = file;
        script.content = content;
        script.enabled = true;
        script.installedAt = file.lastModified();

        // Parse metadata block
        Pattern metaPattern = Pattern.compile("//\\s*==UserScript==([\\s\\S]*?)//\\s*==/UserScript==");
        Matcher matcher = metaPattern.matcher(content);
        if (matcher.find()) {
            String metaBlock = matcher.group(1);
            script.name = extractMeta(metaBlock, "name");
            if (script.name == null || script.name.isEmpty()) script.name = file.getName().replace(".user.js", "");
            script.version = extractMeta(metaBlock, "version");
            if (script.version == null) script.version = "1.0";
            script.description = extractMeta(metaBlock, "description");
            script.author = extractMeta(metaBlock, "author");
            script.namespace = extractMeta(metaBlock, "namespace");
            script.homepage = extractMeta(metaBlock, "homepage");
            script.updateURL = extractMeta(metaBlock, "updateURL");
            script.downloadURL = extractMeta(metaBlock, "downloadURL");

            // @match patterns
            script.matches = extractMetaList(metaBlock, "match");
            // @include patterns
            script.includes = extractMetaList(metaBlock, "include");
            // @exclude patterns
            script.excludes = extractMetaList(metaBlock, "exclude");
            // @grant
            script.grants = extractMetaList(metaBlock, "grant");
            // @require (external scripts)
            script.requires = extractMetaList(metaBlock, "require");
            // @resource
            script.resources = extractMetaList(metaBlock, "resource");
            // @run-at
            script.runAt = extractMeta(metaBlock, "run-at");
            if (script.runAt == null) script.runAt = "document-idle";
            // @noframes
            script.noFrames = metaBlock.contains("@noframes");

            // Extract the script body (everything after the metadata block)
            int scriptStart = matcher.end();
            script.body = content.substring(scriptStart).trim();
        } else {
            script.name = file.getName().replace(".user.js", "");
            script.body = content;
            script.version = "1.0";
        }

        return script;
    }

    private String extractMeta(String block, String key) {
        Pattern p = Pattern.compile("//\\s*@" + key + "\\s+(.+)");
        Matcher m = p.matcher(block);
        if (m.find()) return m.group(1).trim();
        return null;
    }

    private List<String> extractMetaList(String block, String key) {
        List<String> result = new ArrayList<>();
        Pattern p = Pattern.compile("//\\s*@" + key + "\\s+(.+)");
        Matcher m = p.matcher(block);
        while (m.find()) {
            result.add(m.group(1).trim());
        }
        return result;
    }

    /**
     * Check if a userscript should run on the given URL.
     */
    public boolean shouldRunOnUrl(Userscript script, String url) {
        if (url == null || url.isEmpty() || url.startsWith("zenith://") || url.startsWith("file://")) return false;
        if (!script.enabled) return false;

        // Check @exclude first (takes priority)
        for (String pattern : script.excludes) {
            if (matchPattern(pattern, url)) return false;
        }

        // Check @match
        for (String pattern : script.matches) {
            if (matchPattern(pattern, url)) return true;
        }

        // Check @include
        for (String pattern : script.includes) {
            if (matchPattern(pattern, url)) return true;
        }

        return false;
    }

    /**
     * Get all scripts that should run on a given URL.
     */
    public List<Userscript> getScriptsForUrl(String url) {
        List<Userscript> result = new ArrayList<>();
        for (Userscript script : scripts) {
            if (shouldRunOnUrl(script, url)) {
                result.add(script);
            }
        }
        return result;
    }

    /**
     * Build the JavaScript to inject a userscript into a page.
     */
    public String buildInjectionScript(Userscript script) {
        if (script.body == null || script.body.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();

        // Build GM_* API stubs based on @grant
        sb.append("(function(){\n");

        // Namespace wrapper
        sb.append("var __zenithScriptMeta = {name:'").append(escapeJs(script.name)).append("',version:'").append(escapeJs(script.version)).append("'};\n");

        // GM_* API shims
        boolean needsGm = !script.grants.isEmpty() && !script.grants.contains("none");
        if (needsGm) {
            // GM_info
            sb.append("var GM_info = {script: __zenithScriptMeta, scriptHandler: 'Zenith Browser Userscript Manager', version: '1.0'};\n");

            // GM_getValue / GM_setValue (using localStorage)
            String storagePrefix = "zenith_us_" + script.name.hashCode() + "_";
            sb.append("var GM_getValue = function(key, def) { try { var v = localStorage.getItem('" + storagePrefix + "' + key); return v !== null ? JSON.parse(v) : def; } catch(e) { return def; } };\n");
            sb.append("var GM_setValue = function(key, val) { try { localStorage.setItem('" + storagePrefix + "' + key, JSON.stringify(val)); } catch(e) {} };\n");
            sb.append("var GM_deleteValue = function(key) { try { localStorage.removeItem('" + storagePrefix + "' + key); } catch(e) {} };\n");
            sb.append("var GM_listValues = function() { var r=[]; for(var i=0;i<localStorage.length;i++){var k=localStorage.key(i);if(k&&k.startsWith('" + storagePrefix + "'))r.push(k.substring(" + storagePrefix.length() + "));} return r; };\n");

            // GM_addStyle
            sb.append("var GM_addStyle = function(css) { var s=document.createElement('style');s.textContent=css;document.head.appendChild(s);return s; };\n");

            // GM_setClipboard
            sb.append("var GM_setClipboard = function(text, type) { if(navigator.clipboard) navigator.clipboard.writeText(text); };\n");

            // GM_xmlhttpRequest
            sb.append("var GM_xmlhttpRequest = function(details) { var xhr=new XMLHttpRequest();xhr.open(details.method||'GET',details.url,true);if(details.headers){for(var k in details.headers)xhr.setRequestHeader(k,details.headers[k]);}xhr.onload=function(){if(details.onload)details.onload({response:xhr.responseText,responseText:xhr.responseText,responseXML:xhr.responseXML,status:xhr.status,statusText:xhr.statusText});};xhr.onerror=function(){if(details.onerror)details.onerror({status:xhr.status});};xhr.send(details.data||null);};\n");

            // GM_notification
            sb.append("var GM_notification = function(details, ondone) { if(typeof details==='string')details={text:details}; try{if(Notification.permission==='granted'){new Notification(details.title||'Zenith Browser',{body:details.text,icon:details.icon});}else if(Notification.requestPermission){Notification.requestPermission();}}catch(e){} if(ondone)ondone(); };\n");

            // GM_openInTab
            sb.append("var GM_openInTab = function(url, options) { window.open(url, '_blank'); return {close:function(){}}; };\n");

            // GM_registerMenuCommand
            sb.append("var GM_registerMenuCommand = function(caption, callback, accessKey) { /* placeholder */ };\n");

            // GM_getTab / GM_saveTab / GM_getTabs
            sb.append("var GM_getTab = function(cb) { if(cb) cb({}); };\n");
            sb.append("var GM_saveTab = function(obj, cb) { if(cb) cb(); };\n");
            sb.append("var GM_getTabs = function(cb) { if(cb) cb({}); };\n");

            // GM_addElement
            sb.append("var GM_addElement = function(parent, tag, attrs) { var el=document.createElement(tag); if(attrs){for(var k in attrs){if(k==='textContent')el.textContent=attrs[k];else if(k==='innerHTML')el.innerHTML=attrs[k];else el.setAttribute(k,attrs[k]);}} (parent||document.head).appendChild(el); return el; };\n");

            // unsafeWindow
            sb.append("try { var unsafeWindow = window; } catch(e) { var unsafeWindow = window; }\n");
        }

        // Wrap script body in IIFE
        sb.append("// Userscript: ").append(escapeJs(script.name)).append("\n");
        sb.append("(function(){\n");
        sb.append("'use strict';\n");
        sb.append(script.body);
        sb.append("\n})();\n");

        sb.append("})();\n");

        return sb.toString();
    }

    /**
     * Install a userscript from a .user.js file.
     */
    public void installUserscriptFromFile(File file) {
        executor.execute(() -> {
            try {
                String content = FileUtils.readFileToString(file);
                if (content == null || content.isEmpty()) {
                    notifyError("Cannot read file");
                    return;
                }

                // Extract name from metadata
                String name = extractScriptName(content);
                if (name == null) name = file.getName().replace(".user.js", "").replace(".js", "");

                // Clean filename
                name = name.replaceAll("[^a-zA-Z0-9_\\-]", "_");

                File destFile = new File(scriptsRoot, name + ".user.js");
                // If updating existing, keep the same file
                if (destFile.exists()) {
                    destFile.delete();
                }

                FileOutputStream fos = new FileOutputStream(destFile);
                fos.write(content.getBytes("UTF-8"));
                fos.close();

                Userscript script = parseUserscript(destFile);
                if (script != null) {
                    // Remove old version if exists
                    scripts.removeIf(s -> s.name.equals(script.name));
                    scripts.add(script);
                    notifyInstalled(script);
                } else {
                    notifyError("Failed to parse userscript");
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to install userscript", e);
                notifyError(e.getMessage());
            }
        });
    }

    /**
     * Install a userscript from URL.
     */
    public void installUserscriptFromUrl(String url) {
        executor.execute(() -> {
            try {
                File cacheDir = context.getCacheDir();
                if (!cacheDir.exists()) cacheDir.mkdirs();
                File tempFile = new File(cacheDir, "temp_userscript.user.js");

                URL extUrl = new URL(url);
                HttpURLConnection conn = (HttpURLConnection) extUrl.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
                conn.setInstanceFollowRedirects(true);
                conn.setConnectTimeout(30000);
                conn.setReadTimeout(60000);

                int code = conn.getResponseCode();
                if (code == 301 || code == 302 || code == 303 || code == 307) {
                    String newUrl = conn.getHeaderField("Location");
                    if (newUrl != null) {
                        conn.disconnect();
                        extUrl = new URL(newUrl);
                        conn = (HttpURLConnection) extUrl.openConnection();
                        conn.setRequestMethod("GET");
                        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
                        conn.setInstanceFollowRedirects(true);
                        conn.setConnectTimeout(30000);
                        conn.setReadTimeout(60000);
                    }
                }

                if (conn.getResponseCode() != 200) {
                    notifyError("HTTP " + conn.getResponseCode());
                    return;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder content = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
                reader.close();
                conn.disconnect();

                String scriptContent = content.toString();
                if (scriptContent.isEmpty()) {
                    notifyError("Downloaded script is empty");
                    return;
                }

                String name = extractScriptName(scriptContent);
                if (name == null) name = "userscript_" + System.currentTimeMillis();
                name = name.replaceAll("[^a-zA-Z0-9_\\-]", "_");

                File destFile = new File(scriptsRoot, name + ".user.js");
                if (destFile.exists()) destFile.delete();

                FileOutputStream fos = new FileOutputStream(destFile);
                fos.write(scriptContent.getBytes("UTF-8"));
                fos.close();

                Userscript script = parseUserscript(destFile);
                if (script != null) {
                    scripts.removeIf(s -> s.name.equals(script.name));
                    scripts.add(script);
                    notifyInstalled(script);
                } else {
                    notifyError("Failed to parse userscript");
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to install userscript from URL", e);
                notifyError(e.getMessage());
            }
        });
    }

    private String extractScriptName(String content) {
        Pattern p = Pattern.compile("//\\s*@name\\s+(.+)");
        Matcher m = p.matcher(content);
        if (m.find()) return m.group(1).trim();
        return null;
    }

    /**
     * Uninstall a userscript.
     */
    public void uninstallScript(Userscript script) {
        scripts.remove(script);
        executor.execute(() -> {
            if (script.file != null && script.file.exists()) {
                script.file.delete();
            }
            notifyRemoved(script);
        });
    }

    /**
     * Update a userscript's content (for editor).
     */
    public void updateScriptContent(Userscript script, String newContent) {
        executor.execute(() -> {
            try {
                FileOutputStream fos = new FileOutputStream(script.file);
                fos.write(newContent.getBytes("UTF-8"));
                fos.close();
                Userscript updated = parseUserscript(script.file);
                if (updated != null) {
                    int idx = scripts.indexOf(script);
                    if (idx >= 0) scripts.set(idx, updated);
                    notifyUpdated(updated);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to update script", e);
            }
        });
    }

    /**
     * Toggle script enabled state.
     */
    public void toggleScript(Userscript script) {
        script.enabled = !script.enabled;
    }

    public List<Userscript> getScripts() {
        return new ArrayList<>(scripts);
    }

    public List<Userscript> getEnabledScripts() {
        List<Userscript> enabled = new ArrayList<>();
        for (Userscript s : scripts) {
            if (s.enabled) enabled.add(s);
        }
        return enabled;
    }

    private void notifyInstalled(Userscript script) {
        if (listener != null) {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> listener.onScriptInstalled(script));
        }
    }

    private void notifyError(String error) {
        if (listener != null) {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> listener.onScriptError(error));
        }
    }

    private void notifyRemoved(Userscript script) {
        if (listener != null) {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> listener.onScriptRemoved(script));
        }
    }

    private void notifyUpdated(Userscript script) {
        if (listener != null) {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> listener.onScriptUpdated(script));
        }
    }

    /**
     * Pattern matching for @match/@include/@exclude.
     * Supports: *://*.example.com/*, https://example.com/path*, etc.
     */
    private boolean matchPattern(String pattern, String url) {
        if (pattern == null || url == null) return false;
        pattern = pattern.trim();

        // Exact match
        if (pattern.equals(url)) return true;

        // Convert glob pattern to regex
        String regex = pattern
            .replace(".", "\\.")
            .replace("*", ".*")
            .replace("?", ".");

        try {
            return url.matches(regex);
        } catch (Exception e) {
            return false;
        }
    }

    private String escapeJs(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }

    // ==================== Userscript Data Class ====================

    public static class Userscript {
        public File file;
        public String content;
        public String body;
        public String name = "Unnamed Script";
        public String version = "1.0";
        public String description = "";
        public String author = "";
        public String namespace = "";
        public String homepage = "";
        public String updateURL = "";
        public String downloadURL = "";
        public List<String> matches = new ArrayList<>();
        public List<String> includes = new ArrayList<>();
        public List<String> excludes = new ArrayList<>();
        public List<String> grants = new ArrayList<>();
        public List<String> requires = new ArrayList<>();
        public List<String> resources = new ArrayList<>();
        public String runAt = "document-idle";
        public boolean noFrames = false;
        public boolean enabled = true;
        public long installedAt;

        public String getMatchSummary() {
            if (!matches.isEmpty()) {
                if (matches.size() == 1) return matches.get(0);
                return matches.size() + " match patterns";
            }
            if (!includes.isEmpty()) {
                if (includes.size() == 1) return includes.get(0);
                return includes.size() + " include patterns";
            }
            return "All sites";
        }

        public String getGrantsSummary() {
            if (grants.isEmpty() || (grants.size() == 1 && grants.get(0).equals("none"))) return "none";
            return String.join(", ", grants);
        }
    }
}
