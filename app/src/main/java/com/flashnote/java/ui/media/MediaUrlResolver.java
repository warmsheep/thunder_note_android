package com.flashnote.java.ui.media;

import android.content.Context;
import android.net.Uri;

import com.flashnote.java.BuildConfig;
import com.flashnote.java.FlashNoteApp;

import java.io.File;

public final class MediaUrlResolver {
    private MediaUrlResolver() {
    }

    public static File resolveCachedFile(Context context, String mediaPathOrUrl) {
        if (context == null || mediaPathOrUrl == null || mediaPathOrUrl.trim().isEmpty()) {
            return null;
        }
        String raw = mediaPathOrUrl.trim();
        if (raw.startsWith("file://")) {
            File file = new File(raw.substring("file://".length()));
            return file.exists() && file.length() > 0 ? file : null;
        }
        if (raw.startsWith("/")) {
            File file = new File(raw);
            return file.exists() && file.length() > 0 ? file : null;
        }
        String objectName = extractObjectName(raw);
        if (objectName == null || objectName.isBlank()) {
            return null;
        }
        File cacheFile = new File(context.getCacheDir(), objectName.replace('/', '_'));
        return cacheFile.exists() && cacheFile.length() > 0 ? cacheFile : null;
    }

    public static String extractObjectName(String mediaPathOrUrl) {
        if (mediaPathOrUrl == null || mediaPathOrUrl.trim().isEmpty()) {
            return "";
        }
        String raw = mediaPathOrUrl.trim();
        if (!(raw.startsWith("http://") || raw.startsWith("https://"))) {
            return raw;
        }
        Uri uri = Uri.parse(raw);
        String objectName = uri.getQueryParameter("objectName");
        if (objectName != null && !objectName.isBlank()) {
            return objectName;
        }
        return "";
    }

    public static String resolve(String mediaPathOrUrl) {
        if (mediaPathOrUrl == null || mediaPathOrUrl.trim().isEmpty()) {
            return "";
        }
        String raw = mediaPathOrUrl.trim();
        if (raw.startsWith("http://") || raw.startsWith("https://")) {
            return raw;
        }
        String base = BuildConfig.BASE_URL;
        FlashNoteApp app = FlashNoteApp.getInstance();
        if (app != null && app.getServerConfigStore() != null) {
            base = app.getServerConfigStore().getBaseUrl();
        }
        if (!base.endsWith("/")) {
            base = base + "/";
        }
        return base + "api/files/download?objectName=" + Uri.encode(raw);
    }
}
