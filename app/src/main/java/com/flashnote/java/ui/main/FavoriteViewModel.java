package com.flashnote.java.ui.main;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.flashnote.java.FlashNoteApp;
import com.flashnote.java.data.model.FavoriteItem;
import com.flashnote.java.data.repository.FavoriteRepository;

import java.util.List;

public class FavoriteViewModel extends AndroidViewModel {
    private final FavoriteRepository repository;
    private final LiveData<List<FavoriteItem>> favorites;
    private final LiveData<Boolean> isLoading;
    private final LiveData<String> errorMessage;

    public FavoriteViewModel(@NonNull Application application) {
        super(application);
        repository = ((FlashNoteApp) application).getFavoriteRepository();
        favorites = repository.getFavorites();
        isLoading = repository.isLoading();
        errorMessage = repository.getErrorMessage();
        repository.refresh();
    }

    public LiveData<List<FavoriteItem>> getFavorites() {
        return favorites;
    }

    public LiveData<Boolean> isLoading() {
        return isLoading;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public void refresh() {
        repository.refresh();
    }

    public void removeFavorite(Long messageId, FavoriteRepository.ActionCallback callback) {
        repository.removeFavorite(messageId, callback);
    }
}
