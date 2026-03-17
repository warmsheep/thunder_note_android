package com.flashnote.java.ui.settings;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.flashnote.java.FlashNoteApp;
import com.flashnote.java.R;
import com.flashnote.java.databinding.FragmentSettingsBinding;
import com.flashnote.java.ui.auth.AuthViewModel;
import com.flashnote.java.ui.navigation.ShellNavigator;
import androidx.lifecycle.ViewModelProvider;

public class SettingsFragment extends Fragment {
    private FragmentSettingsBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        binding.backButton.setOnClickListener(v -> navigateBack());
        
        binding.aboutItem.setOnClickListener(v -> openAbout());
        binding.websiteItem.setOnClickListener(v -> openWebsite());
        binding.privacyItem.setOnClickListener(v -> showComingSoon("隐私政策"));
        binding.creditsItem.setOnClickListener(v -> showCredits());
        binding.feedbackItem.setOnClickListener(v -> showComingSoon("反馈BUG"));
        binding.logoutItem.setOnClickListener(v -> logout());
    }

    private void openAbout() {
        if (!isAdded() || getActivity() == null) {
            return;
        }
        getActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.rootFragmentContainer, new AboutFragment())
                .addToBackStack(null)
                .commit();
    }

    private void openWebsite() {
        if (!isAdded() || getContext() == null) {
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://flashnote.app"));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(getContext(), "无法打开浏览器", Toast.LENGTH_SHORT).show();
        }
    }

    private void showComingSoon(String feature) {
        if (!isAdded() || getContext() == null) {
            return;
        }
        Toast.makeText(getContext(), feature + "暂未上线", Toast.LENGTH_SHORT).show();
    }

    private void showCredits() {
        if (!isAdded() || getContext() == null) {
            return;
        }
        Toast.makeText(getContext(), "感谢开源社区", Toast.LENGTH_SHORT).show();
    }

    private void logout() {
        if (!isAdded() || getActivity() == null) {
            return;
        }
        
        AuthViewModel authViewModel = new ViewModelProvider(getActivity()).get(AuthViewModel.class);
        authViewModel.logout();
        
        if (getActivity() instanceof ShellNavigator navigator) {
            navigator.logoutToLogin();
        }
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
