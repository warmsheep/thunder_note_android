package com.flashnote.java.ui.auth;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.flashnote.java.FlashNoteApp;
import com.flashnote.java.R;
import com.flashnote.java.TokenManager;
import com.flashnote.java.data.model.LoginResponse;
import com.flashnote.java.data.repository.AuthRepository;
import com.flashnote.java.databinding.FragmentRegisterBinding;
import com.flashnote.java.ui.navigation.ShellNavigator;

public class RegisterFragment extends Fragment {
    private FragmentRegisterBinding binding;
    private AuthRepository authRepository;
    private TokenManager tokenManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentRegisterBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        FlashNoteApp app = FlashNoteApp.getInstance();
        authRepository = app.getAuthRepository();
        tokenManager = app.getTokenManager();

        binding.registerButton.setOnClickListener(v -> submitRegister());
        binding.goLoginText.setOnClickListener(v -> {
            ShellNavigator navigator = getNavigator();
            if (navigator != null) {
                navigator.openLogin();
            }
        });
    }

    private void submitRegister() {
        clearError();
        String username = getTrimmed(binding.usernameInput.getText());
        String email = getTrimmed(binding.emailInput.getText());
        String password = getTrimmed(binding.passwordInput.getText());

        if (TextUtils.isEmpty(username)) {
            renderError(getString(R.string.error_username_required));
            return;
        }
        if (TextUtils.isEmpty(email)) {
            renderError(getString(R.string.error_email_required));
            return;
        }
        if (TextUtils.isEmpty(password)) {
            renderError(getString(R.string.error_password_required));
            return;
        }

        setLoading(true);
        authRepository.register(username, email, password, new AuthRepository.SimpleCallback() {
            @Override
            public void onSuccess() {
                if (!isAdded()) {
                    return;
                }
                authRepository.login(username, password, new AuthRepository.AuthCallback() {
                    @Override
                    public void onSuccess(LoginResponse response) {
                        if (!isAdded() || getActivity() == null) {
                            return;
                        }
                        getActivity().runOnUiThread(() -> {
                            if (!isAdded()) {
                                return;
                            }
                            setLoading(false);
                            persistToken(response);
                            FlashNoteApp.getInstance().reloadSessionScopedDependencies();
                            Toast.makeText(requireContext(), "注册成功", Toast.LENGTH_SHORT).show();
                            ShellNavigator navigator = getNavigator();
                            if (navigator != null) {
                                navigator.openMainShell();
                            }
                        });
                    }

                    @Override
                    public void onError(String message, int code) {
                        if (!isAdded() || getActivity() == null) {
                            return;
                        }
                        getActivity().runOnUiThread(() -> {
                            if (!isAdded()) {
                                return;
                            }
                            setLoading(false);
                            renderError(getString(R.string.error_login_default));
                        });
                    }
                });
            }

            @Override
            public void onError(String message, int code) {
                if (!isAdded() || getActivity() == null) {
                    return;
                }
                getActivity().runOnUiThread(() -> {
                    if (!isAdded()) {
                        return;
                    }
                    setLoading(false);
                    if (message == null || message.trim().isEmpty()) {
                        renderError(getString(R.string.error_register_unavailable));
                    } else {
                        renderError(message);
                    }
                });
            }
        });
    }

    private void persistToken(@Nullable LoginResponse response) {
        if (response == null || tokenManager == null) {
            return;
        }
        String accessToken = response.getToken();
        if (TextUtils.isEmpty(accessToken)) {
            return;
        }
        tokenManager.saveTokens(
                accessToken,
                response.getRefreshToken(),
                response.getExpiresIn()
        );
    }

    private void setLoading(boolean loading) {
        binding.registerButton.setEnabled(!loading);
        binding.registerButton.setText(loading
                ? getString(R.string.action_register_loading)
                : getString(R.string.action_register));
        binding.goLoginText.setEnabled(!loading);
    }

    private void renderError(String message) {
        binding.errorText.setVisibility(View.VISIBLE);
        binding.errorText.setText(message);
    }

    private void clearError() {
        binding.errorText.setVisibility(View.GONE);
        binding.errorText.setText("");
    }

    @Nullable
    private ShellNavigator getNavigator() {
        if (getActivity() instanceof ShellNavigator navigator) {
            return navigator;
        }
        return null;
    }

    @NonNull
    private String getTrimmed(@Nullable CharSequence text) {
        return text == null ? "" : text.toString().trim();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
