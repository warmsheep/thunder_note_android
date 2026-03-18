package com.flashnote.java.data.remote;

import com.flashnote.java.data.model.ApiResponse;
import com.flashnote.java.data.model.ContactUser;
import com.flashnote.java.data.model.UserProfile;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;

public interface UserService {
    @POST("api/users/profile")
    Call<ApiResponse<UserProfile>> getProfile();

    @PUT("api/users/profile")
    Call<ApiResponse<UserProfile>> updateProfile(@Body UserProfile profile);

    @PUT("api/users/avatar")
    Call<ApiResponse<String>> updateAvatar(@Body Map<String, String> body);

    @GET("api/users/contacts")
    Call<ApiResponse<List<ContactUser>>> listContacts();
}
