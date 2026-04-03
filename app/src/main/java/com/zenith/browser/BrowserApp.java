package com.zenith.browser;

import android.app.Application;
import android.webkit.WebView;

public class BrowserApp extends Application {

    private static BrowserApp instance;
    private boolean isIncognitoMode = false;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        // Enable WebView debugging for devtools integration
        WebView.setWebContentsDebuggingEnabled(true);
    }

    public static BrowserApp getInstance() {
        return instance;
    }

    public boolean isIncognitoMode() {
        return isIncognitoMode;
    }

    public void setIncognitoMode(boolean incognitoMode) {
        isIncognitoMode = incognitoMode;
    }
}
