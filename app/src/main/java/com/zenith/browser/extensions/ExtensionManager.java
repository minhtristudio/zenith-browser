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

    public void installFromFile(File file) {
        executor.execute(() -> {
            try {
                String extName = file.getName().replace(".crx", "").replace(".zip", "");
                File extDir = new File(extensionsRoot, extName);

                // Remove existing if re-installing
                if (extDir.exists()) deleteDirectory(extDir);
                extDir.mkdirs();

                // Extract ZIP/CRX
                if (file.getName().endsWith(".crx")) {
                    extractCrx(file, extDir);
                } else {
                    extractZip(file, extDir);
                }

                ChromeExtension ext = new ChromeExtension(extDir);
                if (ext.loadManifest()) {
                    extensions.add(ext);
                    mainHandler.post(() -> {
                        if (listener != null) listener.onExtensionLoaded(ext);
                    });
                } else {
                    deleteDirectory(extDir);
                    mainHandler.post(() -> {
                        if (listener != null) listener.onExtensionError("Invalid manifest.json");
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
        // CRX v3 header: 16 bytes magic + 4 bytes header length + pubkey + signature
        // We need to find the ZIP content after the header
        byte[] header = new byte[12];
        java.io.RandomAccessFile raf = new java.io.RandomAccessFile(crxFile, "r");
        raf.readFully(header);

        // Check for CRX magic
        if (header[0] == 'C' && header[1] == 'R' && header[2] == 'X') {
            // CRX v3 format
            int headerLength = ((header[8] & 0xFF) << 24) | ((header[9] & 0xFF) << 16) |
                               ((header[10] & 0xFF) << 8) | (header[11] & 0xFF);
            raf.seek(12 + headerLength);
            // Copy remaining as ZIP
            java.io.FileOutputStream fos = new FileOutputStream(new File(destDir, "temp.zip"));
            byte[] buffer = new byte[8192];
            int len;
            while ((len = raf.read(buffer)) > 0) {
                fos.write(buffer, 0, len);
            }
            fos.close();
            raf.close();
            extractZip(new File(destDir, "temp.zip"), destDir);
            new File(destDir, "temp.zip").delete();
        } else {
            raf.close();
            // Might just be a renamed ZIP
            extractZip(crxFile, destDir);
        }
    }

    private void extractZip(File zipFile, File destDir) throws Exception {
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

    /**
     * Inject content scripts from all enabled extensions into a WebView.
     * Should be called when a page finishes loading.
     */
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

    /**
     * Inject extension API shim into the page for chrome.* API compatibility
     */
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

            // chrome.runtime stub
            "window.chrome = window.chrome || {};" +
            "window.chrome.runtime = window.chrome.runtime || {};" +
            "window.chrome.runtime.sendMessage = function(msg, cb) {" +
            "  window.__zenithExtension.postMessage(JSON.stringify({type:'runtime_message', data: msg}));" +
            "};" +
            "window.chrome.runtime.onMessage = { addListener: function(cb) { window.__zenithRuntimeListener = cb; } };" +
            "window.chrome.runtime.id = 'zenith-extension-host';" +
            "window.chrome.runtime.getManifest = function() { return {};};" +
            "window.chrome.runtime.getURL = function(path) { return path; };" +

            // chrome.storage stub
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

            // chrome.tabs stub
            "window.chrome.tabs = window.chrome.tabs || {};" +
            "window.chrome.tabs.query = function(info, cb) { if(cb) cb([{id:1, url: location.href, title: document.title}]); };" +
            "window.chrome.tabs.sendMessage = function(tabId, msg, cb) { if(cb) cb(); };" +
            "window.chrome.tabs.create = function(props) { window.open(props.url, '_blank'); };" +
            "window.chrome.tabs.update = function(tabId, props, cb) { if(cb) cb(); };" +

            // chrome.browserAction stub
            "window.chrome.action = window.chrome.action || {};" +
            "window.chrome.browserAction = window.chrome.browserAction || window.chrome.action;" +
            "window.chrome.browserAction.setBadgeText = function(){};" +
            "window.chrome.browserAction.setBadgeBackgroundColor = function(){};" +
            "window.chrome.browserAction.getTitle = function(cb) { if(cb) cb('');};" +
            "window.chrome.browserAction.setPopup = function(){};" +

            // chrome.i18n stub
            "window.chrome.i18n = window.chrome.i18n || {};" +
            "window.chrome.i18n.getMessage = function(name, subs) { return name; };" +

            // chrome.bookmarks stub
            "window.chrome.bookmarks = window.chrome.bookmarks || {};" +
            "window.chrome.bookmarks.create = function(bm, cb) { if(cb) cb({id:'1'}); };" +
            "window.chrome.bookmarks.getTree = function(cb) { if(cb) cb([]); };" +

            // chrome.history stub
            "window.chrome.history = window.chrome.history || {};" +
            "window.chrome.history.search = function(q, cb) { if(cb) cb([]); };" +

            // chrome.windows stub
            "window.chrome.windows = window.chrome.windows || {};" +
            "window.chrome.windows.create = function(data, cb) { window.open(data.url); if(cb) cb(); };" +
            "window.chrome.windows.getCurrent = function(cb) { if(cb) cb({id:1, type:'normal'}); };" +

            // chrome.webNavigation stub
            "window.chrome.webNavigation = window.chrome.webNavigation || {};" +
            "window.chrome.webNavigation.getAllFrames = function(details, cb) { if(cb) cb([]); };" +

            // chrome.webRequest stub
            "window.chrome.webRequest = window.chrome.webRequest || {};" +
            "window.chrome.webRequest.onBeforeRequest = { addListener: function(){} };" +
            "window.chrome.webRequest.onHeadersReceived = { addListener: function(){} };" +
            "window.chrome.webRequest.onCompleted = { addListener: function(){} };" +

            // chrome.notifications stub
            "window.chrome.notifications = window.chrome.notifications || {};" +
            "window.chrome.notifications.create = function(id, opts, cb) { if(cb) cb(id); };" +

            // chrome.contextMenus stub
            "window.chrome.contextMenus = window.chrome.contextMenus || {};" +
            "window.chrome.contextMenus.create = function(){};" +
            "window.chrome.contextMenus.onClicked = { addListener: function(){} };" +

            // fetch/XHR interception for webRequest API
            "var _origFetch = window.fetch;" +
            "window.fetch = function(url, opts) {" +
            "  opts = opts || {};" +
            "  return _origFetch.apply(this, arguments);" +
            "};" +

            "})();";
    }

    /**
     * Install a userscript (.user.js) via the UserscriptManager
     */
    public void installUserscript(File file, String sourceUrl) {
        UserscriptManager usManager = UserscriptManager.getInstance(context);
        if (sourceUrl != null && !sourceUrl.isEmpty()) {
            usManager.installUserscriptFromUrl(sourceUrl);
        } else {
            usManager.installUserscriptFromFile(file);
        }
    }

    /**
     * JavaScript bridge for extension communication
     */
    public static class JsBridge {
        @JavascriptInterface
        public void postMessage(String message) {
            // Handle messages from content scripts
            try {
                JSONObject msg = new JSONObject(message);
                // Process extension messages
                Log.d("JsBridge", "Received extension message: " + message);
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
                info.put("version", "1.0.0");
                return info.toString();
            } catch (Exception e) {
                return "{}";
            }
        }
    }
}
