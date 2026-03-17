package com.flashnote.java.data.repository;

import androidx.lifecycle.LiveData;

import com.flashnote.java.data.model.Message;

import java.util.List;

public interface MessageRepository {
    LiveData<List<Message>> getMessages(long flashNoteId);
    
    LiveData<Boolean> isLoading();
    
    LiveData<String> getErrorMessage();

    List<Message> getCachedMessages();

    void bindFlashNote(long flashNoteId);

    void sendText(long flashNoteId, String content, Runnable onSuccess);

    void deleteMessage(long flashNoteId, long messageId, Runnable onSuccess);

    void sendMessage(long flashNoteId, Message message, Runnable onSuccess);

    void loadMoreMessages(long flashNoteId);

    LiveData<Boolean> getHasMore();
}
