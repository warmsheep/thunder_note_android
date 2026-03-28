package com.flashnote.java.security;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GestureUnlockResumeTrackerTest {

    @Test
    public void consumeResumeStateReturnsElapsedBackgroundDurationByDefault() {
        GestureUnlockResumeTracker tracker = new GestureUnlockResumeTracker();

        tracker.markBackgrounded(1_000L);
        GestureUnlockResumeTracker.ResumeState state = tracker.consumeResumeState(6_500L);

        assertFalse(state.shouldSkipUnlockCheck());
        assertEquals(5_500L, state.getBackgroundDurationMs());
    }

    @Test
    public void consumeResumeStateSkipsUnlockOnceAfterExternalFlow() {
        GestureUnlockResumeTracker tracker = new GestureUnlockResumeTracker();

        tracker.markBackgrounded(2_000L);
        tracker.suppressNextUnlockCheck();

        GestureUnlockResumeTracker.ResumeState skippedState = tracker.consumeResumeState(9_000L);
        assertTrue(skippedState.shouldSkipUnlockCheck());
        assertEquals(-1L, skippedState.getBackgroundDurationMs());

        GestureUnlockResumeTracker.ResumeState nextState = tracker.consumeResumeState(12_000L);
        assertFalse(nextState.shouldSkipUnlockCheck());
        assertEquals(-1L, nextState.getBackgroundDurationMs());
    }

    @Test
    public void consumeResumeStateSkipsEachRegisteredExternalFlowOnce() {
        GestureUnlockResumeTracker tracker = new GestureUnlockResumeTracker();

        tracker.markBackgrounded(1_000L);
        tracker.suppressNextUnlockCheck();
        tracker.suppressNextUnlockCheck();

        GestureUnlockResumeTracker.ResumeState firstState = tracker.consumeResumeState(4_000L);
        assertTrue(firstState.shouldSkipUnlockCheck());
        assertEquals(-1L, firstState.getBackgroundDurationMs());

        tracker.markBackgrounded(5_000L);
        GestureUnlockResumeTracker.ResumeState secondState = tracker.consumeResumeState(8_000L);
        assertTrue(secondState.shouldSkipUnlockCheck());
        assertEquals(-1L, secondState.getBackgroundDurationMs());

        tracker.markBackgrounded(10_000L);
        GestureUnlockResumeTracker.ResumeState thirdState = tracker.consumeResumeState(12_500L);
        assertFalse(thirdState.shouldSkipUnlockCheck());
        assertEquals(2_500L, thirdState.getBackgroundDurationMs());
    }
}
