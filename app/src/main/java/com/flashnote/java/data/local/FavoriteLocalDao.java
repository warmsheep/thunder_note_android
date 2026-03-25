package com.flashnote.java.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface FavoriteLocalDao {
    @Query("SELECT * FROM favorites_local WHERE user_id = :userId ORDER BY favorited_at DESC, id DESC")
    LiveData<List<FavoriteLocalEntity>> observeAllByUserId(long userId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(FavoriteLocalEntity item);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<FavoriteLocalEntity> items);

    @Query("DELETE FROM favorites_local WHERE user_id = :userId")
    void clearAllByUserId(long userId);

    @Query("DELETE FROM favorites_local WHERE message_id = :messageId")
    void deleteByMessageId(long messageId);
}
