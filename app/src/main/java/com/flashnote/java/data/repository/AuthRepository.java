package com.flashnote.java.data.repository;

import com.flashnote.java.data.model.LoginRequest;
import com.flashnote.java.data.model.LoginResponse;
import com.flashnote.java.data.model.User;

public interface AuthRepository {
    interface AuthCallback {
        void onSuccess(LoginResponse response);
        void onError(String message, int code);
    }

    interface SimpleCallback {
        void onSuccess();

        void onError(String message, int code);
    }

    interface PasswordCallback {
        void onSuccess();
        void onError(String message, int code);
    }

    void login(String username, String password, AuthCallback callback);

    void register(String username, String email, String password, SimpleCallback callback);

    void logout();

    void changePassword(String currentPassword, String newPassword, PasswordCallback callback);

    User getCurrentUser();

    boolean isLoggedIn();
}
