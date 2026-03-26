package com.flashnote.java.data.repository;

import androidx.lifecycle.LiveData;

import com.flashnote.java.data.model.FlashNote;
import com.flashnote.java.data.model.FlashNoteSearchResult;

import java.util.List;

public interface FlashNoteRepository {
    interface SearchCallback {
        void onSuccess(List<FlashNoteSearchResult> noteNameResults, List<FlashNoteSearchResult> messageContentResults);

        void onError(String message);
    }

    LiveData<List<FlashNote>> getNotes();
    
    LiveData<Boolean> isLoading();
    
    LiveData<String> getErrorMessage();

    void clearError();

    void refresh();

    void searchNotes(String query, SearchCallback callback);

    void createNote(String title, String icon, String collectionName);
    
    void updateNote(Long id, String title, String content, String icon, String collectionName);

    void setPinned(Long id, boolean pinned);

    void setHidden(Long id, boolean hidden);
    
    void deleteNote(Long id);

    void clearInboxMessages(Runnable onSuccess);

    void updateInboxPreviewLocally(String latestMessage);
}
