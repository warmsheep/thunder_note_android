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
    private final LiveData<List<Collection>> collectionsLiveData;
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final ExecutorService localExecutor = Executors.newSingleThreadExecutor();

    public CollectionRepositoryImpl(CollectionService collectionService, CollectionLocalDao collectionLocalDao, TokenManager tokenManager) {
        this.collectionService = collectionService;
        this.collectionLocalDao = collectionLocalDao;
        this.tokenManager = tokenManager;
        long currentUserId = requireCurrentUserId();
        this.collectionsLiveData = Transformations.map(collectionLocalDao.observeAllByUserId(currentUserId), this::toModelList);
    }

    @Override
    public LiveData<List<Collection>> getCollections() {
        return collectionsLiveData;
    }

    @Override
    public LiveData<Boolean> isLoading() {
        return isLoading;
    }

    @Override
    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    @Override
    public void clearError() {
        errorMessage.setValue(null);
    }

    @Override
    public void refresh() {
        isLoading.setValue(true);
        collectionService.list().enqueue(new Callback<ApiResponse<List<Collection>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<Collection>>> call, 
                                 Response<ApiResponse<List<Collection>>> response) {
                isLoading.setValue(false);
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<List<Collection>> apiResponse = response.body();
                    if (apiResponse.isSuccess() && apiResponse.getData() != null) {
                        clearError();
                        persistRemoteCollections(apiResponse.getData());
                    } else {
                        String errMsg = apiResponse.getMessage();
                        DebugLog.w("CollectionRepo", errMsg);
                        errorMessage.setValue(errMsg);
                    }
                } else {
                    String errMsg = "Failed to load collections: " + response.code();
                    DebugLog.w("CollectionRepo", errMsg);
                    errorMessage.setValue(errMsg);
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<Collection>>> call, Throwable t) {
                isLoading.setValue(false);
                DebugLog.e("CollectionRepo", "refresh failed", t);
                errorMessage.setValue("Network error: " + t.getMessage());
            }
        });
    }

    @Override
    public void createCollection(String name, String description, Runnable onSuccess) {
        isLoading.setValue(true);
        Collection collection = new Collection();
        collection.setName(name);
        collection.setDescription(description);
        
        collectionService.create(collection).enqueue(new Callback<ApiResponse<Collection>>() {
            @Override
            public void onResponse(Call<ApiResponse<Collection>> call, 
                                 Response<ApiResponse<Collection>> response) {
                isLoading.setValue(false);
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
                        errorMessage.setValue(errMsg);
                    }
                } else {
                    String errMsg = "Failed to create collection: " + response.code();
                    DebugLog.w("CollectionRepo", errMsg);
                    errorMessage.setValue(errMsg);
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Collection>> call, Throwable t) {
                isLoading.setValue(false);
                DebugLog.e("CollectionRepo", "create failed", t);
                errorMessage.setValue("Network error: " + t.getMessage());
            }
        });
    }

    @Override
    public void updateCollection(Long id, String name, String description, Runnable onSuccess) {
        isLoading.setValue(true);
        Collection collection = new Collection();
        collection.setName(name);
        collection.setDescription(description);
        
        collectionService.update(id, collection).enqueue(new Callback<ApiResponse<Collection>>() {
            @Override
            public void onResponse(Call<ApiResponse<Collection>> call, 
                                 Response<ApiResponse<Collection>> response) {
                isLoading.setValue(false);
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
                        errorMessage.setValue(errMsg);
                    }
                } else {
                    String errMsg = "Failed to update collection: " + response.code();
                    DebugLog.w("CollectionRepo", errMsg);
                    errorMessage.setValue(errMsg);
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Collection>> call, Throwable t) {
                isLoading.setValue(false);
                DebugLog.e("CollectionRepo", "update failed", t);
                errorMessage.setValue("Network error: " + t.getMessage());
            }
        });
    }

    @Override
    public void deleteCollection(Long id, Runnable onSuccess) {
        isLoading.setValue(true);
        collectionService.delete(id).enqueue(new Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(Call<ApiResponse<Void>> call, 
                                 Response<ApiResponse<Void>> response) {
                isLoading.setValue(false);
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
                        errorMessage.setValue(errMsg);
                    }
                } else {
                    String errMsg = "Failed to delete collection: " + response.code();
                    DebugLog.w("CollectionRepo", errMsg);
                    errorMessage.setValue(errMsg);
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                isLoading.setValue(false);
                DebugLog.e("CollectionRepo", "delete failed", t);
                errorMessage.setValue("Network error: " + t.getMessage());
            }
        });
    }

    private void persistRemoteCollections(List<Collection> collections) {
        List<CollectionLocalEntity> entities = toLocalList(collections);
        long currentUserId = requireCurrentUserId();
        localExecutor.execute(() -> {
            collectionLocalDao.clearAllByUserId(currentUserId);
            if (!entities.isEmpty()) {
                collectionLocalDao.upsertAll(entities);
            }
        });
    }

    private void persistSingleCollection(Collection collection) {
        CollectionLocalEntity entity = toLocal(collection);
        if (entity == null) {
            return;
        }
        localExecutor.execute(() -> collectionLocalDao.upsert(entity));
    }

    private List<CollectionLocalEntity> toLocalList(List<Collection> collections) {
        List<CollectionLocalEntity> result = new ArrayList<>();
        if (collections == null) {
            return result;
        }
        for (Collection collection : collections) {
            CollectionLocalEntity entity = toLocal(collection);
            if (entity != null) {
                result.add(entity);
            }
        }
        return result;
    }

    private CollectionLocalEntity toLocal(Collection collection) {
        if (collection == null || collection.getId() == null) {
            return null;
        }
        CollectionLocalEntity entity = new CollectionLocalEntity();
        entity.setId(collection.getId());
        entity.setUserId(collection.getUserId());
        entity.setName(collection.getName());
        entity.setDescription(collection.getDescription());
        entity.setCreatedAt(collection.getCreatedAt() == null ? null : collection.getCreatedAt().toString());
        entity.setUpdatedAt(collection.getUpdatedAt() == null ? null : collection.getUpdatedAt().toString());
        return entity;
    }

    private List<Collection> toModelList(List<CollectionLocalEntity> entities) {
        List<Collection> result = new ArrayList<>();
        if (entities == null) {
            return result;
        }
        for (CollectionLocalEntity entity : entities) {
            Collection collection = new Collection();
            collection.setId(entity.getId());
            collection.setUserId(entity.getUserId());
            collection.setName(entity.getName());
            collection.setDescription(entity.getDescription());
            if (entity.getCreatedAt() != null && !entity.getCreatedAt().trim().isEmpty()) {
                collection.setCreatedAt(LocalDateTime.parse(entity.getCreatedAt()));
            }
            if (entity.getUpdatedAt() != null && !entity.getUpdatedAt().trim().isEmpty()) {
                collection.setUpdatedAt(LocalDateTime.parse(entity.getUpdatedAt()));
            }
            result.add(collection);
        }
        return result;
    }

    private long requireCurrentUserId() {
        Long userId = tokenManager.getUserId();
        return userId == null ? -1L : userId;
    }
}
