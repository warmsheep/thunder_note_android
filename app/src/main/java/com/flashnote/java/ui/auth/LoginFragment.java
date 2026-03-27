package com.flashnote.java.ui.auth;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.flashnote.java.FlashNoteApp;
import com.flashnote.java.R;
import com.flashnote.java.TokenManager;
import com.flashnote.java.data.remote.ServerConfigStore;
import com.flashnote.java.data.model.LoginResponse;
import com.flashnote.java.databinding.FragmentLoginBinding;
import com.flashnote.java.ui.navigation.ShellNavigator;

public class LoginFragment extends Fragment {

    private FragmentLoginBinding binding;
    private AuthViewModel authViewModel;
    private TokenManager tokenManager;
    private ServerConfigStore serverConfigStore;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentLoginBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        FlashNoteApp app = FlashNoteApp.getInstance();
        tokenManager = app.getTokenManager();
        serverConfigStore = app.getServerConfigStore();
        if (getActivity() != null) {
            authViewModel = new ViewModelProvider(getActivity()).get(AuthViewModel.class);
        }

        binding.goRegisterText.setOnClickListener(v -> {
            ShellNavigator navigator = getNavigator();
            if (navigator != null) {
                navigator.openRegister();
            }
        });
        binding.serverText.setOnClickListener(v -> {
            ShellNavigator navigator = getNavigator();
            if (navigator != null) {
                navigator.openServerSettings();
            }
        });
        binding.loginButton.setOnClickListener(v -> attemptLogin());
        updateServerLabel();

        if (authViewModel != null) {
            authViewModel.getAuthState().observe(getViewLifecycleOwner(), state -> {
                if (state == null || binding == null) {
                    return;
                }
                switch (state) {
                    case LOADING:
                        setLoading(true);
                        break;
                    case SUCCESS:
                        setLoading(false);
                        break;
                    case ERROR:
                    case IDLE:
                    default:
                        setLoading(false);
                        break;
                }
            });

            authViewModel.getErrorMessage().observe(getViewLifecycleOwner(), this::handleErrorFromViewModel);
            authViewModel.getLoginResponse().observe(getViewLifecycleOwner(), this::handleLoginResponse);
        }
    }

    private void attemptLogin() {
        clearError();
        final String username = binding.usernameInput.getText() == null
                ? ""
                : binding.usernameInput.getText().toString().trim();
        final String password = binding.passwordInput.getText() == null
                ? ""
                : binding.passwordInput.getText().toString();

        if (TextUtils.isEmpty(username)) {
            showError(getString(R.string.error_username_required));
            return;
        }
        if (TextUtils.isEmpty(password)) {
            showError(getString(R.string.error_password_required));
            return;
        }

        authViewModel.clearError();
        authViewModel.login(username, password);
    }

    private void handleErrorFromViewModel(@Nullable String message) {
        if (message == null || message.trim().isEmpty()) {
            return;
        }
        if (message.startsWith("Network error:")) {
            String raw = message.replace("Network error:", "").trim();
            showError(getString(R.string.error_network_prefix, raw));
            return;
        }
        if (message.startsWith("Login failed:")) {
            showError(getString(R.string.error_login_default));
            return;
        }
        if ("Username cannot be empty".equals(message)) {
            showError(getString(R.string.error_username_required));
            return;
        }
        if ("Password cannot be empty".equals(message)) {
            showError(getString(R.string.error_password_required));
            return;
        }
        showError(message);
    }

    private void handleLoginResponse(@Nullable LoginResponse response) {
        if (response == null || tokenManager == null || !isAdded()) {
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

        ShellNavigator navigator = getNavigator();
        if (navigator != null) {
            navigator.openMainShell();
        }
    }

    private void setLoading(boolean loading) {
        binding.loginButton.setEnabled(!loading);
        binding.loginButton.setText(loading
                ? getString(R.string.action_login_loading)
                : getString(R.string.action_login));
        binding.goRegisterText.setEnabled(!loading);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateServerLabel();
    }

    private void updateServerLabel() {
        if (binding == null || serverConfigStore == null) {
            return;
        }
        binding.serverText.setText(serverConfigStore.getDisplayLabel() + "  ›");
    }

    private void showError(@NonNull String msg) {
        binding.errorText.setVisibility(View.VISIBLE);
        binding.errorText.setText(msg);
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
