package com.flashnote.java.ui.main;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.flashnote.java.FlashNoteApp;
import com.flashnote.java.data.model.Collection;
import com.flashnote.java.data.repository.CollectionRepository;

import java.util.List;

public class CollectionViewModel extends AndroidViewModel {
    private final CollectionRepository repository;
    private final LiveData<List<Collection>> collections;
    private final RefreshableRepositoryDelegate delegate;

    public CollectionViewModel(@NonNull Application application) {
        super(application);
        repository = ((FlashNoteApp) application).getCollectionRepository();
        collections = repository.getCollections();
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

    public LiveData<List<Collection>> getCollections() {
        return collections;
    }

    public void clearError() {
        delegate.clearError();
    }

    public void refresh() {
        delegate.refresh();
    }

    public void createCollection(String name, String description, Runnable onSuccess) {
        repository.createCollection(name, description, onSuccess);
    }

    public void updateCollection(Long id, String name, String description, Runnable onSuccess) {
        repository.updateCollection(id, name, description, onSuccess);
    }

    public void deleteCollection(Long id, Runnable onSuccess) {
        repository.deleteCollection(id, onSuccess);
    }
}
