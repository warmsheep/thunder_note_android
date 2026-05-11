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
        return resolveWithBase(raw, currentBaseUrl());
    }

    /**
     * D1-W28-16 跨端兼容：头像 / 媒体在 Web 端上传时写入的是
     * 绝对 URL（含 `host:port`），host 通常是 Web 端浏览器当时的 origin
     * （比如开发者用 `http://localhost:8080` 访问 Web 上传头像，
     * avatar 字段就会被写死成 `http://localhost:8080/api/files/download?objectName=...`）。
     * Android 端如果原样请求这个 URL，访问的是手机自身的 localhost → 加载失败 → 头像空白。
     *
     * 解决：Android 端不信任 URL 中的 host，先抽 `objectName`，再用本机
     * `ServerConfigStore.getBaseUrl()` 拼回去。这样无论 Web 当时 origin 是什么，
     * Android 始终用 APP 配置的服务器地址访问。
     *
     * 兼容矩阵：
     *   - 含 `?objectName=...` 的下载 URL → 抽 objectName + 重写为本机 baseUrl
     *   - 纯 objectName（无 scheme） → 拼本机 baseUrl
     *   - HTTP/HTTPS URL 但不含 objectName 查询参数（外链图片）→ 原样返回
     */
    static String resolveWithBase(String raw, String base) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        if (base == null || base.isEmpty()) {
            return raw;
        }
        String normalizedBase = base.endsWith("/") ? base : base + "/";

        boolean isHttpUrl = raw.startsWith("http://") || raw.startsWith("https://");
        if (isHttpUrl) {
            String objectName = extractObjectName(raw);
            if (objectName != null && !objectName.isEmpty()) {
                return normalizedBase + "api/files/download?objectName=" + Uri.encode(objectName);
            }
            // 外链头像 / 跨域图片：APP 不能改写
            return raw;
        }
        // 纯 objectName
        return normalizedBase + "api/files/download?objectName=" + Uri.encode(raw);
    }

    private static String currentBaseUrl() {
        String base = BuildConfig.BASE_URL;
        FlashNoteApp app = FlashNoteApp.getInstance();
        if (app != null && app.getServerConfigStore() != null) {
            base = app.getServerConfigStore().getBaseUrl();
        }
        return base;
    }
}
