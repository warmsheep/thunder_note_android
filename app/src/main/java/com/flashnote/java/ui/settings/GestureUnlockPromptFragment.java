package com.flashnote.java.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.activity.OnBackPressedCallback;
import androidx.fragment.app.Fragment;

import com.flashnote.java.FlashNoteApp;
import com.flashnote.java.R;
import com.flashnote.java.data.repository.AuthRepository;
import com.flashnote.java.databinding.FragmentGestureUnlockPromptBinding;
import com.flashnote.java.security.GestureLockManager;
import com.flashnote.java.ui.navigation.ShellNavigator;

public class GestureUnlockPromptFragment extends Fragment {
    private static final String ARG_LAUNCH_MAIN_SHELL = "launch_main_shell";
    private static final int MAX_RETRY_COUNT = 5;

    public interface UnlockInteractionListener {
        void onUnlockPatternSubmitted(@NonNull String pattern);

        void onForgotGestureRequested();
    }

    private FragmentGestureUnlockPromptBinding binding;
    private GestureLockManager gestureLockManager;
    private AuthRepository authRepository;
    private int retryCount = 0;
    private OnBackPressedCallback onBackPressedCallback;

    public static GestureUnlockPromptFragment newInstance(boolean launchMainShellAfterUnlock) {
        GestureUnlockPromptFragment fragment = new GestureUnlockPromptFragment();
        Bundle args = new Bundle();
        args.putBoolean(ARG_LAUNCH_MAIN_SHELL, launchMainShellAfterUnlock);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentGestureUnlockPromptBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        gestureLockManager = FlashNoteApp.getInstance().getGestureLockManager();
        authRepository = FlashNoteApp.getInstance().getAuthRepository();
        onBackPressedCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isAdded() && getActivity() != null) {
                    getActivity().moveTaskToBack(true);
                }
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), onBackPressedCallback);

        binding.backButton.setOnClickListener(v -> navigateBack());
        binding.usePasswordButton.setOnClickListener(v -> openLogin());
        binding.forgotGestureButton.setOnClickListener(v -> handleForgotGesture());
        binding.patternView.setOnPatternListener(new GestureLockPatternView.OnPatternListener() {
            @Override
            public void onPatternStart() {
                if (binding == null) {
                    return;
                }
                binding.patternView.setDisplayMode(GestureLockPatternView.DisplayMode.NORMAL);
            }

            @Override
            public void onPatternProgress(@NonNull String pattern) {
            }

            @Override
            public void onPatternComplete(@NonNull String pattern) {
                handlePatternSubmit(pattern);
            }

            @Override
            public void onPatternCleared() {
            }
        });
        renderRetryState();
    }

    public void markUnlockSuccess() {
        if (binding == null || getContext() == null) {
            return;
        }
        retryCount = 0;
        binding.statusText.setText(R.string.gesture_unlock_status_success);
        binding.statusText.setTextColor(ContextCompat.getColor(getContext(), R.color.primary));
        binding.patternView.setDisplayMode(GestureLockPatternView.DisplayMode.CORRECT);
        if (gestureLockManager != null) {
            gestureLockManager.markUnlockedNow();
        }
        binding.patternView.postDelayed(this::handleUnlockNavigation, 350L);
    }

    public void markUnlockFailure(@Nullable String message) {
        if (binding == null || getContext() == null) {
            return;
        }
        retryCount++;
        String displayMessage = message;
        if (displayMessage == null || displayMessage.trim().isEmpty()) {
            displayMessage = getString(R.string.gesture_unlock_status_failure);
        }

        binding.statusText.setText(displayMessage);
        binding.statusText.setTextColor(ContextCompat.getColor(getContext(), R.color.danger));
        binding.patternView.setDisplayMode(GestureLockPatternView.DisplayMode.ERROR);
        binding.patternView.postDelayed(() -> {
            if (binding == null) {
                return;
            }
            binding.patternView.clearPattern();
        }, 550L);
        renderRetryState();
    }

    private void handlePatternSubmit(@NonNull String pattern) {
        if (!isAdded() || getContext() == null) {
            return;
        }

        if (gestureLockManager != null && gestureLockManager.verifyGesture(pattern)) {
            markUnlockSuccess();
            return;
        }
        markUnlockFailure(getString(R.string.gesture_unlock_status_failure));
    }

    private void handleForgotGesture() {
        if (!isAdded() || getContext() == null) {
            return;
        }
        if (gestureLockManager != null) {
            gestureLockManager.clearGesture();
        }
        if (authRepository != null) {
            authRepository.clearGestureLockBackup(new AuthRepository.GestureLockBackupCallback() {
                @Override
                public void onSuccess() {
                }

                @Override
                public void onError(String message, int code) {
                }
            });
        }
        Toast.makeText(getContext(), getString(R.string.gesture_unlock_forgot_done), Toast.LENGTH_SHORT).show();
        openLogin();
    }

    private void renderRetryState() {
        if (binding == null || getContext() == null) {
            return;
        }
        int remaining = Math.max(0, MAX_RETRY_COUNT - retryCount);
        binding.retryHintText.setText(getString(R.string.gesture_unlock_retry_remaining, remaining));
        if (retryCount == 0) {
            binding.statusText.setText(getString(R.string.gesture_unlock_status_default));
            binding.statusText.setTextColor(ContextCompat.getColor(getContext(), R.color.text_secondary));
        }
    }

    private void openLogin() {
        if (!isAdded() || getActivity() == null) {
            return;
        }
        if (getActivity() instanceof ShellNavigator navigator) {
            navigator.logoutToLogin();
        }
    }

    private void handleUnlockNavigation() {
        if (!isAdded() || getActivity() == null) {
            return;
        }
        boolean launchMainShellAfterUnlock = getArguments() != null
                && getArguments().getBoolean(ARG_LAUNCH_MAIN_SHELL, false);
        if (getActivity() instanceof ShellNavigator navigator && launchMainShellAfterUnlock) {
            navigator.openMainShell();
            return;
        }
        getActivity().getSupportFragmentManager().popBackStack();
    }

    private void navigateBack() {
        if (!isAdded() || getActivity() == null) {
            return;
        }
        getActivity().moveTaskToBack(true);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
