package com.flashnote.java.data.repository;

import java.io.File;

public interface VideoPreparationService {
    interface Callback {
        void onSuccess(File processedFile);

        void onError(String message);
    }

    void prepareVideo(File inputFile, Callback callback);
}
