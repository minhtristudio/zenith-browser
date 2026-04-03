package com.zenith.browser.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebView;

import androidx.appcompat.app.AppCompatActivity;

import com.zenith.browser.R;

import java.io.File;

public class ExtensionPopupActivity extends AppCompatActivity {

    private WebView popupWebView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.popup_extension);

        // Configure as floating popup
        Window window = getWindow();
        window.setGravity(Gravity.CENTER);
        WindowManager.LayoutParams params = window.getAttributes();
        params.width = WindowManager.LayoutParams.WRAP_CONTENT;
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.dimAmount = 0.5f;
        window.setAttributes(params);

        String extensionName = getIntent().getStringExtra("extension_name");
        if (extensionName == null) {
            finish();
            return;
        }

        setTitle(extensionName);

        popupWebView = new WebView(this);
        popupWebView.getSettings().setJavaScriptEnabled(true);
        popupWebView.getSettings().setDomStorageEnabled(true);

        com.google.android.material.card.MaterialCardView card =
            new com.google.android.material.card.MaterialCardView(this);
        card.addView(popupWebView);
        setContentView(card);

        // Load popup HTML from extension directory
        loadPopup(extensionName);
    }

    private void loadPopup(String extensionName) {
        File extDir = new File(getFilesDir(), "Extensions/" + extensionName);
        // Find the popup HTML
        if (extDir.exists()) {
            File[] files = extDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.getName().endsWith(".html") &&
                        (file.getName().equals("popup.html") || file.getName().startsWith("popup"))) {
                        popupWebView.loadUrl("file://" + file.getAbsolutePath());
                        return;
                    }
                }
            }
        }
        // No popup found
        finish();
    }

    @Override
    protected void onDestroy() {
        if (popupWebView != null) {
            popupWebView.stopLoading();
            popupWebView.destroy();
        }
        super.onDestroy();
    }
}
