package com.flashnote.java.data.remote;

import androidx.annotation.NonNull;

import com.flashnote.java.BuildConfig;
import com.flashnote.java.DebugLog;
import com.flashnote.java.TokenManager;
import com.flashnote.java.data.model.ApiResponse;
import com.flashnote.java.data.model.LoginResponse;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;

import okhttp3.Authenticator;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;

public class TokenAuthenticator implements Authenticator {
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");
    private static final Type LOGIN_RESPONSE_TYPE = new TypeToken<ApiResponse<LoginResponse>>() { }
            .getType();

    private final TokenManager tokenManager;
    private final ServerConfigStore serverConfigStore;
    private final Gson gson = new Gson();

    public TokenAuthenticator(TokenManager tokenManager, ServerConfigStore serverConfigStore) {
        this.tokenManager = tokenManager;
        this.serverConfigStore = serverConfigStore;
    }

    @Override
    public Request authenticate(Route route, @NonNull Response response) throws IOException {
        if (responseCount(response) >= 2) {
            return null;
        }

        String refreshToken = tokenManager.getRefreshToken();
        if (refreshToken == null || refreshToken.isEmpty()) {
            tokenManager.clearTokens();
            return null;
        }

        if (response.request().url().encodedPath().contains("/api/auth/refresh")) {
            tokenManager.clearTokens();
            return null;
        }

        OkHttpClient refreshClient = new OkHttpClient.Builder().build();
        RefreshTokenRequest refreshTokenRequest = new RefreshTokenRequest(refreshToken);
        String baseUrl = serverConfigStore == null ? BuildConfig.BASE_URL : serverConfigStore.getBaseUrl();
        Request refreshRequest = new Request.Builder()
                .url(baseUrl + "api/auth/refresh")
                .post(RequestBody.create(gson.toJson(refreshTokenRequest), JSON_MEDIA_TYPE))
                .build();

        try (Response refreshResponse = refreshClient.newCall(refreshRequest).execute()) {
            if (!refreshResponse.isSuccessful() || refreshResponse.body() == null) {
                tokenManager.clearTokens();
                return null;
            }

            String body = refreshResponse.body().string();
            ApiResponse<LoginResponse> apiResponse = gson.fromJson(body, LOGIN_RESPONSE_TYPE);
            if (apiResponse == null || !apiResponse.isSuccess() || apiResponse.getData() == null) {
                tokenManager.clearTokens();
                return null;
            }

            LoginResponse loginResponse = apiResponse.getData();
            tokenManager.saveTokens(loginResponse.getToken(), loginResponse.getRefreshToken(), loginResponse.getExpiresIn());
            if (loginResponse.getUser() != null && loginResponse.getUser().getId() != null) {
                tokenManager.saveUserId(loginResponse.getUser().getId());
            }

            return response.request().newBuilder()
                    .header("Authorization", "Bearer " + loginResponse.getToken())
                    .build();
        } catch (Exception exception) {
            DebugLog.e("TokenAuth", "Refresh token request failed", exception);
            tokenManager.clearTokens();
            return null;
        }
    }

    private int responseCount(Response response) {
        int count = 1;
        while ((response = response.priorResponse()) != null) {
            count++;
        }
        return count;
    }
}
