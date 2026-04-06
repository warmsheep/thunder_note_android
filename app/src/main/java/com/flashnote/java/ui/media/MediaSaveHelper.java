package com.flashnote.java.ui.media;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.Menu;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.RequiresApi;

import com.flashnote.java.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public final class MediaSaveHelper {
    private static final String TARGET_DIR_NAME = "ThunderNote";
    private static final int MAX_NAME_SUFFIX = 999;

    private MediaSaveHelper() {
    }

    public static void showSaveMenu(Context context, View anchor, File sourceFile, String displayName) {
        if (context == null || anchor == null) {
            return;
        }
        PopupMenu popupMenu = new PopupMenu(context, anchor);
        popupMenu.getMenu().add(Menu.NONE, Menu.FIRST, Menu.NONE, R.string.media_save_action);
        popupMenu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == Menu.FIRST) {
                boolean success = saveToDownload(context, sourceFile, displayName);
                Toast.makeText(context,
                        success ? R.string.media_save_success : R.string.media_save_failed,
                        Toast.LENGTH_SHORT).show();
                return true;
            }
            return false;
        });
        popupMenu.show();
    }

    public static boolean saveToDownload(Context context, File sourceFile, String displayName) {
        if (context == null || sourceFile == null || !sourceFile.exists() || !sourceFile.isFile()) {
            return false;
        }

        String finalName = sanitizeDisplayName(displayName, sourceFile.getName());
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return saveToDownloadByMediaStore(context, sourceFile, finalName);
            }
            return saveToDownloadByLegacyFile(sourceFile, finalName);
        } catch (Exception ignored) {
            return false;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private static boolean saveToDownloadByMediaStore(Context context, File sourceFile, String displayName) throws Exception {
        ContentResolver resolver = context.getContentResolver();
        String relativePath = Environment.DIRECTORY_DOWNLOADS + "/" + TARGET_DIR_NAME + "/";
        String finalDisplayName = resolveAvailableDisplayNameByMediaStore(resolver, displayName, relativePath);
        if (TextUtils.isEmpty(finalDisplayName)) {
            return false;
        }
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, finalDisplayName);
        values.put(MediaStore.Downloads.MIME_TYPE, resolveMimeType(finalDisplayName));
        values.put(MediaStore.Downloads.RELATIVE_PATH, relativePath);
        values.put(MediaStore.Downloads.IS_PENDING, 1);

        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            return false;
        }

        boolean success = false;
        try (InputStream inputStream = new FileInputStream(sourceFile);
             OutputStream outputStream = resolver.openOutputStream(uri, "w")) {
            if (outputStream == null) {
                return false;
            }
            copy(inputStream, outputStream);
            success = true;
        } finally {
            ContentValues update = new ContentValues();
            update.put(MediaStore.Downloads.IS_PENDING, 0);
            resolver.update(uri, update, null, null);
            if (!success) {
                resolver.delete(uri, null, null);
            }
        }
        return true;
    }

    private static boolean saveToDownloadByLegacyFile(File sourceFile, String displayName) throws Exception {
        File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File targetDir = new File(downloadDir, TARGET_DIR_NAME);
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            return false;
        }
        File targetFile = resolveAvailableLegacyTargetFile(targetDir, displayName);
        if (targetFile == null) {
            return false;
        }
        try (InputStream inputStream = new FileInputStream(sourceFile);
             OutputStream outputStream = new FileOutputStream(targetFile, false)) {
            copy(inputStream, outputStream);
        }
        return true;
    }

    private static void copy(InputStream inputStream, OutputStream outputStream) throws Exception {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, read);
        }
        outputStream.flush();
    }

    private static String sanitizeDisplayName(String displayName, String fallbackName) {
        String preferred = TextUtils.isEmpty(displayName) ? fallbackName : displayName;
        String trimmed = preferred == null ? "" : preferred.trim();
        if (trimmed.isEmpty()) {
            return "media_" + System.currentTimeMillis();
        }
        return trimmed.replace('/', '_');
    }

    private static File resolveAvailableLegacyTargetFile(File targetDir, String displayName) {
        File direct = new File(targetDir, displayName);
        if (!direct.exists()) {
            return direct;
        }
        String baseName = extractBaseName(displayName);
        String extension = extractExtension(displayName);
        for (int i = 1; i <= MAX_NAME_SUFFIX; i++) {
            String candidate = buildCandidateName(baseName, extension, i);
            File candidateFile = new File(targetDir, candidate);
            if (!candidateFile.exists()) {
                return candidateFile;
            }
        }
        return null;
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private static String resolveAvailableDisplayNameByMediaStore(ContentResolver resolver,
                                                                  String displayName,
                                                                  String relativePath) {
        if (!isMediaStoreNameExists(resolver, displayName, relativePath)) {
            return displayName;
        }
        String baseName = extractBaseName(displayName);
        String extension = extractExtension(displayName);
        for (int i = 1; i <= MAX_NAME_SUFFIX; i++) {
            String candidate = buildCandidateName(baseName, extension, i);
            if (!isMediaStoreNameExists(resolver, candidate, relativePath)) {
                return candidate;
            }
        }
        return null;
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private static boolean isMediaStoreNameExists(ContentResolver resolver,
                                                  String displayName,
                                                  String relativePath) {
        String[] projection = new String[]{MediaStore.Downloads._ID};
        String selection = MediaStore.Downloads.DISPLAY_NAME + " = ? AND "
                + MediaStore.Downloads.RELATIVE_PATH + " = ?";
        String[] selectionArgs = new String[]{displayName, relativePath};
        try (Cursor cursor = resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
        )) {
            return cursor != null && cursor.moveToFirst();
        }
    }

    private static String extractBaseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0) {
            return fileName;
        }
        return fileName.substring(0, dot);
    }

    private static String extractExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0 || dot >= fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot);
    }

    private static String buildCandidateName(String baseName, String extension, int index) {
        return baseName + "(" + index + ")" + extension;
    }

    private static String resolveMimeType(String fileName) {
        String extension = MimeTypeMap.getFileExtensionFromUrl(fileName);
        if (TextUtils.isEmpty(extension)) {
            return "application/octet-stream";
        }
        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase());
        return TextUtils.isEmpty(mimeType) ? "application/octet-stream" : mimeType;
    }
}
