package com.flashnote.java.data.remote;

import com.flashnote.java.data.model.ApiResponse;
import com.flashnote.java.data.model.ContactSearchUser;
import com.flashnote.java.data.model.ContactUser;
import com.flashnote.java.data.model.FriendRequestActionRequest;
import com.flashnote.java.data.model.FriendRequestCreateRequest;
import com.flashnote.java.data.model.FriendRequest;
import com.flashnote.java.data.model.UserProfile;

import java.util.List;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.DELETE;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface UserService {
    @POST("api/users/profile")
    Call<ApiResponse<UserProfile>> getProfile();

    @PUT("api/users/profile")
    Call<ApiResponse<UserProfile>> updateProfile(@Body UserProfile profile);

    @PUT("api/users/avatar")
    Call<ApiResponse<String>> updateAvatar(@Body com.flashnote.java.data.model.AvatarUpdateRequest body);

    @GET("api/users/contacts")
    Call<ApiResponse<List<ContactUser>>> listContacts();

    @GET("api/users/contacts/requests")
    Call<ApiResponse<List<FriendRequest>>> listFriendRequests();

    @GET("api/users/contacts/requests/count")
    Call<ApiResponse<Long>> countFriendRequests();

    @POST("api/users/contacts/request")
    Call<ApiResponse<Void>> sendFriendRequest(@Body FriendRequestCreateRequest body);

    @POST("api/users/contacts/request/accept")
    Call<ApiResponse<Void>> acceptFriendRequest(@Body FriendRequestActionRequest body);

    @POST("api/users/contacts/request/reject")
    Call<ApiResponse<Void>> rejectFriendRequest(@Body FriendRequestActionRequest body);

    @DELETE("api/users/contacts/{contactUserId}")
    Call<ApiResponse<Void>> deleteContact(@Path("contactUserId") Long contactUserId);

    @GET("api/users/contacts/search")
    Call<ApiResponse<List<ContactSearchUser>>> searchContacts(@Query("keyword") String keyword);
}
