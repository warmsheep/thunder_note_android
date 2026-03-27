package com.flashnote.java.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.flashnote.java.FlashNoteApp;
import com.flashnote.java.R;
import com.flashnote.java.data.repository.AuthRepository;
import com.flashnote.java.databinding.FragmentGestureLockSetupBinding;
import com.flashnote.java.security.GestureLockManager;
import com.flashnote.java.ui.navigation.ShellNavigator;

public class GestureLockSetupFragment extends Fragment {
    private static final int MIN_PATTERN_LENGTH = 4;

    public interface SetupInteractionListener {
        void onGesturePatternConfirmed(@NonNull String pattern);
    }

    private enum SetupStep {
        FIRST_INPUT,
        CONFIRM_INPUT,
        COMPLETED
    }

    private FragmentGestureLockSetupBinding binding;
    private SetupStep setupStep = SetupStep.FIRST_INPUT;
    private String firstPattern = "";
    private String confirmedPattern = "";
    private GestureLockManager gestureLockManager;
    private AuthRepository authRepository;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentGestureLockSetupBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        gestureLockManager = FlashNoteApp.getInstance().getGestureLockManager();
        authRepository = FlashNoteApp.getInstance().getAuthRepository();
        binding.backButton.setOnClickListener(v -> navigateBack());
        binding.resetButton.setOnClickListener(v -> resetSetupFlow());
        binding.saveButton.setOnClickListener(v -> submitSetupResult());
        binding.patternView.setOnPatternListener(new GestureLockPatternView.OnPatternListener() {
            @Override
            public void onPatternStart() {
                if (binding == null) {
                    return;
                }
                binding.statusText.setVisibility(View.GONE);
                binding.patternView.setDisplayMode(GestureLockPatternView.DisplayMode.NORMAL);
            }

            @Override
            public void onPatternProgress(@NonNull String pattern) {
            }

            @Override
            public void onPatternComplete(@NonNull String pattern) {
                handlePatternComplete(pattern);
            }

            @Override
            public void onPatternCleared() {
            }
        });
        renderStep();
    }

    private void handlePatternComplete(@NonNull String pattern) {
        if (binding == null) {
            return;
        }
        if (pattern.length() < MIN_PATTERN_LENGTH) {
            showStatus(getString(R.string.gesture_lock_error_pattern_too_short, MIN_PATTERN_LENGTH), true);
            binding.patternView.setDisplayMode(GestureLockPatternView.DisplayMode.ERROR);
            binding.patternView.postDelayed(() -> {
                if (binding == null) {
                    return;
                }
                binding.patternView.clearPattern();
            }, 500L);
            return;
        }

        if (setupStep == SetupStep.FIRST_INPUT) {
            firstPattern = pattern;
            setupStep = SetupStep.CONFIRM_INPUT;
            showStatus(getString(R.string.gesture_lock_setup_step_confirm_hint), false);
            renderStep();
            binding.patternView.clearPattern();
            return;
        }

        if (setupStep == SetupStep.CONFIRM_INPUT) {
            if (!firstPattern.equals(pattern)) {
                showStatus(getString(R.string.gesture_lock_error_pattern_not_match), true);
                binding.patternView.setDisplayMode(GestureLockPatternView.DisplayMode.ERROR);
                binding.patternView.postDelayed(() -> {
                    if (binding == null) {
                        return;
                    }
                    binding.patternView.clearPattern();
                }, 600L);
                return;
            }

            confirmedPattern = pattern;
            setupStep = SetupStep.COMPLETED;
            binding.patternView.setDisplayMode(GestureLockPatternView.DisplayMode.CORRECT);
            showStatus(getString(R.string.gesture_lock_setup_ready_to_save), false);
            renderStep();
        }
    }

    private void renderStep() {
        if (binding == null) {
            return;
        }

        boolean canSave = setupStep == SetupStep.COMPLETED;
        binding.saveButton.setEnabled(canSave);

        if (setupStep == SetupStep.FIRST_INPUT) {
            binding.stepTitleText.setText(R.string.gesture_lock_setup_step_first_title);
            binding.stepHintText.setText(R.string.gesture_lock_setup_step_first_hint);
            return;
        }

        if (setupStep == SetupStep.CONFIRM_INPUT) {
            binding.stepTitleText.setText(R.string.gesture_lock_setup_step_confirm_title);
            binding.stepHintText.setText(R.string.gesture_lock_setup_step_confirm_hint);
            return;
        }

        binding.stepTitleText.setText(R.string.gesture_lock_setup_step_complete_title);
        binding.stepHintText.setText(R.string.gesture_lock_setup_step_complete_hint);
    }

    private void submitSetupResult() {
        if (binding == null || !isAdded() || getContext() == null) {
            return;
        }
        if (setupStep != SetupStep.COMPLETED || confirmedPattern.isEmpty()) {
            Toast.makeText(getContext(), getString(R.string.gesture_lock_setup_not_ready), Toast.LENGTH_SHORT).show();
            return;
        }

        if (gestureLockManager == null || authRepository == null) {
            Toast.makeText(getContext(), getString(R.string.gesture_lock_setup_save_failed), Toast.LENGTH_SHORT).show();
            return;
        }

        GestureLockManager.GestureBackupMaterial backupMaterial = gestureLockManager.saveGesture(confirmedPattern);
        authRepository.saveGestureLockBackup(
                backupMaterial.getCiphertext(),
                backupMaterial.getNonce(),
                backupMaterial.getKdfParams(),
                backupMaterial.getVersion(),
                new AuthRepository.GestureLockBackupCallback() {
                    @Override
                    public void onSuccess() {
                        if (!isAdded() || getActivity() == null) {
                            return;
                        }
                        getActivity().runOnUiThread(() -> {
                            if (!isAdded() || getContext() == null) {
                                return;
                            }
                            Toast.makeText(getContext(), getString(R.string.gesture_lock_setup_saved), Toast.LENGTH_SHORT).show();
                            navigateBack();
                        });
                    }

                    @Override
                    public void onError(String message, int code) {
                        if (!isAdded() || getActivity() == null) {
                            return;
                        }
                        getActivity().runOnUiThread(() -> showStatus(
                                message == null || message.isBlank()
                                        ? getString(R.string.gesture_lock_setup_save_failed)
                                        : message,
                                true
                        ));
                    }
                }
        );
    }

    private void resetSetupFlow() {
        if (binding == null) {
            return;
        }
        firstPattern = "";
        confirmedPattern = "";
        setupStep = SetupStep.FIRST_INPUT;
        binding.patternView.clearPattern();
        binding.patternView.setDisplayMode(GestureLockPatternView.DisplayMode.NORMAL);
        binding.statusText.setVisibility(View.GONE);
        renderStep();
    }

    private void showStatus(@NonNull String message, boolean error) {
        if (binding == null || getContext() == null) {
            return;
        }
        binding.statusText.setText(message);
        binding.statusText.setTextColor(ContextCompat.getColor(getContext(), error ? R.color.danger : R.color.text_secondary));
        binding.statusText.setVisibility(View.VISIBLE);
    }

    private void navigateBack() {
        if (!isAdded() || getActivity() == null) {
            return;
        }
        getActivity().getSupportFragmentManager().popBackStack();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
