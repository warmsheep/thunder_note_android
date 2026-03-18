package com.flashnote.java.data.repository;

import androidx.lifecycle.LiveData;

import com.flashnote.java.data.model.ContactUser;
import com.flashnote.java.data.model.UserProfile;

import java.util.List;

public interface UserRepository {
    LiveData<UserProfile> getProfile();
    void fetchProfile(ProfileCallback callback);
    void updateProfile(UserProfile profile, ProfileCallback callback);
    void updateAvatar(String avatar, ProfileCallback callback);
    void refresh();
    LiveData<List<ContactUser>> getContacts();
    void fetchContacts(ContactsCallback callback);

    interface ProfileCallback {
        void onSuccess(UserProfile profile);
        void onError(String message, int code);
    }

    interface ContactsCallback {
        void onSuccess(List<ContactUser> contacts);
        void onError(String message, int code);
    }
}
