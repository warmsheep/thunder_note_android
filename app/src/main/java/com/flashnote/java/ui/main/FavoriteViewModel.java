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
    private final RefreshableRepositoryDelegate delegate;

    public FavoriteViewModel(@NonNull Application application) {
        super(application);
        repository = ((FlashNoteApp) application).getFavoriteRepository();
        favorites = repository.getFavorites();
        delegate = new RefreshableRepositoryDelegate(
                repository.isLoading(),
                repository.getErrorMessage(),
                repository::clearError,
                repository::refresh
        );
    }

    public LiveData<Boolean> isLoading() {
        return delegate.isLoading();
    }

    public LiveData<String> getErrorMessage() {
        return delegate.getErrorMessage();
    }

    public LiveData<List<FavoriteItem>> getFavorites() {
        return favorites;
    }

    public void clearError() {
        delegate.clearError();
    }

    public void refresh() {
        delegate.refresh();
    }

    public void removeFavorite(Long messageId, FavoriteRepository.ActionCallback callback) {
        repository.removeFavorite(messageId, callback);
    }
}
