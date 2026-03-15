package com.flashnote.java.data.remote;

import com.flashnote.java.data.model.ApiResponse;

import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface SyncService {
    @POST("api/sync/bootstrap")
    Call<ApiResponse<Map<String, Object>>> bootstrap();

    @POST("api/sync/pull")
    Call<ApiResponse<Map<String, Object>>> pull();

    @POST("api/sync/push")
    Call<ApiResponse<Map<String, Object>>> push(@Body Map<String, Object> payload);
}
