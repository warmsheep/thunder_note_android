package com.flashnote.java.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.flashnote.java.data.model.PendingMessage;

import java.util.List;

@Dao
public interface PendingMessageDao {

    @Insert
    long insert(PendingMessage pendingMessage);

    @Update
    void update(PendingMessage pendingMessage);

    @Delete
    void delete(PendingMessage pendingMessage);

    @Query("SELECT * FROM pending_messages WHERE conversation_key = :conversationKey ORDER BY created_at ASC")
    List<PendingMessage> getByConversationKey(long conversationKey);

    @Query("SELECT * FROM pending_messages WHERE conversation_key = :conversationKey ORDER BY created_at ASC")
    LiveData<List<PendingMessage>> observeByConversationKey(long conversationKey);

    @Query("SELECT * FROM pending_messages WHERE status = :status ORDER BY created_at ASC")
    List<PendingMessage> getByStatus(String status);

    @Query("SELECT COUNT(*) FROM pending_messages WHERE status IN (:statuses)")
    LiveData<Integer> observeCountByStatuses(List<String> statuses);

    @Query("SELECT COUNT(*) FROM pending_messages WHERE status IN (:statuses)")
    int countByStatuses(List<String> statuses);

    @Query("SELECT * FROM pending_messages WHERE status IN (:statuses) ORDER BY created_at DESC")
    LiveData<List<PendingMessage>> observeByStatuses(List<String> statuses);

    @Query("SELECT * FROM pending_messages WHERE conversation_key = :conversationKey AND status = :status ORDER BY created_at ASC")
    List<PendingMessage> getByConversationKeyAndStatus(long conversationKey, String status);

    @Query("SELECT * FROM pending_messages WHERE localId = :localId LIMIT 1")
    PendingMessage findByLocalId(long localId);

    @Query("DELETE FROM pending_messages WHERE conversation_key = :conversationKey")
    void clearConversation(long conversationKey);

    @Query("UPDATE pending_messages SET status = :targetStatus, error_message = NULL WHERE status IN (:sourceStatuses)")
    void resetStatuses(List<String> sourceStatuses, String targetStatus);
}
