package com.flashnote.java.security;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.flashnote.java.TokenManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

public class GestureLockManager {
    private static final String PREFS_NAME = "flashnote_gesture_lock";
    private static final String KEY_GESTURE_HASH = "gesture_hash";
    private static final String KEY_GESTURE_SALT = "gesture_salt";
    private static final String KEY_ENABLED = "gesture_enabled";
    private static final String KEY_LAST_UNLOCK_AT = "gesture_last_unlock_at";
    private static final long FOREGROUND_TIMEOUT_MS = 5 * 60 * 1000L;

    private final SharedPreferences prefs;
    private final TokenManager tokenManager;
    private final SecureRandom secureRandom = new SecureRandom();

    public static final class GestureBackupMaterial {
        private final String ciphertext;
        private final String nonce;
        private final String kdfParams;
        private final String version;

        public GestureBackupMaterial(String ciphertext, String nonce, String kdfParams, String version) {
            this.ciphertext = ciphertext;
            this.nonce = nonce;
            this.kdfParams = kdfParams;
            this.version = version;
        }

        public String getCiphertext() {
            return ciphertext;
        }

        public String getNonce() {
            return nonce;
        }

        public String getKdfParams() {
            return kdfParams;
        }

        public String getVersion() {
            return version;
        }
    }

    public GestureLockManager(@NonNull Context context, @NonNull TokenManager tokenManager) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.tokenManager = tokenManager;
    }

    public boolean isGestureEnabled() {
        return prefs.getBoolean(scopedKey(KEY_ENABLED), false)
                && prefs.contains(scopedKey(KEY_GESTURE_HASH))
                && prefs.contains(scopedKey(KEY_GESTURE_SALT));
    }

    @NonNull
    public GestureBackupMaterial saveGesture(@NonNull String pattern) {
        String salt = generateSalt();
        String hash = hash(pattern, salt);
        prefs.edit()
                .putString(scopedKey(KEY_GESTURE_SALT), salt)
                .putString(scopedKey(KEY_GESTURE_HASH), hash)
                .putBoolean(scopedKey(KEY_ENABLED), true)
                .putLong(scopedKey(KEY_LAST_UNLOCK_AT), System.currentTimeMillis())
                .commit();
        return new GestureBackupMaterial(hash, salt, "sha256", "v1");
    }

    public boolean verifyGesture(@NonNull String pattern) {
        String salt = prefs.getString(scopedKey(KEY_GESTURE_SALT), null);
        String storedHash = prefs.getString(scopedKey(KEY_GESTURE_HASH), null);
        if (salt == null || storedHash == null) {
            return false;
        }
        boolean matches = storedHash.equals(hash(pattern, salt));
        if (matches) {
            markUnlockedNow();
        }
        return matches;
    }

    public void clearGesture() {
        prefs.edit()
                .remove(scopedKey(KEY_GESTURE_SALT))
                .remove(scopedKey(KEY_GESTURE_HASH))
                .remove(scopedKey(KEY_ENABLED))
                .remove(scopedKey(KEY_LAST_UNLOCK_AT))
                .commit();
    }

    public boolean requiresUnlock(long backgroundDurationMs) {
        if (!isGestureEnabled()) {
            return false;
        }
        return backgroundDurationMs < 0L || backgroundDurationMs >= FOREGROUND_TIMEOUT_MS;
    }

    public void markUnlockedNow() {
        prefs.edit().putLong(scopedKey(KEY_LAST_UNLOCK_AT), System.currentTimeMillis()).apply();
    }

    public long getLastUnlockAt() {
        return prefs.getLong(scopedKey(KEY_LAST_UNLOCK_AT), 0L);
    }

    private String scopedKey(String rawKey) {
        String username = tokenManager.getUsername();
        return (username == null || username.isBlank() ? "anonymous" : username) + "_" + rawKey;
    }

    private String generateSalt() {
        byte[] bytes = new byte[16];
        secureRandom.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private String hash(String pattern, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] raw = digest.digest((pattern + ":" + salt).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(raw);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }
}
