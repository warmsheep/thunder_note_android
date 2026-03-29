package com.flashnote.java.data.remote;

import com.flashnote.java.data.model.ApiResponse;
import com.flashnote.java.data.model.ChangePasswordRequest;
import com.flashnote.java.data.model.GestureLockBackupRequest;
import com.flashnote.java.data.model.GestureLockBackupResponse;
import com.flashnote.java.data.model.LoginRequest;
import com.flashnote.java.data.model.LoginResponse;
import com.flashnote.java.data.model.RegisterRequest;

import retrofit2.Call;
import retrofit2.http.DELETE;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;

public interface AuthService {
    @POST("api/auth/login")
    Call<ApiResponse<LoginResponse>> login(@Body LoginRequest request);

    @POST("api/auth/register")
    Call<ApiResponse<Void>> register(@Body RegisterRequest request);
    
    @POST("api/auth/refresh")
    Call<ApiResponse<LoginResponse>> refreshToken(@Body RefreshTokenRequest request);
    
    @POST("api/auth/logout")
    Call<ApiResponse<Void>> logout();

    @PUT("api/auth/password")
    Call<ApiResponse<Void>> changePassword(@Body ChangePasswordRequest body);

    @PUT("api/auth/gesture-lock")
    Call<ApiResponse<Void>> saveGestureLockBackup(@Body GestureLockBackupRequest body);

    @GET("api/auth/gesture-lock")
    Call<ApiResponse<GestureLockBackupResponse>> getGestureLockBackup();

    @DELETE("api/auth/gesture-lock")
    Call<ApiResponse<Void>> clearGestureLockBackup();
}
