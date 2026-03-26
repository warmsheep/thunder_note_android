package com.flashnote.java;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class TokenManagerTest {

    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
    }

    @Test
    public void constructor_fallsBackToPlainSharedPreferencesWhenSecureStorageInitFails() {
        SharedPreferences fallbackPrefs = context.getSharedPreferences("token-manager-test-fallback", Context.MODE_PRIVATE);
        fallbackPrefs.edit().clear().commit();

        TokenManager tokenManager = new TokenManager(context, ctx -> {
            throw new RuntimeException("boom");
        }, fallbackPrefs);

        tokenManager.saveTokens("access", "refresh", 60L);

        assertFalse(tokenManager.isUsingEncryptedStorage());
        assertEquals("access", tokenManager.getAccessToken());
        assertEquals("refresh", tokenManager.getRefreshToken());
        assertTrue(tokenManager.isTokenValid());
    }
}
