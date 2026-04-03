package com.zenith.browser.browser;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.zenith.browser.R;
import com.zenith.browser.extensions.ExtensionManager;

import java.util.UUID;

public class BrowserTab {

    public enum TabType {
        NORMAL, INCOGNITO, NEW_TAB
    }

    // Tab group colors
    public static final int[] GROUP_COLORS = {
        0xFFBE0D1E, 0xFF705C2E, 0xFF1B6B3A, 0xFF1565C0,
        0xFF6A1B9A, 0xFFE65100, 0xFF00838F, 0xFF4E342E
    };

    private final String id;
    private final WebView webView;
    private final TabType type;
    private String title = "New tab";
    private String url = "";
    private Bitmap favicon;
    private boolean isLoading = false;
    private boolean isDesktopMode = false;
    private final long createdAt;

    // Tab group support
    private String groupName = null;
    private int groupColor = 0xFFBE0D1E;

    // Long-press context menu data
    private String hitResultUrl = null;     // Link URL
    private String hitResultImageUrl = null; // Image URL
    private String hitResultTitle = null;   // Image title/alt

    public BrowserTab(Context context, TabType type, TabListener listener) {
        this.id = UUID.randomUUID().toString();
        this.type = type;
        this.createdAt = System.currentTimeMillis();
        this.webView = new WebView(context) {
            private boolean isLongPressing = false;
            private boolean contextMenuShown = false;

            @Override
            public void onStartTemporaryDetach() {
                super.onStartTemporaryDetach();
            }

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        isLongPressing = true;
                        contextMenuShown = false;
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (contextMenuShown) {
                            // After our context menu fires, consume all moves
                            // to prevent native image drag from starting
                            return true;
                        }
                        // If moved significantly, cancel long-press detection
                        isLongPressing = false;
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        isLongPressing = false;
                        contextMenuShown = false;
                        break;
                }
                return super.onTouchEvent(event);
            }

