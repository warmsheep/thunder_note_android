package com.flashnote.java.data.remote;

import com.flashnote.java.data.model.ApiResponse;
import com.flashnote.java.data.model.Message;
import com.flashnote.java.data.model.MessageListRequest;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface MessageService {
    @POST("api/messages/list")
    Call<ApiResponse<List<Message>>> list(@Body MessageListRequest request);

    @POST("api/messages")
    Call<ApiResponse<Message>> send(@Body Message message);
}
