package com.flashnote.java.data.local;

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

    @Query("SELECT * FROM pending_messages WHERE status = :status ORDER BY created_at ASC")
    List<PendingMessage> getByStatus(String status);

    @Query("SELECT * FROM pending_messages WHERE localId = :localId LIMIT 1")
    PendingMessage findByLocalId(long localId);
}
