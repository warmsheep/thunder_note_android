package com.flashnote.java.data.repository;

import androidx.annotation.NonNull;

import com.flashnote.java.TokenManager;

public final class RepositoryAuthSupport {
    private RepositoryAuthSupport() {}

    @NonNull
    public static String requireCurrentUserId(@NonNull TokenManager tokenManager) {
        Long userId = tokenManager.getUserId();
        if (userId == null) {
            throw new IllegalStateException("User not logged in");
        }
        return String.valueOf(userId);
    }
}
