package com.zenith.browser.ui;

import android.content.Intent;
import android.os.Bundle;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreferenceCompat;

import com.zenith.browser.R;
import com.zenith.browser.data.AppDatabase;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings_container, new SettingsFragment())
                .commit();
        }

        findViewById(R.id.toolbar).setOnClickListener(v -> onBackPressed());
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences_settings, rootKey);

            // Search engine
            ListPreference searchEngine = findPreference("search_engine");
            if (searchEngine != null) {
                searchEngine.setSummary(searchEngine.getEntry());
                searchEngine.setOnPreferenceChangeListener((preference, newValue) -> {
                    searchEngine.setSummary(searchEngine.getEntries()[searchEngine.findIndexOfValue(newValue.toString())]);
                    return true;
                });
            }

            // Theme
            ListPreference theme = findPreference("theme");
            if (theme != null) {
                theme.setSummary(theme.getEntry());
                theme.setOnPreferenceChangeListener((preference, newValue) -> {
                    theme.setSummary(theme.getEntries()[theme.findIndexOfValue(newValue.toString())]);
                    recreate();
                    return true;
                });
            }

            // Clear browsing data
            Preference clearData = findPreference("clear_browsing_data");
            if (clearData != null) {
                clearData.setOnPreferenceClickListener(preference -> {
                    // Clear cookies
                    android.webkit.CookieManager.getInstance().removeAllCookies(null);
                    // Clear cache
                    WebView webView = new WebView(getContext());
                    webView.clearCache(true);
                    webView.clearHistory();
                    webView.clearFormData();
                    webView.destroy();
                    // Clear history
                    AppDatabase.getInstance(getContext()).clearHistory();
                    Toast.makeText(getContext(), R.string.browsing_data_cleared, Toast.LENGTH_SHORT).show();
                    return true;
                });
            }

            // Clear cookies
            Preference clearCookies = findPreference("clear_cookies");
            if (clearCookies != null) {
                clearCookies.setOnPreferenceClickListener(preference -> {
                    android.webkit.CookieManager.getInstance().removeAllCookies(null);
                    Toast.makeText(getContext(), R.string.cookies_enabled, Toast.LENGTH_SHORT).show();
                    return true;
                });
            }

            // Clear cache
            Preference clearCache = findPreference("clear_cache");
            if (clearCache != null) {
                clearCache.setOnPreferenceClickListener(preference -> {
                    WebView webView = new WebView(getContext());
                    webView.clearCache(true);
                    webView.destroy();
                    Toast.makeText(getContext(), R.string.clear_cache, Toast.LENGTH_SHORT).show();
                    return true;
                });
            }

            // About
            Preference about = findPreference("about");
            if (about != null) {
                about.setSummary("Zenith Browser v" + BuildConfig.VERSION_NAME);
                about.setOnPreferenceClickListener(preference -> {
                    // Could show an about dialog
                    return true;
                });
            }
        }
    }
}
