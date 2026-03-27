package com.flashnote.java.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.SharedPreferences;

import androidx.lifecycle.MutableLiveData;

import com.flashnote.java.data.model.ApiResponse;
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

    private UserRepositoryImpl repository;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(sharedPreferences.edit()).thenReturn(editor);
        when(editor.putString(any(), any())).thenReturn(editor);
        when(editor.remove(any())).thenReturn(editor);
        when(userService.getProfile()).thenReturn(profileCall);
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
}
