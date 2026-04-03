package com.zenith.browser.extensions;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ExtensionManager {

    private static final String TAG = "ExtensionManager";
    private static final String EXTENSIONS_DIR = "Extensions";
    private static ExtensionManager instance;

    private final Context context;
    private final File extensionsRoot;
    private final List<ChromeExtension> extensions = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private ExtensionListener listener;

    public interface ExtensionListener {
        void onExtensionLoaded(ChromeExtension extension);
        void onExtensionError(String error);
        void onExtensionRemoved(ChromeExtension extension);
    }

    public ExtensionManager(Context context) {
        this.context = context.getApplicationContext();
        this.extensionsRoot = new File(this.context.getFilesDir(), EXTENSIONS_DIR);
        extensionsRoot.mkdirs();
        instance = this;
        loadAllExtensions();
    }

    public static ExtensionManager getInstance() {
        return instance;
    }

    public static ExtensionManager getInstance(Context context) {
        if (instance == null) {
            instance = new ExtensionManager(context);
        }
        return instance;
    }

    public void setListener(ExtensionListener listener) {
        this.listener = listener;
    }

    private void loadAllExtensions() {
        executor.execute(() -> {
            File[] dirs = extensionsRoot.listFiles(File::isDirectory);
            if (dirs == null) return;
            for (File dir : dirs) {
                ChromeExtension ext = new ChromeExtension(dir);
                if (ext.loadManifest()) {
                    extensions.add(ext);
                }
            }
        });
    }

    /**
     * Install an extension from a .crx or .zip file.
     * Properly handles both formats and preserves the original extension name.
     */
    public void installFromFile(File file) {
        if (file == null || !file.exists()) {
            notifyError("File not found");
            return;
        }

        executor.execute(() -> {
            try {
                // Determine extension name from file
                String fileName = file.getName();
                String baseName;
                if (fileName.endsWith(".crx")) {
                    baseName = fileName.substring(0, fileName.length() - 4);
                } else if (fileName.endsWith(".zip")) {
                    baseName = fileName.substring(0, fileName.length() - 4);
                } else if (fileName.endsWith(".user.js")) {
                    baseName = fileName.substring(0, fileName.length() - 8);
                } else {
                    baseName = fileName;
                }

                // Clean the name for filesystem
                baseName = baseName.replaceAll("[^a-zA-Z0-9_\\-\\.]", "_");
                if (baseName.isEmpty()) baseName = "extension_" + System.currentTimeMillis();

                File extDir = new File(extensionsRoot, baseName);

                // Remove existing if re-installing
                if (extDir.exists()) deleteDirectory(extDir);
                extDir.mkdirs();

                // Extract based on file type
                if (fileName.endsWith(".crx")) {
                    extractCrx(file, extDir);
                } else {
                    // Handle both .zip and renamed files
                    try {
                        extractZip(file, extDir);
                    } catch (Exception zipEx) {
                        // If ZIP extraction fails, try CRX extraction (might be mislabeled)
                        try {
                            extractCrx(file, extDir);
                        } catch (Exception crxEx) {
                            deleteDirectory(extDir);
                            throw new Exception("Failed to extract: " + zipEx.getMessage());
                        }
                    }
                }

                ChromeExtension ext = new ChromeExtension(extDir);
                if (ext.loadManifest()) {
                    // Remove old version if exists
                    extensions.removeIf(e -> e.getName().equals(ext.getName()));
                    extensions.add(ext);
                    mainHandler.post(() -> {
                        if (listener != null) listener.onExtensionLoaded(ext);
                    });
                } else {
                    deleteDirectory(extDir);
                    mainHandler.post(() -> {
                        if (listener != null) listener.onExtensionError("Invalid manifest.json - not a valid extension");
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to install extension", e);
                mainHandler.post(() -> {
                    if (listener != null) listener.onExtensionError(e.getMessage());
                });
            }
        });
    }

    private void extractCrx(File crxFile, File destDir) throws Exception {
        byte[] header = new byte[12];
        java.io.RandomAccessFile raf = new java.io.RandomAccessFile(crxFile, "r");
        raf.readFully(header);

        if (header[0] == 'C' && header[1] == 'R' && header[2] == 'X') {
            int headerLength = ((header[8] & 0xFF) << 24) | ((header[9] & 0xFF) << 16) |
                               ((header[10] & 0xFF) << 8) | (header[11] & 0xFF);
            raf.seek(12 + headerLength);
            File tempZip = new File(destDir, "temp_extract.zip");
            FileOutputStream fos = new FileOutputStream(tempZip);
            byte[] buffer = new byte[8192];
            int len;
            while ((len = raf.read(buffer)) > 0) {
                fos.write(buffer, 0, len);
            }
            fos.close();
            raf.close();
            extractZip(tempZip, destDir);
            tempZip.delete();
        } else {
            raf.close();
            extractZip(crxFile, destDir);
        }
    }

    private void extractZip(File zipFile, File destDir) throws Exception {
        if (!zipFile.exists()) {
            throw new Exception("ZIP file does not exist: " + zipFile.getAbsolutePath());
        }

        ZipFile zip = new ZipFile(zipFile);
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            File outFile = new File(destDir, entry.getName());

            // Security: prevent path traversal
            if (!outFile.getCanonicalPath().startsWith(destDir.getCanonicalPath())) continue;

            if (entry.isDirectory()) {
                outFile.mkdirs();
            } else {
                outFile.getParentFile().mkdirs();
                try (InputStream is = zip.getInputStream(entry);
                     FileOutputStream fos = new FileOutputStream(outFile)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = is.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                }
            }
        }
        zip.close();
    }

    private boolean deleteDirectory(File dir) {
        if (dir.isDirectory()) {
            File[] children = dir.listFiles();
            if (children != null) {
                for (File child : children) deleteDirectory(child);
            }
        }
        return dir.delete();
    }

    public void uninstallExtension(ChromeExtension extension) {
        extensions.remove(extension);
        executor.execute(() -> {
            deleteDirectory(extension.getExtensionDir());
        });
        if (listener != null) listener.onExtensionRemoved(extension);
    }

    public List<ChromeExtension> getExtensions() {
        return new ArrayList<>(extensions);
    }

    public List<ChromeExtension> getEnabledExtensions() {
        List<ChromeExtension> enabled = new ArrayList<>();
        for (ChromeExtension ext : extensions) {
            if (ext.isEnabled()) enabled.add(ext);
        }
        return enabled;
    }

    public void injectContentScripts(WebView webView, String url) {
        if (url == null || url.startsWith("zenith://") || url.startsWith("file://")) return;

        for (ChromeExtension ext : getEnabledExtensions()) {
            String script = ext.getScriptForInjection(url);
            if (!script.isEmpty()) {
                try {
                    webView.evaluateJavascript(script, null);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to inject content script from " + ext.getName(), e);
                }
            }
        }
    }

    public void injectExtensionApi(WebView webView, String url) {
        if (url == null || url.startsWith("zenith://")) return;

        try {
            String apiScript = buildApiShim();
            webView.evaluateJavascript(apiScript, null);
        } catch (Exception e) {
            Log.e(TAG, "Failed to inject extension API", e);
        }
    }

    private String buildApiShim() {
        return "(function(){" +
            "if(window.__zenithApiInjected) return;" +
            "window.__zenithApiInjected = true;" +

            // chrome.runtime
            "window.chrome = window.chrome || {};" +
            "window.chrome.runtime = window.chrome.runtime || {};" +
            "window.chrome.runtime.sendMessage = function(msg, cb) {" +
            "  window.__zenithExtension.postMessage(JSON.stringify({type:'runtime_message', data: msg}));" +
            "};" +
            "window.chrome.runtime.onMessage = { addListener: function(cb) { window.__zenithRuntimeListener = cb; } };" +
            "window.chrome.runtime.id = 'zenith-extension-host';" +
            "window.chrome.runtime.getManifest = function() { return {};};" +
            "window.chrome.runtime.getURL = function(path) { return path; };" +

            // chrome.storage
            "window.chrome.storage = window.chrome.storage || {};" +
            "window.chrome.storage.local = window.chrome.storage || {};" +
            "window.chrome.storage.local.get = function(keys, cb) {" +
            "  try { var d = JSON.parse(localStorage.getItem('__zenith_storage') || '{}'); if(cb) cb(d); } catch(e) { if(cb) cb({}); }" +
            "};" +
            "window.chrome.storage.local.set = function(data, cb) {" +
            "  try { var d = JSON.parse(localStorage.getItem('__zenith_storage') || '{}'); Object.assign(d, data); localStorage.setItem('__zenith_storage', JSON.stringify(d)); if(cb) cb(); } catch(e) { if(cb) cb(); }" +
            "};" +
            "window.chrome.storage.local.remove = function(keys, cb) {" +
            "  try { var d = JSON.parse(localStorage.getItem('__zenith_storage') || '{}'); (Array.isArray(keys) ? keys : [keys]).forEach(function(k){ delete d[k]; }); localStorage.setItem('__zenith_storage', JSON.stringify(d)); if(cb) cb(); } catch(e) { if(cb) cb(); }" +
            "};" +
            "window.chrome.storage.sync = window.chrome.storage.local;" +

            // chrome.tabs
            "window.chrome.tabs = window.chrome.tabs || {};" +
            "window.chrome.tabs.query = function(info, cb) { if(cb) cb([{id:1, url: location.href, title: document.title}]); };" +
            "window.chrome.tabs.sendMessage = function(tabId, msg, cb) { if(cb) cb(); };" +
            "window.chrome.tabs.create = function(props) { window.__zenithExtension.postMessage(JSON.stringify({type:'openInTab',data:props})); };" +
            "window.chrome.tabs.update = function(tabId, props, cb) { if(cb) cb(); };" +

            // chrome.action / browserAction
            "window.chrome.action = window.chrome.action || {};" +
            "window.chrome.browserAction = window.chrome.browserAction || window.chrome.action;" +
            "window.chrome.browserAction.setBadgeText = function(){};" +
            "window.chrome.browserAction.setBadgeBackgroundColor = function(){};" +
            "window.chrome.browserAction.getTitle = function(cb) { if(cb) cb('');};" +
            "window.chrome.browserAction.setPopup = function(){};" +

            // chrome.i18n
            "window.chrome.i18n = window.chrome.i18n || {};" +
            "window.chrome.i18n.getMessage = function(name, subs) { return name; };" +

            // chrome.bookmarks
            "window.chrome.bookmarks = window.chrome.bookmarks || {};" +
            "window.chrome.bookmarks.create = function(bm, cb) { if(cb) cb({id:'1'}); };" +
            "window.chrome.bookmarks.getTree = function(cb) { if(cb) cb([]); };" +

            // chrome.history
            "window.chrome.history = window.chrome.history || {};" +
            "window.chrome.history.search = function(q, cb) { if(cb) cb([]); };" +

            // chrome.windows
            "window.chrome.windows = window.chrome.windows || {};" +
            "window.chrome.windows.create = function(data, cb) { window.__zenithExtension.postMessage(JSON.stringify({type:'openInTab',data:data})); if(cb) cb(); };" +
            "window.chrome.windows.getCurrent = function(cb) { if(cb) cb({id:1, type:'normal'}); };" +

            // chrome.webNavigation
            "window.chrome.webNavigation = window.chrome.webNavigation || {};" +
            "window.chrome.webNavigation.getAllFrames = function(details, cb) { if(cb) cb([]); };" +

            // chrome.webRequest
            "window.chrome.webRequest = window.chrome.webRequest || {};" +
            "window.chrome.webRequest.onBeforeRequest = { addListener: function(){} };" +
            "window.chrome.webRequest.onHeadersReceived = { addListener: function(){} };" +
            "window.chrome.webRequest.onCompleted = { addListener: function(){} };" +

            // chrome.notifications
            "window.chrome.notifications = window.chrome.notifications || {};" +
            "window.chrome.notifications.create = function(id, opts, cb) { if(cb) cb(id); };" +

            // chrome.contextMenus
            "window.chrome.contextMenus = window.chrome.contextMenus || {};" +
            "window.chrome.contextMenus.create = function(){};" +
            "window.chrome.contextMenus.onClicked = { addListener: function(){} };" +

            "})();";
    }

    public void installUserscript(File file, String sourceUrl) {
        UserscriptManager usManager = UserscriptManager.getInstance(context);
        if (sourceUrl != null && !sourceUrl.isEmpty()) {
            usManager.installUserscriptFromUrl(sourceUrl);
        } else {
            usManager.installUserscriptFromFile(file);
        }
    }

    private void notifyError(String error) {
        if (listener != null) {
            mainHandler.post(() -> listener.onExtensionError(error));
        }
    }

    public static class JsBridge {
        @JavascriptInterface
        public void postMessage(String message) {
            try {
                JSONObject msg = new JSONObject(message);
                String type = msg.optString("type", "");

                switch (type) {
                    case "runtime_message":
                        Log.d("JsBridge", "Runtime message: " + message);
                        break;
                    case "openInTab":
                        Log.d("JsBridge", "Open in tab request: " + message);
                        break;
                    case "download":
                        Log.d("JsBridge", "Download request: " + message);
                        break;
                    case "notification":
                        Log.d("JsBridge", "Notification request: " + message);
                        break;
                    default:
                        Log.d("JsBridge", "Received extension message: " + message);
                }
            } catch (Exception e) {
                Log.e("JsBridge", "Error parsing extension message", e);
            }
        }

        @JavascriptInterface
        public String getExtensionInfo() {
            try {
                JSONObject info = new JSONObject();
                info.put("platform", "android");
                info.put("browser", "ZenithBrowser");
                info.put("version", "2.0");
                return info.toString();
            } catch (Exception e) {
                return "{}";
            }
        }
    }
}
