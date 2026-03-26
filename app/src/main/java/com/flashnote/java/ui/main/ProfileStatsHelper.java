package com.flashnote.java.ui.main;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.flashnote.java.FlashNoteApp;

final class ProfileStatsHelper {
    private static final String PREFS_PROFILE = "profile_tab";
    private static final String PREF_KEY_RECORD_COUNT = "record_count";

    interface RecordCountCallback {
        void onCountReady(long count);

        void onError();
    }

    @Nullable
    Long getCachedRecordCount(@Nullable Context context) {
        if (context == null) {
            return null;
        }
        long cached = context.getSharedPreferences(PREFS_PROFILE, Context.MODE_PRIVATE)
                .getLong(PREF_KEY_RECORD_COUNT, -1L);
        return cached >= 0 ? cached : null;
    }

    void persistRecordCount(@Nullable Context context, long count) {
        if (context == null) {
            return;
        }
        context.getSharedPreferences(PREFS_PROFILE, Context.MODE_PRIVATE)
                .edit()
                .putLong(PREF_KEY_RECORD_COUNT, count)
                .apply();
    }

    int resolveFlashNoteCount(@NonNull FlashNoteApp app) {
        java.util.List<?> notes = app.getFlashNoteRepository().getNotes().getValue();
        return notes == null ? 0 : notes.size();
    }

    int resolveFavoriteCount(@NonNull FlashNoteApp app) {
        java.util.List<?> favorites = app.getFavoriteRepository().getFavorites().getValue();
        return favorites == null ? 0 : favorites.size();
    }

    void loadRecordCount(@NonNull FlashNoteApp app, @NonNull RecordCountCallback callback) {
        app.getMessageRepository().countMessages(new com.flashnote.java.data.repository.MessageRepository.CountCallback() {
            @Override
            public void onSuccess(long count) {
                callback.onCountReady(count);
            }

            @Override
            public void onError(String message, int code) {
                callback.onError();
            }
        });
    }
}
