package com.flashnote.java.data.repository;

import android.content.Context;

import com.flashnote.java.util.VideoCompressor;

import java.io.File;

public class VideoPreparationServiceImpl implements VideoPreparationService {
    private final Context appContext;

    public VideoPreparationServiceImpl(Context context) {
        this.appContext = context.getApplicationContext();
    }

    @Override
    public void prepareVideo(File inputFile, Callback callback) {
        VideoCompressor.compress(appContext, inputFile, new VideoCompressor.CompressCallback() {
            @Override
            public void onSuccess(File compressedFile) {
                callback.onSuccess(compressedFile);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }
}
