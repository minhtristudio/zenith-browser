package com.zenith.browser.browser;

import android.webkit.WebSettings;
import android.webkit.WebView;
import android.os.Build;

public class WebSettingsManager {

    public static void configure(WebView webView, boolean isIncognito) {
        WebSettings settings = webView.getSettings();

        // Enable JavaScript
        settings.setJavaScriptEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);

        // DOM Storage & Database
        settings.setDomStorageEnabled(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            settings.setMediaPlaybackRequiresUserGesture(false);
        }

        // Viewport
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);

        // File access
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            settings.setAllowFileAccessFromFileURLs(true);
            settings.setAllowUniversalAccessFromFileURLs(true);
        }

        // Mixed content
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        // Cache
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setDatabaseEnabled(true);

        // Text encoding
        settings.setDefaultTextEncodingName("UTF-8");

        // User agent
        settings.setUserAgentString(getUserAgent(webView, false));

        // Layout algorithm
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            settings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING);
        }

        // Request focus
        webView.requestFocus();

        // Third-party cookies
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        }

        // HTTP/2 and other modern features
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            // Enable safe browsing (optional)
            // settings.setSafeBrowsingEnabled(true);
        }
    }

    public static void enableDesktopMode(WebView webView) {
        WebSettings settings = webView.getSettings();
        settings.setUserAgentString(getUserAgent(webView, true));
        webView.getSettings().setUseWideViewPort(true);
    }

    public static void enableMobileMode(WebView webView) {
        WebSettings settings = webView.getSettings();
        settings.setUserAgentString(getUserAgent(webView, false));
    }

    private static String getUserAgent(WebView webView, boolean isDesktop) {
        String original = webView.getSettings().getUserAgentString();
        if (isDesktop) {
            // Chrome on Windows UA
            return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
        }
        // Add Zenith identifier to mobile UA
        if (original != null && !original.contains("ZenithBrowser")) {
            return original + " ZenithBrowser/1.0";
        }
        return original != null ? original : getDefaultUserAgent();
    }

    private static String getDefaultUserAgent() {
        return "Mozilla/5.0 (Linux; Android " + Build.VERSION.RELEASE + "; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 ZenithBrowser/1.0";
    }
}
