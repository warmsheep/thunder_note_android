package com.flashnote.java.ui.main;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.flashnote.java.databinding.FragmentTabPlaceholderBinding;

public abstract class BaseTabPlaceholderFragment extends Fragment {
    private FragmentTabPlaceholderBinding binding;

    protected abstract int getTitleResId();

    protected abstract int getSubtitleResId();

    protected abstract String getEmoji();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentTabPlaceholderBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding.emojiText.setText(getEmoji());
        binding.titleText.setText(getTitleResId());
        binding.subtitleText.setText(getSubtitleResId());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
