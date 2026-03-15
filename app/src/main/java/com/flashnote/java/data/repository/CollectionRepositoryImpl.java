package com.flashnote.java.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.flashnote.java.data.model.ApiResponse;
import com.flashnote.java.data.model.Collection;
import com.flashnote.java.data.remote.CollectionService;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CollectionRepositoryImpl implements CollectionRepository {
    private final CollectionService collectionService;
    private final MutableLiveData<List<Collection>> collectionsLiveData = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    public CollectionRepositoryImpl(CollectionService collectionService) {
        this.collectionService = collectionService;
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
                        collectionsLiveData.setValue(apiResponse.getData());
                    } else {
                        errorMessage.setValue(apiResponse.getMessage());
                    }
                } else {
                    errorMessage.setValue("Failed to load collections: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<Collection>>> call, Throwable t) {
                isLoading.setValue(false);
                errorMessage.setValue("Network error: " + t.getMessage());
            }
        });
    }

    @Override
    public void createCollection(String name, String description) {
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
                        List<Collection> current = collectionsLiveData.getValue();
                        List<Collection> updated = current == null ? new ArrayList<>() : new ArrayList<>(current);
                        updated.add(0, apiResponse.getData());
                        collectionsLiveData.setValue(updated);
                    } else {
                        errorMessage.setValue(apiResponse.getMessage());
                    }
                } else {
                    errorMessage.setValue("Failed to create collection: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Collection>> call, Throwable t) {
                isLoading.setValue(false);
                errorMessage.setValue("Network error: " + t.getMessage());
            }
        });
    }

    @Override
    public void updateCollection(Long id, String name, String description) {
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
                        List<Collection> current = collectionsLiveData.getValue();
                        if (current != null) {
                            List<Collection> updated = new ArrayList<>();
                            for (Collection c : current) {
                                if (c.getId().equals(id)) {
                                    updated.add(apiResponse.getData());
                                } else {
                                    updated.add(c);
                                }
                            }
                            collectionsLiveData.setValue(updated);
                        }
                    } else {
                        errorMessage.setValue(apiResponse.getMessage());
                    }
                } else {
                    errorMessage.setValue("Failed to update collection: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Collection>> call, Throwable t) {
                isLoading.setValue(false);
                errorMessage.setValue("Network error: " + t.getMessage());
            }
        });
    }

    @Override
    public void deleteCollection(Long id) {
        isLoading.setValue(true);
        collectionService.delete(id).enqueue(new Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(Call<ApiResponse<Void>> call, 
                                 Response<ApiResponse<Void>> response) {
                isLoading.setValue(false);
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<Void> apiResponse = response.body();
                    if (apiResponse.isSuccess()) {
                        List<Collection> current = collectionsLiveData.getValue();
                        if (current != null) {
                            List<Collection> updated = new ArrayList<>();
                            for (Collection c : current) {
                                if (!c.getId().equals(id)) {
                                    updated.add(c);
                                }
                            }
                            collectionsLiveData.setValue(updated);
                        }
                    } else {
                        errorMessage.setValue(apiResponse.getMessage());
                    }
                } else {
                    errorMessage.setValue("Failed to delete collection: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                isLoading.setValue(false);
                errorMessage.setValue("Network error: " + t.getMessage());
            }
        });
    }
}
