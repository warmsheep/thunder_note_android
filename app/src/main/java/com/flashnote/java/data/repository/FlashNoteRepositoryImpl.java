package com.flashnote.java.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.flashnote.java.TokenManager;
import com.flashnote.java.DebugLog;
import com.flashnote.java.data.local.FlashNoteLocalDao;
import com.flashnote.java.data.local.FlashNoteLocalEntity;
import com.flashnote.java.data.model.ApiResponse;
import com.flashnote.java.data.model.FlashNote;
import com.flashnote.java.data.model.FlashNoteSearchRequest;
import com.flashnote.java.data.model.FlashNoteSearchResponse;
import com.flashnote.java.data.model.FlashNoteSearchResult;
import com.flashnote.java.data.remote.FlashNoteService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class FlashNoteRepositoryImpl implements FlashNoteRepository {
    private static final long INBOX_NOTE_ID = -1L;
    private final FlashNoteService flashNoteService;
    private final FlashNoteLocalDao flashNoteLocalDao;
    private final TokenManager tokenManager;
    private final MessageRepository messageRepository;
    private final LiveData<List<FlashNote>> notesLiveData;
    private final androidx.lifecycle.MutableLiveData<Boolean> isLoading = new androidx.lifecycle.MutableLiveData<>(false);
    private final androidx.lifecycle.MutableLiveData<String> errorMessage = new androidx.lifecycle.MutableLiveData<>();
    private final ExecutorService localExecutor = Executors.newSingleThreadExecutor();

    public FlashNoteRepositoryImpl(FlashNoteService flashNoteService, FlashNoteLocalDao flashNoteLocalDao, TokenManager tokenManager) {
        this(flashNoteService, flashNoteLocalDao, tokenManager, null);
    }

    public FlashNoteRepositoryImpl(FlashNoteService flashNoteService,
                                   FlashNoteLocalDao flashNoteLocalDao,
                                   TokenManager tokenManager,
                                   MessageRepository messageRepository) {
        this.flashNoteService = flashNoteService;
        this.flashNoteLocalDao = flashNoteLocalDao;
        this.tokenManager = tokenManager;
        this.messageRepository = messageRepository;
        long currentUserId = Long.parseLong(RepositoryAuthSupport.requireCurrentUserId(tokenManager));
        this.notesLiveData = Transformations.map(
                flashNoteLocalDao.observeAllByUserId(currentUserId),
                this::toModelList
        );
    }

    @Override
    public LiveData<List<FlashNote>> getNotes() {
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
    public void clearError() {
        errorMessage.postValue(null);
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
                        clearError();
                        persistRemoteNotes(apiResponse.getData());
                    } else {
                        String errMsg = apiResponse.getMessage();
                        DebugLog.w("FlashNoteRepo", errMsg);
                        errorMessage.postValue(errMsg);
                    }
                } else {
                    String errMsg = "Failed to load notes: " + response.code();
                    DebugLog.w("FlashNoteRepo", errMsg);
                    errorMessage.postValue(errMsg);
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
        flashNoteService.search(new FlashNoteSearchRequest(query)).enqueue(new Callback<ApiResponse<FlashNoteSearchResponse>>() {
            @Override
            public void onResponse(Call<ApiResponse<FlashNoteSearchResponse>> call,
                                   Response<ApiResponse<FlashNoteSearchResponse>> response) {
                isLoading.postValue(false);
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<FlashNoteSearchResponse> apiResponse = response.body();
                    if (apiResponse.isSuccess() && apiResponse.getData() != null) {
                        FlashNoteSearchResponse searchResponse = apiResponse.getData();
                        List<FlashNoteSearchResult> noteName = searchResponse.getNoteNameMatched();
                        List<FlashNoteSearchResult> messageContent = searchResponse.getMessageContentMatched();
                        noteName = noteName != null ? noteName : new ArrayList<>();
                        messageContent = messageContent != null ? messageContent : new ArrayList<>();
                        callback.onSuccess(noteName, messageContent);
                        return;
                    }
                    String errMsg = apiResponse.getMessage();
                    DebugLog.w("FlashNoteRepo", errMsg);
                    callback.onError(errMsg);
                    return;
                }
                String errMsg = "Failed to search notes: " + response.code();
                DebugLog.w("FlashNoteRepo", errMsg);
                callback.onError(errMsg);
            }

            @Override
            public void onFailure(Call<ApiResponse<FlashNoteSearchResponse>> call, Throwable t) {
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
                        clearError();
                        persistSingleNote(apiResponse.getData());
                    } else {
                        String errMsg = apiResponse.getMessage();
                        DebugLog.w("FlashNoteRepo", errMsg);
                        errorMessage.postValue(errMsg);
                    }
                } else {
                    String errMsg = "Failed to create note: " + response.code();
                    DebugLog.w("FlashNoteRepo", errMsg);
                    errorMessage.postValue(errMsg);
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
                        clearError();
                        persistSingleNote(apiResponse.getData());
                    } else {
                        String errMsg = apiResponse.getMessage();
                        DebugLog.w("FlashNoteRepo", errMsg);
                        errorMessage.postValue(errMsg);
                    }
                } else {
                    String errMsg = "Failed to update note: " + response.code();
                    DebugLog.w("FlashNoteRepo", errMsg);
                    errorMessage.postValue(errMsg);
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
    public void setPinned(Long id, boolean pinned) {
        if (id == null) {
            return;
        }
        isLoading.postValue(true);
        flashNoteService.pin(id, pinned).enqueue(new Callback<ApiResponse<FlashNote>>() {
            @Override
            public void onResponse(Call<ApiResponse<FlashNote>> call, Response<ApiResponse<FlashNote>> response) {
                isLoading.postValue(false);
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess() && response.body().getData() != null) {
                    applyUpdatedNote(response.body().getData());
                } else {
                    errorMessage.postValue(response.body() == null ? "Failed to update pin" : response.body().getMessage());
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<FlashNote>> call, Throwable t) {
                isLoading.postValue(false);
                errorMessage.postValue("Network error: " + t.getMessage());
            }
        });
    }

    @Override
    public void setHidden(Long id, boolean hidden) {
        if (id == null) {
            return;
        }
        isLoading.postValue(true);
        flashNoteService.hide(id, hidden).enqueue(new Callback<ApiResponse<FlashNote>>() {
            @Override
            public void onResponse(Call<ApiResponse<FlashNote>> call, Response<ApiResponse<FlashNote>> response) {
                isLoading.postValue(false);
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess() && response.body().getData() != null) {
                    applyUpdatedNote(response.body().getData());
                } else {
                    errorMessage.postValue(response.body() == null ? "Failed to update hidden state" : response.body().getMessage());
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<FlashNote>> call, Throwable t) {
                isLoading.postValue(false);
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
                        clearError();
                        localExecutor.execute(() -> flashNoteLocalDao.deleteById(id));
                    } else {
                        String errMsg = apiResponse.getMessage();
                        DebugLog.w("FlashNoteRepo", errMsg);
                        errorMessage.postValue(errMsg);
                    }
                } else {
                    String errMsg = "Failed to delete note: " + response.code();
                    DebugLog.w("FlashNoteRepo", errMsg);
                    errorMessage.postValue(errMsg);
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

    @Override
    public void clearInboxMessages(Runnable onSuccess) {
        if (messageRepository == null) {
            errorMessage.postValue("消息仓库未初始化");
            return;
        }
        isLoading.postValue(true);
        messageRepository.clearInboxMessages(() -> {
            isLoading.postValue(false);
            clearError();
            if (onSuccess != null) {
                onSuccess.run();
            }
        });
    }

    @Override
    public void updateInboxPreviewLocally(String latestMessage) {
        String preview = latestMessage == null ? null : latestMessage.trim();
        if (preview == null || preview.isEmpty()) {
            return;
        }
        String updatedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        localExecutor.execute(() -> flashNoteLocalDao.updateLatestMessage(INBOX_NOTE_ID, preview, updatedAt));
    }

    private void applyUpdatedNote(FlashNote updatedNote) {
        clearError();
        persistSingleNote(updatedNote);
    }

    private void persistRemoteNotes(List<FlashNote> notes) {
        List<FlashNoteLocalEntity> localNotes = toLocalList(notes);
        localExecutor.execute(() -> {
            if (!localNotes.isEmpty()) {
                flashNoteLocalDao.upsertAll(localNotes);
            }
        });
    }

    private void persistSingleNote(FlashNote note) {
        FlashNoteLocalEntity entity = toLocal(note);
        if (entity == null) {
            return;
        }
        localExecutor.execute(() -> flashNoteLocalDao.upsert(entity));
    }

    private List<FlashNoteLocalEntity> toLocalList(List<FlashNote> notes) {
        List<FlashNoteLocalEntity> result = new ArrayList<>();
        if (notes == null) {
            return result;
        }
        for (FlashNote note : notes) {
            FlashNoteLocalEntity entity = toLocal(note);
            if (entity != null) {
                result.add(entity);
            }
        }
        result.sort(Comparator
                .comparing((FlashNoteLocalEntity note) -> Boolean.TRUE.equals(note.getPinned()))
                .reversed()
                .thenComparing(FlashNoteLocalEntity::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }

    private FlashNoteLocalEntity toLocal(FlashNote note) {
        if (note == null || note.getId() == null) {
            return null;
        }
        FlashNoteLocalEntity entity = new FlashNoteLocalEntity();
        entity.setId(note.getId());
        entity.setUserId(note.getUserId());
        entity.setTitle(note.getTitle());
        entity.setIcon(note.getIcon());
        entity.setContent(note.getContent());
        entity.setLatestMessage(note.getLatestMessage());
        entity.setTags(note.getTags());
        entity.setDeleted(note.getDeleted());
        entity.setPinned(note.getPinned());
        entity.setHidden(note.getHidden());
        entity.setInbox(note.getInbox());
        entity.setCreatedAt(note.getCreatedAt() == null ? null : note.getCreatedAt().toString());
        entity.setUpdatedAt(note.getUpdatedAt() == null ? null : note.getUpdatedAt().toString());
        return entity;
    }

    private List<FlashNote> toModelList(List<FlashNoteLocalEntity> entities) {
        List<FlashNote> result = new ArrayList<>();
        if (entities == null) {
            return result;
        }
        for (FlashNoteLocalEntity entity : entities) {
            FlashNote note = new FlashNote();
            note.setId(entity.getId());
            note.setUserId(entity.getUserId());
            note.setTitle(entity.getTitle());
            note.setIcon(entity.getIcon());
            note.setContent(entity.getContent());
            note.setLatestMessage(entity.getLatestMessage());
            note.setTags(entity.getTags());
            note.setDeleted(entity.getDeleted());
            note.setPinned(entity.getPinned());
            note.setHidden(entity.getHidden());
            note.setInbox(entity.getInbox());
            note.setCreatedAt(parseDateTime(entity.getCreatedAt()));
            note.setUpdatedAt(parseDateTime(entity.getUpdatedAt()));
            result.add(note);
        }
        return result;
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return LocalDateTime.parse(value);
    }

}
