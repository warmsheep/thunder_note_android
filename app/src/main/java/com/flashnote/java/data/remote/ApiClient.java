package com.flashnote.java.data.remote;

import com.flashnote.java.BuildConfig;
import com.flashnote.java.DebugLog;
import com.flashnote.java.TokenManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {
    private final TokenManager tokenManager;
    private final OkHttpClient okHttpClient;
    private final Retrofit retrofit;
    private final AuthService authService;
    private FlashNoteService flashNoteService;
    private MessageService messageService;
    private CollectionService collectionService;
    private SyncService syncService;
    private FileService fileService;
    private FavoriteService favoriteService;
    private UserService userService;

    public ApiClient(TokenManager tokenManager) {
        this.tokenManager = tokenManager;

        HttpLoggingInterceptor loggingInterceptor = createLoggingInterceptor(BuildConfig.DEBUG);

        okHttpClient = new OkHttpClient.Builder()
                .addInterceptor(new AuthInterceptor(tokenManager))
                .authenticator(new TokenAuthenticator(tokenManager))
                .addInterceptor(loggingInterceptor)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .callTimeout(180, TimeUnit.SECONDS)
                .build();

        Gson gson = new GsonBuilder()
                .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
                .create();

        retrofit = new Retrofit.Builder()
                .baseUrl(BuildConfig.BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create(gson))
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

    public UserService getUserService() {
        if (userService == null) {
            userService = retrofit.create(UserService.class);
        }
        return userService;
    }

    public TokenManager getTokenManager() {
        return tokenManager;
    }

    public OkHttpClient getOkHttpClient() {
        return okHttpClient;
    }

    static HttpLoggingInterceptor createLoggingInterceptor(boolean debug) {
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        loggingInterceptor.redactHeader("Authorization");
        loggingInterceptor.setLevel(debug
                ? HttpLoggingInterceptor.Level.BASIC
                : HttpLoggingInterceptor.Level.NONE);
        return loggingInterceptor;
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
            } catch (Exception e) {
                DebugLog.w("ApiClient", "Failed to parse LocalDateTime: " + raw);
                return null;
            }
        }
    }
}
