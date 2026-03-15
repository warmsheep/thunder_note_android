package com.flashnote.java.data.remote;

import com.flashnote.java.data.model.ApiResponse;
import com.flashnote.java.data.model.Collection;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;

public interface CollectionService {
    @POST("api/collections/list")
    Call<ApiResponse<List<Collection>>> list();

    @POST("api/collections")
    Call<ApiResponse<Collection>> create(@Body Collection collection);

    @PUT("api/collections/{id}")
    Call<ApiResponse<Collection>> update(@Path("id") Long id, @Body Collection collection);

    @DELETE("api/collections/{id}")
    Call<ApiResponse<Void>> delete(@Path("id") Long id);
}
