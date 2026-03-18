package com.flashnote.java.ui.main;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.flashnote.java.FlashNoteApp;
import com.flashnote.java.data.model.ContactSearchUser;
import com.flashnote.java.data.model.ContactUser;
import com.flashnote.java.data.model.FriendRequest;
import com.flashnote.java.data.repository.UserRepository;

import java.util.ArrayList;
import java.util.List;

public class ContactViewModel extends AndroidViewModel {
    private final UserRepository userRepository;
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<List<ContactSearchUser>> searchResults = new MutableLiveData<>(new ArrayList<>());

    public ContactViewModel(@NonNull Application application) {
        super(application);
        this.userRepository = ((FlashNoteApp) application).getUserRepository();
        refreshContacts();
    }

    public LiveData<List<ContactUser>> getContacts() {
        return userRepository.getContacts();
    }

    public LiveData<List<FriendRequest>> getFriendRequests() {
        return userRepository.getFriendRequests();
    }

    public LiveData<Long> getPendingRequestCount() {
        return userRepository.getPendingRequestCount();
    }

    public LiveData<List<ContactSearchUser>> getSearchResults() {
        return searchResults;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public void clearError() {
        errorMessage.setValue(null);
    }

    public void refreshContacts() {
        userRepository.fetchContacts(new UserRepository.ContactsCallback() {
            @Override
            public void onSuccess(List<ContactUser> contacts) {
                clearError();
            }

            @Override
            public void onError(String message, int code) {
                errorMessage.setValue(message);
            }
        });
        userRepository.fetchFriendRequests(null);
        userRepository.refreshPendingRequestCount();
    }

    public void acceptRequest(Long requestId) {
        userRepository.acceptFriendRequest(requestId, new UserRepository.ActionCallback() {
            @Override
            public void onSuccess() {
                refreshContacts();
            }

            @Override
            public void onError(String message, int code) {
                errorMessage.setValue(message);
            }
        });
    }

    public void rejectRequest(Long requestId) {
        userRepository.rejectFriendRequest(requestId, new UserRepository.ActionCallback() {
            @Override
            public void onSuccess() {
                refreshContacts();
            }

            @Override
            public void onError(String message, int code) {
                errorMessage.setValue(message);
            }
        });
    }

    public void deleteContact(Long contactUserId) {
        userRepository.deleteContact(contactUserId, new UserRepository.ActionCallback() {
            @Override
            public void onSuccess() {
                refreshContacts();
            }

            @Override
            public void onError(String message, int code) {
                errorMessage.setValue(message);
            }
        });
    }

    public void searchContacts(String keyword) {
        userRepository.searchContacts(keyword, new UserRepository.SearchCallback() {
            @Override
            public void onSuccess(List<ContactSearchUser> users) {
                searchResults.setValue(users == null ? new ArrayList<>() : users);
            }

            @Override
            public void onError(String message, int code) {
                errorMessage.setValue(message);
            }
        });
    }

    public void sendFriendRequest(Long targetUserId) {
        userRepository.sendFriendRequest(targetUserId, new UserRepository.ActionCallback() {
            @Override
            public void onSuccess() {
                refreshContacts();
            }

            @Override
            public void onError(String message, int code) {
                errorMessage.setValue(message);
            }
        });
    }
}
