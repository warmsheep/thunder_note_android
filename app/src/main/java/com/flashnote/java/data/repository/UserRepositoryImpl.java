package com.flashnote.java.data.repository;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.flashnote.java.DebugLog;
import com.flashnote.java.data.model.ApiResponse;
import com.flashnote.java.data.model.ContactSearchUser;
import com.flashnote.java.data.model.ContactUser;
import com.flashnote.java.data.model.FriendRequest;
import com.flashnote.java.data.model.UserProfile;
import com.flashnote.java.data.remote.UserService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class UserRepositoryImpl implements UserRepository {
    private static final String KEY_CACHED_PROFILE = "cached_user_profile";

    private final UserService userService;
    private final SharedPreferences sharedPreferences;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final MutableLiveData<UserProfile> profileLiveData = new MutableLiveData<>();
    private final MutableLiveData<List<ContactUser>> contactsLiveData = new MutableLiveData<>();
    private final MutableLiveData<List<FriendRequest>> friendRequestsLiveData = new MutableLiveData<>();
    private final MutableLiveData<Long> pendingRequestCountLiveData = new MutableLiveData<>(0L);
    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .create();

    public UserRepositoryImpl(UserService userService) {
        this(userService, null);
    }

    public UserRepositoryImpl(UserService userService, SharedPreferences sharedPreferences) {
        this.userService = userService;
        this.sharedPreferences = sharedPreferences;
        UserProfile cachedProfile = readCachedProfile();
        if (cachedProfile != null) {
            profileLiveData.setValue(cachedProfile);
        }
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
                        persistProfile(profile);
                        profileLiveData.setValue(profile);
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
                        persistProfile(updatedProfile);
                        profileLiveData.setValue(updatedProfile);
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
                            persistProfile(currentProfile);
                            profileLiveData.setValue(currentProfile);
                        }
                        if (callback != null) {
                            UserProfile profile = currentProfile != null ? currentProfile : new UserProfile();
                            profile.setAvatar(avatar);
                            persistProfile(profile);
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

    @Override
    public LiveData<List<ContactUser>> getContacts() {
        return contactsLiveData;
    }

    @Override
    public void fetchContacts(ContactsCallback callback) {
        userService.listContacts().enqueue(new Callback<ApiResponse<List<ContactUser>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<ContactUser>>> call, Response<ApiResponse<List<ContactUser>>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<List<ContactUser>> apiResponse = response.body();
                    if (apiResponse.getCode() == 0) {
                        List<ContactUser> contacts = apiResponse.getData();
                        contactsLiveData.postValue(contacts);
                        notifyContactsSuccess(callback, contacts);
                    } else {
                        notifyContactsError(callback, apiResponse.getMessage(), apiResponse.getCode());
                    }
                } else {
                    notifyContactsError(callback, "HTTP " + response.code(), response.code());
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<ContactUser>>> call, Throwable t) {
                notifyContactsError(callback, t.getMessage(), -1);
            }
        });
    }

    @Override
    public LiveData<List<FriendRequest>> getFriendRequests() {
        return friendRequestsLiveData;
    }

    @Override
    public LiveData<Long> getPendingRequestCount() {
        return pendingRequestCountLiveData;
    }

    @Override
    public void fetchFriendRequests(ContactsCallback callback) {
        userService.listFriendRequests().enqueue(new Callback<ApiResponse<List<FriendRequest>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<FriendRequest>>> call, Response<ApiResponse<List<FriendRequest>>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<List<FriendRequest>> apiResponse = response.body();
                    if (apiResponse.getCode() == 0) {
                        List<FriendRequest> requests = apiResponse.getData();
                        friendRequestsLiveData.postValue(requests);
                        pendingRequestCountLiveData.postValue(requests == null ? 0L : (long) requests.size());
                        notifyContactsSuccess(callback, contactsLiveData.getValue());
                    } else {
                        notifyContactsError(callback, apiResponse.getMessage(), apiResponse.getCode());
                    }
                } else {
                    notifyContactsError(callback, "HTTP " + response.code(), response.code());
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<FriendRequest>>> call, Throwable t) {
                notifyContactsError(callback, t.getMessage(), -1);
            }
        });
    }

    @Override
    public void refreshPendingRequestCount() {
        userService.countFriendRequests().enqueue(new Callback<ApiResponse<Long>>() {
            @Override
            public void onResponse(Call<ApiResponse<Long>> call, Response<ApiResponse<Long>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    pendingRequestCountLiveData.postValue(response.body().getData() == null ? 0L : response.body().getData());
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Long>> call, Throwable t) {
                DebugLog.w("UserRepo", "refreshPendingRequestCount failed: " + t.getMessage());
            }
        });
    }

    private void persistProfile(UserProfile profile) {
        if (profile == null) {
            return;
        }
        if (sharedPreferences == null) {
            return;
        }
        sharedPreferences.edit()
                .putString(KEY_CACHED_PROFILE, gson.toJson(profile))
                .apply();
    }

    private UserProfile readCachedProfile() {
        if (sharedPreferences == null) {
            return null;
        }
        String raw = sharedPreferences.getString(KEY_CACHED_PROFILE, null);
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            return gson.fromJson(raw, UserProfile.class);
        } catch (RuntimeException exception) {
            DebugLog.e("UserRepo", "Failed to parse cached profile", exception);
            sharedPreferences.edit().remove(KEY_CACHED_PROFILE).apply();
            return null;
        }
    }

    @Override
    public void sendFriendRequest(Long targetUserId, ActionCallback callback) {
        Map<String, Long> body = new HashMap<>();
        body.put("targetUserId", targetUserId);
        userService.sendFriendRequest(body).enqueue(wrapAction(callback));
    }

    @Override
    public void acceptFriendRequest(Long requestId, ActionCallback callback) {
        Map<String, Long> body = new HashMap<>();
        body.put("requestId", requestId);
        userService.acceptFriendRequest(body).enqueue(wrapAction(callback));
    }

    @Override
    public void rejectFriendRequest(Long requestId, ActionCallback callback) {
        Map<String, Long> body = new HashMap<>();
        body.put("requestId", requestId);
        userService.rejectFriendRequest(body).enqueue(wrapAction(callback));
    }

    @Override
    public void deleteContact(Long contactUserId, ActionCallback callback) {
        userService.deleteContact(contactUserId).enqueue(wrapAction(callback));
    }

    @Override
    public void searchContacts(String keyword, SearchCallback callback) {
        userService.searchContacts(keyword == null ? "" : keyword.trim()).enqueue(new Callback<ApiResponse<List<ContactSearchUser>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<ContactSearchUser>>> call, Response<ApiResponse<List<ContactSearchUser>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    mainHandler.post(() -> callback.onSuccess(response.body().getData()));
                    return;
                }
                String message = response.body() == null ? "HTTP " + response.code() : response.body().getMessage();
                int code = response.body() == null ? response.code() : response.body().getCode();
                mainHandler.post(() -> callback.onError(message, code));
            }

            @Override
            public void onFailure(Call<ApiResponse<List<ContactSearchUser>>> call, Throwable t) {
                mainHandler.post(() -> callback.onError(t.getMessage(), -1));
            }
        });
    }

    private Callback<ApiResponse<Void>> wrapAction(ActionCallback callback) {
        return new Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    if (callback != null) {
                        mainHandler.post(callback::onSuccess);
                    }
                    fetchContacts(null);
                    fetchFriendRequests(null);
                    refreshPendingRequestCount();
                    return;
                }
                String message = response.body() == null ? "HTTP " + response.code() : response.body().getMessage();
                int code = response.body() == null ? response.code() : response.body().getCode();
                if (callback != null) {
                    mainHandler.post(() -> callback.onError(message, code));
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                if (callback != null) {
                    mainHandler.post(() -> callback.onError(t.getMessage(), -1));
                }
            }
        };
    }

    private static final class LocalDateTimeAdapter extends TypeAdapter<LocalDateTime> {
        private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

        @Override
        public void write(JsonWriter out, LocalDateTime value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(FORMATTER.format(value));
            }
        }

        @Override
        public LocalDateTime read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            String raw = in.nextString();
            try {
                return LocalDateTime.parse(raw, FORMATTER);
            } catch (Exception exception) {
                DebugLog.w("UserRepo", "Failed to parse LocalDateTime: " + raw);
                return null;
            }
        }
    }

    private void notifySuccess(ProfileCallback callback, UserProfile profile) {
        if (callback != null) {
            mainHandler.post(() -> callback.onSuccess(profile));
        }
    }

    private void notifyError(ProfileCallback callback, String message, int code) {
        DebugLog.w("UserRepo", message);
        if (callback != null) {
            mainHandler.post(() -> callback.onError(message, code));
        }
    }

    private void notifyContactsSuccess(ContactsCallback callback, List<ContactUser> contacts) {
        if (callback != null) {
            mainHandler.post(() -> callback.onSuccess(contacts));
        }
    }

    private void notifyContactsError(ContactsCallback callback, String message, int code) {
        DebugLog.w("UserRepo", message);
        if (callback != null) {
            mainHandler.post(() -> callback.onError(message, code));
        }
    }
}
