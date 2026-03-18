package com.flashnote.java.data.repository;

import androidx.lifecycle.LiveData;

import com.flashnote.java.data.model.FavoriteItem;

import java.util.List;

public interface FavoriteRepository {
    interface ActionCallback {
        void onSuccess(String message);

        void onError(String message, int code);
    }

    LiveData<List<FavoriteItem>> getFavorites();

    LiveData<Boolean> isLoading();

    LiveData<String> getErrorMessage();

    void clearError();

    void refresh();

    void addFavorite(Long messageId, ActionCallback callback);

    void removeFavorite(Long messageId, ActionCallback callback);
}
