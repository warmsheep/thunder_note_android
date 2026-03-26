package com.flashnote.java.ui.chat;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import com.flashnote.java.data.model.Message;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

final class ChatShareHelper {

    @Nullable
    File tryResolveLocalFile(@NonNull Context context, @Nullable String mediaUrl) {
        if (TextUtils.isEmpty(mediaUrl)) {
            return null;
        }
        File direct = new File(mediaUrl);
        if (direct.exists()) {
            return direct;
        }
        String objectName = extractObjectNameForDownload(mediaUrl);
        if (TextUtils.isEmpty(objectName)) {
            return null;
        }
        File cached = new File(context.getCacheDir(), objectName.replace('/', '_'));
        if (cached.exists()) {
            return cached;
        }
        File externalCache = context.getExternalCacheDir() == null
                ? null
                : new File(context.getExternalCacheDir(), objectName.replace('/', '_'));
        if (externalCache != null && externalCache.exists()) {
            return externalCache;
        }
        String originalName = sanitizeFileName(fallbackFileNameFromObjectName(objectName));
        File shared = new File(new File(context.getCacheDir(), "share"), originalName);
        return shared.exists() ? shared : null;
    }

    @Nullable
    String extractObjectNameForDownload(@Nullable String mediaUrl) {
        if (TextUtils.isEmpty(mediaUrl)) {
            return null;
        }
        if (mediaUrl.startsWith("http")) {
            Uri uri = Uri.parse(mediaUrl);
            String objectName = uri.getQueryParameter("objectName");
            if (!TextUtils.isEmpty(objectName)) {
                return Uri.decode(objectName);
            }
            String path = uri.getPath();
            if (!TextUtils.isEmpty(path) && path.startsWith("/")) {
                return path.substring(1);
            }
            return null;
        }
        return mediaUrl;
    }

    @NonNull
    String fallbackFileNameFromObjectName(@NonNull String objectName) {
        int slashIndex = objectName.lastIndexOf('/');
        if (slashIndex >= 0 && slashIndex < objectName.length() - 1) {
            return objectName.substring(slashIndex + 1);
        }
        return objectName;
    }

    void shareFileByIntent(@NonNull Context context, @NonNull File file, @NonNull Message message) {
        File shareFile = prepareShareFile(context, file, message.getFileName());
        Uri contentUri = FileProvider.getUriForFile(
                context,
                context.getPackageName() + ".fileprovider",
                shareFile
        );
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(resolveShareMimeType(shareFile, message));
        intent.putExtra(Intent.EXTRA_STREAM, contentUri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setClipData(android.content.ClipData.newRawUri("share", contentUri));
        context.startActivity(Intent.createChooser(intent, "分享到"));
    }

    @NonNull
    private File prepareShareFile(@NonNull Context context, @NonNull File sourceFile, @Nullable String originalFileName) {
        if (TextUtils.isEmpty(originalFileName)) {
            return sourceFile;
        }
        String safeName = sanitizeFileName(originalFileName);
        if (safeName.equals(sourceFile.getName())) {
            return sourceFile;
        }
        File shareDir = new File(context.getCacheDir(), "share");
        if (!shareDir.exists()) {
            shareDir.mkdirs();
        }
        File target = new File(shareDir, safeName);
        try (InputStream inputStream = java.nio.file.Files.newInputStream(sourceFile.toPath());
             FileOutputStream outputStream = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            return target;
        } catch (IOException ignored) {
            return sourceFile;
        }
    }

    @NonNull
    private String sanitizeFileName(@NonNull String fileName) {
        String sanitized = fileName.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (sanitized.isEmpty()) {
            return "shared_file";
        }
        return sanitized;
    }

    @NonNull
    private String resolveShareMimeType(@NonNull File file, @NonNull Message message) {
        String mediaType = message.getMediaType();
        if ("IMAGE".equalsIgnoreCase(mediaType)) {
            return "image/*";
        }
        if ("VIDEO".equalsIgnoreCase(mediaType)) {
            return "video/*";
        }
        if ("VOICE".equalsIgnoreCase(mediaType)) {
            return "audio/*";
        }

        String extension = MimeTypeMap.getFileExtensionFromUrl(file.getName());
        if (TextUtils.isEmpty(extension) && !TextUtils.isEmpty(message.getFileName())) {
            extension = MimeTypeMap.getFileExtensionFromUrl(message.getFileName());
        }
        if (!TextUtils.isEmpty(extension)) {
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase(Locale.ROOT));
            if (!TextUtils.isEmpty(mime)) {
                return mime;
            }
        }
        return "*/*";
    }
}
