package com.flashnote.java.data.repository;

import androidx.annotation.Nullable;

import com.flashnote.java.data.model.PendingMessage;

import java.util.List;

public interface PendingMessageRepository {
    long insert(PendingMessage pendingMessage);

    void update(PendingMessage pendingMessage);

    void delete(PendingMessage pendingMessage);

    @Nullable
    PendingMessage findByLocalId(long localId);

    List<PendingMessage> getByConversationKey(long conversationKey);

    List<PendingMessage> getByStatus(String status);
}
