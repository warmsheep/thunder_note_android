package com.flashnote.java.data.remote;

import com.flashnote.java.data.model.ApiResponse;
import com.flashnote.java.data.model.FlashNote;
import com.flashnote.java.data.model.FlashNoteSearchRequest;
import com.flashnote.java.data.model.FlashNoteSearchResponse;
import com.flashnote.java.data.model.FlashNoteSearchResult;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.Query;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;

public interface FlashNoteService {
    @POST("api/flash-notes/list")
    Call<ApiResponse<List<FlashNote>>> list();

    @POST("api/flash-notes/search")
    Call<ApiResponse<FlashNoteSearchResponse>> search(@Body FlashNoteSearchRequest request);

    @POST("api/flash-notes")
    Call<ApiResponse<FlashNote>> create(@Body FlashNote note);

    @PUT("api/flash-notes/{id}")
    Call<ApiResponse<FlashNote>> update(@Path("id") Long id, @Body FlashNote note);

    @PUT("api/flash-notes/{id}/pin")
    Call<ApiResponse<FlashNote>> pin(@Path("id") Long id, @Query("value") boolean value);

    @PUT("api/flash-notes/{id}/hide")
    Call<ApiResponse<FlashNote>> hide(@Path("id") Long id, @Query("value") boolean value);

    @DELETE("api/flash-notes/{id}")
    Call<ApiResponse<Void>> delete(@Path("id") Long id);
}
