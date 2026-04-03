package com.zenith.browser.devtools;

import android.content.Context;
import android.util.Log;
import android.webkit.WebView;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class DevToolsHelper {

    private static final String TAG = "DevToolsHelper";
    private static boolean erudaInitialized = false;
    private static boolean devToolsEnabled = false;

    /**
     * Enable/disable built-in DevTools (Eruda)
     */
    public static void setDevToolsEnabled(boolean enabled) {
        devToolsEnabled = enabled;
    }

    public static boolean isDevToolsEnabled() {
        return devToolsEnabled;
    }

    /**
     * Toggle DevTools overlay on a WebView
     */
    public static void toggleDevTools(WebView webView) {
        if (webView == null) return;
        String js = "if(typeof eruda !== 'undefined') { eruda.isShow() ? eruda.hide() : eruda.show(); }";
        try {
            webView.evaluateJavascript(js, null);
        } catch (Exception e) {
            Log.e(TAG, "Failed to toggle DevTools", e);
        }
    }

    /**
     * Show DevTools overlay on a WebView
     */
    public static void showDevTools(WebView webView) {
        if (webView == null) return;
        try {
            webView.evaluateJavascript("if(typeof eruda !== 'undefined') { eruda.show(); }", null);
        } catch (Exception e) {
            Log.e(TAG, "Failed to show DevTools", e);
        }
    }

    /**
     * Hide DevTools overlay
     */
    public static void hideDevTools(WebView webView) {
        if (webView == null) return;
        try {
            webView.evaluateJavascript("if(typeof eruda !== 'undefined') { eruda.hide(); }", null);
        } catch (Exception e) {
            Log.e(TAG, "Failed to hide DevTools", e);
        }
    }

    /**
     * Initialize Eruda DevTools by injecting its script.
     * Should be called early in page load.
     */
    public static void initDevTools(WebView webView) {
        if (webView == null || !devToolsEnabled) return;
        try {
            String erudaScript = loadErudaScript();
            if (erudaScript != null && !erudaScript.isEmpty()) {
                webView.evaluateJavascript(erudaScript, value -> {
                    if ("true".equals(value) || value == null) {
                        erudaInitialized = true;
                        // Initialize Eruda but don't show it
                        webView.evaluateJavascript(
                            "if(typeof eruda !== 'undefined' && !eruda._inited) {" +
                            "  eruda.init();" +
                            "  eruda.add(eruda.dom);" +
                            "  eruda.hide();" +
                            "}", null);
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to init DevTools", e);
        }
    }

    /**
     * Execute a console command via Eruda
     */
    public static void executeConsoleCommand(WebView webView, String command) {
        if (webView == null) return;
        try {
            webView.evaluateJavascript(
                "if(typeof eruda !== 'undefined' && eruda.isShow()) {" +
                "  eruda.get('console')._execCommand(" + escapeForJs(command) + ");" +
                "}", null);
        } catch (Exception e) {
            Log.e(TAG, "Failed to execute console command", e);
        }
    }

    /**
     * Get the current HTML source of the page
     */
    public static void getSource(WebView webView, android.webkit.ValueCallback<String> callback) {
        if (webView == null) return;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            webView.evaluateJavascript("document.documentElement.outerHTML", callback);
        }
    }

    /**
     * Clear browser data for the WebView
     */
    public static void clearDevToolsData(WebView webView) {
        if (webView == null) return;
        try {
            webView.evaluateJavascript("if(typeof eruda !== 'undefined') { eruda.clear(); }", null);
        } catch (Exception e) {
            Log.e(TAG, "Failed to clear DevTools data", e);
        }
    }

    private static String loadErudaScript() {
        try {
            // Load Eruda from assets
            return readRawText();
        } catch (Exception e) {
            Log.e(TAG, "Failed to load Eruda script", e);
            // Fallback: load from CDN
            return "fetch('https://cdn.jsdelivr.net/npm/eruda@3.0.1/eruda.min.js')" +
                   ".then(r=>r.text()).then(s=>{eval(s);return true;}).catch(()=>false);";
        }
    }

    private static String readRawText() {
        return null; // Will use CDN fallback; asset loading handled by initErudaFromAssets()
    }

    private static String escapeForJs(String s) {
        if (s == null) return "''";
        return "'" + s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "") + "'";
    }

    /**
     * Initialize Eruda from bundled assets (if available)
     */
    public static void initErudaFromAssets(Context context, WebView webView) {
        if (webView == null || !devToolsEnabled) return;
        try {
            // Try to load eruda.min.js from assets
            String script = loadAssetFile(context, "eruda/eruda.min.js");
            if (script != null) {
                webView.evaluateJavascript(script, result -> {
                    webView.evaluateJavascript(
                        "if(typeof eruda !== 'undefined') {" +
                        "  eruda.init();" +
                        "  eruda.hide();" +
                        "}", null);
                });
            } else {
                // Fallback to CDN
                webView.evaluateJavascript(
                    "(function(){var s=document.createElement('script');" +
                    "s.src='https://cdn.jsdelivr.net/npm/eruda@3.0.1/eruda.min.js';" +
                    "s.onload=function(){eruda.init();eruda.hide();};" +
                    "document.head.appendChild(s);})();", null);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to init Eruda", e);
        }
    }

    private static String loadAssetFile(Context context, String path) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(context.getAssets().open(path)));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
