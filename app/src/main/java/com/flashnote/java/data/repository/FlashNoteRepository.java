package com.flashnote.java.data.repository;

import androidx.lifecycle.LiveData;

import com.flashnote.java.data.model.FlashNote;

import java.util.List;

public interface FlashNoteRepository {
    LiveData<List<FlashNote>> getNotes();
    
    LiveData<Boolean> isLoading();
    
    LiveData<String> getErrorMessage();

    void refresh();

    void createNote(String title, String content);
    
    void updateNote(Long id, String title, String content);
    
    void deleteNote(Long id);
}
