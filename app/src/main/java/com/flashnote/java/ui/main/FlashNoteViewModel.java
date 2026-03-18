package com.flashnote.java.ui.main;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.flashnote.java.FlashNoteApp;
import com.flashnote.java.data.model.Collection;
import com.flashnote.java.data.model.FlashNote;
import com.flashnote.java.data.repository.CollectionRepository;
import com.flashnote.java.data.repository.FlashNoteRepository;

import java.util.ArrayList;
import java.util.List;

public class FlashNoteViewModel extends AndroidViewModel {
    private final FlashNoteRepository repository;
    private final CollectionRepository collectionRepository;
    private final LiveData<List<FlashNote>> notes;
    private final LiveData<List<Collection>> collections;
    private final LiveData<Boolean> isLoading;
    private final LiveData<String> errorMessage;

    public FlashNoteViewModel(@NonNull Application application) {
        super(application);
        repository = ((FlashNoteApp) application).getFlashNoteRepository();
        collectionRepository = ((FlashNoteApp) application).getCollectionRepository();
        notes = repository.getNotes();
        collections = collectionRepository.getCollections();
        isLoading = repository.isLoading();
        errorMessage = repository.getErrorMessage();
        repository.clearError();
        collectionRepository.clearError();
        repository.refresh();
        collectionRepository.refresh();
    }

    public LiveData<List<FlashNote>> getNotes() {
        return notes;
    }

    public LiveData<Boolean> isLoading() {
        return isLoading;
    }

    public LiveData<List<Collection>> getCollections() {
        return collections;
    }

    public void refresh() {
        repository.clearError();
        collectionRepository.clearError();
        repository.refresh();
        collectionRepository.refresh();
    }

    public void searchNotes(String query, FlashNoteRepository.SearchCallback callback) {
        repository.searchNotes(query, callback);
    }

    public void renameCollectionLocally(String oldName, String newName) {
        List<FlashNote> currentNotes = notes.getValue();
        if (currentNotes == null || oldName == null || oldName.equals(newName)) {
            return;
        }
        for (FlashNote note : currentNotes) {
            if (oldName.equals(note.getTags())) {
                note.setTags(newName);
            }
        }
    }

    public void clearCollectionLocally(String collectionName) {
        List<FlashNote> currentNotes = notes.getValue();
        if (currentNotes == null || collectionName == null) {
            return;
        }
        for (FlashNote note : currentNotes) {
            if (collectionName.equals(note.getTags())) {
                note.setTags(null);
            }
        }
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public void clearError() {
        repository.clearError();
    }

    public void createNote(String title, String icon, String collectionName) {
        ensureCollectionExists(collectionName, () -> repository.createNote(title, icon, collectionName));
    }

    public void createNote() {
        repository.createNote("New flash note", "⚡", null);
    }
    
    public void updateNote(Long id, String title, String content, String icon, String collectionName) {
        ensureCollectionExists(collectionName, () -> repository.updateNote(id, title, content, icon, collectionName));
    }
    
    public void deleteNote(Long id) {
        repository.deleteNote(id);
    }

    private void ensureCollectionExists(String collectionName, Runnable onReady) {
        String normalized = normalizeCollectionName(collectionName);
        if (normalized == null) {
            onReady.run();
            return;
        }

        List<Collection> currentCollections = collections.getValue();
        if (currentCollections != null) {
            for (Collection collection : currentCollections) {
                if (normalized.equals(normalizeCollectionName(collection.getName()))) {
                    onReady.run();
                    return;
                }
            }
        }

        collectionRepository.createCollection(normalized, "", () -> {
            List<Collection> current = collections.getValue();
            List<Collection> updated = current == null ? new ArrayList<>() : new ArrayList<>(current);
            Collection collection = new Collection();
            collection.setName(normalized);
            updated.add(0, collection);
            onReady.run();
        });
    }

    private String normalizeCollectionName(String rawName) {
        if (rawName == null) {
            return null;
        }
        String trimmed = rawName.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
