package com.flashnote.java.data.repository;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import com.flashnote.java.data.model.PendingMessage;

import java.util.List;

public interface PendingMessageRepository {
    long insert(PendingMessage pendingMessage);

    void update(PendingMessage pendingMessage);

    void delete(PendingMessage pendingMessage);

    @Nullable
    PendingMessage findByLocalId(long localId);

    List<PendingMessage> getByConversationKey(long conversationKey);

    LiveData<List<PendingMessage>> observeByConversationKey(long conversationKey);

    List<PendingMessage> getByStatus(String status);

    List<PendingMessage> getFailedByConversationKey(long conversationKey);

    void clearConversation(long conversationKey);
}
