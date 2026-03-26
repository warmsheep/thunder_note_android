package com.flashnote.java.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

final class RepositoryStateStore {
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    LiveData<Boolean> isLoading() {
        return isLoading;
    }

    LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    void setLoading(boolean loading) {
        isLoading.setValue(loading);
    }

    void clearError() {
        errorMessage.setValue(null);
    }

    void setError(String message) {
        errorMessage.setValue(message);
    }
}
