package com.flashnote.java.data.repository;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;

import androidx.annotation.Nullable;

import com.flashnote.java.DebugLog;

import java.io.File;
import java.io.FileOutputStream;
import java.util.UUID;

public final class ThumbnailUtils {
    private static final int THUMBNAIL_SIZE = 240;
    private static final int JPEG_QUALITY = 82;

    private ThumbnailUtils() {
    }

    @Nullable
    public static File createThumbnailFile(@Nullable File sourceFile, @Nullable String mediaType) {
        if (sourceFile == null || mediaType == null || !sourceFile.exists()) {
            return null;
        }
        try {
            if ("IMAGE".equalsIgnoreCase(mediaType)) {
                return generateImageThumbnail(sourceFile);
            }
            if ("VIDEO".equalsIgnoreCase(mediaType)) {
                return generateVideoThumbnail(sourceFile);
            }
        } catch (Exception exception) {
            DebugLog.w("ThumbnailUtils", "Failed to create thumbnail for mediaType=" + mediaType);
        }
        return null;
    }

    @Nullable
    private static File generateImageThumbnail(File sourceFile) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(sourceFile.getAbsolutePath(), bounds);

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, THUMBNAIL_SIZE, THUMBNAIL_SIZE);
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap bitmap = BitmapFactory.decodeFile(sourceFile.getAbsolutePath(), options);
        if (bitmap == null) {
            return null;
        }
        return saveScaledBitmap(bitmap, sourceFile.getParentFile());
    }

    @Nullable
    private static File generateVideoThumbnail(File sourceFile) throws Exception {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        Bitmap bitmap = null;
        try {
            retriever.setDataSource(sourceFile.getAbsolutePath());
            bitmap = retriever.getFrameAtTime(-1, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if (bitmap == null) {
                return null;
            }
            return saveScaledBitmap(bitmap, sourceFile.getParentFile());
        } finally {
            retriever.release();
        }
    }

    private static File saveScaledBitmap(Bitmap source, File parentDir) throws Exception {
        int width = source.getWidth();
        int height = source.getHeight();
        int scaledWidth = width;
        int scaledHeight = height;
        int max = Math.max(width, height);
        if (max > THUMBNAIL_SIZE) {
            float scale = (float) THUMBNAIL_SIZE / (float) max;
            scaledWidth = Math.max(1, Math.round(width * scale));
            scaledHeight = Math.max(1, Math.round(height * scale));
        }
        Bitmap scaled = source == null ? null : Bitmap.createScaledBitmap(source, scaledWidth, scaledHeight, true);
        if (scaled == null) {
            return null;
        }
        File target = new File(parentDir == null ? new File(System.getProperty("java.io.tmpdir")) : parentDir,
                "thumb_" + UUID.randomUUID() + ".jpg");
        try (FileOutputStream fos = new FileOutputStream(target)) {
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, fos);
            fos.flush();
        } finally {
            if (scaled != source) {
                scaled.recycle();
            }
            source.recycle();
        }
        return target;
    }

    private static int calculateInSampleSize(int width, int height, int reqWidth, int reqHeight) {
        int inSampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            int halfHeight = height / 2;
            int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return Math.max(1, inSampleSize);
    }
}
