package com.flashnote.java.data.remote;

import okhttp3.MultipartBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Query;
import retrofit2.http.Streaming;

public interface FileService {
    @Multipart
    @POST("api/files/upload")
    Call<com.flashnote.java.data.model.ApiResponse<String>> upload(@Part MultipartBody.Part filePart);

    @Streaming
    @GET("api/files/download")
    Call<ResponseBody> download(@Query("objectName") String objectName);
}
