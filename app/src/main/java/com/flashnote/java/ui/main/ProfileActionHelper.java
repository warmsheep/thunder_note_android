package com.flashnote.java.ui.main;

import androidx.annotation.NonNull;

import com.flashnote.java.data.repository.SyncRepository;
import com.flashnote.java.ui.auth.AuthViewModel;
import com.flashnote.java.ui.navigation.ShellNavigator;

final class ProfileActionHelper {

    interface UiBridge {
        void updateSyncProgress(boolean syncing);
        void updateSyncBadge(int count);
        void reloadStats();
        void runIfUiAlive(@NonNull Runnable action);
    }

    void triggerSync(@NonNull SyncRepository syncRepository,
                     boolean syncInProgress,
                     @NonNull Runnable markSyncStarted,
                     @NonNull Runnable markSyncFinished,
                     @NonNull UiBridge uiBridge) {
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
