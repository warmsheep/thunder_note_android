package com.flashnote.java.ui.main;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;

final class RefreshableRepositoryDelegate {
    private final LiveData<Boolean> isLoading;
    private final LiveData<String> errorMessage;
    private final Runnable clearErrorAction;
    private final Runnable refreshAction;

    RefreshableRepositoryDelegate(@NonNull LiveData<Boolean> isLoading,
                                  @NonNull LiveData<String> errorMessage,
                                  @NonNull Runnable clearErrorAction,
                                  @NonNull Runnable refreshAction) {
        this.isLoading = isLoading;
        this.errorMessage = errorMessage;
        this.clearErrorAction = clearErrorAction;
        this.refreshAction = refreshAction;
        this.clearErrorAction.run();
        this.refreshAction.run();
    }

    public LiveData<Boolean> isLoading() {
        return isLoading;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public void clearError() {
        clearErrorAction.run();
    }

    public void refresh() {
        clearErrorAction.run();
        refreshAction.run();
    }
}
