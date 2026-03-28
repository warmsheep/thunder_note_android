package com.flashnote.java.ui.settings;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.flashnote.java.databinding.FragmentAboutBinding;
import com.flashnote.java.ui.FragmentUiSafe;

public class AboutFragment extends Fragment {
    private FragmentAboutBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAboutBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.backButton.setOnClickListener(v -> FragmentUiSafe.navigateBack(this));
        bindVersionText();
    }

    private void bindVersionText() {
        if (!isAdded()) {
            return;
        }
        try {
            String versionName = requireContext().getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0).versionName;
            binding.versionText.setText(getString(com.flashnote.java.R.string.about_version, versionName));
        } catch (PackageManager.NameNotFoundException ignored) {
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
