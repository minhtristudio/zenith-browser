package com.zenith.browser.data;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.webkit.DownloadListener;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.zenith.browser.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class DownloadService extends Service {

    private static final String CHANNEL_ID = "download_channel";
    private static final int NOTIFICATION_ID = 1001;
    private NotificationManager notificationManager;

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        createNotificationChannel();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("File download notifications");
            channel.setShowBadge(false);
            notificationManager.createNotificationChannel(channel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String url = intent.getStringExtra("url");
        String userAgent = intent.getStringExtra("userAgent");
        String contentDisposition = intent.getStringExtra("contentDisposition");
        String mimeType = intent.getStringExtra("mimeType");
        long contentLength = intent.getLongExtra("contentLength", -1);

        startForeground(NOTIFICATION_ID, buildNotification("Downloading...", 0));

        new Thread(() -> {
            downloadFile(url, userAgent, contentDisposition, mimeType);
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }).start();

        return START_NOT_STICKY;
    }

    private void downloadFile(String url, String userAgent, String contentDisposition, String mimeType) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestProperty("User-Agent", userAgent);
            connection.connect();

            String fileName = parseFileName(url, contentDisposition);
            File downloadDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (downloadDir != null) downloadDir.mkdirs();
            File outputFile = new File(downloadDir, fileName);

            // Avoid overwriting
            int counter = 1;
            while (outputFile.exists()) {
                String name = fileName.contains(".") ?
                    fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
                String ext = fileName.contains(".") ?
                    fileName.substring(fileName.lastIndexOf('.')) : "";
                outputFile = new File(downloadDir, name + " (" + counter + ")" + ext);
                counter++;
            }

            long totalBytes = connection.getContentLength();
            long downloadedBytes = 0;

            try (InputStream input = connection.getInputStream();
                 FileOutputStream output = new FileOutputStream(outputFile)) {

                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = input.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                    downloadedBytes += bytesRead;
                    if (totalBytes > 0) {
                        int progress = (int) ((downloadedBytes * 100) / totalBytes);
                        notificationManager.notify(NOTIFICATION_ID,
                            buildNotification("Downloading " + fileName, progress));
                    }
                }
            }

            notificationManager.notify(NOTIFICATION_ID,
                buildNotification("Download complete: " + fileName, 100));

        } catch (Exception e) {
            notificationManager.notify(NOTIFICATION_ID,
                buildNotification("Download failed", -1));
        }
    }

    private String parseFileName(String url, String contentDisposition) {
        if (contentDisposition != null) {
            // Try to get filename from Content-Disposition header
            String[] parts = contentDisposition.split(";");
            for (String part : parts) {
                part = part.trim();
                if (part.startsWith("filename=")) {
                    String name = part.substring("filename=".length()).replace("\"", "").trim();
                    if (!name.isEmpty()) return name;
                }
            }
        }
        // Extract from URL
        String name = url.substring(url.lastIndexOf('/') + 1);
        if (name.contains("?")) name = name.substring(0, name.indexOf('?'));
        if (name.isEmpty()) name = "download_" + System.currentTimeMillis();
        return name;
    }

    private Notification buildNotification(String text, int progress) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setContentTitle("Zenith Browser")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true);

        if (progress > 0 && progress <= 100) {
            builder.setProgress(100, progress, false);
        } else {
            builder.setProgress(0, 0, true);
        }

        return builder.build();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
