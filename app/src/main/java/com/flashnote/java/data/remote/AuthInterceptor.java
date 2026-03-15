package com.flashnote.java.data.remote;

import androidx.annotation.NonNull;

import com.flashnote.java.TokenManager;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class AuthInterceptor implements Interceptor {
    private final TokenManager tokenManager;

    public AuthInterceptor(TokenManager tokenManager) {
        this.tokenManager = tokenManager;
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request originalRequest = chain.request();
        
        String token = tokenManager.getAccessToken();
        if (token == null || token.isEmpty()) {
            return chain.proceed(originalRequest);
        }

        Request.Builder builder = originalRequest.newBuilder()
                .header("Authorization", "Bearer " + token);

        if (originalRequest.header("Content-Type") == null
                && originalRequest.body() != null
                && !(originalRequest.body() instanceof okhttp3.MultipartBody)) {
            builder.header("Content-Type", "application/json");
        }

        return chain.proceed(builder.build());
    }
}
