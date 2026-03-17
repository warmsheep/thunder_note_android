package com.flashnote.java.util;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.nio.ByteBuffer;

public class VideoCompressor {
    private static final String TAG = "VideoCompressor";
    private static final int MIN_COMPRESS_SIZE_BYTES = 5 * 1024 * 1024;

    public interface CompressCallback {
        void onSuccess(File compressedFile);

        void onError(String message);
    }

    public static void compress(Context context, File inputFile, CompressCallback callback) {
        if (inputFile == null || !inputFile.exists()) {
            new Handler(Looper.getMainLooper()).post(() -> callback.onError("输入文件无效"));
            return;
        }

        if (inputFile.length() < MIN_COMPRESS_SIZE_BYTES) {
            new Handler(Looper.getMainLooper()).post(() -> callback.onSuccess(inputFile));
            return;
        }

        new Thread(() -> {
            try {
                File outputFile = new File(context.getCacheDir(), "compressed_" + System.currentTimeMillis() + ".mp4");
                boolean success = remuxVideo(inputFile.getAbsolutePath(), outputFile.getAbsolutePath());

                Handler handler = new Handler(Looper.getMainLooper());
                if (success && outputFile.exists() && outputFile.length() > 0
                        && outputFile.length() < inputFile.length()) {
                    handler.post(() -> callback.onSuccess(outputFile));
                } else {
                    if (outputFile.exists()) {
                        outputFile.delete();
                    }
                    handler.post(() -> callback.onSuccess(inputFile));
                }
            } catch (Exception e) {
                Log.e(TAG, "Compression failed", e);
                new Handler(Looper.getMainLooper()).post(() -> callback.onSuccess(inputFile));
            }
        }).start();
    }

    private static boolean remuxVideo(String inputPath, String outputPath) {
        MediaExtractor extractor = null;
        MediaMuxer muxer = null;
        try {
            extractor = new MediaExtractor();
            extractor.setDataSource(inputPath);

            int videoTrackIndex = -1;
            int audioTrackIndex = -1;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime == null) {
                    continue;
                }
                if (mime.startsWith("video/")) {
                    videoTrackIndex = i;
                } else if (mime.startsWith("audio/")) {
                    audioTrackIndex = i;
                }
            }

            if (videoTrackIndex < 0) {
                return false;
            }

            muxer = new MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int muxerVideoTrack = muxer.addTrack(extractor.getTrackFormat(videoTrackIndex));
            int muxerAudioTrack = -1;
            if (audioTrackIndex >= 0) {
                muxerAudioTrack = muxer.addTrack(extractor.getTrackFormat(audioTrackIndex));
            }
            muxer.start();

            copyTrackSamples(extractor, muxer, videoTrackIndex, muxerVideoTrack);
            if (audioTrackIndex >= 0 && muxerAudioTrack >= 0) {
                copyTrackSamples(extractor, muxer, audioTrackIndex, muxerAudioTrack);
            }

            muxer.stop();
            return true;
        } catch (Exception exception) {
            Log.e(TAG, "remuxVideo failed", exception);
            return false;
        } finally {
            try {
                if (extractor != null) {
                    extractor.release();
                }
            } catch (Exception ignored) {
            }
            try {
                if (muxer != null) {
                    muxer.release();
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static void copyTrackSamples(MediaExtractor extractor,
                                         MediaMuxer muxer,
                                         int extractorTrack,
                                         int muxerTrack) {
        extractor.selectTrack(extractorTrack);
        extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);

        ByteBuffer buffer = ByteBuffer.allocate(1024 * 1024);
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

        while (true) {
            int sampleSize = extractor.readSampleData(buffer, 0);
            if (sampleSize < 0) {
                break;
            }
            bufferInfo.offset = 0;
            bufferInfo.size = sampleSize;
            bufferInfo.presentationTimeUs = extractor.getSampleTime();
            bufferInfo.flags = extractor.getSampleFlags();
            muxer.writeSampleData(muxerTrack, buffer, bufferInfo);
            extractor.advance();
        }

        extractor.unselectTrack(extractorTrack);
    }
}
