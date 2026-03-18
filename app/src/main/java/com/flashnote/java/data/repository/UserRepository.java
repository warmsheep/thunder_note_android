package com.flashnote.java.data.repository;

import androidx.lifecycle.LiveData;

import com.flashnote.java.data.model.ContactSearchUser;
import com.flashnote.java.data.model.ContactUser;
import com.flashnote.java.data.model.FriendRequest;
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
    LiveData<List<FriendRequest>> getFriendRequests();
    LiveData<Long> getPendingRequestCount();
    void fetchFriendRequests(ContactsCallback callback);
    void refreshPendingRequestCount();
    void sendFriendRequest(Long targetUserId, ActionCallback callback);
    void acceptFriendRequest(Long requestId, ActionCallback callback);
    void rejectFriendRequest(Long requestId, ActionCallback callback);
    void deleteContact(Long contactUserId, ActionCallback callback);
    void searchContacts(String keyword, SearchCallback callback);

    interface ProfileCallback {
        void onSuccess(UserProfile profile);
        void onError(String message, int code);
    }

    interface ContactsCallback {
        void onSuccess(List<ContactUser> contacts);
        void onError(String message, int code);
    }

    interface ActionCallback {
        void onSuccess();
        void onError(String message, int code);
    }

    interface SearchCallback {
        void onSuccess(List<ContactSearchUser> users);
        void onError(String message, int code);
    }
}
