package com.flashnote.java.data.remote;

import com.flashnote.java.data.model.ApiResponse;
import com.flashnote.java.data.model.FavoriteItem;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.DELETE;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface FavoriteService {
    @POST("api/favorites/list")
    Call<ApiResponse<List<FavoriteItem>>> list();

    @POST("api/favorites/{messageId}")
    Call<ApiResponse<FavoriteItem>> add(@Path("messageId") Long messageId);

    @DELETE("api/favorites/{messageId}")
    Call<ApiResponse<Void>> remove(@Path("messageId") Long messageId);
}
