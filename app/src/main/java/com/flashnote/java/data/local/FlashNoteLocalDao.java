package com.flashnote.java.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface FlashNoteLocalDao {
    @Query("SELECT * FROM flash_notes_local WHERE user_id = :userId ORDER BY pinned DESC, updated_at DESC")
    LiveData<List<FlashNoteLocalEntity>> observeAllByUserId(long userId);

    @Query("SELECT * FROM flash_notes_local WHERE user_id = :userId ORDER BY pinned DESC, updated_at DESC")
    List<FlashNoteLocalEntity> getAllNowByUserId(long userId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<FlashNoteLocalEntity> notes);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(FlashNoteLocalEntity note);

    @Query("DELETE FROM flash_notes_local WHERE id = :id")
    void deleteById(long id);

    @Query("DELETE FROM flash_notes_local WHERE user_id = :userId")
    void clearAllByUserId(long userId);
}
