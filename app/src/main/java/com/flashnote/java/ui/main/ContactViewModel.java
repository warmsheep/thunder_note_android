package com.flashnote.java.ui.main;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.flashnote.java.FlashNoteApp;
import com.flashnote.java.data.model.ContactUser;
import com.flashnote.java.data.repository.UserRepository;

import java.util.List;

public class ContactViewModel extends AndroidViewModel {
    private final UserRepository userRepository;
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    public ContactViewModel(@NonNull Application application) {
        super(application);
        this.userRepository = ((FlashNoteApp) application).getUserRepository();
        refreshContacts();
    }

    public LiveData<List<ContactUser>> getContacts() {
        return userRepository.getContacts();
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
    }
}
