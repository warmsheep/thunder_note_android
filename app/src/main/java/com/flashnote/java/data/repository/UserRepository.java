package com.flashnote.java.data.repository;

import androidx.lifecycle.LiveData;

import com.flashnote.java.data.model.UserProfile;

public interface UserRepository {
    LiveData<UserProfile> getProfile();
    void fetchProfile(ProfileCallback callback);
    void updateProfile(UserProfile profile, ProfileCallback callback);
    void updateAvatar(String avatar, ProfileCallback callback);
    void refresh();

    interface ProfileCallback {
        void onSuccess(UserProfile profile);
        void onError(String message, int code);
    }
}
