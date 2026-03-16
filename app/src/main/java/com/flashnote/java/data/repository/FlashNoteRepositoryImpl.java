package com.flashnote.java.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.flashnote.java.DebugLog;
import com.flashnote.java.data.model.ApiResponse;
import com.flashnote.java.data.model.FlashNote;
import com.flashnote.java.data.model.FlashNoteSearchRequest;
import com.flashnote.java.data.model.FlashNoteSearchResult;
import com.flashnote.java.data.remote.FlashNoteService;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class FlashNoteRepositoryImpl implements FlashNoteRepository {
    private final FlashNoteService flashNoteService;
    private final MutableLiveData<List<FlashNote>> notesLiveData = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    public FlashNoteRepositoryImpl(FlashNoteService flashNoteService) {
        this.flashNoteService = flashNoteService;
    }

    @Override
    public LiveData<List<FlashNote>> getNotes() {
        return notesLiveData;
    }

    public MutableLiveData<List<FlashNote>> getNotesMutable() {
        return notesLiveData;
    }

    @Override
    public LiveData<Boolean> isLoading() {
        return isLoading;
    }

    @Override
    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    @Override
    public void refresh() {
        isLoading.postValue(true);
        flashNoteService.list().enqueue(new Callback<ApiResponse<List<FlashNote>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<FlashNote>>> call, 
                                 Response<ApiResponse<List<FlashNote>>> response) {
                isLoading.postValue(false);
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<List<FlashNote>> apiResponse = response.body();
                    if (apiResponse.isSuccess() && apiResponse.getData() != null) {
                        notesLiveData.postValue(apiResponse.getData());
                    } else {
                        errorMessage.postValue(apiResponse.getMessage());
                    }
                } else {
                    errorMessage.postValue("Failed to load notes: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<FlashNote>>> call, Throwable t) {
                isLoading.postValue(false);
                DebugLog.e("FlashNoteRepo", "refresh failed", t);
                errorMessage.postValue("Network error: " + t.getMessage());
            }
        });
    }

    @Override
    public void searchNotes(String query, SearchCallback callback) {
        isLoading.postValue(true);
        flashNoteService.search(new FlashNoteSearchRequest(query)).enqueue(new Callback<ApiResponse<List<FlashNoteSearchResult>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<FlashNoteSearchResult>>> call,
                                   Response<ApiResponse<List<FlashNoteSearchResult>>> response) {
                isLoading.postValue(false);
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<List<FlashNoteSearchResult>> apiResponse = response.body();
                    if (apiResponse.isSuccess()) {
                        callback.onSuccess(apiResponse.getData() == null ? new ArrayList<>() : apiResponse.getData());
                        return;
                    }
                    callback.onError(apiResponse.getMessage());
                    return;
                }
                callback.onError("Failed to search notes: " + response.code());
            }

            @Override
            public void onFailure(Call<ApiResponse<List<FlashNoteSearchResult>>> call, Throwable t) {
                isLoading.postValue(false);
                DebugLog.e("FlashNoteRepo", "search failed", t);
                callback.onError("Network error: " + t.getMessage());
            }
        });
    }

    @Override
    public void createNote(String title, String icon, String collectionName) {
        isLoading.postValue(true);
        FlashNote note = new FlashNote();
        note.setTitle(title);
        note.setIcon(icon);
        note.setContent("");
        note.setTags(collectionName);
        
        flashNoteService.create(note).enqueue(new Callback<ApiResponse<FlashNote>>() {
            @Override
            public void onResponse(Call<ApiResponse<FlashNote>> call, 
                                 Response<ApiResponse<FlashNote>> response) {
                isLoading.postValue(false);
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<FlashNote> apiResponse = response.body();
                    if (apiResponse.isSuccess() && apiResponse.getData() != null) {
                        List<FlashNote> current = notesLiveData.getValue();
                        List<FlashNote> updated = current == null ? new ArrayList<>() : new ArrayList<>(current);
                        updated.add(0, apiResponse.getData());
                        notesLiveData.postValue(updated);
                    } else {
                        errorMessage.postValue(apiResponse.getMessage());
                    }
                } else {
                    errorMessage.postValue("Failed to create note: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<FlashNote>> call, Throwable t) {
                isLoading.postValue(false);
                DebugLog.e("FlashNoteRepo", "create failed", t);
                errorMessage.postValue("Network error: " + t.getMessage());
            }
        });
    }

    @Override
    public void updateNote(Long id, String title, String content, String icon, String collectionName) {
        isLoading.postValue(true);
        FlashNote note = new FlashNote();
        note.setTitle(title);
        note.setContent(content);
        note.setIcon(icon);
        note.setTags(collectionName);
        
        flashNoteService.update(id, note).enqueue(new Callback<ApiResponse<FlashNote>>() {
            @Override
            public void onResponse(Call<ApiResponse<FlashNote>> call, 
                                 Response<ApiResponse<FlashNote>> response) {
                isLoading.postValue(false);
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<FlashNote> apiResponse = response.body();
                    if (apiResponse.isSuccess() && apiResponse.getData() != null) {
                        List<FlashNote> current = notesLiveData.getValue();
                        if (current != null) {
                            List<FlashNote> updated = new ArrayList<>();
                            for (FlashNote n : current) {
                                if (n.getId().equals(id)) {
                                    updated.add(apiResponse.getData());
                                } else {
                                    updated.add(n);
                                }
                            }
                            notesLiveData.postValue(updated);
                        }
                    } else {
                        errorMessage.postValue(apiResponse.getMessage());
                    }
                } else {
                    errorMessage.postValue("Failed to update note: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<FlashNote>> call, Throwable t) {
                isLoading.postValue(false);
                DebugLog.e("FlashNoteRepo", "update failed", t);
                errorMessage.postValue("Network error: " + t.getMessage());
            }
        });
    }

    @Override
    public void deleteNote(Long id) {
        isLoading.postValue(true);
        flashNoteService.delete(id).enqueue(new Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(Call<ApiResponse<Void>> call, 
                                 Response<ApiResponse<Void>> response) {
                isLoading.postValue(false);
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<Void> apiResponse = response.body();
                    if (apiResponse.isSuccess()) {
                        List<FlashNote> current = notesLiveData.getValue();
                        if (current != null) {
                            List<FlashNote> updated = new ArrayList<>();
                            for (FlashNote n : current) {
                                if (!n.getId().equals(id)) {
                                    updated.add(n);
                                }
                            }
                            notesLiveData.postValue(updated);
                        }
                    } else {
                        errorMessage.postValue(apiResponse.getMessage());
                    }
                } else {
                    errorMessage.postValue("Failed to delete note: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                isLoading.postValue(false);
                DebugLog.e("FlashNoteRepo", "delete failed", t);
                errorMessage.postValue("Network error: " + t.getMessage());
            }
        });
    }
}
