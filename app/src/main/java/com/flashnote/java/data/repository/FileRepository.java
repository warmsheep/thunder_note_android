package com.flashnote.java.data.repository;

import java.io.File;

public interface FileRepository {
    interface FileCallback {
        void onSuccess(String value);

        void onError(String message, int code);
    }

    void upload(File file, FileCallback callback);

    void download(String objectName, FileCallback callback);
}
