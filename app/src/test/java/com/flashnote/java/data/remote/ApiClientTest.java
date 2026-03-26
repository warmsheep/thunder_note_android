package com.flashnote.java.data.remote;

import static org.junit.Assert.assertEquals;

import okhttp3.logging.HttpLoggingInterceptor;
import org.junit.Test;

public class ApiClientTest {

    @Test
    public void createLoggingInterceptor_debugUsesBasicLevel() {
        HttpLoggingInterceptor interceptor = ApiClient.createLoggingInterceptor(true);

        assertEquals(HttpLoggingInterceptor.Level.BASIC, interceptor.getLevel());
    }

    @Test
    public void createLoggingInterceptor_releaseDisablesLogging() {
        HttpLoggingInterceptor interceptor = ApiClient.createLoggingInterceptor(false);

        assertEquals(HttpLoggingInterceptor.Level.NONE, interceptor.getLevel());
    }
}
