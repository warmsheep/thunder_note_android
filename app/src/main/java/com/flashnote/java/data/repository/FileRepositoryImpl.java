package com.flashnote.java.data.repository;

import android.content.Context;

import com.flashnote.java.DebugLog;
import com.flashnote.java.data.model.ApiResponse;
import com.flashnote.java.data.remote.FileService;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

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

    public FileRepositoryImpl(FileService fileService, Context context) {
        this.fileService = fileService;
        this.context = context.getApplicationContext();
    }

    @Override
    public void upload(File file, FileCallback callback) {
        RequestBody requestBody = RequestBody.create(file, MediaType.parse("application/octet-stream"));
        MultipartBody.Part filePart = MultipartBody.Part.createFormData("file", file.getName(), requestBody);
        fileService.upload(filePart).enqueue(new Callback<ApiResponse<String>>() {
            @Override
            public void onResponse(Call<ApiResponse<String>> call, Response<ApiResponse<String>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    callback.onSuccess(response.body().getData());
                    return;
                }
                int code = response.body() == null ? response.code() : response.body().getCode();
                String message = response.body() == null ? "Upload failed" : response.body().getMessage();
                DebugLog.w("FileRepo", message);
                callback.onError(message, code);
            }

            @Override
            public void onFailure(Call<ApiResponse<String>> call, Throwable t) {
                String errMsg = "Network error: " + t.getMessage();
                DebugLog.w("FileRepo", errMsg);
                callback.onError(errMsg, -1);
            }
        });
    }

    @Override
    public void download(String objectName, FileCallback callback) {
        fileService.download(objectName).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    String errMsg = "Download failed: " + response.code();
                    DebugLog.w("FileRepo", errMsg);
                    callback.onError(errMsg, response.code());
                    return;
                }

                File target = new File(context.getCacheDir(), objectName.replace('/', '_'));
                try (ResponseBody body = response.body();
                     InputStream inputStream = body.byteStream();
                     FileOutputStream outputStream = new FileOutputStream(target)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                    callback.onSuccess(target.getAbsolutePath());
                } catch (IOException exception) {
                    String errMsg = "Save failed: " + exception.getMessage();
                    DebugLog.w("FileRepo", errMsg);
                    callback.onError(errMsg, -1);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                String errMsg = "Network error: " + t.getMessage();
                DebugLog.w("FileRepo", errMsg);
                callback.onError(errMsg, -1);
            }
        });
    }
}
