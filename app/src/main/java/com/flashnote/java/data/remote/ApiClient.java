package com.flashnote.java.data.remote;

import com.flashnote.java.BuildConfig;
import com.flashnote.java.TokenManager;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {
    private final TokenManager tokenManager;
    private final Retrofit retrofit;
    private final AuthService authService;
    private FlashNoteService flashNoteService;
    private MessageService messageService;
    private CollectionService collectionService;
    private SyncService syncService;
    private FileService fileService;
    private FavoriteService favoriteService;

    public ApiClient(TokenManager tokenManager) {
        this.tokenManager = tokenManager;
        
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        loggingInterceptor.setLevel(
                BuildConfig.DEBUG 
                    ? HttpLoggingInterceptor.Level.BODY 
                    : HttpLoggingInterceptor.Level.NONE
        );

        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .addInterceptor(new AuthInterceptor(tokenManager))
                .authenticator(new TokenAuthenticator(tokenManager))
                .addInterceptor(loggingInterceptor)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        retrofit = new Retrofit.Builder()
                .baseUrl(BuildConfig.BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        authService = retrofit.create(AuthService.class);
    }

    public AuthService getAuthService() {
        return authService;
    }

    public FlashNoteService getFlashNoteService() {
        if (flashNoteService == null) {
            flashNoteService = retrofit.create(FlashNoteService.class);
        }
        return flashNoteService;
    }

    public MessageService getMessageService() {
        if (messageService == null) {
            messageService = retrofit.create(MessageService.class);
        }
        return messageService;
    }

    public CollectionService getCollectionService() {
        if (collectionService == null) {
            collectionService = retrofit.create(CollectionService.class);
        }
        return collectionService;
    }

    public SyncService getSyncService() {
        if (syncService == null) {
            syncService = retrofit.create(SyncService.class);
        }
        return syncService;
    }

    public FileService getFileService() {
        if (fileService == null) {
            fileService = retrofit.create(FileService.class);
        }
        return fileService;
    }

    public FavoriteService getFavoriteService() {
        if (favoriteService == null) {
            favoriteService = retrofit.create(FavoriteService.class);
        }
        return favoriteService;
    }

    public TokenManager getTokenManager() {
        return tokenManager;
    }
}
