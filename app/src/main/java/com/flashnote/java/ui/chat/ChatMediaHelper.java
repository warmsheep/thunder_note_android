package com.flashnote.java.ui.chat;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import com.flashnote.java.DebugLog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class ChatMediaHelper {

    interface TempFileCallback {
        void onFileReady(@Nullable File file);
    }

    interface UiPoster {
        void post(@NonNull Runnable action);
    }

    boolean hasCamera(@NonNull Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY);
    }

    @Nullable
    File prepareCameraPhotoFile(@NonNull Context context) {
        try {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String storageDir = context.getCacheDir().getAbsolutePath();
            return new File(storageDir, "IMG_" + timeStamp + ".jpg");
        } catch (Exception ex) {
            DebugLog.e("ChatMediaHelper", "Failed to prepare camera temp file", ex);
            return null;
        }
    }

    @NonNull
    Uri buildCameraUri(@NonNull Context context, @NonNull File photoFile) {
        return FileProvider.getUriForFile(
                context,
                context.getPackageName() + ".fileprovider",
                photoFile
        );
    }

    void copyUriToTempFile(@NonNull Context appContext,
                           @NonNull UiPoster uiPoster,
                           @NonNull Uri uri,
                           @NonNull String prefix,
                           @NonNull TempFileCallback callback) {
        new Thread(() -> {
            File tempFile = null;
            try {
                String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                String extension = getFileExtension(appContext.getContentResolver(), uri);
                String storageDir = appContext.getCacheDir().getAbsolutePath();
                tempFile = new File(storageDir, prefix + "_" + timeStamp + "." + extension);

                try (InputStream inputStream = appContext.getContentResolver().openInputStream(uri);
                     FileOutputStream outputStream = new FileOutputStream(tempFile)) {
                    if (inputStream != null) {
                        byte[] buffer = new byte[65536];
                        int bytesRead;
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                        }
                    }
                }

                File finalFile = tempFile;
                uiPoster.post(() -> callback.onFileReady(finalFile));
            } catch (IOException e) {
                DebugLog.e("ChatMediaHelper", "Failed to copy picked file to temp storage", e);
                uiPoster.post(() -> callback.onFileReady(null));
            }
        }).start();
    }

    @Nullable
    String resolveMimeType(@NonNull Context context, @NonNull Uri uri) {
        return context.getContentResolver().getType(uri);
    }

    @Nullable
    String getOriginalFileName(@NonNull Context context, @NonNull Uri uri) {
        String displayName = null;
        if ("content".equals(uri.getScheme())) {
            try (android.database.Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        displayName = cursor.getString(nameIndex);
                    }
                }
            }
        }
        if (displayName == null) {
            displayName = uri.getLastPathSegment();
        }
        return displayName;
    }

    @NonNull
    String getFileExtension(@NonNull ContentResolver resolver, @NonNull Uri uri) {
        String mimeType = resolver.getType(uri);
        if (mimeType != null) {
            switch (mimeType) {
                case "image/jpeg":
                    return "jpg";
                case "image/png":
                    return "png";
                case "image/gif":
                    return "gif";
                case "video/mp4":
                    return "mp4";
                case "video/3gpp":
                    return "3gp";
                case "video/webm":
                    return "webm";
                case "application/pdf":
                    return "pdf";
                case "application/msword":
                    return "doc";
                case "application/vnd.openxmlformats-officedocument.wordprocessingml.document":
                    return "docx";
                default:
                    break;
            }
        }

        String path = uri.getPath();
        if (path != null) {
            int lastDot = path.lastIndexOf('.');
            if (lastDot > 0) {
                return path.substring(lastDot + 1);
            }
        }
        return "bin";
    }
}
