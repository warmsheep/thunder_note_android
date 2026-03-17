package com.flashnote.java.data.repository;

import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.flashnote.java.data.model.ApiResponse;
import com.flashnote.java.data.model.UserProfile;
import com.flashnote.java.data.remote.UserService;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class UserRepositoryImpl implements UserRepository {
    private final UserService userService;
    private final MutableLiveData<UserProfile> profileLiveData = new MutableLiveData<>();

    public UserRepositoryImpl(UserService userService) {
        this.userService = userService;
    }

    @Override
    public LiveData<UserProfile> getProfile() {
        return profileLiveData;
    }

    @Override
    public void fetchProfile(ProfileCallback callback) {
        userService.getProfile().enqueue(new Callback<ApiResponse<UserProfile>>() {
            @Override
            public void onResponse(Call<ApiResponse<UserProfile>> call, Response<ApiResponse<UserProfile>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<UserProfile> apiResponse = response.body();
                    if (apiResponse.getCode() == 0 && apiResponse.getData() != null) {
                        UserProfile profile = apiResponse.getData();
                        profileLiveData.postValue(profile);
                        notifySuccess(callback, profile);
                    } else {
                        notifyError(callback, apiResponse.getMessage(), apiResponse.getCode());
                    }
                } else {
                    notifyError(callback, "HTTP " + response.code(), response.code());
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<UserProfile>> call, Throwable t) {
                notifyError(callback, t.getMessage(), -1);
            }
        });
    }

    @Override
    public void updateProfile(UserProfile profile, ProfileCallback callback) {
        userService.updateProfile(profile).enqueue(new Callback<ApiResponse<UserProfile>>() {
            @Override
            public void onResponse(Call<ApiResponse<UserProfile>> call, Response<ApiResponse<UserProfile>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<UserProfile> apiResponse = response.body();
                    if (apiResponse.getCode() == 0 && apiResponse.getData() != null) {
                        UserProfile updatedProfile = apiResponse.getData();
                        profileLiveData.postValue(updatedProfile);
                        notifySuccess(callback, updatedProfile);
                    } else {
                        notifyError(callback, apiResponse.getMessage(), apiResponse.getCode());
                    }
                } else {
                    notifyError(callback, "HTTP " + response.code(), response.code());
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<UserProfile>> call, Throwable t) {
                notifyError(callback, t.getMessage(), -1);
            }
        });
    }

    @Override
    public void refresh() {
        fetchProfile(null);
    }

    @Override
    public void updateAvatar(String avatar, ProfileCallback callback) {
        Map<String, String> body = new HashMap<>();
        body.put("avatar", avatar);
        
        userService.updateAvatar(body).enqueue(new Callback<ApiResponse<String>>() {
            @Override
            public void onResponse(Call<ApiResponse<String>> call, Response<ApiResponse<String>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<String> apiResponse = response.body();
                    if (apiResponse.getCode() == 0) {
                        UserProfile currentProfile = profileLiveData.getValue();
                        if (currentProfile != null) {
                            currentProfile.setAvatar(avatar);
                            profileLiveData.postValue(currentProfile);
                        }
                        if (callback != null) {
                            UserProfile profile = currentProfile != null ? currentProfile : new UserProfile();
                            profile.setAvatar(avatar);
                            notifySuccess(callback, profile);
                        }
                    } else {
                        notifyError(callback, apiResponse.getMessage(), apiResponse.getCode());
                    }
                } else {
                    notifyError(callback, "HTTP " + response.code(), response.code());
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<String>> call, Throwable t) {
                notifyError(callback, t.getMessage(), -1);
            }
        });
    }

    private void notifySuccess(ProfileCallback callback, UserProfile profile) {
        if (callback != null) {
            new Handler(Looper.getMainLooper()).post(() -> callback.onSuccess(profile));
        }
    }

    private void notifyError(ProfileCallback callback, String message, int code) {
        if (callback != null) {
            new Handler(Looper.getMainLooper()).post(() -> callback.onError(message, code));
        }
    }
}
