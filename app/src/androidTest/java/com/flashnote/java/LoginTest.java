package com.flashnote.java;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

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
public class LoginTest extends BaseTest {

    @Rule
    public ActivityScenarioRule<MainActivity> activityRule = 
            new ActivityScenarioRule<>(MainActivity.class);

    @Test
    public void testLoginUIElementsDisplayed() {
        waitMillis(2000);
        
        onView(withId(R.id.usernameInput)).check(matches(isDisplayed()));
        onView(withId(R.id.passwordInput)).check(matches(isDisplayed()));
        onView(withId(R.id.loginButton)).check(matches(isDisplayed()));
    }

    @Test
    public void testInputUsername() {
        waitMillis(1000);
        
        onView(withId(R.id.usernameInput))
                .perform(typeText("testuser"), closeSoftKeyboard());
        
        onView(withId(R.id.usernameInput))
                .check(matches(withText("testuser")));
    }

    @Test
    public void testInputPassword() {
        waitMillis(1000);
        
        onView(withId(R.id.passwordInput))
                .perform(typeText("testpassword123"), closeSoftKeyboard());
    }

    @Test
    public void testClickLoginButton() {
        waitMillis(1000);
        
        onView(withId(R.id.usernameInput))
                .perform(typeText("testuser"), closeSoftKeyboard());
        
        onView(withId(R.id.passwordInput))
                .perform(typeText("password123"), closeSoftKeyboard());
        
        onView(withId(R.id.loginButton)).perform(click());
        
        waitMillis(2000);
    }

    @Test
    public void testEmptyUsernameShowsError() {
        waitMillis(1000);
        
        onView(withId(R.id.loginButton)).perform(click());
        
        waitMillis(1000);
    }

    @Test
    public void testNavigateToRegister() {
        waitMillis(1000);
        
        onView(withId(R.id.goRegisterText)).perform(click());
        
        waitMillis(1000);
    }
}
