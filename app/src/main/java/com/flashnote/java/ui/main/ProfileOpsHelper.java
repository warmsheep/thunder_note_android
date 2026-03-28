package com.flashnote.java.ui.main;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.flashnote.java.FlashNoteApp;
import com.flashnote.java.data.repository.MessageRepository;
import com.flashnote.java.data.repository.SyncRepository;
import com.flashnote.java.ui.auth.AuthViewModel;
import com.flashnote.java.ui.navigation.ShellNavigator;

import java.util.List;

final class ProfileOpsHelper {

    interface ProfileUiBridge {
        void updateSyncProgress(boolean syncing);

        void updateSyncBadge(int count);

        void reloadStats();

        void runIfUiAlive(@NonNull Runnable action);

        void onCountReady(long count);

        void onCountError();
    }

    private static final String PREFS_PROFILE = "profile_tab";
    private static final String PREF_KEY_RECORD_COUNT = "record_count";

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
        List<?> notes = app.getFlashNoteRepository().getNotes().getValue();
        return notes == null ? 0 : notes.size();
    }

    int resolveFavoriteCount(@NonNull FlashNoteApp app) {
        List<?> favorites = app.getFavoriteRepository().getFavorites().getValue();
        return favorites == null ? 0 : favorites.size();
    }

    void loadRecordCount(@NonNull FlashNoteApp app, @NonNull ProfileUiBridge callback) {
        app.getMessageRepository().countMessages(new MessageRepository.CountCallback() {
            @Override
            public void onSuccess(long count) {
                callback.onCountReady(count);
            }

            @Override
            public void onError(String message, int code) {
                callback.onCountError();
            }
        });
    }

    void triggerSync(@NonNull SyncRepository syncRepository,
                     boolean syncInProgress,
                     @NonNull Runnable markSyncStarted,
                     @NonNull Runnable markSyncFinished,
                     @NonNull ProfileUiBridge uiBridge) {
        if (syncInProgress) {
            return;
        }
        markSyncStarted.run();
        uiBridge.updateSyncProgress(true);
        syncRepository.syncAll(new SyncRepository.SyncCallback() {
            @Override
            public void onSuccess(java.util.Map<String, Object> data) {
                uiBridge.runIfUiAlive(() -> {
                    markSyncFinished.run();
                    uiBridge.updateSyncProgress(false);
                    syncRepository.getPendingSyncCount(uiBridge::updateSyncBadge);
                    uiBridge.reloadStats();
                });
            }

            @Override
            public void onError(String message, int code) {
                uiBridge.runIfUiAlive(() -> {
                    markSyncFinished.run();
                    uiBridge.updateSyncProgress(false);
                });
            }
        });
    }

    void openChangePassword(@NonNull ShellNavigator navigator) {
        navigator.openChangePassword();
    }

    void openSettings(@NonNull ShellNavigator navigator) {
        navigator.openSettings();
    }

    void logout(@NonNull AuthViewModel authViewModel, @NonNull ShellNavigator navigator) {
        authViewModel.logout();
        navigator.logoutToLogin();
    }
}
