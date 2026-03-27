package com.flashnote.java.ui.media;

import android.net.Uri;

import com.flashnote.java.BuildConfig;
import com.flashnote.java.FlashNoteApp;

public final class MediaUrlResolver {
    private MediaUrlResolver() {
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
