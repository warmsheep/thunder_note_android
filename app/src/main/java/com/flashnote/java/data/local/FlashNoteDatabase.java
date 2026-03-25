package com.flashnote.java.data.local;

import androidx.room.Database;
import androidx.room.RoomDatabase;

import com.flashnote.java.data.model.PendingMessage;

@Database(entities = {PendingMessage.class, FlashNoteLocalEntity.class, MessageLocalEntity.class, FavoriteLocalEntity.class, CollectionLocalEntity.class}, version = 9, exportSchema = false)
public abstract class FlashNoteDatabase extends RoomDatabase {
    public abstract PendingMessageDao pendingMessageDao();

    public abstract FlashNoteLocalDao flashNoteLocalDao();

    public abstract MessageLocalDao messageLocalDao();

    public abstract FavoriteLocalDao favoriteLocalDao();

    public abstract CollectionLocalDao collectionLocalDao();
}
