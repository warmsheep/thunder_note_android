package com.flashnote.java.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.times;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.SharedPreferences;

import androidx.lifecycle.MutableLiveData;

import com.flashnote.java.data.model.ApiResponse;
import com.flashnote.java.data.model.ContactUser;
import com.flashnote.java.data.model.UserProfile;
import com.flashnote.java.data.remote.UserService;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Map;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class UserRepositoryImplTest {

    @Mock private UserService userService;
    @Mock private SharedPreferences sharedPreferences;
    @Mock private SharedPreferences.Editor editor;
    @Mock private Call<ApiResponse<UserProfile>> profileCall;
    @Mock private Call<ApiResponse<List<ContactUser>>> contactsCall;
    @Mock private Call<ApiResponse<Long>> pendingCountCall;

    private UserRepositoryImpl repository;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(sharedPreferences.edit()).thenReturn(editor);
        when(editor.putString(any(), any())).thenReturn(editor);
        when(editor.remove(any())).thenReturn(editor);
        when(userService.getProfile()).thenReturn(profileCall);
        when(userService.listContacts()).thenReturn(contactsCall);
        when(userService.countFriendRequests()).thenReturn(pendingCountCall);
        repository = new UserRepositoryImpl(userService, sharedPreferences);
    }

    @Test
    public void getProfileLoadsCachedProfileImmediately() {
        when(sharedPreferences.getString(any(), any())).thenReturn("{\"nickname\":\"离线昵称\",\"avatar\":\"🙂\"}");

        repository = new UserRepositoryImpl(userService, sharedPreferences);

        UserProfile profile = repository.getProfile().getValue();
        assertNotNull(profile);
        assertEquals("离线昵称", profile.getNickname());
        assertEquals("🙂", profile.getAvatar());
    }

    @Test
    public void fetchProfilePersistsReturnedProfileToCache() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Callback<ApiResponse<UserProfile>>> callbackCaptor = ArgumentCaptor.forClass(Callback.class);
        UserProfile profile = new UserProfile();
        profile.setNickname("在线昵称");
        profile.setAvatar("https://avatar.example/test.png");

        repository.fetchProfile(null);

        verify(profileCall).enqueue(callbackCaptor.capture());
        callbackCaptor.getValue().onResponse(profileCall, Response.success(new ApiResponse<>(0, "ok", profile)));

        verify(editor).putString(any(), any());
        UserProfile current = repository.getProfile().getValue();
        assertNotNull(current);
        assertEquals("在线昵称", current.getNickname());
    }

    @Test
    public void fetchProfile_skipsDuplicateRequestWithinCooldown() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Callback<ApiResponse<UserProfile>>> callbackCaptor = ArgumentCaptor.forClass(Callback.class);
        UserProfile profile = new UserProfile();
        profile.setNickname("在线昵称");

        repository.fetchProfile(null);
        verify(profileCall, times(1)).enqueue(callbackCaptor.capture());
        callbackCaptor.getValue().onResponse(profileCall, Response.success(new ApiResponse<>(0, "ok", profile)));

        repository.fetchProfile(null);

        verify(userService, times(1)).getProfile();
        verify(profileCall, times(1)).enqueue(any());
    }

    @Test
    public void fetchContacts_skipsDuplicateRequestWithinCooldown() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Callback<ApiResponse<List<ContactUser>>>> callbackCaptor = ArgumentCaptor.forClass(Callback.class);
        ContactUser contact = new ContactUser();
        contact.setUserId(2L);
        contact.setUsername("bob");

        repository.fetchContacts(null);
        verify(contactsCall, times(1)).enqueue(callbackCaptor.capture());
        callbackCaptor.getValue().onResponse(contactsCall, Response.success(new ApiResponse<>(0, "ok", List.of(contact))));

        repository.fetchContacts(null);

        verify(userService, times(1)).listContacts();
        verify(contactsCall, times(1)).enqueue(any());
    }

    @Test
    public void refreshPendingRequestCount_skipsDuplicateRequestWithinCooldown() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Callback<ApiResponse<Long>>> callbackCaptor = ArgumentCaptor.forClass(Callback.class);

        repository.refreshPendingRequestCount();
        verify(pendingCountCall, times(1)).enqueue(callbackCaptor.capture());
        callbackCaptor.getValue().onResponse(pendingCountCall, Response.success(new ApiResponse<>(0, "ok", 3L)));

        repository.refreshPendingRequestCount();

        verify(userService, times(1)).countFriendRequests();
        verify(pendingCountCall, times(1)).enqueue(any());
    }
}
