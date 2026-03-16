package com.flashnote.java.data.repository;

import androidx.lifecycle.LiveData;

import com.flashnote.java.data.model.Collection;

import java.util.List;

public interface CollectionRepository {
    LiveData<List<Collection>> getCollections();
    
    LiveData<Boolean> isLoading();
    
    LiveData<String> getErrorMessage();

    void refresh();

    void createCollection(String name, String description, Runnable onSuccess);
    
    void updateCollection(Long id, String name, String description, Runnable onSuccess);
    
    void deleteCollection(Long id, Runnable onSuccess);
}
