package com.flashnote.java.ui.auth;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.flashnote.java.FlashNoteApp;
import com.flashnote.java.data.sync.PendingRecoveryWorker;
import com.flashnote.java.data.model.LoginResponse;
import com.flashnote.java.data.model.User;
import com.flashnote.java.data.repository.AuthRepository;

public class AuthViewModel extends AndroidViewModel {
    public enum AuthState {
        IDLE,
        LOADING,
        SUCCESS,
        ERROR
    }

    private AuthRepository authRepository;
    
    private final MutableLiveData<AuthState> authState = new MutableLiveData<>(AuthState.IDLE);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<LoginResponse> loginResponse = new MutableLiveData<>();
    private final MutableLiveData<User> currentUser = new MutableLiveData<>();

    public AuthViewModel(@NonNull Application application) {
        super(application);
        refreshAuthRepository();
    }

    public void login(String username, String password) {
        refreshAuthRepository();
        if (username == null || username.trim().isEmpty()) {
            authState.setValue(AuthState.ERROR);
            errorMessage.setValue("Username cannot be empty");
            return;
        }
        if (password == null || password.isEmpty()) {
            authState.setValue(AuthState.ERROR);
            errorMessage.setValue("Password cannot be empty");
            return;
        }

        authState.setValue(AuthState.LOADING);
        
        authRepository.login(username.trim(), password, new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess(LoginResponse response) {
                PendingRecoveryWorker.enqueue(getApplication());
                authState.postValue(AuthState.SUCCESS);
                loginResponse.postValue(response);
                currentUser.postValue(response.getUser());
            }

            @Override
            public void onError(String message, int code) {
                errorMessage.postValue(message);
                authState.postValue(AuthState.ERROR);
            }
        });
    }

    public void logout() {
        refreshAuthRepository();
        authRepository.logout();
        authState.setValue(AuthState.IDLE);
        loginResponse.setValue(null);
        currentUser.setValue(null);
    }

    public void checkLoginStatus() {
        refreshAuthRepository();
        if (authRepository.isLoggedIn()) {
            currentUser.setValue(authRepository.getCurrentUser());
            authState.setValue(AuthState.SUCCESS);
        } else {
            authState.setValue(AuthState.IDLE);
        }
    }

    public LiveData<AuthState> getAuthState() {
        return authState;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public LiveData<LoginResponse> getLoginResponse() {
        return loginResponse;
    }

    public LiveData<User> getCurrentUser() {
        return currentUser;
    }

    public void clearError() {
        errorMessage.setValue(null);
    }

    public void resetLoginState() {
        authState.setValue(AuthState.IDLE);
        errorMessage.setValue(null);
        loginResponse.setValue(null);
        currentUser.setValue(null);
    }

    public void consumeLoginResponse() {
        loginResponse.setValue(null);
    }

    private void refreshAuthRepository() {
        FlashNoteApp app = (FlashNoteApp) getApplication();
        this.authRepository = app.getAuthRepository();
    }
}
