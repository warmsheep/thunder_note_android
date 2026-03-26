package com.flashnote.java.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface MessageLocalDao {
    @Query("SELECT * FROM messages_local WHERE conversation_key = :conversationKey ORDER BY created_at ASC")
    LiveData<List<MessageLocalEntity>> observeByConversationKey(long conversationKey);

    @Query("SELECT * FROM messages_local WHERE conversation_key = :conversationKey ORDER BY created_at ASC")
    List<MessageLocalEntity> getByConversationKeyNow(long conversationKey);

    @Query("SELECT * FROM messages_local ORDER BY created_at ASC")
    List<MessageLocalEntity> getAllNow();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(MessageLocalEntity message);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<MessageLocalEntity> messages);

    @Query("DELETE FROM messages_local WHERE conversation_key = :conversationKey")
    void clearConversation(long conversationKey);

    @Query("DELETE FROM messages_local WHERE id = :messageId")
    void deleteById(long messageId);
}
