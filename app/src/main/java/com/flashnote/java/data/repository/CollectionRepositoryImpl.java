package com.flashnote.java.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.flashnote.java.DebugLog;
import com.flashnote.java.TokenManager;
import com.flashnote.java.data.local.CollectionLocalDao;
import com.flashnote.java.data.local.CollectionLocalEntity;
import com.flashnote.java.data.model.ApiResponse;
import com.flashnote.java.data.model.Collection;
import com.flashnote.java.data.remote.CollectionService;

import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CollectionRepositoryImpl implements CollectionRepository {
    private final CollectionService collectionService;
    private final CollectionLocalDao collectionLocalDao;
    private final TokenManager tokenManager;
    private final CollectionLocalMapper localMapper = new CollectionLocalMapper();
    private final RepositoryStateStore stateStore = new RepositoryStateStore();
    private final LiveData<List<Collection>> collectionsLiveData;
    private final ExecutorService localExecutor = Executors.newSingleThreadExecutor();

    public CollectionRepositoryImpl(CollectionService collectionService, CollectionLocalDao collectionLocalDao, TokenManager tokenManager) {
        this.collectionService = collectionService;
        this.collectionLocalDao = collectionLocalDao;
        this.tokenManager = tokenManager;
        long currentUserId = Long.parseLong(RepositoryAuthSupport.requireCurrentUserId(tokenManager));
        this.collectionsLiveData = Transformations.map(collectionLocalDao.observeAllByUserId(currentUserId), localMapper::toModelList);
    }

    @Override
    public LiveData<List<Collection>> getCollections() {
        return collectionsLiveData;
    }

    @Override
    public LiveData<Boolean> isLoading() {
        return stateStore.isLoading();
    }

    @Override
    public LiveData<String> getErrorMessage() {
        return stateStore.getErrorMessage();
    }

    @Override
    public void clearError() {
        stateStore.clearError();
    }

    @Override
    public void refresh() {
        stateStore.setLoading(true);
        collectionService.list().enqueue(new Callback<ApiResponse<List<Collection>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<Collection>>> call, 
                                 Response<ApiResponse<List<Collection>>> response) {
                stateStore.setLoading(false);
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<List<Collection>> apiResponse = response.body();
                    if (apiResponse.isSuccess() && apiResponse.getData() != null) {
                        clearError();
                        persistRemoteCollections(apiResponse.getData());
                    } else {
                        String errMsg = apiResponse.getMessage();
                        DebugLog.w("CollectionRepo", errMsg);
                        stateStore.setError(errMsg);
                    }
                } else {
                    String errMsg = "Failed to load collections: " + response.code();
                    DebugLog.w("CollectionRepo", errMsg);
                    stateStore.setError(errMsg);
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<Collection>>> call, Throwable t) {
                stateStore.setLoading(false);
                DebugLog.e("CollectionRepo", "refresh failed", t);
                stateStore.setError("Network error: " + t.getMessage());
            }
        });
    }

    @Override
    public void createCollection(String name, String description, Runnable onSuccess) {
        stateStore.setLoading(true);
        Collection collection = new Collection();
        collection.setName(name);
        collection.setDescription(description);
        
        collectionService.create(collection).enqueue(new Callback<ApiResponse<Collection>>() {
            @Override
            public void onResponse(Call<ApiResponse<Collection>> call, 
                                 Response<ApiResponse<Collection>> response) {
                stateStore.setLoading(false);
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<Collection> apiResponse = response.body();
                    if (apiResponse.isSuccess() && apiResponse.getData() != null) {
                        clearError();
                        persistSingleCollection(apiResponse.getData());
                        if (onSuccess != null) {
                            onSuccess.run();
                        }
                    } else {
                        String errMsg = apiResponse.getMessage();
                        DebugLog.w("CollectionRepo", errMsg);
                        stateStore.setError(errMsg);
                    }
                } else {
                    String errMsg = "Failed to create collection: " + response.code();
                    DebugLog.w("CollectionRepo", errMsg);
                    stateStore.setError(errMsg);
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Collection>> call, Throwable t) {
                stateStore.setLoading(false);
                DebugLog.e("CollectionRepo", "create failed", t);
                stateStore.setError("Network error: " + t.getMessage());
            }
        });
    }

    @Override
    public void updateCollection(Long id, String name, String description, Runnable onSuccess) {
        stateStore.setLoading(true);
        Collection collection = new Collection();
        collection.setName(name);
        collection.setDescription(description);
        
        collectionService.update(id, collection).enqueue(new Callback<ApiResponse<Collection>>() {
            @Override
            public void onResponse(Call<ApiResponse<Collection>> call, 
                                 Response<ApiResponse<Collection>> response) {
                stateStore.setLoading(false);
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<Collection> apiResponse = response.body();
                    if (apiResponse.isSuccess() && apiResponse.getData() != null) {
                        clearError();
                        persistSingleCollection(apiResponse.getData());
                        if (onSuccess != null) {
                            onSuccess.run();
                        }
                    } else {
                        String errMsg = apiResponse.getMessage();
                        DebugLog.w("CollectionRepo", errMsg);
                        stateStore.setError(errMsg);
                    }
                } else {
                    String errMsg = "Failed to update collection: " + response.code();
                    DebugLog.w("CollectionRepo", errMsg);
                    stateStore.setError(errMsg);
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Collection>> call, Throwable t) {
                stateStore.setLoading(false);
                DebugLog.e("CollectionRepo", "update failed", t);
                stateStore.setError("Network error: " + t.getMessage());
            }
        });
    }

    @Override
    public void deleteCollection(Long id, Runnable onSuccess) {
        stateStore.setLoading(true);
        collectionService.delete(id).enqueue(new Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(Call<ApiResponse<Void>> call, 
                                 Response<ApiResponse<Void>> response) {
                stateStore.setLoading(false);
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<Void> apiResponse = response.body();
                    if (apiResponse.isSuccess()) {
                        clearError();
                        localExecutor.execute(() -> collectionLocalDao.deleteById(id));
                        if (onSuccess != null) {
                            onSuccess.run();
                        }
                    } else {
                        String errMsg = apiResponse.getMessage();
                        DebugLog.w("CollectionRepo", errMsg);
                        stateStore.setError(errMsg);
                    }
                } else {
                    String errMsg = "Failed to delete collection: " + response.code();
                    DebugLog.w("CollectionRepo", errMsg);
                    stateStore.setError(errMsg);
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                stateStore.setLoading(false);
                DebugLog.e("CollectionRepo", "delete failed", t);
                stateStore.setError("Network error: " + t.getMessage());
            }
        });
    }

    private void persistRemoteCollections(List<Collection> collections) {
        List<CollectionLocalEntity> entities = localMapper.toLocalList(collections);
        long currentUserId = Long.parseLong(RepositoryAuthSupport.requireCurrentUserId(tokenManager));
        localExecutor.execute(() -> {
            collectionLocalDao.clearAllByUserId(currentUserId);
            if (!entities.isEmpty()) {
                collectionLocalDao.upsertAll(entities);
            }
        });
    }

    private void persistSingleCollection(Collection collection) {
        CollectionLocalEntity entity = localMapper.toLocal(collection);
        if (entity == null) {
            return;
        }
        localExecutor.execute(() -> collectionLocalDao.upsert(entity));
    }

}
