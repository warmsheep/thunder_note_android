package com.flashnote.java;

import android.app.Application;
import android.content.SharedPreferences;

import androidx.room.Room;
import androidx.work.WorkManager;

import com.bumptech.glide.Glide;
import com.flashnote.java.data.local.FlashNoteDatabase;
import com.flashnote.java.data.remote.ApiClient;
import com.flashnote.java.data.remote.ServerConfigStore;
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
import com.flashnote.java.data.repository.PendingMessageRepository;
import com.flashnote.java.data.repository.PendingMessageRepositoryImpl;
import com.flashnote.java.data.repository.SyncRepository;
import com.flashnote.java.data.repository.SyncRepositoryImpl;
import com.flashnote.java.data.repository.UserRepository;
import com.flashnote.java.data.repository.UserRepositoryImpl;
import com.flashnote.java.data.repository.VideoPreparationService;
import com.flashnote.java.data.repository.VideoPreparationServiceImpl;
import com.flashnote.java.data.sync.PendingRecoveryWorker;
import com.flashnote.java.security.GestureLockManager;

import java.io.File;

public class FlashNoteApp extends Application {

    private static FlashNoteApp instance;
    private TokenManager tokenManager;
    private ServerConfigStore serverConfigStore;
    private GestureLockManager gestureLockManager;
    private ApiClient apiClient;
    private AuthRepository authRepository;
    private FlashNoteRepository flashNoteRepository;
    private MessageRepository messageRepository;
    private CollectionRepository collectionRepository;
    private SyncRepository syncRepository;
    private FileRepository fileRepository;
    private FavoriteRepository favoriteRepository;
    private UserRepository userRepository;
    private FlashNoteDatabase database;
    private PendingMessageRepository pendingMessageRepository;
    private VideoPreparationService videoPreparationService;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        DebugLog.init(this);
        clearStaleCacheOnUpgrade();
        initializeDependencies();
    }

    private void clearStaleCacheOnUpgrade() {
        SharedPreferences prefs = getSharedPreferences("flashnote_app", MODE_PRIVATE);
        int lastCacheClearVersion = prefs.getInt("cache_clear_version", 0);
        int requiredVersion = 1;
        if (lastCacheClearVersion < requiredVersion) {
            new Thread(() -> {
                try {
                    Glide.get(FlashNoteApp.this).clearDiskCache();
                } catch (Exception exception) {
                    DebugLog.w("FlashNoteApp", "Failed to clear stale Glide cache");
                }
            }).start();
            prefs.edit().putInt("cache_clear_version", requiredVersion).apply();
        }
    }

    private void initializeDependencies() {
        serverConfigStore = new ServerConfigStore(this);
        tokenManager = new TokenManager(this);
        gestureLockManager = new GestureLockManager(this, tokenManager);
        apiClient = new ApiClient(tokenManager, serverConfigStore);
        database = Room.databaseBuilder(getApplicationContext(), FlashNoteDatabase.class, "flashnote.db")
                .fallbackToDestructiveMigration()
                .build();

        authRepository = new AuthRepositoryImpl(apiClient.getAuthService(), tokenManager);
        pendingMessageRepository = new PendingMessageRepositoryImpl(database.pendingMessageDao());
        fileRepository = new FileRepositoryImpl(apiClient.getFileService(), this);
        videoPreparationService = new VideoPreparationServiceImpl(this);
        messageRepository = new MessageRepositoryImpl(apiClient.getMessageService(), pendingMessageRepository, fileRepository, videoPreparationService, database.messageLocalDao());
        flashNoteRepository = new FlashNoteRepositoryImpl(apiClient.getFlashNoteService(), database.flashNoteLocalDao(), tokenManager, messageRepository);
        collectionRepository = new CollectionRepositoryImpl(apiClient.getCollectionService(), database.collectionLocalDao(), tokenManager);
        favoriteRepository = new FavoriteRepositoryImpl(apiClient.getFavoriteService(), database.favoriteLocalDao(), tokenManager);
        syncRepository = new SyncRepositoryImpl(
                apiClient.getSyncService(),
                tokenManager,
                database.flashNoteLocalDao(),
                database.collectionLocalDao(),
                database.favoriteLocalDao(),
                database.messageLocalDao(),
                pendingMessageRepository,
                flashNoteRepository,
                collectionRepository,
                favoriteRepository,
                messageRepository
        );
        userRepository = new UserRepositoryImpl(apiClient.getUserService());

    }

    public static FlashNoteApp getInstance() {
        return instance;
    }

    public TokenManager getTokenManager() {
        return tokenManager;
    }

    public ServerConfigStore getServerConfigStore() {
        return serverConfigStore;
    }

    public GestureLockManager getGestureLockManager() {
        return gestureLockManager;
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

    public UserRepository getUserRepository() {
        return userRepository;
    }

    public FlashNoteDatabase getDatabase() {
        return database;
    }

    public PendingMessageRepository getPendingMessageRepository() {
        return pendingMessageRepository;
    }

    public void switchServerEnvironment() {
        if (tokenManager != null) {
            tokenManager.clearTokens();
        }
        if (pendingMessageRepository != null) {
            clearPendingMessages();
        }
        WorkManager.getInstance(this).cancelUniqueWork(PendingRecoveryWorker.UNIQUE_WORK_NAME);
        DebugLog.clear();
        clearAvatarCache();
        clearAppCache();
        initializeDependencies();
    }

    private void clearPendingMessages() {
        if (database == null) {
            return;
        }
        new Thread(() -> {
            try {
                database.clearAllTables();
            } catch (RuntimeException exception) {
                DebugLog.e("FlashNoteApp", "Failed to clear local tables for server switch", exception);
            }
        }).start();
    }

    private void clearAvatarCache() {
        File avatarFile = new File(getFilesDir(), "avatar.jpg");
        if (avatarFile.exists()) {
            avatarFile.delete();
        }
    }

    private void clearAppCache() {
        File cacheDir = getCacheDir();
        if (cacheDir == null || !cacheDir.exists()) {
            return;
        }
        File[] children = cacheDir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            deleteRecursively(child);
        }
    }

    private void deleteRecursively(File target) {
        if (target == null || !target.exists()) {
            return;
        }
        if (target.isDirectory()) {
            File[] children = target.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        target.delete();
    }
}
