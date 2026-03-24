package com.flashnote.java.data.repository;

import androidx.annotation.Nullable;

import com.flashnote.java.data.local.PendingMessageDao;
import com.flashnote.java.data.model.PendingMessage;

import java.util.List;

public class PendingMessageRepositoryImpl implements PendingMessageRepository {

    private final PendingMessageDao pendingMessageDao;

    public PendingMessageRepositoryImpl(PendingMessageDao pendingMessageDao) {
        this.pendingMessageDao = pendingMessageDao;
    }

    @Override
    public long insert(PendingMessage pendingMessage) {
        return pendingMessageDao.insert(pendingMessage);
    }

    @Override
    public void update(PendingMessage pendingMessage) {
        pendingMessageDao.update(pendingMessage);
    }

    @Override
    public void delete(PendingMessage pendingMessage) {
        pendingMessageDao.delete(pendingMessage);
    }

    @Nullable
    @Override
    public PendingMessage findByLocalId(long localId) {
        return pendingMessageDao.findByLocalId(localId);
    }

    @Override
    public List<PendingMessage> getByConversationKey(long conversationKey) {
        return pendingMessageDao.getByConversationKey(conversationKey);
    }

    @Override
    public List<PendingMessage> getByStatus(String status) {
        return pendingMessageDao.getByStatus(status);
    }
}
