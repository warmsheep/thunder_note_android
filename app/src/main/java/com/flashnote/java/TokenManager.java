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
    private static final String FALLBACK_PREFS_NAME = "flashnote_fallback_prefs";

    private final SharedPreferences encryptedPrefs;
    private final MasterKey masterKey;
    private final boolean usingEncryptedStorage;

    interface SecurePrefsFactory {
        SecurePrefsResult create(Context context) throws GeneralSecurityException, IOException;
    }

    static final class SecurePrefsResult {
        private final SharedPreferences sharedPreferences;
        private final MasterKey masterKey;

        SecurePrefsResult(SharedPreferences sharedPreferences, MasterKey masterKey) {
            this.sharedPreferences = sharedPreferences;
            this.masterKey = masterKey;
        }

        SharedPreferences getSharedPreferences() {
            return sharedPreferences;
        }

        MasterKey getMasterKey() {
            return masterKey;
        }
    }

    public TokenManager(Context context) {
        this(context, TokenManager::createSecurePrefs, context.getSharedPreferences(FALLBACK_PREFS_NAME, Context.MODE_PRIVATE));
    }

    TokenManager(Context context, SecurePrefsFactory securePrefsFactory, SharedPreferences fallbackPreferences) {
        SharedPreferences resolvedPrefs;
        MasterKey resolvedMasterKey = null;
        boolean encrypted = false;
        try {
            SecurePrefsResult result = securePrefsFactory.create(context);
            resolvedPrefs = result.getSharedPreferences();
            resolvedMasterKey = result.getMasterKey();
            encrypted = true;
        } catch (GeneralSecurityException | IOException | RuntimeException e) {
            DebugLog.e("TokenManager", "Falling back to plain shared preferences", e);
            resolvedPrefs = fallbackPreferences;
        }
        encryptedPrefs = resolvedPrefs;
        masterKey = resolvedMasterKey;
        usingEncryptedStorage = encrypted;
    }

    private static SecurePrefsResult createSecurePrefs(Context context) throws GeneralSecurityException, IOException {
        MasterKey masterKey = new MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build();

        SharedPreferences encryptedPrefs = EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        );
        return new SecurePrefsResult(encryptedPrefs, masterKey);
    }

    public void saveTokens(String accessToken, String refreshToken, long expiresIn) {
        encryptedPrefs.edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .putLong(KEY_TOKEN_EXPIRY, System.currentTimeMillis() + (expiresIn * 1000))
                .commit();
    }

    public void saveAccessToken(String accessToken) {
        encryptedPrefs.edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .commit();
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
        try {
            String token = getAccessToken();
            long expiry = getTokenExpiry();
            boolean hasToken = token != null && !token.isEmpty();
            boolean hasExpiry = expiry > 0;
            boolean notExpired = System.currentTimeMillis() < expiry;
            return hasToken && hasExpiry && notExpired;
        } catch (Exception e) {
            DebugLog.e("TokenManager", "Failed to evaluate token validity", e);
            return false;
        }
    }

    public void saveUserId(Long userId) {
        if (userId != null) {
            encryptedPrefs.edit().putLong(KEY_USER_ID, userId).commit();
        }
    }

    public Long getUserId() {
        long userId = encryptedPrefs.getLong(KEY_USER_ID, -1);
        return userId == -1 ? null : userId;
    }

    public void saveUsername(String username) {
        if (username != null) {
            encryptedPrefs.edit().putString(KEY_USERNAME, username).commit();
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
                .commit();
    }

    public boolean hasToken() {
        return getAccessToken() != null && !getAccessToken().isEmpty();
    }

    boolean isUsingEncryptedStorage() {
        return usingEncryptedStorage;
    }
}
