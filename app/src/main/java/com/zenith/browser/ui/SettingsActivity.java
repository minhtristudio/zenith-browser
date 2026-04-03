package com.zenith.browser.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
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
        try {
            setContentView(R.layout.activity_settings);
        } catch (Exception e) {
            finish();
            return;
        }

        try {
            if (savedInstanceState == null) {
                getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings_container, new SettingsFragment())
                    .commit();
            }
        } catch (Exception e) {
            // Ignore fragment errors
        }

        try {
            View toolbar = findViewById(R.id.toolbar);
            if (toolbar != null) {
                toolbar.setOnClickListener(v -> onBackPressed());
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            try {
                setPreferencesFromResource(R.xml.preferences_settings, rootKey);
            } catch (Exception e) {
                return;
            }

            // Search engine
            try {
                ListPreference searchEngine = findPreference("search_engine");
                if (searchEngine != null) {
                    searchEngine.setSummary(searchEngine.getEntry());
                    searchEngine.setOnPreferenceChangeListener((preference, newValue) -> {
                        searchEngine.setSummary(searchEngine.getEntries()[searchEngine.findIndexOfValue(newValue.toString())]);
                        return true;
                    });
                }
            } catch (Exception e) {
                // Ignore
            }

            // Theme
            try {
                ListPreference theme = findPreference("theme");
                if (theme != null) {
                    theme.setSummary(theme.getEntry());
                    theme.setOnPreferenceChangeListener((preference, newValue) -> {
                        theme.setSummary(theme.getEntries()[theme.findIndexOfValue(newValue.toString())]);
                        if (getActivity() != null) {
                            getActivity().recreate();
                        }
                        return true;
                    });
                }
            } catch (Exception e) {
                // Ignore
            }

            // Clear browsing data
            try {
                Preference clearData = findPreference("clear_browsing_data");
                if (clearData != null) {
                    clearData.setOnPreferenceClickListener(preference -> {
                        try {
                            android.webkit.CookieManager.getInstance().removeAllCookies(null);
                            WebView webView = new WebView(getContext());
                            webView.clearCache(true);
                            webView.clearHistory();
                            webView.clearFormData();
                            webView.clearSslPreferences();
                            webView.destroy();
                            AppDatabase db = AppDatabase.getInstance(getContext());
                            if (db != null) db.clearHistory();
                            if (getContext() != null) {
                                Toast.makeText(getContext(), R.string.browsing_data_cleared, Toast.LENGTH_SHORT).show();
                            }
                        } catch (Exception ex) {
                            // Ignore
                        }
                        return true;
                    });
                }
            } catch (Exception e) {
                // Ignore
            }

            // Clear cookies
            try {
                Preference clearCookies = findPreference("clear_cookies");
                if (clearCookies != null) {
                    clearCookies.setOnPreferenceClickListener(preference -> {
                        try {
                            android.webkit.CookieManager.getInstance().removeAllCookies(null);
                            if (getContext() != null) {
                                Toast.makeText(getContext(), "Cookies cleared", Toast.LENGTH_SHORT).show();
                            }
                        } catch (Exception ex) {
                            // Ignore
                        }
                        return true;
                    });
                }
            } catch (Exception e) {
                // Ignore
            }

            // Clear cache
            try {
                Preference clearCache = findPreference("clear_cache");
                if (clearCache != null) {
                    clearCache.setOnPreferenceClickListener(preference -> {
                        try {
                            WebView webView = new WebView(getContext());
                            webView.clearCache(true);
                            webView.destroy();
                            if (getContext() != null) {
                                Toast.makeText(getContext(), "Cache cleared", Toast.LENGTH_SHORT).show();
                            }
                        } catch (Exception ex) {
                            // Ignore
                        }
                        return true;
                    });
                }
            } catch (Exception e) {
                // Ignore
            }

            // About
            try {
                Preference about = findPreference("about");
                if (about != null) {
                    about.setSummary("Zenith Browser v1.1.0");
                    about.setOnPreferenceClickListener(preference -> true);
                }
            } catch (Exception e) {
                // Ignore
            }
        }
    }
}
