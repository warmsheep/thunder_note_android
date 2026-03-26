package com.flashnote.java.data.repository;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import com.flashnote.java.data.local.PendingMessageDao;
import com.flashnote.java.data.model.PendingMessage;

import java.util.Arrays;
import java.util.List;

public class PendingMessageRepositoryImpl implements PendingMessageRepository {

    private static final List<String> PENDING_SYNC_STATUSES = Arrays.asList(
            PendingMessageDispatcher.STATUS_QUEUED,
            PendingMessageDispatcher.STATUS_PROCESSING,
            PendingMessageDispatcher.STATUS_UPLOADING,
            PendingMessageDispatcher.STATUS_UPLOADED,
            PendingMessageDispatcher.STATUS_SENDING,
            PendingMessageDispatcher.STATUS_FAILED
    );

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
    public LiveData<List<PendingMessage>> observeByConversationKey(long conversationKey) {
        return pendingMessageDao.observeByConversationKey(conversationKey);
    }

    @Override
    public LiveData<Integer> observePendingSyncCount() {
        return pendingMessageDao.observeCountByStatuses(PENDING_SYNC_STATUSES);
    }

    @Override
    public List<PendingMessage> getByStatus(String status) {
        return pendingMessageDao.getByStatus(status);
    }

    @Override
    public int getPendingSyncCountNow() {
        return pendingMessageDao.countByStatuses(PENDING_SYNC_STATUSES);
    }

    @Override
    public List<PendingMessage> getFailedByConversationKey(long conversationKey) {
        return pendingMessageDao.getByConversationKeyAndStatus(conversationKey, PendingMessageDispatcher.STATUS_FAILED);
    }

    @Override
    public void clearConversation(long conversationKey) {
        pendingMessageDao.clearConversation(conversationKey);
    }
}
