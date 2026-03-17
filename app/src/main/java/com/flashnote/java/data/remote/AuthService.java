package com.flashnote.java.data.remote;

import com.flashnote.java.data.model.ApiResponse;
import com.flashnote.java.data.model.LoginRequest;
import com.flashnote.java.data.model.LoginResponse;
import com.flashnote.java.data.model.RegisterRequest;

import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Query;

public interface AuthService {
    @POST("api/auth/login")
    Call<ApiResponse<LoginResponse>> login(@Body LoginRequest request);

    @POST("api/auth/register")
    Call<ApiResponse<Void>> register(@Body RegisterRequest request);
    
    @POST("api/auth/refresh")
    Call<ApiResponse<LoginResponse>> refreshToken(@Query("refreshToken") String refreshToken);
    
    @POST("api/auth/logout")
    Call<ApiResponse<Void>> logout();

    @PUT("api/auth/password")
    Call<ApiResponse<Void>> changePassword(@Body Map<String, String> body);
}
