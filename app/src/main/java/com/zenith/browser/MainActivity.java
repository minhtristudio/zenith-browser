package com.zenith.browser;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.webkit.ValueCallback;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.zenith.browser.R;
import com.zenith.browser.browser.BrowserTab;
import com.zenith.browser.browser.SearchEngine;
import com.zenith.browser.browser.TabManager;
import com.zenith.browser.browser.UrlUtils;
import com.zenith.browser.browser.WebSettingsManager;
import com.zenith.browser.data.AppDatabase;
import com.zenith.browser.data.DownloadService;
import com.zenith.browser.devtools.DevToolsHelper;
import com.zenith.browser.extensions.ExtensionManager;
import com.zenith.browser.extensions.FileUtils;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.bottomappbar.BottomAppBar;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.card.MaterialCardView;

import android.webkit.WebChromeClient;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements BrowserTab.TabListener {

    private static final String TAG = "MainActivity";
    private static final String PREFS_NAME = "zenith_prefs";
    private static final String PREF_DEVTOOLS = "devtools_enabled";
    private static final String PREF_SEARCH_ENGINE = "search_engine";
    private static final String PREF_THEME = "theme";
    private static final int REQUEST_CODE_FILE_CHOOSER = 2001;
    private static final int REQUEST_CODE_PERMISSIONS = 3001;
    private static final int REQUEST_SETTINGS = 100;
    private static final int REQUEST_BOOKMARKS = 101;
    private static final int REQUEST_HISTORY = 102;
    private static final int REQUEST_EXTENSIONS = 103;

    // Views
    private CoordinatorLayout coordinator;
    private MaterialCardView urlBarContainer;
    private EditText etUrl;
    private ImageView ivSecurity;
    private View btnClearUrl;
    private ProgressBar progressBar;
    private FrameLayout tabContent;
    private BottomAppBar bottomBar;
    private FloatingActionButton fabNewTab;
    private View btnBack, btnForward, btnHome, btnBookmark, btnDevtools, btnTabCounter, btnMenu;
    private LinearLayout findInPageBar;
    private EditText etFind;
    private TextView tvFindCount;
    private LinearLayout snackbarArea;
    private AppBarLayout appBarLayout;

    // Core
    private TabManager tabManager;
    private ExtensionManager extensionManager;
    private AppDatabase db;
    private SharedPreferences prefs;

    // State
    private boolean isUrlBarFocused = false;
    private boolean isIncognito = false;
    private ValueCallback<Uri[]> filePathCallback;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Apply theme
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        applyTheme();

        // Initialize
        db = AppDatabase.getInstance(this);
        extensionManager = new ExtensionManager(this);

        // Setup DevTools
        boolean devToolsEnabled = prefs.getBoolean(PREF_DEVTOOLS, true);
        DevToolsHelper.setDevToolsEnabled(devToolsEnabled);

        // Initialize views
        initViews();

        // Setup tab manager
        tabManager = new TabManager(this);
        tabManager.setListener(new TabManager.TabManagerListener() {
            @Override
            public void onTabAdded(BrowserTab tab) {
                updateTabCounter();
            }

            @Override
            public void onTabRemoved(BrowserTab tab) {
                updateTabCounter();
            }

            @Override
            public void onTabSelected(BrowserTab tab) {
                attachTab(tab);
            }

            @Override
            public void onAllTabsClosed() {
                // Create a new tab when all are closed
                BrowserTab newTab = tabManager.createTab(isIncognito ? BrowserTab.TabType.INCOGNITO : BrowserTab.TabType.NEW_TAB, MainActivity.this);
                tabManager.selectTab(newTab);
            }
        });

        // Restore or create initial tab
        if (savedInstanceState != null) {
            tabManager.restoreState(savedInstanceState, this);
        } else {
            handleIntent(getIntent());
        }

        // Setup event listeners
        setupEventListeners();
        requestPermissions();
    }

    private void applyTheme() {
        String theme = prefs.getString(PREF_THEME, "follow_system");
        switch (theme) {
            case "light":
                setTheme(R.style.Theme_ZenithBrowser);
                break;
            case "dark":
                setTheme(R.style.Theme_ZenithBrowser_Incognito);
                break;
            default: // follow_system
                if (isIncognito) {
                    setTheme(R.style.Theme_ZenithBrowser_Incognito);
                }
                break;
        }
    }

    private void initViews() {
        coordinator = findViewById(R.id.coordinator);
        urlBarContainer = findViewById(R.id.url_bar_container);
        etUrl = findViewById(R.id.et_url);
        ivSecurity = findViewById(R.id.iv_security);
        btnClearUrl = findViewById(R.id.btn_clear_url);
        progressBar = findViewById(R.id.progress_bar);
        tabContent = findViewById(R.id.tab_content);
        bottomBar = findViewById(R.id.bottom_bar);
        fabNewTab = findViewById(R.id.fab_new_tab);
        findInPageBar = findViewById(R.id.find_in_page_bar);
        etFind = findViewById(R.id.et_find);
        tvFindCount = findViewById(R.id.tv_find_count);
        snackbarArea = findViewById(R.id.snackbar_area);

        btnBack = findViewById(R.id.btn_back);
        btnForward = findViewById(R.id.btn_forward);
        btnHome = findViewById(R.id.btn_home);
        btnBookmark = findViewById(R.id.btn_bookmark);
        btnDevtools = findViewById(R.id.btn_devtools);
        btnTabCounter = findViewById(R.id.btn_tab_counter);
        btnMenu = findViewById(R.id.btn_menu);
    }

    private void setupEventListeners() {
        // URL bar
        etUrl.setOnFocusChangeListener((v, hasFocus) -> {
            isUrlBarFocused = hasFocus;
            BrowserTab tab = tabManager.getActiveTab();
            if (hasFocus && tab != null) {
                etUrl.selectAll();
                ivSecurity.setVisibility(View.GONE);
                btnClearUrl.setVisibility(tab.getUrl().isEmpty() ? View.GONE : View.VISIBLE);
            } else if (!hasFocus && tab != null) {
                updateUrlBar(tab);
            }
        });

        etUrl.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO ||
                (event != null && event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                navigateToUrl(etUrl.getText().toString().trim());
                etUrl.clearFocus();
                return true;
            }
            return false;
        });

        btnClearUrl.setOnClickListener(v -> {
            etUrl.setText("");
            etUrl.requestFocus();
        });

        // Bottom bar buttons
        btnBack.setOnClickListener(v -> {
            BrowserTab tab = tabManager.getActiveTab();
            if (tab != null) tab.goBack();
        });

        btnForward.setOnClickListener(v -> {
            BrowserTab tab = tabManager.getActiveTab();
            if (tab != null) tab.goForward();
        });

        btnHome.setOnClickListener(v -> {
            BrowserTab tab = tabManager.getActiveTab();
            if (tab != null) tab.loadUrl("zenith://newtab");
        });

        btnBookmark.setOnClickListener(v -> toggleBookmark());

        btnDevtools.setOnClickListener(v -> {
            BrowserTab tab = tabManager.getActiveTab();
            if (tab != null) {
                if (DevToolsHelper.isDevToolsEnabled()) {
                    DevToolsHelper.toggleDevTools(tab.getWebView());
                } else {
                    Snackbar.make(coordinator, "Enable DevTools in Settings", Snackbar.LENGTH_SHORT).show();
                }
            }
        });

        fabNewTab.setOnClickListener(v -> openNewTab());

        btnTabCounter.setOnClickListener(v -> showTabsSheet());

        btnMenu.setOnClickListener(v -> showOverflowMenu());

        // Find in page
        etFind.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                findInPage(etFind.getText().toString());
                return true;
            }
            return false;
        });

        findViewById(R.id.btn_find_next).setOnClickListener(v -> findNext());
        findViewById(R.id.btn_find_prev).setOnClickListener(v -> findPrev());
        findViewById(R.id.btn_find_close).setOnClickListener(v -> {
            findInPageBar.setVisibility(View.GONE);
            BrowserTab tab = tabManager.getActiveTab();
            if (tab != null) tab.getWebView().clearMatches();
        });
    }

    private void handleIntent(Intent intent) {
        if (intent == null) {
            openNewTab();
            return;
        }

        String action = intent.getAction();
        Uri data = intent.getData();

        if (Intent.ACTION_VIEW.equals(action) && data != null) {
            String url = data.toString();
            BrowserTab tab = tabManager.createTab(BrowserTab.TabType.NORMAL, this);
            tab.loadUrl(url);
            tabManager.selectTab(tab);
        } else {
            openNewTab();
        }
    }

    private void openNewTab() {
        BrowserTab.TabType type = isIncognito ? BrowserTab.TabType.INCOGNITO : BrowserTab.TabType.NEW_TAB;
        BrowserTab tab = tabManager.createTab(type, this);
        tabManager.selectTab(tab);
    }

    private void openNewIncognitoTab() {
        isIncognito = true;
        BrowserTab tab = tabManager.createTab(BrowserTab.TabType.INCOGNITO, this);
        tabManager.selectTab(tab);
    }

    private void navigateToUrl(String input) {
        if (input.isEmpty()) return;
        BrowserTab tab = tabManager.getActiveTab();
        if (tab == null) {
            tab = tabManager.createTab(BrowserTab.TabType.NORMAL, this);
            tabManager.selectTab(tab);
        }
        String url = UrlUtils.normalize(input);
        tab.loadUrl(url);
    }

    private void attachTab(BrowserTab tab) {
        if (tab == null) return;

        // Remove current WebView from tab content
        WebView currentWebView = tabContent.findViewById(R.id.tab_webview);
        if (currentWebView != null) {
            tabContent.removeView(currentWebView);
        }

        // Add new WebView
        WebView webView = tab.getWebView();
        webView.setId(R.id.tab_webview);
        tabContent.removeAllViews();
        tabContent.addView(webView, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Update UI
        updateUrlBar(tab);
        updateNavigationButtons(tab);
        updateBookmarkButton(tab);

        // Inject DevTools and extensions
        webView.postDelayed(() -> {
            if (DevToolsHelper.isDevToolsEnabled()) {
                DevToolsHelper.initErudaFromAssets(this, webView);
            }
            extensionManager.injectExtensionApi(webView, tab.getUrl());
            extensionManager.injectContentScripts(webView, tab.getUrl());
        }, 500);
    }

    private void updateUrlBar(BrowserTab tab) {
        if (tab == null) return;
        String url = tab.getUrl();

        if (UrlUtils.isInternal(url) || url.isEmpty()) {
            etUrl.setText("");
            etUrl.setHint(R.string.search_hint);
            ivSecurity.setVisibility(View.GONE);
            btnClearUrl.setVisibility(View.GONE);
        } else {
            String displayUrl = UrlUtils.getDisplayUrl(url);
            etUrl.setText(displayUrl);
            if (UrlUtils.isSecure(url)) {
                ivSecurity.setVisibility(View.VISIBLE);
                ivSecurity.setImageResource(android.R.drawable.ic_lock_lock);
                ivSecurity.setColorFilter(ContextCompat.getColor(this, R.color.secure_connection));
            } else if (url.startsWith("http://")) {
                ivSecurity.setVisibility(View.VISIBLE);
                ivSecurity.setImageResource(android.R.drawable.ic_dialog_alert);
                ivSecurity.setColorFilter(ContextCompat.getColor(this, R.color.insecure_connection));
            } else {
                ivSecurity.setVisibility(View.GONE);
            }
            btnClearUrl.setVisibility(View.VISIBLE);
        }
    }

    private void updateNavigationButtons(BrowserTab tab) {
        if (tab == null) return;
        btnBack.setAlpha(tab.canGoBack() ? 1.0f : 0.3f);
        btnBack.setEnabled(tab.canGoBack());
        btnForward.setAlpha(tab.canGoForward() ? 1.0f : 0.3f);
        btnForward.setEnabled(tab.canGoForward());
    }

    private void updateBookmarkButton(BrowserTab tab) {
        if (tab == null) return;
        String url = tab.getUrl();
        if (url.startsWith("zenith://") || url.isEmpty()) {
            btnBookmark.setAlpha(0.3f);
            return;
        }
        boolean isBookmarked = db.isBookmarked(url);
        // Change icon based on bookmark state
        if (btnBookmark instanceof ImageView) {
            ((ImageView) btnBookmark).setImageResource(
                isBookmarked ? android.R.drawable.btn_star_big_on : android.R.drawable.btn_star_big_off);
        }
        btnBookmark.setAlpha(1.0f);
    }

    private void updateTabCounter() {
        int count = tabManager.getTabCount();
        if (btnTabCounter instanceof TextView) {
            ((TextView) btnTabCounter).setText(count > 0 ? String.valueOf(count) : "");
        }
        if (btnTabCounter instanceof MaterialButton) {
            ((MaterialButton) btnTabCounter).setText(count > 0 ? String.valueOf(count) : "");
        }
    }

    private void toggleBookmark() {
        BrowserTab tab = tabManager.getActiveTab();
        if (tab == null) return;
        String url = tab.getUrl();
        if (url.startsWith("zenith://") || url.isEmpty()) return;

        if (db.isBookmarked(url)) {
            // Find and remove
            List<AppDatabase.BookmarkItem> bookmarks = db.getAllBookmarks();
            for (AppDatabase.BookmarkItem item : bookmarks) {
                if (item.url.equals(url)) {
                    db.removeBookmark(item.id);
                    break;
                }
            }
            Snackbar.make(coordinator, R.string.bookmark_removed, Snackbar.LENGTH_SHORT).show();
        } else {
            db.addBookmark(tab.getTitle(), url);
            Snackbar.make(coordinator, R.string.bookmark_added, Snackbar.LENGTH_SHORT).show();
        }
        updateBookmarkButton(tab);
    }

    private void showTabsSheet() {
        BottomSheetDialog bottomSheet = new BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.dialog_tab_list, null);
        bottomSheet.setContentView(sheetView);

        RecyclerView recyclerView = sheetView.findViewById(R.id.recycler_tabs);
        List<BrowserTab> tabs = tabManager.getTabs();
        recyclerView.setLayoutManager(new GridLayoutManager(this, 3));
        TabSheetAdapter adapter = new TabSheetAdapter(tabs, tab -> {
            tabManager.selectTab(tab);
            bottomSheet.dismiss();
        }, tab -> {
            tabManager.closeTab(tab);
            adapter.setItems(tabManager.getTabs());
            if (tabManager.getTabs().isEmpty()) {
                bottomSheet.dismiss();
            }
        });
        recyclerView.setAdapter(adapter);

        sheetView.findViewById(R.id.fab_new_tab).setOnClickListener(v -> {
            openNewTab();
            bottomSheet.dismiss();
        });

        bottomSheet.show();
    }

    private void showOverflowMenu() {
        BrowserTab tab = tabManager.getActiveTab();

        String[] menuItems;
        if (tab != null && tab.getUrl() != null && !tab.getUrl().startsWith("zenith://")) {
            menuItems = new String[]{
                "New tab", "New incognito tab", null,
                "Share", "Find in page", null,
                "Desktop site" + (tab.isDesktopMode() ? " \u2713" : ""), null,
                "Developer Tools", "View source", null,
                "Bookmarks", "History", "Downloads", "Extensions", null,
                "Settings"
            };
        } else {
            menuItems = new String[]{
                "New tab", "New incognito tab", null,
                "Bookmarks", "History", "Downloads", "Extensions", null,
                "Settings"
            };
        }

        new MaterialAlertDialogBuilder(this)
            .setItems(menuItems, (dialog, which) -> {
                String item = menuItems[which];
                if (item == null) return;

                if (item.equals("New tab")) openNewTab();
                else if (item.equals("New incognito tab")) openNewIncognitoTab();
                else if (item.equals("Share")) sharePage();
                else if (item.equals("Find in page")) showFindInPage();
                else if (item.startsWith("Desktop site")) toggleDesktopMode();
                else if (item.equals("Developer Tools")) {
                    if (tab != null && DevToolsHelper.isDevToolsEnabled()) {
                        DevToolsHelper.toggleDevTools(tab.getWebView());
                    }
                }
                else if (item.equals("View source")) viewSource();
                else if (item.equals("Bookmarks")) startActivityForResult(new Intent(this, com.zenith.browser.ui.BookmarksActivity.class), REQUEST_BOOKMARKS);
                else if (item.equals("History")) startActivityForResult(new Intent(this, com.zenith.browser.ui.HistoryActivity.class), REQUEST_HISTORY);
                else if (item.equals("Downloads")) startActivity(new Intent(this, com.zenith.browser.ui.DownloadsActivity.class));
                else if (item.equals("Extensions")) startActivityForResult(new Intent(this, com.zenith.browser.ui.ExtensionsActivity.class), REQUEST_EXTENSIONS);
                else if (item.equals("Settings")) startActivityForResult(new Intent(this, com.zenith.browser.ui.SettingsActivity.class), REQUEST_SETTINGS);
            })
            .show();
    }

    private void sharePage() {
        BrowserTab tab = tabManager.getActiveTab();
        if (tab == null) return;
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, tab.getUrl());
        share.putExtra(Intent.EXTRA_SUBJECT, tab.getTitle());
        startActivity(Intent.createChooser(share, "Share"));
    }

    private void showFindInPage() {
        findInPageBar.setVisibility(View.VISIBLE);
        etFind.requestFocus();
    }

    private void findInPage(String query) {
        BrowserTab tab = tabManager.getActiveTab();
        if (tab == null || query.isEmpty()) return;
        tab.getWebView().findAllAsync(query);
    }

    private void findNext() {
        BrowserTab tab = tabManager.getActiveTab();
        if (tab != null) tab.getWebView().findNext(true);
    }

    private void findPrev() {
        BrowserTab tab = tabManager.getActiveTab();
        if (tab != null) tab.getWebView().findNext(false);
    }

    private void toggleDesktopMode() {
        BrowserTab tab = tabManager.getActiveTab();
        if (tab == null) return;
        tab.setDesktopMode(!tab.isDesktopMode());
        if (tab.isDesktopMode()) {
            WebSettingsManager.enableDesktopMode(tab.getWebView());
        } else {
            WebSettingsManager.enableMobileMode(tab.getWebView());
        }
        tab.reload();
    }

    private void viewSource() {
        BrowserTab tab = tabManager.getActiveTab();
        if (tab == null) return;
        DevToolsHelper.getSource(tab.getWebView(), source -> {
            if (source != null) {
                // Show source in new tab
                BrowserTab sourceTab = tabManager.createTab(BrowserTab.TabType.NORMAL, this);
                String encoded = android.net.Uri.encode(source);
                sourceTab.getWebView().loadUrl("data:text/html;charset=utf-8," + encoded);
                tabManager.selectTab(sourceTab);
            }
        });
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            List<String> permissions = new ArrayList<>();
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(android.Manifest.permission.ACCESS_FINE_LOCATION);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    permissions.add(android.Manifest.permission.POST_NOTIFICATIONS);
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (!android.os.Environment.isExternalStorageManager()) {
                    // Optionally request MANAGE_EXTERNAL_STORAGE
                }
            }
            if (!permissions.isEmpty()) {
                requestPermissions(permissions.toArray(new String[0]), REQUEST_CODE_PERMISSIONS);
            }
        }
    }

    // ==================== TabListener Implementation ====================

    @Override
    public void onTitleChanged(BrowserTab tab, String title) {
        if (tab == tabManager.getActiveTab()) {
            // Title updated - could update toolbar or recents
            updateBookmarkButton(tab);
        }
    }

    @Override
    public void onUrlChanged(BrowserTab tab, String url) {
        if (tab == tabManager.getActiveTab()) {
            if (!isUrlBarFocused) updateUrlBar(tab);
            updateNavigationButtons(tab);
        }
    }

    @Override
    public void onProgressChanged(BrowserTab tab, int progress) {
        if (tab == tabManager.getActiveTab()) {
            if (progress == 100) {
                progressBar.setVisibility(View.GONE);
            } else {
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setProgress(progress);
            }
        }
    }

    @Override
    public void onPageStarted(BrowserTab tab, String url, Bitmap favicon) {
        if (tab == tabManager.getActiveTab()) {
            progressBar.setVisibility(View.VISIBLE);
            progressBar.setProgress(0);
            tab.setLoading(true);
        }
    }

    @Override
    public void onPageFinished(BrowserTab tab, String url) {
        if (tab == tabManager.getActiveTab()) {
            progressBar.setVisibility(View.GONE);
            tab.setLoading(false);
            updateUrlBar(tab);
            updateNavigationButtons(tab);
            updateBookmarkButton(tab);

            // Save to history
            db.addHistory(tab.getTitle(), url);

            // Inject extensions after page load
            extensionManager.injectExtensionApi(tab.getWebView(), url);
            extensionManager.injectContentScripts(tab.getWebView(), url);
        }
    }

    @Override
    public void onLoadResource(BrowserTab tab, String url) {}

    @Override
    public void onReceivedError(BrowserTab tab, int errorCode, String description, String failingUrl) {
        if (tab == tabManager.getActiveTab()) {
            progressBar.setVisibility(View.GONE);
        }
    }

    @Override
    public void onSslError(BrowserTab tab, String url) {
        if (tab == tabManager.getActiveTab()) {
            new MaterialAlertDialogBuilder(this)
                .setTitle("Security Warning")
                .setMessage(R.string.ssl_error)
                .setPositiveButton(R.string.ssl_error_continue, (dialog, which) -> {
                    // Continue loading
                })
                .setNegativeButton(R.string.ssl_error_go_back, (dialog, which) -> {
                    tab.goBack();
                })
                .show();
        }
    }

    @Override
    public void onDownloadRequested(BrowserTab tab, String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
        // Record download
        String fileName = "download";
        if (contentDisposition != null) {
            String[] parts = contentDisposition.split(";");
            for (String part : parts) {
                part = part.trim();
                if (part.startsWith("filename=")) {
                    fileName = part.substring("filename=".length()).replace("\"", "").trim();
                    break;
                }
            }
        }
        if (fileName.equals("download")) {
            fileName = url.substring(url.lastIndexOf('/') + 1);
            if (fileName.contains("?")) fileName = fileName.substring(0, fileName.indexOf('?'));
        }

        db.addDownload(fileName, url, mimetype, contentLength);

        // Start download service
        Intent serviceIntent = new Intent(this, DownloadService.class);
        serviceIntent.putExtra("url", url);
        serviceIntent.putExtra("userAgent", userAgent);
        serviceIntent.putExtra("contentDisposition", contentDisposition);
        serviceIntent.putExtra("mimeType", mimetype);
        serviceIntent.putExtra("contentLength", contentLength);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        Snackbar.make(coordinator, "Downloading: " + fileName, Snackbar.LENGTH_LONG).show();
    }

    @Override
    public void onShowCustomView(BrowserTab tab, View view) {
        // Fullscreen video
    }

    @Override
    public void onHideCustomView(BrowserTab tab) {}

    @Override
    public void onConsoleMessage(BrowserTab tab, String message, int lineNumber, String sourceId) {
        Log.d("Console", message);
    }

    @Override
    public void onPageIconChanged(BrowserTab tab, Bitmap icon) {
        tab.setFavicon(icon);
    }

    // ==================== Activity Lifecycle ====================

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        tabManager.saveState(outState);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SETTINGS) {
            // Reload settings
            boolean devToolsEnabled = prefs.getBoolean(PREF_DEVTOOLS, true);
            DevToolsHelper.setDevToolsEnabled(devToolsEnabled);
            String theme = prefs.getString(PREF_THEME, "follow_system");
            if (!theme.equals(getCurrentTheme())) {
                recreate();
            }
        } else if (requestCode == REQUEST_BOOKMARKS && resultCode == RESULT_OK && data != null) {
            String url = data.getStringExtra("url");
            if (url != null) {
                BrowserTab tab = tabManager.getActiveTab();
                if (tab != null) tab.loadUrl(url);
            }
        } else if (requestCode == REQUEST_HISTORY && resultCode == RESULT_OK && data != null) {
            String url = data.getStringExtra("url");
            if (url != null) {
                BrowserTab tab = tabManager.getActiveTab();
                if (tab != null) tab.loadUrl(url);
            }
        } else if (requestCode == REQUEST_CODE_FILE_CHOOSER && resultCode == RESULT_OK) {
            // Handle file chooser result
        }
    }

    private String getCurrentTheme() {
        return prefs.getString(PREF_THEME, "follow_system");
    }

    @Override
    public void onBackPressed() {
        BrowserTab tab = tabManager.getActiveTab();

        // Check if DevTools is showing
        if (tab != null && DevToolsHelper.isDevToolsEnabled()) {
            DevToolsHelper.hideDevTools(tab.getWebView());
        }

        // Check if find in page is showing
        if (findInPageBar.getVisibility() == View.VISIBLE) {
            findInPageBar.setVisibility(View.GONE);
            if (tab != null) tab.getWebView().clearMatches();
            return;
        }

        // Check if URL bar is focused
        if (isUrlBarFocused) {
            etUrl.clearFocus();
            return;
        }

        // Check if WebView can go back
        if (tab != null && tab.canGoBack() && !tab.getUrl().startsWith("zenith://")) {
            tab.goBack();
            return;
        }

        // If only one tab and it's new tab page, exit
        if (tabManager.getTabCount() <= 1) {
            if (tab != null && tab.getUrl().startsWith("zenith://")) {
                super.onBackPressed();
                return;
            }
        }

        // Close current tab
        if (tabManager.getTabCount() > 1) {
            tabManager.closeTab(tab);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (tabManager.getActiveTab() != null) {
            tabManager.getActiveTab().onPause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (tabManager.getActiveTab() != null) {
            tabManager.getActiveTab().onResume();
        }
        // Refresh preferences
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tabManager != null) {
            tabManager.closeAllTabs();
        }
    }

    // ==================== Tab Sheet Adapter ====================

    private static class TabSheetAdapter extends RecyclerView.Adapter<TabSheetAdapter.ViewHolder> {
        private List<BrowserTab> tabs;
        private final TabClickListener clickListener;
        private final TabCloseListener closeListener;

        interface TabClickListener { void onClick(BrowserTab tab); }
        interface TabCloseListener { void onClose(BrowserTab tab); }

        TabSheetAdapter(List<BrowserTab> tabs, TabClickListener clickListener, TabCloseListener closeListener) {
            this.tabs = tabs;
            this.clickListener = clickListener;
            this.closeListener = closeListener;
        }

        void setItems(List<BrowserTab> tabs) {
            this.tabs = tabs;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_tab, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            BrowserTab tab = tabs.get(position);
            holder.tvTitle.setText(tab.getTitle());

            if (tab.getFavicon() != null) {
                holder.ivFavicon.setImageBitmap(tab.getFavicon());
            }

            holder.itemView.setOnClickListener(v -> {
                if (clickListener != null) clickListener.onClick(tab);
            });

            holder.ivClose.setOnClickListener(v -> {
                if (closeListener != null) closeListener.onClose(tab);
            });
        }

        @Override
        public int getItemCount() { return tabs != null ? tabs.size() : 0; }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvTitle;
            ImageView ivFavicon, ivClose;

            ViewHolder(View view) {
                super(view);
                tvTitle = view.findViewById(R.id.tv_title);
                ivFavicon = view.findViewById(R.id.iv_favicon);
                ivClose = view.findViewById(R.id.iv_close);
            }
        }
    }
}
