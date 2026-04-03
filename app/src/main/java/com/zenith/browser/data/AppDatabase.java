package com.zenith.browser.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class AppDatabase extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "zenith_browser.db";
    private static final int DATABASE_VERSION = 1;

    // Tables
    private static final String TABLE_BOOKMARKS = "bookmarks";
    private static final String TABLE_HISTORY = "history";
    private static final String TABLE_DOWNLOADS = "downloads";

    // Common columns
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_TITLE = "title";
    private static final String COLUMN_URL = "url";
    private static final String COLUMN_CREATED_AT = "created_at";
    private static final String COLUMN_UPDATED_AT = "updated_at";

    // Download-specific columns
    private static final String COLUMN_FILE_PATH = "file_path";
    private static final String COLUMN_FILE_NAME = "file_name";
    private static final String COLUMN_MIME_TYPE = "mime_type";
    private static final String COLUMN_FILE_SIZE = "file_size";
    private static final String COLUMN_STATUS = "status"; // 0=pending, 1=downloading, 2=completed, 3=failed
    private static final String COLUMN_FAVICON = "favicon_url";

    private static AppDatabase instance;

    public static synchronized AppDatabase getInstance(Context context) {
        if (instance == null) {
            instance = new AppDatabase(context.getApplicationContext());
        }
        return instance;
    }

    private AppDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_BOOKMARKS + " (" +
            COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
            COLUMN_TITLE + " TEXT NOT NULL," +
            COLUMN_URL + " TEXT NOT NULL UNIQUE," +
            COLUMN_FAVICON + " TEXT," +
            COLUMN_CREATED_AT + " INTEGER NOT NULL," +
            COLUMN_UPDATED_AT + " INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE " + TABLE_HISTORY + " (" +
            COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
            COLUMN_TITLE + " TEXT," +
            COLUMN_URL + " TEXT NOT NULL," +
            COLUMN_FAVICON + " TEXT," +
            COLUMN_CREATED_AT + " INTEGER NOT NULL)");

        db.execSQL("CREATE INDEX idx_history_created ON " + TABLE_HISTORY + "(" + COLUMN_CREATED_AT + ")");

        db.execSQL("CREATE TABLE " + TABLE_DOWNLOADS + " (" +
            COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
            COLUMN_FILE_NAME + " TEXT NOT NULL," +
            COLUMN_URL + " TEXT NOT NULL," +
            COLUMN_FILE_PATH + " TEXT," +
            COLUMN_MIME_TYPE + " TEXT," +
            COLUMN_FILE_SIZE + " INTEGER DEFAULT 0," +
            COLUMN_STATUS + " INTEGER DEFAULT 0," +
            COLUMN_CREATED_AT + " INTEGER NOT NULL)");

        db.execSQL("CREATE INDEX idx_downloads_status ON " + TABLE_DOWNLOADS + "(" + COLUMN_STATUS + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Simple upgrade: drop and recreate
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_BOOKMARKS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_HISTORY);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_DOWNLOADS);
        onCreate(db);
    }

    // ==================== BOOKMARKS ====================

    public long addBookmark(String title, String url) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_TITLE, title);
        values.put(COLUMN_URL, url);
        values.put(COLUMN_CREATED_AT, System.currentTimeMillis());
        values.put(COLUMN_UPDATED_AT, System.currentTimeMillis());
        // Insert or replace if URL already exists
        return db.insertWithOnConflict(TABLE_BOOKMARKS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public boolean removeBookmark(long id) {
        SQLiteDatabase db = getWritableDatabase();
        return db.delete(TABLE_BOOKMARKS, COLUMN_ID + "=?", new String[]{String.valueOf(id)}) > 0;
    }

    public boolean isBookmarked(String url) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_BOOKMARKS, new String[]{COLUMN_ID},
            COLUMN_URL + "=?", new String[]{url}, null, null, null);
        boolean exists = cursor != null && cursor.getCount() > 0;
        if (cursor != null) cursor.close();
        return exists;
    }

    public List<BookmarkItem> getAllBookmarks() {
        List<BookmarkItem> bookmarks = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_BOOKMARKS, null, null, null, null, null, COLUMN_UPDATED_AT + " DESC");
        if (cursor != null && cursor.moveToFirst()) {
            do {
                BookmarkItem item = new BookmarkItem();
                item.id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID));
                item.title = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TITLE));
                item.url = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_URL));
                item.createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_CREATED_AT));
                bookmarks.add(item);
            } while (cursor.moveToNext());
            cursor.close();
        }
        return bookmarks;
    }

    public List<BookmarkItem> getRecentBookmarks(int limit) {
        List<BookmarkItem> bookmarks = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_BOOKMARKS, null, null, null, null, null,
            COLUMN_UPDATED_AT + " DESC", String.valueOf(limit));
        if (cursor != null && cursor.moveToFirst()) {
            do {
                BookmarkItem item = new BookmarkItem();
                item.id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID));
                item.title = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TITLE));
                item.url = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_URL));
                item.createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_CREATED_AT));
                bookmarks.add(item);
            } while (cursor.moveToNext());
            cursor.close();
        }
        return bookmarks;
    }

    public void clearBookmarks() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_BOOKMARKS, null, null);
    }

    // ==================== HISTORY ====================

    public long addHistory(String title, String url) {
        if (url == null || url.startsWith("zenith://") || url.startsWith("file://")) return -1;
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_TITLE, title != null ? title : "");
        values.put(COLUMN_URL, url);
        values.put(COLUMN_CREATED_AT, System.currentTimeMillis());
        return db.insert(TABLE_HISTORY, null, values);
    }

    public List<HistoryItem> getAllHistory() {
        List<HistoryItem> history = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_HISTORY, null, null, null, null, null, COLUMN_CREATED_AT + " DESC");
        if (cursor != null && cursor.moveToFirst()) {
            do {
                HistoryItem item = new HistoryItem();
                item.id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID));
                item.title = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TITLE));
                item.url = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_URL));
                item.createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_CREATED_AT));
                history.add(item);
            } while (cursor.moveToNext());
            cursor.close();
        }
        return history;
    }

    public List<HistoryItem> searchHistory(String query) {
        List<HistoryItem> history = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        String selection = COLUMN_TITLE + " LIKE ? OR " + COLUMN_URL + " LIKE ?";
        String[] selectionArgs = new String[]{"%" + query + "%", "%" + query + "%"};
        Cursor cursor = db.query(TABLE_HISTORY, null, selection, selectionArgs, null, null, COLUMN_CREATED_AT + " DESC");
        if (cursor != null && cursor.moveToFirst()) {
            do {
                HistoryItem item = new HistoryItem();
                item.id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID));
                item.title = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TITLE));
                item.url = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_URL));
                item.createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_CREATED_AT));
                history.add(item);
            } while (cursor.moveToNext());
            cursor.close();
        }
        return history;
    }

    public void clearHistory() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_HISTORY, null, null);
    }

    public void deleteHistoryOlderThan(long timestamp) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_HISTORY, COLUMN_CREATED_AT + " < ?", new String[]{String.valueOf(timestamp)});
    }

    // ==================== DOWNLOADS ====================

    public long addDownload(String fileName, String url, String mimeType, long fileSize) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_FILE_NAME, fileName);
        values.put(COLUMN_URL, url);
        values.put(COLUMN_MIME_TYPE, mimeType);
        values.put(COLUMN_FILE_SIZE, fileSize);
        values.put(COLUMN_STATUS, 0);
        values.put(COLUMN_CREATED_AT, System.currentTimeMillis());
        return db.insert(TABLE_DOWNLOADS, null, values);
    }

    public void updateDownloadStatus(long id, int status, String filePath) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_STATUS, status);
        if (filePath != null) values.put(COLUMN_FILE_PATH, filePath);
        db.update(TABLE_DOWNLOADS, values, COLUMN_ID + "=?", new String[]{String.valueOf(id)});
    }

    public List<DownloadItem> getAllDownloads() {
        List<DownloadItem> downloads = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_DOWNLOADS, null, null, null, null, null, COLUMN_CREATED_AT + " DESC");
        if (cursor != null && cursor.moveToFirst()) {
            do {
                DownloadItem item = new DownloadItem();
                item.id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID));
                item.fileName = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FILE_NAME));
                item.url = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_URL));
                item.filePath = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FILE_PATH));
                item.mimeType = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MIME_TYPE));
                item.fileSize = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_FILE_SIZE));
                item.status = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_STATUS));
                item.createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_CREATED_AT));
                downloads.add(item);
            } while (cursor.moveToNext());
            cursor.close();
        }
        return downloads;
    }

    public void deleteDownload(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_DOWNLOADS, COLUMN_ID + "=?", new String[]{String.valueOf(id)});
    }

    public void clearDownloads() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_DOWNLOADS, null, null);
    }

    // ==================== DATA CLASSES ====================

    public static class BookmarkItem {
        public long id;
        public String title;
        public String url;
        public long createdAt;
    }

    public static class HistoryItem {
        public long id;
        public String title;
        public String url;
        public long createdAt;
    }

    public static class DownloadItem {
        public long id;
        public String fileName;
        public String url;
        public String filePath;
        public String mimeType;
        public long fileSize;
        public int status; // 0=pending, 1=downloading, 2=completed, 3=failed
        public long createdAt;

        public boolean isCompleted() { return status == 2; }
        public boolean isFailed() { return status == 3; }
        public boolean isDownloading() { return status == 1; }
    }
}
