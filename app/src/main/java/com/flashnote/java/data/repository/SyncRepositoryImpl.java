package com.flashnote.java.data.repository;

import com.flashnote.java.DebugLog;
import com.flashnote.java.data.model.ApiResponse;
import com.flashnote.java.data.remote.SyncService;

import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SyncRepositoryImpl implements SyncRepository {
    private final SyncService syncService;

    public SyncRepositoryImpl(SyncService syncService) {
        this.syncService = syncService;
    }

    @Override
    public void bootstrap(SyncCallback callback) {
        syncService.bootstrap().enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<ApiResponse<Map<String, Object>>> call, 
                                 Response<ApiResponse<Map<String, Object>>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<Map<String, Object>> apiResponse = response.body();
                    if (apiResponse.isSuccess() && apiResponse.getData() != null) {
                        callback.onSuccess(apiResponse.getData());
                    } else {
                        String errMsg = apiResponse.getMessage();
                        DebugLog.w("SyncRepo", errMsg);
                        callback.onError(errMsg, apiResponse.getCode());
                    }
                } else {
                    String errMsg = "Bootstrap failed: " + response.code();
                    DebugLog.w("SyncRepo", errMsg);
                    callback.onError(errMsg, response.code());
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                String errMsg = "Network error: " + t.getMessage();
                DebugLog.w("SyncRepo", errMsg);
                callback.onError(errMsg, -1);
            }
        });
    }

    @Override
    public void pull(SyncCallback callback) {
        syncService.pull().enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<ApiResponse<Map<String, Object>>> call, 
                                 Response<ApiResponse<Map<String, Object>>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<Map<String, Object>> apiResponse = response.body();
                    if (apiResponse.isSuccess() && apiResponse.getData() != null) {
                        callback.onSuccess(apiResponse.getData());
                    } else {
                        String errMsg = apiResponse.getMessage();
                        DebugLog.w("SyncRepo", errMsg);
                        callback.onError(errMsg, apiResponse.getCode());
                    }
                } else {
                    String errMsg = "Pull failed: " + response.code();
                    DebugLog.w("SyncRepo", errMsg);
                    callback.onError(errMsg, response.code());
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                String errMsg = "Network error: " + t.getMessage();
                DebugLog.w("SyncRepo", errMsg);
                callback.onError(errMsg, -1);
            }
        });
    }

    @Override
    public void push(Map<String, Object> payload, SyncCallback callback) {
        syncService.push(payload).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<ApiResponse<Map<String, Object>>> call, 
                                 Response<ApiResponse<Map<String, Object>>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<Map<String, Object>> apiResponse = response.body();
                    if (apiResponse.isSuccess() && apiResponse.getData() != null) {
                        callback.onSuccess(apiResponse.getData());
                    } else {
                        String errMsg = apiResponse.getMessage();
                        DebugLog.w("SyncRepo", errMsg);
                        callback.onError(errMsg, apiResponse.getCode());
                    }
                } else {
                    String errMsg = "Push failed: " + response.code();
                    DebugLog.w("SyncRepo", errMsg);
                    callback.onError(errMsg, response.code());
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                String errMsg = "Network error: " + t.getMessage();
                DebugLog.w("SyncRepo", errMsg);
                callback.onError(errMsg, -1);
            }
        });
    }
}
