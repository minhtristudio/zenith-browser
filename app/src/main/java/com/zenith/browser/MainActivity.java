package com.zenith.browser;

import android.animation.ValueAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
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
import com.zenith.browser.extensions.UserscriptManager;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;

import android.webkit.WebChromeClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
    private static final int REQUEST_VIEW_IMAGE = 104;

    // Views
    private CoordinatorLayout coordinator;
    private MaterialCardView urlBarContainer;
    private EditText etUrl;
    private ImageView ivSecurity;
    private View btnClearUrl;
    private ProgressBar progressBar;
    private FrameLayout tabContent;
    private View bottomBar;
    private View btnBack, btnForward, btnHome, btnBookmark, btnDevtools, btnTabCounter, btnMenu;
    private LinearLayout findInPageBar;
    private EditText etFind;
    private TextView tvFindCount;
    private LinearLayout snackbarArea;
    private AppBarLayout appBarLayout;

    // Core
    private TabManager tabManager;
    private ExtensionManager extensionManager;
    private UserscriptManager userscriptManager;
    private AppDatabase db;
    private SharedPreferences prefs;

    // State
    private boolean isUrlBarFocused = false;
    private boolean isIncognito = false;
    private ValueCallback<Uri[]> filePathCallback;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;

    // Context menu state
    private String contextLinkUrl = null;
    private String contextImageUrl = null;

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
        userscriptManager = UserscriptManager.getInstance(this);

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

    /**
     * Open a URL in a new tab, optionally in a tab group.
     */
    private void openInNewTab(String url, String groupName) {
        BrowserTab tab = tabManager.createTab(BrowserTab.TabType.NORMAL, this);
        if (groupName != null && !groupName.isEmpty()) {
            tabManager.assignTabToGroup(tab, groupName);
        }
        tab.loadUrl(url);
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

        WebView currentWebView = tabContent.findViewById(R.id.tab_webview);
        if (currentWebView != null) {
            tabContent.removeView(currentWebView);
        }

        WebView webView = tab.getWebView();
        webView.setTag(tab);
        webView.setId(R.id.tab_webview);
        tabContent.removeAllViews();
        tabContent.addView(webView, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        updateUrlBar(tab);
        updateNavigationButtons(tab);
        updateBookmarkButton(tab);

        webView.postDelayed(() -> {
            if (DevToolsHelper.isDevToolsEnabled()) {
                DevToolsHelper.initErudaFromAssets(this, webView);
            }
            extensionManager.injectExtensionApi(webView, tab.getUrl());
            extensionManager.injectContentScripts(webView, tab.getUrl());
            // Inject userscripts (Tampermonkey-like) - includes MITM pre-load
            if (userscriptManager != null && tab.getUrl() != null && !tab.getUrl().startsWith("zenith://")) {
                List<UserscriptManager.Userscript> scripts = userscriptManager.getScriptsForUrl(tab.getUrl());
                for (UserscriptManager.Userscript script : scripts) {
                    String injectScript = userscriptManager.buildInjectionScript(script);
                    if (!injectScript.isEmpty()) {
                        try {
                            webView.evaluateJavascript(injectScript, null);
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to inject userscript: " + script.name, e);
                        }
                    }
                }
            }
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
        if (url == null || url.startsWith("zenith://") || url.isEmpty()) {
            btnBookmark.setAlpha(0.3f);
            return;
        }
        try {
            boolean isBookmarked = db.isBookmarked(url);
            if (btnBookmark instanceof MaterialButton) {
                ((MaterialButton) btnBookmark).setIcon(
                    ContextCompat.getDrawable(this, isBookmarked ? R.drawable.ic_bookmark_filled : R.drawable.ic_bookmark_add));
            }
            btnBookmark.setAlpha(1.0f);
        } catch (Exception e) {
            Log.e(TAG, "Error updating bookmark", e);
        }
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

    // ==================== Tab Sheet with Groups ====================

    private void showTabsSheet() {
        BottomSheetDialog bottomSheet = new BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.dialog_tab_list, null);
        bottomSheet.setContentView(sheetView);

        TextView tvHeader = sheetView.findViewById(R.id.tv_tab_count_header);
        RecyclerView recyclerView = sheetView.findViewById(R.id.recycler_tabs);
        View groupScroll = sheetView.findViewById(R.id.group_scroll);
        LinearLayout groupChips = sheetView.findViewById(R.id.group_chips);
        View groupDivider = sheetView.findViewById(R.id.group_divider);

        // New tab button
        sheetView.findViewById(R.id.btn_new_tab).setOnClickListener(v -> {
            openNewTab();
            bottomSheet.dismiss();
        });

        // Close all button
        sheetView.findViewById(R.id.btn_close_all).setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(this)
                .setTitle("Close all tabs?")
                .setPositiveButton("Close all", (dialog, which) -> {
                    tabManager.closeAllTabs();
                    bottomSheet.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
        });

        // Create group button
        sheetView.findViewById(R.id.btn_create_group).setOnClickListener(v -> showCreateGroupDialog(bottomSheet));

        // Setup group chips
        String currentFilter = null;
        String[] filterHolder = {null};

        Set<String> groupNames = tabManager.getGroupNames();
        if (!groupNames.isEmpty()) {
            groupScroll.setVisibility(View.VISIBLE);
            groupDivider.setVisibility(View.VISIBLE);
            groupChips.removeAllViews();

            // "All" chip
            Chip allChip = new Chip(this);
            allChip.setText("All");
            allChip.setChipBackgroundColorResource(R.color.md_theme_primary);
            allChip.setTextColor(ContextCompat.getColor(this, R.color.md_theme_on_primary));
            allChip.setCheckable(true);
            allChip.setChecked(true);
            allChip.setOnClickListener(chip -> {
                filterHolder[0] = null;
                updateTabList(recyclerView, null, bottomSheet);
                // Update chip states
                for (int i = 0; i < groupChips.getChildCount(); i++) {
                    ((Chip) groupChips.getChildAt(i)).setChecked(i == 0);
                }
            });
            groupChips.addView(allChip);

            for (String name : groupNames) {
                Chip chip = new Chip(this);
                chip.setText(name);
                int color = tabManager.getGroupColor(name);
                chip.setChipBackgroundColor(color);
                chip.setTextColor(Color.WHITE);
                chip.setCheckable(true);
                chip.setOnClickListener(c -> {
                    filterHolder[0] = name;
                    updateTabList(recyclerView, name, bottomSheet);
                    for (int i = 0; i < groupChips.getChildCount(); i++) {
                        ((Chip) groupChips.getChildAt(i)).setChecked(false);
                    }
                    chip.setChecked(true);
                });
                // Long press to rename/delete group
                chip.setOnLongClickListener(c -> {
                    showGroupOptionsDialog(name, bottomSheet);
                    return true;
                });
                groupChips.addView(chip);
            }
        }

        updateTabList(recyclerView, null, bottomSheet);
        bottomSheet.show();
    }

    private void updateTabList(RecyclerView recyclerView, String filterGroup, BottomSheetDialog bottomSheet) {
        List<BrowserTab> tabsToShow;
        if (filterGroup != null) {
            tabsToShow = tabManager.getTabsInGroup(filterGroup);
        } else {
            tabsToShow = tabManager.getTabs();
        }

        TabSheetAdapter[] adapterHolder = new TabSheetAdapter[1];
        adapterHolder[0] = new TabSheetAdapter(tabsToShow, tab -> {
            tabManager.selectTab(tab);
            bottomSheet.dismiss();
        }, tab -> {
            tabManager.closeTab(tab);
            adapterHolder[0].setItems(tabManager.getTabs());
            if (tabManager.getTabs().isEmpty()) {
                bottomSheet.dismiss();
            }
        });
        recyclerView.setLayoutManager(new GridLayoutManager(this, 3));
        recyclerView.setAdapter(adapterHolder[0]);
    }

    private void showCreateGroupDialog(BottomSheetDialog bottomSheet) {
        EditText input = new EditText(this);
        input.setHint("Group name");
        new MaterialAlertDialogBuilder(this)
            .setTitle("Create tab group")
            .setView(input)
            .setPositiveButton(R.string.ok, (dialog, which) -> {
                String name = input.getText().toString().trim();
                if (!name.isEmpty()) {
                    tabManager.createGroup(name);
                    bottomSheet.dismiss();
                    showTabsSheet(); // Refresh
                }
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    private void showGroupOptionsDialog(String groupName, BottomSheetDialog bottomSheet) {
        new MaterialAlertDialogBuilder(this)
            .setTitle("Group: " + groupName)
            .setItems(new String[]{"Rename", "Change color", "Ungroup all tabs", "Delete group"}, (dialog, which) -> {
                switch (which) {
                    case 0: // Rename
                        EditText renameInput = new EditText(this);
                        renameInput.setText(groupName);
                        new MaterialAlertDialogBuilder(this)
                            .setTitle("Rename group")
                            .setView(renameInput)
                            .setPositiveButton(R.string.ok, (d, w) -> {
                                String newName = renameInput.getText().toString().trim();
                                if (!newName.isEmpty()) {
                                    tabManager.renameGroup(groupName, newName);
                                    bottomSheet.dismiss();
                                    showTabsSheet();
                                }
                            })
                            .setNegativeButton(R.string.cancel, null)
                            .show();
                        break;
                    case 1: // Change color
                        showColorPickerDialog(groupName, bottomSheet);
                        break;
                    case 2: // Ungroup
                        tabManager.removeGroup(groupName);
                        bottomSheet.dismiss();
                        showTabsSheet();
                        break;
                    case 3: // Delete
                        tabManager.removeGroup(groupName);
                        bottomSheet.dismiss();
                        showTabsSheet();
                        break;
                }
            })
            .show();
    }

    private void showColorPickerDialog(String groupName, BottomSheetDialog bottomSheet) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setPadding(32, 24, 32, 24);
        layout.setGravity(Gravity.CENTER);

        for (int i = 0; i < BrowserTab.GROUP_COLORS.length; i++) {
            int color = BrowserTab.GROUP_COLORS[i];
            View swatch = new View(this);
            swatch.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) swatch.getLayoutParams();
            lp.height = 120;
            lp.setMargins(8, 0, 8, 0);
            swatch.setLayoutParams(lp);
            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.OVAL);
            shape.setColor(color);
            swatch.setBackground(shape);
            final int c = color;
            swatch.setOnClickListener(v -> {
                tabManager.setGroupColor(groupName, c);
                bottomSheet.dismiss();
                showTabsSheet();
            });
            layout.addView(swatch);
        }

        new MaterialAlertDialogBuilder(this)
            .setTitle("Pick group color")
            .setView(layout)
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    // ==================== Context Menu (Long Press) ====================

    @Override
    public void onLongPressLink(BrowserTab tab, String url) {
        showLinkContextMenu(url);
    }

    @Override
    public void onLongPressImage(BrowserTab tab, String imageUrl) {
        showImageContextMenu(imageUrl);
    }

    private void showLinkContextMenu(String url) {
        contextLinkUrl = url;
        List<String> items = new ArrayList<>();
        items.add("Open in new tab");
        items.add("Open in new tab (background)");

        // Add group options if groups exist
        Set<String> groups = tabManager.getGroupNames();
        if (!groups.isEmpty()) {
            items.add(null); // divider
            items.add("Open in group...");
        }

        items.add(null); // divider
        items.add("Copy link");
        items.add("Share link");

        String[] displayItems = items.toArray(new String[0]);
        // Filter nulls for the dialog
        List<String> filtered = new ArrayList<>();
        for (String s : displayItems) {
            if (s != null) filtered.add(s);
        }

        new MaterialAlertDialogBuilder(this)
            .setTitle("Link")
            .setItems(filtered.toArray(new String[0]), (dialog, which) -> {
                String item = filtered.get(which);
                if (item == null) return;

                if (item.equals("Open in new tab")) {
                    openInNewTab(url, null);
                } else if (item.equals("Open in new tab (background)")) {
                    BrowserTab tab = tabManager.createTab(BrowserTab.TabType.NORMAL, this);
                    tab.loadUrl(url);
                    // Don't select - opens in background
                    Snackbar.make(coordinator, "Tab opened in background", Snackbar.LENGTH_SHORT).show();
                } else if (item.equals("Open in group...")) {
                    showOpenInGroupDialog(url);
                } else if (item.equals("Copy link")) {
                    copyToClipboard(url);
                    Snackbar.make(coordinator, "Link copied", Snackbar.LENGTH_SHORT).show();
                } else if (item.equals("Share link")) {
                    Intent share = new Intent(Intent.ACTION_SEND);
                    share.setType("text/plain");
                    share.putExtra(Intent.EXTRA_TEXT, url);
                    startActivity(Intent.createChooser(share, "Share"));
                }
            })
            .show();
    }

    private void showImageContextMenu(String imageUrl) {
        contextImageUrl = imageUrl;
        List<String> items = new ArrayList<>();
        items.add("View image");
        items.add("Download image");
        items.add("Open in new tab");

        Set<String> groups = tabManager.getGroupNames();
        if (!groups.isEmpty()) {
            items.add("Open in group...");
        }

        items.add("Copy image URL");

        String[] displayItems = items.toArray(new String[0]);

        new MaterialAlertDialogBuilder(this)
            .setTitle("Image")
            .setItems(displayItems, (dialog, which) -> {
                String item = displayItems[which];
                if (item == null) return;

                if (item.equals("View image")) {
                    // Open image in new tab
                    BrowserTab tab = tabManager.createTab(BrowserTab.TabType.NORMAL, this);
                    tab.loadUrl(imageUrl);
                    tabManager.selectTab(tab);
                } else if (item.equals("Download image")) {
                    // Start download
                    downloadFile(imageUrl, null, "image/*", -1);
                    Snackbar.make(coordinator, "Downloading image...", Snackbar.LENGTH_SHORT).show();
                } else if (item.equals("Open in new tab")) {
                    openInNewTab(imageUrl, null);
                } else if (item.equals("Open in group...")) {
                    showOpenInGroupDialog(imageUrl);
                } else if (item.equals("Copy image URL")) {
                    copyToClipboard(imageUrl);
                    Snackbar.make(coordinator, "Image URL copied", Snackbar.LENGTH_SHORT).show();
                }
            })
            .show();
    }

    private void showOpenInGroupDialog(String url) {
        Set<String> groups = tabManager.getGroupNames();
        if (groups.isEmpty()) {
            Snackbar.make(coordinator, "No groups created. Create one from the tab switcher.", Snackbar.LENGTH_SHORT).show();
            return;
        }

        String[] groupArray = groups.toArray(new String[0]);
        new MaterialAlertDialogBuilder(this)
            .setTitle("Open in group")
            .setItems(groupArray, (dialog, which) -> {
                String groupName = groupArray[which];
                openInNewTab(url, groupName);
                Snackbar.make(coordinator, "Opened in " + groupName, Snackbar.LENGTH_SHORT).show();
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    private void downloadFile(String url, String userAgent, String mimeType, long contentLength) {
        String fileName = url.substring(url.lastIndexOf('/') + 1);
        if (fileName.contains("?")) fileName = fileName.substring(0, fileName.indexOf('?'));
        if (fileName.isEmpty()) fileName = "download_" + System.currentTimeMillis();

        db.addDownload(fileName, url, mimeType, contentLength);

        Intent serviceIntent = new Intent(this, DownloadService.class);
        serviceIntent.putExtra("url", url);
        serviceIntent.putExtra("userAgent", userAgent != null ? userAgent : "Mozilla/5.0");
        serviceIntent.putExtra("contentDisposition", "");
        serviceIntent.putExtra("mimeType", mimeType);
        serviceIntent.putExtra("contentLength", contentLength);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void copyToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Zenith Browser", text);
        clipboard.setPrimaryClip(clip);
    }

    // ==================== Overflow Menu ====================

    private void showOverflowMenu() {
        try {
            BrowserTab tab = tabManager.getActiveTab();

            List<String> menuItems = new ArrayList<>();
            boolean onWebPage = tab != null && tab.getUrl() != null && !tab.getUrl().startsWith("zenith://");

            menuItems.add("New tab");
            menuItems.add("New incognito tab");
            menuItems.add(null);

            if (onWebPage) {
                menuItems.add("Share");
                menuItems.add("Find in page");
                menuItems.add("Desktop site" + (tab.isDesktopMode() ? " \u2713" : ""));
                menuItems.add(null);
                menuItems.add("Developer Tools");
                menuItems.add("View source");
                menuItems.add(null);
            }

            menuItems.add("Bookmarks");
            menuItems.add("History");
            menuItems.add("Downloads");
            menuItems.add("Extensions");
            menuItems.add("Userscripts");
            menuItems.add(null);
            menuItems.add("Settings");

            final String[] displayItems = new String[menuItems.size()];
            final int[] itemIndexMap = new int[menuItems.size()];
            int displayPos = 0;
            for (int i = 0; i < menuItems.size(); i++) {
                String item = menuItems.get(i);
                if (item != null) {
                    displayItems[displayPos] = item;
                    itemIndexMap[displayPos] = i;
                    displayPos++;
                }
            }

            String[] finalItems = new String[displayPos];
            System.arraycopy(displayItems, 0, finalItems, 0, displayPos);

            new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.menu)
                .setItems(finalItems, (dialog, which) -> {
                    String item = finalItems[which];
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
                    else if (item.equals("Userscripts")) startActivity(new Intent(this, com.zenith.browser.ui.UserscriptsActivity.class));
                    else if (item.equals("Settings")) startActivityForResult(new Intent(this, com.zenith.browser.ui.SettingsActivity.class), REQUEST_SETTINGS);
                })
                .show();
        } catch (Exception e) {
            Log.e(TAG, "Error showing menu", e);
            Toast.makeText(this, "Error opening menu", Toast.LENGTH_SHORT).show();
        }
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
            if (!permissions.isEmpty()) {
                requestPermissions(permissions.toArray(new String[0]), REQUEST_CODE_PERMISSIONS);
            }
        }
    }

    // ==================== TabListener Implementation ====================

    @Override
    public void onTitleChanged(BrowserTab tab, String title) {
        if (tab == tabManager.getActiveTab()) {
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

            db.addHistory(tab.getTitle(), url);

            extensionManager.injectExtensionApi(tab.getWebView(), url);
            extensionManager.injectContentScripts(tab.getWebView(), url);
            if (userscriptManager != null && url != null && !url.startsWith("zenith://")) {
                List<UserscriptManager.Userscript> scripts = userscriptManager.getScriptsForUrl(url);
                for (UserscriptManager.Userscript script : scripts) {
                    String injectScript = userscriptManager.buildInjectionScript(script);
                    if (!injectScript.isEmpty()) {
                        try {
                            tab.getWebView().evaluateJavascript(injectScript, null);
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to inject userscript: " + script.name, e);
                        }
                    }
                }
            }
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
                })
                .setNegativeButton(R.string.ssl_error_go_back, (dialog, which) -> {
                    tab.goBack();
                })
                .show();
        }
    }

    @Override
    public void onDownloadRequested(BrowserTab tab, String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
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
    public void onShowCustomView(BrowserTab tab, View view) {}

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
        }
    }

    private String getCurrentTheme() {
        return prefs.getString(PREF_THEME, "follow_system");
    }

    @Override
    public void onBackPressed() {
        BrowserTab tab = tabManager.getActiveTab();

        if (tab != null && DevToolsHelper.isDevToolsEnabled()) {
            DevToolsHelper.hideDevTools(tab.getWebView());
        }

        if (findInPageBar.getVisibility() == View.VISIBLE) {
            findInPageBar.setVisibility(View.GONE);
            if (tab != null) tab.getWebView().clearMatches();
            return;
        }

        if (isUrlBarFocused) {
            etUrl.clearFocus();
            return;
        }

        if (tab != null && tab.canGoBack() && !tab.getUrl().startsWith("zenith://")) {
            tab.goBack();
            return;
        }

        if (tabManager.getTabCount() <= 1) {
            if (tab != null && tab.getUrl().startsWith("zenith://")) {
                super.onBackPressed();
                return;
            }
        }

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

            // Group indicators
            if (tab.isInGroup()) {
                holder.groupColorBar.setVisibility(View.VISIBLE);
                holder.groupColorBar.setBackgroundColor(tab.getGroupColor());
                holder.tvGroupBadge.setVisibility(View.VISIBLE);
                holder.tvGroupBadge.setText(tab.getGroupName());
                holder.tvGroupBadge.setBackgroundColor(tab.getGroupColor());
            } else {
                holder.groupColorBar.setVisibility(View.GONE);
                holder.tvGroupBadge.setVisibility(View.GONE);
            }

            holder.itemView.setOnClickListener(v -> {
                if (clickListener != null) clickListener.onClick(tab);
            });

            holder.ivClose.setOnClickListener(v -> {
                if (closeListener != null) closeListener.onClose(tab);
            });

            // Long press to show group assignment
            holder.itemView.setOnLongClickListener(v -> {
                showTabGroupMenu(v.getContext(), tab);
                return true;
            });
        }

        private void showTabGroupMenu(Context context, BrowserTab tab) {
            List<String> items = new ArrayList<>();
            items.add(tab.isInGroup() ? "Remove from group" : "Add to group");

            new MaterialAlertDialogBuilder(context)
                .setTitle(tab.getTitle())
                .setItems(items.toArray(new String[0]), (dialog, which) -> {
                    // This is a simplified version - the full version would need TabManager reference
                })
                .show();
        }

        @Override
        public int getItemCount() { return tabs != null ? tabs.size() : 0; }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvTitle, tvGroupBadge;
            ImageView ivFavicon, ivClose;
            View groupColorBar;

            ViewHolder(View view) {
                super(view);
                tvTitle = view.findViewById(R.id.tv_title);
                ivFavicon = view.findViewById(R.id.iv_favicon);
                ivClose = view.findViewById(R.id.iv_close);
                groupColorBar = view.findViewById(R.id.group_color_bar);
                tvGroupBadge = view.findViewById(R.id.tv_group_badge);
            }
        }
    }
}
