package com.flashnote.java.data.remote;

import com.flashnote.java.data.model.ApiResponse;
import com.flashnote.java.data.model.FlashNote;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;

public interface FlashNoteService {
    @POST("api/flash-notes/list")
    Call<ApiResponse<List<FlashNote>>> list();

    @POST("api/flash-notes")
    Call<ApiResponse<FlashNote>> create(@Body FlashNote note);

    @PUT("api/flash-notes/{id}")
    Call<ApiResponse<FlashNote>> update(@Path("id") Long id, @Body FlashNote note);

    @DELETE("api/flash-notes/{id}")
    Call<ApiResponse<Void>> delete(@Path("id") Long id);
}
