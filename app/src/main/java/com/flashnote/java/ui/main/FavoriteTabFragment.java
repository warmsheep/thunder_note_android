package com.flashnote.java.ui.main;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.flashnote.java.data.model.FavoriteItem;
import com.flashnote.java.data.repository.FavoriteRepository;
import com.flashnote.java.databinding.FragmentFavoriteTabBinding;
import com.flashnote.java.ui.navigation.ShellNavigator;

public class FavoriteTabFragment extends Fragment {
    private FragmentFavoriteTabBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentFavoriteTabBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        FavoriteViewModel viewModel = new ViewModelProvider(this).get(FavoriteViewModel.class);
        FavoriteAdapter adapter = new FavoriteAdapter(new FavoriteAdapter.OnFavoriteActionListener() {
            @Override
            public void onOpen(FavoriteItem item) {
                if (item.getFlashNoteId() == null) {
                    Toast.makeText(requireContext(), "该收藏未关联闪记", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (requireActivity() instanceof ShellNavigator navigator) {
                    String title = item.getFlashNoteTitle() == null ? "已收藏消息" : item.getFlashNoteTitle();
                    navigator.openChat(item.getFlashNoteId(), title);
                }
            }

            @Override
            public void onRemove(FavoriteItem item) {
                viewModel.removeFavorite(item.getMessageId(), new FavoriteRepository.ActionCallback() {
                    @Override
                    public void onSuccess(String message) {
                        if (!isAdded()) {
                            return;
                        }
                        requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show());
                    }

                    @Override
                    public void onError(String message, int code) {
                        if (!isAdded()) {
                            return;
                        }
                        requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show());
                    }
                });
            }
        });

        binding.recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerView.setAdapter(adapter);

        viewModel.getFavorites().observe(getViewLifecycleOwner(), favorites -> {
            adapter.submitList(favorites);
            boolean empty = favorites == null || favorites.isEmpty();
            binding.emptyContainer.setVisibility(empty ? View.VISIBLE : View.GONE);
            binding.recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
        });

        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
