package com.flashnote.java.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.OnBackPressedCallback;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.flashnote.java.FlashNoteApp;
import com.flashnote.java.TokenManager;
import com.flashnote.java.R;
import com.flashnote.java.data.model.UserProfile;
import com.flashnote.java.data.repository.AuthRepository;
import com.flashnote.java.data.repository.UserRepository;
import com.flashnote.java.databinding.FragmentGestureUnlockPromptBinding;
import com.flashnote.java.security.GestureLockManager;
import com.flashnote.java.ui.media.MediaUrlResolver;
import com.flashnote.java.ui.FragmentUiSafe;
import com.flashnote.java.ui.navigation.ShellNavigator;

import java.io.File;

public class GestureUnlockPromptFragment extends Fragment {
    private static final String ARG_LAUNCH_MAIN_SHELL = "launch_main_shell";

    public interface UnlockInteractionListener {
        void onUnlockPatternSubmitted(@NonNull String pattern);

        void onForgotGestureRequested();
    }

    private FragmentGestureUnlockPromptBinding binding;
    private GestureLockManager gestureLockManager;
    private AuthRepository authRepository;
    private UserRepository userRepository;
    private TokenManager tokenManager;
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
        FlashNoteApp app = FlashNoteApp.getInstance();
        gestureLockManager = app.getGestureLockManager();
        authRepository = app.getAuthRepository();
        userRepository = app.getUserRepository();
        tokenManager = app.getTokenManager();
        onBackPressedCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isAdded() && getActivity() != null) {
                    getActivity().moveTaskToBack(true);
                }
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), onBackPressedCallback);

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
        bindCurrentUser();
    }

    public void markUnlockSuccess() {
        if (binding == null || getContext() == null) {
            return;
        }
        retryCount = 0;
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
        binding.patternView.setDisplayMode(GestureLockPatternView.DisplayMode.ERROR);
        binding.patternView.postDelayed(() -> {
            if (binding == null) {
                return;
            }
            binding.patternView.clearPattern();
        }, 550L);
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

    private void bindCurrentUser() {
        if (binding == null || getContext() == null) {
            return;
        }
        String username = tokenManager == null ? null : tokenManager.getUsername();
        applyUserName(null, username);
        applyUserAvatar(null, username);

        if (userRepository != null) {
            userRepository.getProfile().observe(getViewLifecycleOwner(), profile -> {
                if (profile == null || binding == null) {
                    return;
                }
                applyUserName(profile, username);
                applyUserAvatar(profile, username);
            });
            userRepository.fetchProfile(null);
        }
    }

    private void applyUserName(@Nullable UserProfile profile, @Nullable String username) {
        if (binding == null) {
            return;
        }
        String nickname = profile == null ? null : profile.getNickname();
        if (nickname != null && !nickname.trim().isEmpty()) {
            binding.unlockUserNameText.setText(nickname.trim());
            return;
        }
        if (username != null && !username.trim().isEmpty()) {
            binding.unlockUserNameText.setText(username.trim());
            return;
        }
        binding.unlockUserNameText.setText(R.string.status_unknown_user);
    }

    private void applyUserAvatar(@Nullable UserProfile profile, @Nullable String username) {
        if (binding == null || getContext() == null) {
            return;
        }
        String avatar = profile == null ? null : profile.getAvatar();
        if (avatar != null && !avatar.isEmpty()) {
            if (avatar.startsWith("http") || avatar.contains("/")) {
                binding.unlockAvatarText.setVisibility(View.GONE);
                binding.unlockAvatarImage.setVisibility(View.VISIBLE);
                Glide.with(this)
                        .load(MediaUrlResolver.resolve(avatar))
                        .placeholder(R.drawable.bg_avatar_circle)
                        .error(R.drawable.bg_avatar_circle)
                        .circleCrop()
                        .into(binding.unlockAvatarImage);
                return;
            }
            binding.unlockAvatarImage.setVisibility(View.GONE);
            binding.unlockAvatarText.setVisibility(View.VISIBLE);
            binding.unlockAvatarText.setText(avatar);
            return;
        }

        File avatarFile = new File(getContext().getFilesDir(), "avatar.jpg");
        if (avatarFile.exists()) {
            binding.unlockAvatarText.setVisibility(View.GONE);
            binding.unlockAvatarImage.setVisibility(View.VISIBLE);
            Glide.with(this)
                    .load(avatarFile)
                    .circleCrop()
                    .into(binding.unlockAvatarImage);
            return;
        }

        binding.unlockAvatarImage.setVisibility(View.GONE);
        binding.unlockAvatarText.setVisibility(View.VISIBLE);
        if (username != null && !username.isEmpty()) {
            binding.unlockAvatarText.setText(String.valueOf(username.charAt(0)));
        } else {
            binding.unlockAvatarText.setText("?");
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
        FragmentUiSafe.navigateBack(this);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
