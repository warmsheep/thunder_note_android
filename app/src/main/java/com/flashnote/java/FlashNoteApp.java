package com.flashnote.java;

import android.app.Application;

import com.flashnote.java.data.remote.ApiClient;
import com.flashnote.java.data.repository.AuthRepository;
import com.flashnote.java.data.repository.AuthRepositoryImpl;
import com.flashnote.java.data.repository.CollectionRepository;
import com.flashnote.java.data.repository.CollectionRepositoryImpl;
import com.flashnote.java.data.repository.FileRepository;
import com.flashnote.java.data.repository.FileRepositoryImpl;
import com.flashnote.java.data.repository.FavoriteRepository;
import com.flashnote.java.data.repository.FavoriteRepositoryImpl;
import com.flashnote.java.data.repository.FlashNoteRepository;
import com.flashnote.java.data.repository.FlashNoteRepositoryImpl;
import com.flashnote.java.data.repository.MessageRepository;
import com.flashnote.java.data.repository.MessageRepositoryImpl;
import com.flashnote.java.data.repository.SyncRepository;
import com.flashnote.java.data.repository.SyncRepositoryImpl;

public class FlashNoteApp extends Application {

    private static FlashNoteApp instance;
    private TokenManager tokenManager;
    private ApiClient apiClient;
    private AuthRepository authRepository;
    private FlashNoteRepository flashNoteRepository;
    private MessageRepository messageRepository;
    private CollectionRepository collectionRepository;
    private SyncRepository syncRepository;
    private FileRepository fileRepository;
    private FavoriteRepository favoriteRepository;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        initializeDependencies();
    }

    private void initializeDependencies() {
        tokenManager = new TokenManager(this);
        apiClient = new ApiClient(tokenManager);

        authRepository = new AuthRepositoryImpl(apiClient.getAuthService(), tokenManager);
        flashNoteRepository = new FlashNoteRepositoryImpl(apiClient.getFlashNoteService());
        messageRepository = new MessageRepositoryImpl(apiClient.getMessageService());
        collectionRepository = new CollectionRepositoryImpl(apiClient.getCollectionService());
        syncRepository = new SyncRepositoryImpl(apiClient.getSyncService());
        fileRepository = new FileRepositoryImpl(apiClient.getFileService(), this);
        favoriteRepository = new FavoriteRepositoryImpl(apiClient.getFavoriteService());
    }

    public static FlashNoteApp getInstance() {
        return instance;
    }

    public TokenManager getTokenManager() {
        return tokenManager;
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    public AuthRepository getAuthRepository() {
        return authRepository;
    }

    public FlashNoteRepository getFlashNoteRepository() {
        return flashNoteRepository;
    }

    public MessageRepository getMessageRepository() {
        return messageRepository;
    }

    public CollectionRepository getCollectionRepository() {
        return collectionRepository;
    }

    public SyncRepository getSyncRepository() {
        return syncRepository;
    }

    public FileRepository getFileRepository() {
        return fileRepository;
    }

    public FavoriteRepository getFavoriteRepository() {
        return favoriteRepository;
    }
}
