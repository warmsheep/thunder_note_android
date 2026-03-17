package com.flashnote.java.data.remote;

import com.flashnote.java.data.model.ApiResponse;
import com.flashnote.java.data.model.Message;
import com.flashnote.java.data.model.MessageListRequest;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface MessageService {
    @POST("api/messages/list")
    Call<ApiResponse<List<Message>>> list(@Body MessageListRequest request);

    @POST("api/messages")
    Call<ApiResponse<Message>> send(@Body Message message);

    @DELETE("api/messages/{id}")
    Call<ApiResponse<Void>> delete(@Path("id") Long id);

    @GET("api/messages/count")
    Call<ApiResponse<Integer>> countMessages();
}
