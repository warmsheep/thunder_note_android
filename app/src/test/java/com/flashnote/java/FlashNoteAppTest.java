package com.flashnote.java;

import static org.junit.Assert.assertNotNull;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, application = FlashNoteApp.class)
public class FlashNoteAppTest {

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("flashnote_fallback_prefs", Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences("flashnote_secure_prefs", Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences("flashnote_user_cache", Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences("flashnote_app", Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test
    public void onCreate_withoutLoggedInUser_doesNotCrash() {
        FlashNoteApp application = (FlashNoteApp) RuntimeEnvironment.getApplication();

        assertNotNull(application.getTokenManager());
        assertNotNull(application.getAuthRepository());
        assertNotNull(application.getMessageRepository());
        assertNotNull(application.getUserRepository());
    }

    @Test
    public void reloadSessionScopedDependencies_afterSavingUserId_initializesLoggedInRepositories() {
        FlashNoteApp application = (FlashNoteApp) RuntimeEnvironment.getApplication();

        application.getTokenManager().saveUserId(1001L);

        application.reloadSessionScopedDependencies();

        assertNotNull(application.getFlashNoteRepository());
        assertNotNull(application.getCollectionRepository());
        assertNotNull(application.getFavoriteRepository());
        assertNotNull(application.getSyncRepository());
    }
}
