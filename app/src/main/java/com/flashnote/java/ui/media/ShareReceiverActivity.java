package com.flashnote.java.ui.media;

import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.flashnote.java.FlashNoteApp;
import com.flashnote.java.R;
import com.flashnote.java.data.repository.MessageRepository;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class ShareReceiverActivity extends AppCompatActivity {

    private static final long COLLECTION_BOX_NOTE_ID = -1L;

    private MessageRepository messageRepository;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private int pendingCount;
    private int successCount;
    private boolean hasFailure;

    private interface TempFileCallback {
        void onFileReady(@Nullable File file);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FlashNoteApp app = FlashNoteApp.getInstance();
        if (app == null || app.getTokenManager() == null || !app.getTokenManager().isTokenValid()) {
            Toast.makeText(this, R.string.share_receive_not_logged_in, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        messageRepository = app.getMessageRepository();
        if (messageRepository == null) {
            Toast.makeText(this, R.string.share_receive_failed, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        handleShareIntent(getIntent());
    }

    private void handleShareIntent(@Nullable Intent intent) {
        if (intent == null) {
            showNoContentAndFinish();
            return;
        }
        String action = intent.getAction();
        String type = intent.getType();

        if (Intent.ACTION_SEND.equals(action)) {
            if ("text/plain".equals(type)) {
                handleSendText(intent);
                return;
            }
            Uri streamUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (streamUri != null) {
                ArrayList<Uri> single = new ArrayList<>();
                single.add(streamUri);
                handleSendMultipleStreams(single, type);
                return;
            }
            showNoContentAndFinish();
            return;
        }

        if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            ArrayList<Uri> uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            handleSendMultipleStreams(uris, type);
            return;
        }

        showNoContentAndFinish();
    }

    private void handleSendText(@NonNull Intent intent) {
        CharSequence text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        if (text == null || text.toString().trim().isEmpty()) {
            showNoContentAndFinish();
            return;
        }
        messageRepository.sendText(COLLECTION_BOX_NOTE_ID, text.toString().trim(), () -> {
            Toast.makeText(ShareReceiverActivity.this, R.string.share_receive_success, Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private void handleSendMultipleStreams(@Nullable ArrayList<Uri> uris, @Nullable String fallbackMimeType) {
        if (uris == null || uris.isEmpty()) {
            showNoContentAndFinish();
            return;
        }
        pendingCount = uris.size();
        successCount = 0;
        hasFailure = false;

        for (Uri uri : uris) {
            if (uri == null) {
                hasFailure = true;
                onOneStreamDone();
                continue;
            }

            String resolvedType = resolveMimeType(uri, fallbackMimeType);
            String mediaType = resolveMediaType(resolvedType);
            String prefix = "FILE".equals(mediaType) ? "file" : ("VIDEO".equals(mediaType) ? "video" : "image");

            copyUriToTempFile(uri, prefix, file -> {
                if (file == null) {
                    hasFailure = true;
                    onOneStreamDone();
                    return;
                }
                String originalName = getOriginalFileName(uri);
                String fileName = (originalName == null || originalName.trim().isEmpty()) ? file.getName() : originalName;
                Integer duration = "VIDEO".equals(mediaType) ? resolveVideoDurationSeconds(file.getAbsolutePath()) : null;
                messageRepository.enqueueMedia(
                        COLLECTION_BOX_NOTE_ID,
                        0L,
                        mediaType,
                        file,
                        fileName,
                        file.length(),
                        duration,
                        () -> {
                            successCount++;
                            onOneStreamDone();
                        }
                );
            });
        }
    }

    private void onOneStreamDone() {
        pendingCount--;
        if (pendingCount > 0) {
            return;
        }
        if (successCount > 0) {
            Toast.makeText(this, R.string.share_receive_success, Toast.LENGTH_SHORT).show();
        } else if (hasFailure) {
            Toast.makeText(this, R.string.share_receive_failed, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, R.string.share_receive_no_content, Toast.LENGTH_SHORT).show();
        }
        finish();
    }

    private void showNoContentAndFinish() {
        Toast.makeText(this, R.string.share_receive_no_content, Toast.LENGTH_SHORT).show();
        finish();
    }

    private void copyUriToTempFile(@NonNull Uri uri, @NonNull String prefix, @NonNull TempFileCallback callback) {
        new Thread(() -> {
            File tempFile = null;
            try {
                String extension = getFileExtension(uri);
                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                tempFile = new File(getCacheDir(), prefix + "_" + timestamp + "." + extension);

                ContentResolver resolver = getContentResolver();
                try (InputStream inputStream = resolver.openInputStream(uri);
                     FileOutputStream outputStream = new FileOutputStream(tempFile)) {
                    if (inputStream == null) {
                        throw new IOException("Input stream is null");
                    }
                    byte[] buffer = new byte[65536];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                }

                File finalFile = tempFile;
                mainHandler.post(() -> callback.onFileReady(finalFile));
            } catch (Exception exception) {
                if (tempFile != null && tempFile.exists()) {
                    tempFile.delete();
                }
                mainHandler.post(() -> callback.onFileReady(null));
            }
        }).start();
    }

    @Nullable
    private String resolveMimeType(@NonNull Uri uri, @Nullable String fallbackMimeType) {
        String mimeType = getContentResolver().getType(uri);
        if (mimeType != null && !mimeType.trim().isEmpty()) {
            return mimeType;
        }
        return fallbackMimeType;
    }

    @NonNull
    private String resolveMediaType(@Nullable String mimeType) {
        if (mimeType != null) {
            if (mimeType.startsWith("image/")) {
                return "IMAGE";
            }
            if (mimeType.startsWith("video/")) {
                return "VIDEO";
            }
        }
        return "FILE";
    }

    @Nullable
    private String getOriginalFileName(@NonNull Uri uri) {
        String displayName = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        displayName = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        if (displayName == null || displayName.trim().isEmpty()) {
            displayName = uri.getLastPathSegment();
        }
        return displayName;
    }

    @NonNull
    private String getFileExtension(@NonNull Uri uri) {
        String mimeType = getContentResolver().getType(uri);
        String extension = null;
        if (mimeType != null) {
            extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
        }
        if (extension == null || extension.trim().isEmpty()) {
            extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
        }
        if (extension == null || extension.trim().isEmpty()) {
            return "tmp";
        }
        return extension;
    }

    @Nullable
    private Integer resolveVideoDurationSeconds(@NonNull String filePath) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(filePath);
            String duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (duration != null) {
                return Integer.parseInt(duration) / 1000;
            }
        } catch (Exception ignored) {
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}
