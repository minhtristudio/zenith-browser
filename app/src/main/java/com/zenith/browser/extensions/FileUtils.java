package com.zenith.browser.extensions;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class FileUtils {

    public static String readFileToString(File file) {
        if (file == null || !file.exists() || !file.canRead()) return null;
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        } catch (IOException e) {
            return null;
        }
        return sb.toString();
    }

    public static String getFileExtension(String filename) {
        if (filename == null) return "";
        int lastDot = filename.lastIndexOf('.');
        if (lastDot >= 0) {
            return filename.substring(lastDot + 1).toLowerCase();
        }
        return "";
    }

    public static String getMimeType(String filename) {
        String ext = getFileExtension(filename);
        switch (ext) {
            case "html": case "htm": return "text/html";
            case "css": return "text/css";
            case "js": return "application/javascript";
            case "json": return "application/json";
            case "png": return "image/png";
            case "jpg": case "jpeg": return "image/jpeg";
            case "gif": return "image/gif";
            case "svg": return "image/svg+xml";
            case "webp": return "image/webp";
            case "ico": return "image/x-icon";
            case "pdf": return "application/pdf";
            case "zip": return "application/zip";
            case "crx": return "application/x-chrome-extension";
            case "mp3": return "audio/mpeg";
            case "mp4": return "video/mp4";
            case "webm": return "video/webm";
            case "xml": return "application/xml";
            case "txt": return "text/plain";
            default: return "application/octet-stream";
        }
    }

    public static long getFileSize(File file) {
        if (file != null && file.exists()) return file.length();
        return 0;
    }

    public static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
