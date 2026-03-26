package com.flashnote.java.data.repository;

import androidx.lifecycle.LiveData;

import java.util.Map;

public interface SyncRepository {
    interface SyncCallback {
        void onSuccess(Map<String, Object> data);
        void onError(String message, int code);
    }

    void bootstrap(SyncCallback callback);
    
    void pull(SyncCallback callback);

    void pullAndRefreshLocal(SyncCallback callback);

    void syncAll(SyncCallback callback);

    void getPendingSyncCount(CountCallback callback);

    void pushLocalState(SyncCallback callback);
    
    void push(Map<String, Object> payload, SyncCallback callback);

    interface CountCallback {
        void onResult(int count);
    }
}
