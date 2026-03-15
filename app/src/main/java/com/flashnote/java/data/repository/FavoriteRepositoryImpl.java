package com.flashnote.java.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.flashnote.java.data.model.ApiResponse;
import com.flashnote.java.data.model.FavoriteItem;
import com.flashnote.java.data.remote.FavoriteService;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class FavoriteRepositoryImpl implements FavoriteRepository {
    private final FavoriteService favoriteService;
    private final MutableLiveData<List<FavoriteItem>> favoritesLiveData = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    public FavoriteRepositoryImpl(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @Override
    public LiveData<List<FavoriteItem>> getFavorites() {
        return favoritesLiveData;
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
        favoriteService.list().enqueue(new Callback<ApiResponse<List<FavoriteItem>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<FavoriteItem>>> call, Response<ApiResponse<List<FavoriteItem>>> response) {
                isLoading.setValue(false);
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    favoritesLiveData.setValue(response.body().getData() == null ? new ArrayList<>() : response.body().getData());
                    return;
                }
                String message = response.body() == null ? "加载收藏失败" : response.body().getMessage();
                errorMessage.setValue(message);
            }

            @Override
            public void onFailure(Call<ApiResponse<List<FavoriteItem>>> call, Throwable t) {
                isLoading.setValue(false);
                errorMessage.setValue("Network error: " + t.getMessage());
            }
        });
    }

    @Override
    public void addFavorite(Long messageId, ActionCallback callback) {
        favoriteService.add(messageId).enqueue(new Callback<ApiResponse<FavoriteItem>>() {
            @Override
            public void onResponse(Call<ApiResponse<FavoriteItem>> call, Response<ApiResponse<FavoriteItem>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    FavoriteItem item = response.body().getData();
                    if (item != null) {
                        List<FavoriteItem> current = favoritesLiveData.getValue();
                        List<FavoriteItem> updated = current == null ? new ArrayList<>() : new ArrayList<>(current);
                        boolean exists = false;
                        for (FavoriteItem favorite : updated) {
                            if (favorite.getMessageId().equals(item.getMessageId())) {
                                exists = true;
                                break;
                            }
                        }
                        if (!exists) {
                            updated.add(0, item);
                            favoritesLiveData.setValue(updated);
                        }
                    }
                    callback.onSuccess("已加入收藏");
                    return;
                }
                int code = response.body() == null ? response.code() : response.body().getCode();
                String message = response.body() == null ? "收藏失败" : response.body().getMessage();
                callback.onError(message, code);
            }

            @Override
            public void onFailure(Call<ApiResponse<FavoriteItem>> call, Throwable t) {
                callback.onError("Network error: " + t.getMessage(), -1);
            }
        });
    }

    @Override
    public void removeFavorite(Long messageId, ActionCallback callback) {
        favoriteService.remove(messageId).enqueue(new Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    List<FavoriteItem> current = favoritesLiveData.getValue();
                    List<FavoriteItem> updated = new ArrayList<>();
                    if (current != null) {
                        for (FavoriteItem item : current) {
                            if (!item.getMessageId().equals(messageId)) {
                                updated.add(item);
                            }
                        }
                    }
                    favoritesLiveData.setValue(updated);
                    callback.onSuccess("已取消收藏");
                    return;
                }
                int code = response.body() == null ? response.code() : response.body().getCode();
                String message = response.body() == null ? "取消收藏失败" : response.body().getMessage();
                callback.onError(message, code);
            }

            @Override
            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                callback.onError("Network error: " + t.getMessage(), -1);
            }
        });
    }
}
