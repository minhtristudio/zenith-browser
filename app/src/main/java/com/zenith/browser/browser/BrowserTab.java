package com.zenith.browser.browser;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.zenith.browser.R;
import com.zenith.browser.extensions.ExtensionManager;

import java.io.File;
import java.util.UUID;

public class BrowserTab {

    public enum TabType {
        NORMAL, INCOGNITO, NEW_TAB
    }

    private final String id;
    private final WebView webView;
    private final TabType type;
    private String title = "New tab";
    private String url = "";
    private Bitmap favicon;
    private boolean isLoading = false;
    private boolean isDesktopMode = false;
    private final long createdAt;

    public BrowserTab(Context context, TabType type, TabListener listener) {
        this.id = UUID.randomUUID().toString();
        this.type = type;
        this.createdAt = System.currentTimeMillis();
        this.webView = new WebView(context) {
            @Override
            public void onStartTemporaryDetach() {
                super.onStartTemporaryDetach();
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

        // Extension JavaScript interface
        webView.addJavascriptInterface(new ExtensionManager.JsBridge(), "__zenithExtension");

        // Initialize with new tab page
        if (type == TabType.NEW_TAB || type == TabType.INCOGNITO) {
            url = "zenith://newtab";
        }
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
        webView.loadUrl(url);
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
    }

    private static class TabWebViewClient extends WebViewClient {
        private final TabListener listener;

        TabWebViewClient(TabListener listener) {
            this.listener = listener;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            if (listener != null) listener.onPageStarted(((BrowserTab) view.getTag()), url, favicon);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            if (listener != null) listener.onPageFinished(((BrowserTab) view.getTag()), url);
        }

        @Override
        public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
            if (listener != null) listener.onUrlChanged(((BrowserTab) view.getTag()), url);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, android.webkit.WebResourceRequest request) {
            return false; // Let WebView handle all URLs
        }

        @Override
        public void onReceivedError(WebView view, android.webkit.WebResourceRequest request, android.webkit.WebResourceError error) {
            if (listener != null && request.isForMainFrame()) {
                listener.onReceivedError((BrowserTab) view.getTag(),
                    error.getErrorCode(), error.getDescription().toString(),
                    request.getUrl().toString());
            }
        }

        @Override
        public void onReceivedSslError(WebView view, android.webkit.SslErrorHandler handler, android.net.http.SslError error) {
            if (listener != null) listener.onSslError((BrowserTab) view.getTag(), view.getUrl());
            handler.cancel();
        }

        @SuppressWarnings("deprecation")
        @Override
        public void onLoadResource(WebView view, String url) {
            if (listener != null) listener.onLoadResource((BrowserTab) view.getTag(), url);
        }

        @SuppressWarnings("deprecation")
        @Override
        public void onReceivedIcon(WebView view, Bitmap icon) {
            if (listener != null) listener.onPageIconChanged((BrowserTab) view.getTag(), icon);
        }

        @SuppressWarnings("deprecation")
        @Override
        public void onReceivedTitle(WebView view, String title) {
            if (listener != null) listener.onTitleChanged((BrowserTab) view.getTag(), title);
        }
    }

    private static class TabChromeClient extends android.webkit.WebChromeClient {
        private final TabListener listener;

        TabChromeClient(TabListener listener) {
            this.listener = listener;
        }

        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            if (listener != null) listener.onProgressChanged((BrowserTab) view.getTag(), newProgress);
        }

        @Override
        public void onReceivedTitle(WebView view, String title) {
            if (listener != null) listener.onTitleChanged((BrowserTab) view.getTag(), title);
        }

        @Override
        public void onReceivedIcon(WebView view, Bitmap icon) {
            if (listener != null) listener.onPageIconChanged((BrowserTab) view.getTag(), icon);
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
            // Store custom view (e.g., fullscreen video)
        }

        @Override
        public void onHideCustomView() {
        }

        @Override
        public boolean onShowFileChooser(WebView webView, android.webkit.ValueCallback<android.net.Uri[]> filePathCallback,
                                          android.webkit.WebChromeClient.FileChooserParams fileChooserParams) {
            return false; // Can be extended with file chooser dialog
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
            // Grant all permissions for full web compatibility
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                request.grant(request.getResources());
            }
        }
    }
}
