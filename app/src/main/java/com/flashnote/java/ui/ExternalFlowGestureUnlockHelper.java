package com.flashnote.java.ui;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.flashnote.java.ui.navigation.ShellNavigator;

public final class ExternalFlowGestureUnlockHelper {
    private ExternalFlowGestureUnlockHelper() {
    }

    public static void registerExternalFlow(@NonNull Fragment fragment) {
        if (fragment.getActivity() instanceof ShellNavigator navigator) {
            navigator.registerExternalFlowForGestureUnlockSkip();
        }
    }
}
