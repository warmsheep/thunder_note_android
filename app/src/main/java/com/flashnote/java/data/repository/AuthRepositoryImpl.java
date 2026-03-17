package com.flashnote.java.data.repository;

import com.flashnote.java.DebugLog;
import com.flashnote.java.TokenManager;
import com.flashnote.java.data.model.ApiResponse;
import com.flashnote.java.data.model.LoginRequest;
import com.flashnote.java.data.model.LoginResponse;
import com.flashnote.java.data.model.RegisterRequest;
import com.flashnote.java.data.model.User;
import com.flashnote.java.data.remote.AuthService;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AuthRepositoryImpl implements AuthRepository {
    private final AuthService authService;
    private final TokenManager tokenManager;
    private User currentUser;

    public AuthRepositoryImpl(AuthService authService, TokenManager tokenManager) {
        this.authService = authService;
        this.tokenManager = tokenManager;
    }

    @Override
    public void login(String username, String password, AuthCallback callback) {
        LoginRequest request = new LoginRequest(username, password);
        
        authService.login(request).enqueue(new Callback<ApiResponse<LoginResponse>>() {
            @Override
            public void onResponse(Call<ApiResponse<LoginResponse>> call, 
                                  Response<ApiResponse<LoginResponse>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<LoginResponse> apiResponse = response.body();
                    if (apiResponse.isSuccess() && apiResponse.getData() != null) {
                        LoginResponse loginResponse = apiResponse.getData();
                        currentUser = loginResponse.getUser();
                        tokenManager.saveTokens(
                                loginResponse.getToken(),
                                loginResponse.getRefreshToken(),
                                loginResponse.getExpiresIn() > 0 ? loginResponse.getExpiresIn() : 3600L
                        );
                        if (currentUser != null) {
                            tokenManager.saveUserId(currentUser.getId());
                            tokenManager.saveUsername(currentUser.getUsername());
                        }
                        callback.onSuccess(loginResponse);
                    } else {
                        String errMsg = apiResponse.getMessage();
                        DebugLog.w("AuthRepo", errMsg);
                        callback.onError(errMsg, apiResponse.getCode());
                    }
                } else {
                    String errMsg = "Login failed: " + response.code();
                    DebugLog.w("AuthRepo", errMsg);
                    callback.onError(errMsg, response.code());
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<LoginResponse>> call, Throwable t) {
                String errMsg = "Network error: " + t.getMessage();
                DebugLog.w("AuthRepo", errMsg);
                callback.onError(errMsg, -1);
            }
        });
    }

    @Override
    public void register(String username, String email, String password, SimpleCallback callback) {
        RegisterRequest request = new RegisterRequest(username, email, password);
        authService.register(request).enqueue(new Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<Void> apiResponse = response.body();
                    if (apiResponse.isSuccess()) {
                        callback.onSuccess();
                    } else {
                        String errMsg = apiResponse.getMessage();
                        DebugLog.w("AuthRepo", errMsg);
                        callback.onError(errMsg, apiResponse.getCode());
                    }
                } else {
                    String errMsg = "Register failed: " + response.code();
                    DebugLog.w("AuthRepo", errMsg);
                    callback.onError(errMsg, response.code());
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                String errMsg = "Network error: " + t.getMessage();
                DebugLog.w("AuthRepo", errMsg);
                callback.onError(errMsg, -1);
            }
        });
    }

    @Override
    public void logout() {
        authService.logout().enqueue(new Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(Call<ApiResponse<Void>> call, 
                                  Response<ApiResponse<Void>> response) {
            }

            @Override
            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
            }
        });
        currentUser = null;
        tokenManager.clearTokens();
    }

    @Override
    public User getCurrentUser() {
        return currentUser;
    }

    @Override
    public boolean isLoggedIn() {
        return tokenManager.hasToken();
    }

    @Override
    public void changePassword(String currentPassword, String newPassword, PasswordCallback callback) {
        Map<String, String> body = new HashMap<>();
        body.put("currentPassword", currentPassword);
        body.put("newPassword", newPassword);
        
        authService.changePassword(body).enqueue(new Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<Void> apiResponse = response.body();
                    if (apiResponse.isSuccess()) {
                        callback.onSuccess();
                    } else {
                        String errMsg = apiResponse.getMessage();
                        DebugLog.w("AuthRepo", errMsg);
                        callback.onError(errMsg, apiResponse.getCode());
                    }
                } else {
                    String errMsg = "HTTP " + response.code();
                    DebugLog.w("AuthRepo", errMsg);
                    callback.onError(errMsg, response.code());
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                String errMsg = "Network error: " + t.getMessage();
                DebugLog.w("AuthRepo", errMsg);
                callback.onError(errMsg, -1);
            }
        });
    }
}
