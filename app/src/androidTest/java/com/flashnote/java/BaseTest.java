package com.flashnote.java;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.platform.app.InstrumentationRegistry;

import com.flashnote.java.MainActivity;

import org.junit.Before;
import org.junit.Rule;

public abstract class BaseTest {

    protected Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
    }

    protected void waitMillis(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    protected String getString(int resId) {
        return context.getString(resId);
    }
}
