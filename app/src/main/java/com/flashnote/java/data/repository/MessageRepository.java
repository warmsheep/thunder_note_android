package com.flashnote.java.data.repository;

import androidx.lifecycle.LiveData;

import com.flashnote.java.data.model.Message;

import java.io.File;
import java.util.List;

public interface MessageRepository {
    interface CountCallback {
        void onSuccess(long count);
        void onError(String message, int code);
    }

    interface MergeCallback {
        void onSuccess(Message mergedMessage);
        void onError(String message);
    }

    interface SendCallback {
        void onSuccess();
        void onError(String message);
    }

    LiveData<List<Message>> getMessages(long flashNoteId);

    LiveData<List<Message>> getContactMessages(long peerUserId);
    
    LiveData<Boolean> isLoading();
    
    LiveData<String> getErrorMessage();

    void clearError();

    List<Message> getCachedMessages();

    void bindFlashNote(long flashNoteId);

    void bindContact(long peerUserId);

    void sendText(long flashNoteId, String content, Runnable onSuccess);

    void sendTextToContact(long peerUserId, String content, Runnable onSuccess);

    void enqueueMedia(long flashNoteId, long peerUserId, String mediaType, File localFile, String fileName, Long fileSize, Integer mediaDuration, Runnable onSuccess);

    void enqueueCompositeMessage(long flashNoteId, long peerUserId, Message message, SendCallback callback);

    void retryPendingMessage(long localId);

    void retryPendingForCurrentConversation();

    void retryAllPendingMessages();

    void deleteMessage(long flashNoteId, long messageId, Runnable onSuccess);

    void deleteContactMessage(long peerUserId, long messageId, Runnable onSuccess);

    void deleteMessages(List<Long> messageIds, Runnable onSuccess);

    void clearInboxMessages(Runnable onSuccess);

    void sendMessage(long flashNoteId, Message message, Runnable onSuccess);

    void sendMessage(long flashNoteId, Message message, SendCallback callback);

    void sendMessageToContact(long peerUserId, Message message, Runnable onSuccess);

    void sendMessageToContact(long peerUserId, Message message, SendCallback callback);

    void loadMoreMessages(long flashNoteId);

    void loadMoreContactMessages(long peerUserId);

    LiveData<Boolean> getHasMore();

    void countMessages(CountCallback callback);

    void addLocalMessage(Message message);

    void removeLocalMessage(Message message);

    void addLocalContactMessage(long peerUserId, Message message);

    void mergeMessages(long flashNoteId, List<Long> messageIds, String title, MergeCallback callback);

    void mergeContactMessages(long peerUserId, List<Long> messageIds, String title, MergeCallback callback);
}
