package com.flashnote.java.data.remote;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.flashnote.java.BuildConfig;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

public class ServerConfigStore {
    public static final String MODE_OFFICIAL = "official";
    public static final String MODE_SELF_HOSTED = "self_hosted";

    private static final String PREFS_NAME = "flashnote_server_config";
    private static final String KEY_MODE = "mode";
    private static final String KEY_SELF_HOSTED_URL = "self_hosted_url";

    private final SharedPreferences prefs;

    public ServerConfigStore(@NonNull Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    @NonNull
    public String getCurrentMode() {
        return prefs.getString(KEY_MODE, MODE_OFFICIAL);
    }

    public boolean isOfficialMode() {
        return MODE_OFFICIAL.equals(getCurrentMode());
    }

    @NonNull
    public String getBaseUrl() {
        if (MODE_SELF_HOSTED.equals(getCurrentMode())) {
            String saved = prefs.getString(KEY_SELF_HOSTED_URL, null);
            if (saved != null && !saved.isBlank()) {
                return saved;
            }
        }
        return ensureTrailingSlash(BuildConfig.BASE_URL);
    }

    @NonNull
    public String getDisplayLabel() {
        if (isOfficialMode()) {
            return "☁️ 闪记服务器";
        }
        return "🖥️ 自托管：" + getBaseUrl();
    }

    public void useOfficialServer() {
        prefs.edit()
                .putString(KEY_MODE, MODE_OFFICIAL)
                .remove(KEY_SELF_HOSTED_URL)
                .commit();
    }

    public void useSelfHostedServer(@NonNull String rawUrl) {
        String normalized = normalizeBaseUrl(rawUrl);
        prefs.edit()
                .putString(KEY_MODE, MODE_SELF_HOSTED)
                .putString(KEY_SELF_HOSTED_URL, normalized)
                .commit();
    }

    @NonNull
    public static String normalizeBaseUrl(@NonNull String rawUrl) {
        String trimmed = rawUrl.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("服务器地址不能为空");
        }
        String withScheme = trimmed.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*$")
                ? trimmed
                : "https://" + trimmed;
        try {
            URI uri = new URI(withScheme);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                throw new IllegalArgumentException("仅支持 http 或 https 地址");
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new IllegalArgumentException("服务器地址缺少主机名");
            }
            String path = uri.getPath();
            if (path != null && !path.isBlank() && !"/".equals(path)) {
                throw new IllegalArgumentException("服务器地址不能包含额外路径");
            }
            String authority = uri.getPort() > 0 ? uri.getHost() + ":" + uri.getPort() : uri.getHost();
            return ensureTrailingSlash(scheme + "://" + authority);
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("服务器地址格式不正确", exception);
        }
    }

    @NonNull
    private static String ensureTrailingSlash(@NonNull String url) {
        return url.endsWith("/") ? url : url + "/";
    }
}