            /** Called by OnLongClickListener to signal that context menu was shown */
            public void notifyContextMenuShown() {
                contextMenuShown = true;
            }
        };
        configureWebView(context, listener);
    }

    private void configureWebView(Context context, TabListener listener) {
        WebView.setWebContentsDebuggingEnabled(true);

        webView.setWillNotDraw(false);
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        WebSettingsManager.configure(webView, type == TabType.INCOGNITO);

        webView.setWebViewClient(new TabWebViewClient(listener));
        webView.setWebChromeClient(new TabChromeClient(listener));

        // CRITICAL: Disable WebView's default context menu so our custom long-press works
        webView.setOnCreateContextMenuListener(null);

        // Enable long-press for our custom context menu
        webView.setLongClickable(true);
        webView.setHapticFeedbackEnabled(true);
        webView.setOnLongClickListener(v -> {
            try {
                android.webkit.WebView.HitTestResult result = ((WebView) v).getHitTestResult();
                if (result == null || listener == null) return false;

                int type = result.getType();
                String extra = result.getExtra();
                if (extra == null || extra.isEmpty()) return false;

                Object tag = v.getTag();
                if (!(tag instanceof BrowserTab)) return false;
                BrowserTab tab = (BrowserTab) tag;

                // Notify custom WebView that context menu was shown so it can block native drag
                if (v instanceof WebView && v.getClass() == WebView.class) {
                    // Use a flag to prevent native image drag after our menu shows
                }

                switch (type) {
                    case android.webkit.WebView.HitTestResult.SRC_ANCHOR_TYPE:
                        hitResultUrl = extra;
                        hitResultImageUrl = null;
                        listener.onLongPressLink(tab, extra);
                        return true;

                    case android.webkit.WebView.HitTestResult.IMAGE_TYPE:
                        hitResultImageUrl = extra;
                        hitResultUrl = null;
                        listener.onLongPressImage(tab, extra);
                        return true;

                    case android.webkit.WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE:
                        // Image wrapped in a link - show combined menu (both link + image options)
                        hitResultUrl = extra;
                        hitResultImageUrl = extra;
                        // For image-anchor, the extra is the link href.
                        // We need to also get the actual image src via JavaScript.
                        final WebView wv = (WebView) v;
                        wv.evaluateJavascript(
                            "(function(){var el=document.elementFromPoint("
                            + wv.getWidth()/2 + "," + wv.getHeight()/2
                            + ");if(el&&(el.tagName==='IMG'||el.querySelector('img'))){var img=el.tagName==='IMG'?el:el.querySelector('img');return img.src;}return '';})()",
                            imgSrc -> {
                                if (imgSrc != null && !imgSrc.equals("null") && !imgSrc.isEmpty()
                                    && !imgSrc.equals("\"\"")) {
                                    String cleanSrc = imgSrc.replace("\"", "");
                                    hitResultImageUrl = cleanSrc;
                                    listener.onLongPressImage(tab, cleanSrc);
                                } else {
                                    listener.onLongPressLink(tab, extra);
                                }
                            }
                        );
                        return true;

                    default:
                        return false;
                }
            } catch (Exception e) {
                android.util.Log.e("BrowserTab", "Long press error", e);
                return false;
            }
        });

        // Extension JavaScript interface
        webView.addJavascriptInterface(new ExtensionManager.JsBridge(), "__zenithExtension");

        // Initialize with new tab page
        if (type == TabType.NEW_TAB || type == TabType.INCOGNITO) {
            url = "zenith://newtab";
        }
    }

    // JavaScript to disable native image drag on every page
    private static final String ANTI_DRAG_JS =
        "(function(){" +
        "  var s=document.createElement('style');" +
        "  s.textContent='img{-webkit-user-drag:none!important;-webkit-touch-callout:none!important;user-select:none!important;pointer-events:auto!important;}' +" +
        "  'a{-webkit-touch-callout:none!important;}' +" +
        "  '*{-webkit-user-drag:none!important;}';" +
        "  (document.head||document.documentElement).appendChild(s);" +
        "  document.addEventListener('dragstart',function(e){e.preventDefault();e.stopPropagation();},true);" +
        "  document.addEventListener('contextmenu',function(e){e.preventDefault();},true);" +
        "  var imgs=document.querySelectorAll('img');" +
        "  for(var i=0;i<imgs.length;i++){imgs[i].setAttribute('draggable','false');imgs[i].ondragstart=function(){return false;};}" +
        "  var observer=new MutationObserver(function(mutations){" +
        "    mutations.forEach(function(m){" +
        "      if(m.addedNodes)for(var i=0;i<m.addedNodes.length;i++){" +
        "        var n=m.addedNodes[i];if(n.tagName==='IMG'){n.setAttribute('draggable','false');n.ondragstart=function(){return false;};}" +
        "        var imgs2=n.querySelectorAll?n.querySelectorAll('img'):[];" +
        "        for(var j=0;j<imgs2.length;j++){imgs2[j].setAttribute('draggable','false');imgs2[j].ondragstart=function(){return false;};}" +
        "      }" +
        "    });" +
        "  });" +
        "  observer.observe(document.body||document.documentElement,{childList:true,subtree:true});" +
        "})();";

    /** Check if a URL points to a direct image file */
    private static boolean isImageUrl(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase();
        // Remove query string for extension check
        String path = lower.contains("?") ? lower.substring(0, lower.indexOf('?')) : lower;
        String[] imageExts = {".png", ".jpg", ".jpeg", ".gif", ".webp", ".bmp", ".avif", ".ico"};
        for (String ext : imageExts) {
            if (path.endsWith(ext)) return true;
        }
        // Check for common image CDN patterns: /image/, /photo/, /img/
        if (lower.contains("/image/") || lower.contains("/photo/") || lower.contains("/img/")
            || lower.contains(".googleusercontent.com/") || lower.contains(".twimg.com/")
            || lower.contains(".fbcdn.net/")) {
            // Also check if it looks like a direct file (has extension or ends without trailing slash)
            if (!lower.endsWith("/")) return true;
        }
        return false;
    }

    public void loadUrl(String url) {
        if (url == null || url.trim().isEmpty()) return;
        if (url.equals("zenith://newtab")) {
            webView.loadUrl("file:///android_asset/newtab.html");
            this.url = url;
            this.title = "New tab";
            return;
        }
        if (!url.startsWith("http://") && !url.startsWith("https://") &&
            !url.startsWith("file://") && !url.startsWith("javascript:")) {
            if (url.contains(".") && !url.contains(" ")) {
                url = "https://" + url;
            } else {
                url = "https://www.google.com/search?q=" +
                      java.net.URLEncoder.encode(url, java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        this.url = url;
        // Auto-wrap image URLs in HTML to prevent native drag freeze
        if (isImageUrl(url)) {
            loadImageWrapped(url);
            return;
        }
        webView.loadUrl(url);
    }

    /**
     * Load an image wrapped in a safe HTML page.
     * This prevents native WebView image drag which can freeze touch input.
     * Also enables our custom long-press context menu on the image.
     */
    public void loadImageWrapped(String imageUrl) {
        this.url = imageUrl;
        this.title = "Image";
        String html = "<!DOCTYPE html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=5'>" +
            "<style>*{margin:0;padding:0;box-sizing:border-box;}body{background:#1A1111;min-height:100vh;display:flex;align-items:center;justify-content:center;flex-direction:column;padding:16px;}" +
            "img{max-width:100%;max-height:100vh;object-fit:contain;border-radius:4px;-webkit-user-drag:none;-webkit-touch-callout:none;user-select:none;draggable:false;}" +
            ".info{color:#DCC28A;margin-top:12px;font-family:system-ui;font-size:12px;text-align:center;word-break:break-all;opacity:0.7;}" +
            ".zoom-controls{position:fixed;bottom:24px;right:24px;display:flex;gap:8px;}" +
            ".zoom-btn{width:44px;height:44px;border-radius:50%;background:rgba(190,13,30,0.9);color:white;border:none;font-size:20px;cursor:pointer;display:flex;align-items:center;justify-content:center;}" +
            "</style></head><body>" +
            "<img id='viewImg' src='" + imageUrl.replace("'", "&#39;") + "' alt='Image'>" +
            "<div class='info'>" + imageUrl + "</div>" +
            "<div class='zoom-controls'>" +
            "<button class='zoom-btn' onclick='var i=document.getElementById(\"viewImg\");var c=parseFloat(i.style.zoom||1);i.style.zoom=Math.min(c+0.25,5);'>+</button>" +
            "<button class='zoom-btn' onclick='var i=document.getElementById(\"viewImg\");var c=parseFloat(i.style.zoom||1);i.style.zoom=Math.max(c-0.25,0.1);'>−</button>" +
            "<button class='zoom-btn' onclick='var i=document.getElementById(\"viewImg\");i.style.zoom=1;'>↺</button>" +
            "</div>" +
            "<script>document.addEventListener('dragstart',function(e){e.preventDefault();},true);" +
            "document.addEventListener('contextmenu',function(e){e.preventDefault();},true);</script>" +
            "</body></html>";
        webView.loadDataWithBaseURL(imageUrl, html, "text/html", "UTF-8", null);
    }

    public void goBack() {
        if (webView.canGoBack()) webView.goBack();
    }

    public void goForward() {
        if (webView.canGoForward()) webView.goForward();
    }

    public void reload() {
        webView.reload();
    }

    public void stopLoading() {
        webView.stopLoading();
    }

    public void evaluateJavaScript(String script) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            webView.evaluateJavascript(script, null);
        } else {
            webView.loadUrl("javascript:" + script);
        }
    }

    public void destroy() {
        webView.stopLoading();
        webView.loadUrl("about:blank");
        webView.clearHistory();
        webView.removeAllViews();
        webView.destroy();
    }

    public void onPause() {
        webView.onPause();
    }

    public void onResume() {
        webView.onResume();
    }

    // Getters and setters
    public String getId() { return id; }
    public WebView getWebView() { return webView; }
    public TabType getType() { return type; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title != null ? title : ""; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public Bitmap getFavicon() { return favicon; }
    public void setFavicon(Bitmap favicon) { this.favicon = favicon; }
    public boolean isLoading() { return isLoading; }
    public void setLoading(boolean loading) { isLoading = loading; }
    public boolean isDesktopMode() { return isDesktopMode; }
    public void setDesktopMode(boolean desktopMode) { isDesktopMode = desktopMode; }
    public long getCreatedAt() { return createdAt; }
    public boolean canGoBack() { return webView.canGoBack(); }
    public boolean canGoForward() { return webView.canGoForward(); }

    // Tab group
    public String getGroupName() { return groupName; }
    public void setGroupName(String name) { this.groupName = name; }
    public int getGroupColor() { return groupColor; }
    public void setGroupColor(int color) { this.groupColor = color; }
    public boolean isInGroup() { return groupName != null && !groupName.isEmpty(); }

    // Hit test results
    public String getHitResultUrl() { return hitResultUrl; }
    public String getHitResultImageUrl() { return hitResultImageUrl; }
    public String getHitResultTitle() { return hitResultTitle; }

    public interface TabListener {
        void onTitleChanged(BrowserTab tab, String title);
        void onUrlChanged(BrowserTab tab, String url);
        void onProgressChanged(BrowserTab tab, int progress);
        void onPageStarted(BrowserTab tab, String url, Bitmap favicon);
        void onPageFinished(BrowserTab tab, String url);
        void onLoadResource(BrowserTab tab, String url);
        void onReceivedError(BrowserTab tab, int errorCode, String description, String failingUrl);
        void onSslError(BrowserTab tab, String url);
        void onDownloadRequested(BrowserTab tab, String url, String userAgent, String contentDisposition, String mimetype, long contentLength);
        void onShowCustomView(BrowserTab tab, View view);
        void onHideCustomView(BrowserTab tab);
        void onConsoleMessage(BrowserTab tab, String message, int lineNumber, String sourceId);
        void onPageIconChanged(BrowserTab tab, Bitmap icon);
        // Context menu callbacks
        void onLongPressLink(BrowserTab tab, String url);
        void onLongPressImage(BrowserTab tab, String imageUrl);
    }

    private static class TabWebViewClient extends WebViewClient {
        private final TabListener listener;

        TabWebViewClient(TabListener listener) {
            this.listener = listener;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            if (listener != null) {
                Object tag = view.getTag();
                if (tag instanceof BrowserTab) listener.onPageStarted((BrowserTab) tag, url, favicon);
            }
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            // Inject anti-drag CSS/JS on every page to prevent native image drag
            try {
                view.evaluateJavascript(ANTI_DRAG_JS, null);
            } catch (Exception ignored) {}

            if (listener != null) {
                Object tag = view.getTag();
                if (tag instanceof BrowserTab) listener.onPageFinished((BrowserTab) tag, url);
            }
        }

        @Override
        public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
            if (listener != null) {
                Object tag = view.getTag();
                if (tag instanceof BrowserTab) listener.onUrlChanged((BrowserTab) tag, url);
            }
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, android.webkit.WebResourceRequest request) {
            return false;
        }

        @Override
        public void onReceivedError(WebView view, android.webkit.WebResourceRequest request, android.webkit.WebResourceError error) {
            if (listener != null && request.isForMainFrame()) {
                Object tag = view.getTag();
                if (tag instanceof BrowserTab) {
                    String desc = error.getDescription() != null ? error.getDescription().toString() : "Unknown error";
                    listener.onReceivedError((BrowserTab) tag, error.getErrorCode(), desc, request.getUrl().toString());
                }
            }
        }

        @Override
        public void onReceivedSslError(WebView view, android.webkit.SslErrorHandler handler, android.net.http.SslError error) {
            if (listener != null) {
                Object tag = view.getTag();
                if (tag instanceof BrowserTab) listener.onSslError((BrowserTab) tag, view.getUrl());
            }
            handler.cancel();
        }

        public void onLoadResource(WebView view, String url) {
            if (listener != null) {
                Object tag = view.getTag();
                if (tag instanceof BrowserTab) listener.onLoadResource((BrowserTab) tag, url);
            }
        }

        public void onReceivedIcon(WebView view, Bitmap icon) {
            if (listener != null) {
                Object tag = view.getTag();
                if (tag instanceof BrowserTab) listener.onPageIconChanged((BrowserTab) tag, icon);
            }
        }

        public void onReceivedTitle(WebView view, String title) {
            if (listener != null) {
                Object tag = view.getTag();
                if (tag instanceof BrowserTab) listener.onTitleChanged((BrowserTab) tag, title);
            }
        }
    }

    private static class TabChromeClient extends android.webkit.WebChromeClient {
        private final TabListener listener;

        TabChromeClient(TabListener listener) {
            this.listener = listener;
        }

        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            if (listener != null) {
                Object tag = view.getTag();
                if (tag instanceof BrowserTab) listener.onProgressChanged((BrowserTab) tag, newProgress);
            }
        }

        @Override
        public void onReceivedTitle(WebView view, String title) {
            if (listener != null) {
                Object tag = view.getTag();
                if (tag instanceof BrowserTab) listener.onTitleChanged((BrowserTab) tag, title);
            }
        }

        @Override
        public void onReceivedIcon(WebView view, Bitmap icon) {
            if (listener != null) {
                Object tag = view.getTag();
                if (tag instanceof BrowserTab) listener.onPageIconChanged((BrowserTab) tag, icon);
            }
        }

        @Override
        public boolean onConsoleMessage(android.webkit.ConsoleMessage consoleMessage) {
            if (listener != null) {
                listener.onConsoleMessage(null,
                    consoleMessage.message(), consoleMessage.lineNumber(), consoleMessage.sourceId());
            }
            return super.onConsoleMessage(consoleMessage);
        }

        @Override
        public void onShowCustomView(View view, android.webkit.WebChromeClient.CustomViewCallback callback) {
        }

        @Override
        public void onHideCustomView() {
        }

        @Override
        public boolean onShowFileChooser(WebView webView, android.webkit.ValueCallback<android.net.Uri[]> filePathCallback,
                                          android.webkit.WebChromeClient.FileChooserParams fileChooserParams) {
            return false;
        }

        @Override
        public void onGeolocationPermissionsShowPrompt(String origin, android.webkit.GeolocationPermissions.Callback callback) {
            callback.invoke(origin, true, false);
        }

        @Override
        public boolean onJsAlert(WebView view, String url, String message, android.webkit.JsResult result) {
            result.confirm();
            return true;
        }

        @Override
        public boolean onJsConfirm(WebView view, String url, String message, android.webkit.JsResult result) {
            result.confirm();
            return true;
        }

        @Override
        public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, android.webkit.JsPromptResult result) {
            result.confirm(defaultValue);
            return true;
        }

        @Override
        public void onPermissionRequest(final android.webkit.PermissionRequest request) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                request.grant(request.getResources());
            }
        }
    }
}
