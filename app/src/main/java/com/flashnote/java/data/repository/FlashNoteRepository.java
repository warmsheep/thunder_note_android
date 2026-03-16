package com.flashnote.java.data.repository;

import androidx.lifecycle.LiveData;

import com.flashnote.java.data.model.FlashNote;

import java.util.List;

public interface FlashNoteRepository {
    LiveData<List<FlashNote>> getNotes();
    
    LiveData<Boolean> isLoading();
    
    LiveData<String> getErrorMessage();

    void refresh();

    void createNote(String title, String icon, String collectionName);
    
    void updateNote(Long id, String title, String content, String icon, String collectionName);
    
    void deleteNote(Long id);
}
