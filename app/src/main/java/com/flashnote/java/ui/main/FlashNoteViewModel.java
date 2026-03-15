package com.flashnote.java.ui.main;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.flashnote.java.FlashNoteApp;
import com.flashnote.java.data.model.FlashNote;
import com.flashnote.java.data.repository.FlashNoteRepository;

import java.util.List;

public class FlashNoteViewModel extends AndroidViewModel {
    private final FlashNoteRepository repository;
    private final LiveData<List<FlashNote>> notes;
    private final LiveData<Boolean> isLoading;
    private final LiveData<String> errorMessage;

    public FlashNoteViewModel(@NonNull Application application) {
        super(application);
        repository = ((FlashNoteApp) application).getFlashNoteRepository();
        notes = repository.getNotes();
        isLoading = repository.isLoading();
        errorMessage = repository.getErrorMessage();
        repository.refresh();
    }

    public LiveData<List<FlashNote>> getNotes() {
        return notes;
    }

    public LiveData<Boolean> isLoading() {
        return isLoading;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public void createNote(String title, String content) {
        repository.createNote(title, content);
    }

    public void createNote() {
        repository.createNote("New flash note", "");
    }
    
    public void updateNote(Long id, String title, String content) {
        repository.updateNote(id, title, content);
    }
    
    public void deleteNote(Long id) {
        repository.deleteNote(id);
    }
}
