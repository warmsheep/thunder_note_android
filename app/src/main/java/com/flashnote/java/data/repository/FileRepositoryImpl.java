package com.flashnote.java.data.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.flashnote.java.DebugLog;
import com.flashnote.java.data.model.ApiResponse;
import com.flashnote.java.data.model.FileUploadResult;
import com.flashnote.java.data.remote.FileService;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.webkit.MimeTypeMap;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class FileRepositoryImpl implements FileRepository {
    private final FileService fileService;
    private final Context context;
    private final Handler mainHandler;
    private final ExecutorService downloadExecutor;

    public FileRepositoryImpl(FileService fileService, Context context) {
        this.fileService = fileService;
        this.context = context.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.downloadExecutor = Executors.newSingleThreadExecutor();
    }

    private void dispatchSuccess(FileCallback callback, String path) {
        mainHandler.post(() -> callback.onSuccess(path));
    }

    private void dispatchError(FileCallback callback, String message, int code) {
        mainHandler.post(() -> callback.onError(message, code));
    }

    @Override
    public void upload(File file, FileCallback callback) {
        RequestBody requestBody = RequestBody.create(file, MediaType.parse(resolveMimeType(file)));
        MultipartBody.Part filePart = MultipartBody.Part.createFormData("file", file.getName(), requestBody);
        fileService.upload(filePart).enqueue(new Callback<ApiResponse<FileUploadResult>>() {
            @Override
            public void onResponse(Call<ApiResponse<FileUploadResult>> call, Response<ApiResponse<FileUploadResult>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    dispatchSuccess(callback, response.body().getData().getObjectName());
                    return;
                }
                int code = response.body() == null ? response.code() : response.body().getCode();
                String message = response.body() == null ? "Upload failed" : response.body().getMessage();
                DebugLog.w("FileRepo", message);
                dispatchError(callback, message, code);
            }

            @Override
            public void onFailure(Call<ApiResponse<FileUploadResult>> call, Throwable t) {
                String errMsg = "Network error: " + t.getMessage();
                DebugLog.w("FileRepo", errMsg);
                dispatchError(callback, errMsg, -1);
            }
        });
    }

    private String resolveMimeType(File file) {
        if (file == null || file.getName() == null) {
            return "application/octet-stream";
        }
        String extension = MimeTypeMap.getFileExtensionFromUrl(file.getName());
        if (extension != null && !extension.isBlank()) {
            String normalized = extension.toLowerCase(Locale.ROOT);
            switch (normalized) {
                case "jpg":
                case "jpeg":
                    return "image/jpeg";
                case "png":
                    return "image/png";
                case "gif":
                    return "image/gif";
                case "webp":
                    return "image/webp";
                case "mp4":
                    return "video/mp4";
                case "mp3":
                    return "audio/mpeg";
                case "m4a":
                    return "audio/mp4";
                case "pdf":
                    return "application/pdf";
                default:
                    break;
            }
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(normalized);
            if (mime != null && !mime.isBlank()) {
                return mime;
            }
        }
        return "application/octet-stream";
    }

    @Override
    public void download(String objectName, FileCallback callback) {
        fileService.download(objectName).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    String errMsg = "Download failed: " + response.code();
                    DebugLog.w("FileRepo", errMsg);
                    dispatchError(callback, errMsg, response.code());
                    return;
                }

                ResponseBody responseBody = response.body();
                downloadExecutor.execute(() -> saveDownloadedFile(objectName, responseBody, callback));
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                String errMsg = "Network error: " + t.getMessage();
                DebugLog.w("FileRepo", errMsg);
                dispatchError(callback, errMsg, -1);
            }
        });
    }

    private void saveDownloadedFile(String objectName, ResponseBody body, FileCallback callback) {
        File target = new File(context.getCacheDir(), objectName.replace('/', '_'));
        File tmpFile = new File(context.getCacheDir(), objectName.replace('/', '_') + ".tmp");
        DebugLog.i("FileRepo", "Download started: object=" + objectName
                + " target=" + target.getAbsolutePath());
        long bytesWritten = 0L;
        try (ResponseBody responseBody = body;
             InputStream inputStream = responseBody.byteStream();
             FileOutputStream outputStream = new FileOutputStream(tmpFile)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                bytesWritten += bytesRead;
            }

            long contentLength = responseBody.contentLength();
            if (contentLength > 0 && bytesWritten != contentLength) {
                throw new IOException("Downloaded size mismatch: expected=" + contentLength + " actual=" + bytesWritten);
            }
        } catch (IOException exception) {
            if (tmpFile.exists() && !tmpFile.delete()) {
                DebugLog.w("FileRepo", "Failed to delete temp file: " + tmpFile.getAbsolutePath());
            }
            String errMsg = "Save failed: " + exception.getMessage();
            DebugLog.w("FileRepo", errMsg);
            DebugLog.i("FileRepo", "Download failed: object=" + objectName + " bytesWritten=" + bytesWritten);
            dispatchError(callback, errMsg, -1);
            return;
        }

        if (target.exists() && !target.delete()) {
            if (tmpFile.exists() && !tmpFile.delete()) {
                DebugLog.w("FileRepo", "Failed to delete temp file after target delete failure: " + tmpFile.getAbsolutePath());
            }
            String errMsg = "Save failed: unable to replace existing file";
            DebugLog.w("FileRepo", errMsg + " target=" + target.getAbsolutePath());
            DebugLog.i("FileRepo", "Download failed: object=" + objectName + " bytesWritten=" + bytesWritten);
            dispatchError(callback, errMsg, -1);
            return;
        }

        if (!tmpFile.renameTo(target)) {
            if (tmpFile.exists() && !tmpFile.delete()) {
                DebugLog.w("FileRepo", "Failed to delete temp file after rename failure: " + tmpFile.getAbsolutePath());
            }
            String errMsg = "Save failed: unable to finalize downloaded file";
            DebugLog.w("FileRepo", errMsg + " target=" + target.getAbsolutePath());
            DebugLog.i("FileRepo", "Download failed: object=" + objectName + " bytesWritten=" + bytesWritten);
            dispatchError(callback, errMsg, -1);
            return;
        }

        DebugLog.i("FileRepo", "Download completed: object=" + objectName
                + " bytes=" + bytesWritten + " path=" + target.getAbsolutePath());
        dispatchSuccess(callback, target.getAbsolutePath());
    }
}
