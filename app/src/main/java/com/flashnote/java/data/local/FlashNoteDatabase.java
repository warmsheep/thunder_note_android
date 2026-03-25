package com.flashnote.java.data.local;

import androidx.room.Database;
import androidx.room.RoomDatabase;

import com.flashnote.java.data.model.PendingMessage;

@Database(entities = {PendingMessage.class}, version = 4, exportSchema = false)
public abstract class FlashNoteDatabase extends RoomDatabase {
    public abstract PendingMessageDao pendingMessageDao();
}
