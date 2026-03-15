package com.flashnote.java;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import com.flashnote.java.MainActivity;
import com.flashnote.java.R;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class FlashNoteTest extends BaseTest {

    @Rule
    public ActivityScenarioRule<MainActivity> activityRule = 
            new ActivityScenarioRule<>(MainActivity.class);

    @Test
    public void testFlashNoteTabDisplayed() {
        waitMillis(2000);
        
        onView(withId(R.id.addButton)).check(matches(isDisplayed()));
    }

    @Test
    public void testClickAddButton() {
        waitMillis(1000);
        
        onView(withId(R.id.addButton)).perform(click());
        
        waitMillis(1000);
    }

    @Test
    public void testFabAddDisplayed() {
        waitMillis(1000);
        
        onView(withId(R.id.fabAdd)).check(matches(isDisplayed()));
    }
}
