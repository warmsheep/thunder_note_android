package com.flashnote.java.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface CollectionLocalDao {
    @Query("SELECT * FROM collections_local WHERE user_id = :userId ORDER BY updated_at DESC, id DESC")
    LiveData<List<CollectionLocalEntity>> observeAllByUserId(long userId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(CollectionLocalEntity item);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<CollectionLocalEntity> items);

    @Query("DELETE FROM collections_local WHERE user_id = :userId")
    void clearAllByUserId(long userId);

    @Query("DELETE FROM collections_local WHERE id = :id")
    void deleteById(long id);
}
