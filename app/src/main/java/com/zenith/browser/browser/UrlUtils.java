package com.zenith.browser.browser;

import android.net.Uri;

public class UrlUtils {

    public static boolean isUrl(String input) {
        if (input == null || input.trim().isEmpty()) return false;
        String trimmed = input.trim().toLowerCase();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://") ||
            trimmed.startsWith("file://") || trimmed.startsWith("javascript:") ||
            trimmed.startsWith("zenith://")) {
            return true;
        }
        if (trimmed.contains("://")) return true;
        // Check if it looks like a domain (has a dot and no spaces)
        if (trimmed.contains(".") && !trimmed.contains(" ") && !trimmed.contains("\n")) {
            String[] parts = trimmed.split("\\.");
            if (parts.length >= 2) {
                String tld = parts[parts.length - 1];
                return tld.length() >= 2 && tld.matches("[a-z]+");
            }
        }
        return false;
    }

    public static String normalize(String input) {
        if (input == null) return "";
        String trimmed = input.trim();
        if (trimmed.isEmpty()) return "";
        if (trimmed.startsWith("zenith://")) return trimmed;
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://") ||
            trimmed.startsWith("file://") || trimmed.startsWith("javascript:")) {
            return trimmed;
        }
        if (isUrl(trimmed)) {
            return "https://" + trimmed;
        }
        return SearchEngine.search(trimmed);
    }

    public static String getDomain(String url) {
        if (url == null) return "";
        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost();
            return host != null ? host : url;
        } catch (Exception e) {
            return url;
        }
    }

    public static String getDisplayUrl(String url) {
        if (url == null) return "";
        if (url.startsWith("zenith://")) return "";
        if (url.startsWith("https://")) {
            return url.substring(8);
        } else if (url.startsWith("http://")) {
            return url.substring(7);
        } else if (url.startsWith("file://")) {
            return url.substring(7);
        }
        return url;
    }

    public static boolean isSecure(String url) {
        return url != null && url.startsWith("https://");
    }

    public static boolean isInternal(String url) {
        return url != null && url.startsWith("zenith://");
    }

    public static String stripHttps(String url) {
        if (url != null && url.startsWith("https://")) return url.substring(8);
        return url;
    }
}
