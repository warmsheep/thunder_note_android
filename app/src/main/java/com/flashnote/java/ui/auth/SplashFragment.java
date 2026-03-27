package com.flashnote.java.ui.auth;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.flashnote.java.DebugLog;
import com.flashnote.java.FlashNoteApp;
import com.flashnote.java.TokenManager;
import com.flashnote.java.databinding.FragmentSplashBinding;
import com.flashnote.java.security.GestureLockManager;
import com.flashnote.java.ui.navigation.ShellNavigator;

public class SplashFragment extends Fragment {

    private static final long SPLASH_DELAY_MS = 1200L;

    private FragmentSplashBinding binding;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable navigateTask;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSplashBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        try {
            String versionName = requireContext().getPackageManager()
                .getPackageInfo(requireContext().getPackageName(), 0).versionName;
            if (binding.versionText != null) {
                binding.versionText.setText(getString(com.flashnote.java.R.string.splash_version_prefix, versionName));
            }
        } catch (Exception exception) {
            DebugLog.w("SplashFragment", "Failed to resolve version name");
        }
        
        navigateTask = this::navigateNext;
        handler.postDelayed(navigateTask, SPLASH_DELAY_MS);
    }

    private void navigateNext() {
        if (!isAdded() || getActivity() == null || getActivity().isFinishing()) {
            return;
        }
        TokenManager tokenManager = FlashNoteApp.getInstance().getTokenManager();
        GestureLockManager gestureLockManager = FlashNoteApp.getInstance().getGestureLockManager();
        ShellNavigator navigator = getNavigator();
        if (navigator == null) {
            return;
        }
        boolean hasValidToken = tokenManager != null && tokenManager.isTokenValid();
        if (hasValidToken) {
            if (gestureLockManager != null && gestureLockManager.isGestureEnabled()) {
                navigator.openGestureUnlockPrompt();
            } else {
                navigator.openMainShell();
            }
        } else {
            navigator.openLogin();
        }
    }

    @Nullable
    private ShellNavigator getNavigator() {
        if (getActivity() instanceof ShellNavigator navigator) {
            return navigator;
        }
        return null;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (navigateTask != null) {
            handler.removeCallbacks(navigateTask);
        }
        binding = null;
    }
}
