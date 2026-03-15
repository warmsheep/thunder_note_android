package com.flashnote.java;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;

public class TokenManager {
    private static final String PREFS_NAME = "flashnote_secure_prefs";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_TOKEN_EXPIRY = "token_expiry";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USERNAME = "username";

    private final SharedPreferences encryptedPrefs;
    private final MasterKey masterKey;

    public TokenManager(Context context) {
        try {
            masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            
            encryptedPrefs = EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException("Failed to initialize encrypted shared preferences", e);
        }
    }

    public void saveTokens(String accessToken, String refreshToken, long expiresIn) {
        encryptedPrefs.edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .putLong(KEY_TOKEN_EXPIRY, System.currentTimeMillis() + (expiresIn * 1000))
                .apply();
    }

    public void saveAccessToken(String accessToken) {
        encryptedPrefs.edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .apply();
    }

    public String getAccessToken() {
        return encryptedPrefs.getString(KEY_ACCESS_TOKEN, null);
    }

    public String getRefreshToken() {
        return encryptedPrefs.getString(KEY_REFRESH_TOKEN, null);
    }

    public long getTokenExpiry() {
        return encryptedPrefs.getLong(KEY_TOKEN_EXPIRY, 0);
    }

    public boolean isTokenValid() {
        String token = getAccessToken();
        long expiry = getTokenExpiry();
        return token != null && !token.isEmpty() && System.currentTimeMillis() < expiry;
    }

    public void saveUserId(Long userId) {
        if (userId != null) {
            encryptedPrefs.edit().putLong(KEY_USER_ID, userId).apply();
        }
    }

    public Long getUserId() {
        long userId = encryptedPrefs.getLong(KEY_USER_ID, -1);
        return userId == -1 ? null : userId;
    }

    public void saveUsername(String username) {
        if (username != null) {
            encryptedPrefs.edit().putString(KEY_USERNAME, username).apply();
        }
    }

    public String getUsername() {
        return encryptedPrefs.getString(KEY_USERNAME, null);
    }

    public void clearTokens() {
        encryptedPrefs.edit()
                .remove(KEY_ACCESS_TOKEN)
                .remove(KEY_REFRESH_TOKEN)
                .remove(KEY_TOKEN_EXPIRY)
                .remove(KEY_USER_ID)
                .remove(KEY_USERNAME)
                .apply();
    }

    public boolean hasToken() {
        return getAccessToken() != null && !getAccessToken().isEmpty();
    }
}
