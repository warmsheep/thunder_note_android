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

import com.flashnote.java.DebugLog;
import com.flashnote.java.R;
import com.flashnote.java.data.model.FavoriteItem;
import com.flashnote.java.data.repository.FavoriteRepository;
import com.flashnote.java.databinding.FragmentFavoriteTabBinding;
import com.flashnote.java.ui.FragmentUiSafe;
import com.flashnote.java.ui.navigation.ShellNavigator;

import java.util.ArrayList;
import java.util.List;

public class FavoriteTabFragment extends Fragment {
    private FragmentFavoriteTabBinding binding;
    private List<FavoriteItem> latestFavorites = new ArrayList<>();

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
                    android.content.Context context = getContext();
                    if (context != null) {
                        Toast.makeText(context, "该收藏未关联闪记", Toast.LENGTH_SHORT).show();
                    }
                    return;
                }
                if (getActivity() instanceof ShellNavigator navigator) {
                    String title;
                    if (item.getFlashNoteId() == -1L) {
                        title = "收集箱";
                    } else {
                        title = item.getFlashNoteTitle() == null ? "已收藏消息" : item.getFlashNoteTitle();
                    }
                    navigator.openChat(item.getFlashNoteId(), title, item.getMessageId());
                }
            }

            @Override
            public void onRemove(FavoriteItem item) {
                viewModel.removeFavorite(item.getMessageId(), new FavoriteRepository.ActionCallback() {
                    @Override
                    public void onSuccess(String message) {
                        runIfUiAlive(() -> {
                            android.content.Context context = getContext();
                            if (context != null) {
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }

                    @Override
                    public void onError(String message, int code) {
                        runIfUiAlive(() -> {
                            android.content.Context context = getContext();
                            if (context != null) {
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                });
            }
        });

        binding.recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerView.setAdapter(adapter);
        android.content.Context ctx = getContext();
        if (ctx != null) {
            androidx.recyclerview.widget.DividerItemDecoration divider = new androidx.recyclerview.widget.DividerItemDecoration(
                ctx, androidx.recyclerview.widget.DividerItemDecoration.VERTICAL);
            android.graphics.drawable.ShapeDrawable dividerDrawable = new android.graphics.drawable.ShapeDrawable();
            dividerDrawable.setIntrinsicHeight((int) (0.5f * ctx.getResources().getDisplayMetrics().density));
            dividerDrawable.getPaint().setColor(ctx.getColor(R.color.border));
            divider.setDrawable(dividerDrawable);
            binding.recyclerView.addItemDecoration(divider);
        }

        viewModel.getFavorites().observe(getViewLifecycleOwner(), favorites -> {
            latestFavorites = favorites == null ? new ArrayList<>() : new ArrayList<>(favorites);
            renderFavorites(adapter);
        });

        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), error -> {
            android.content.Context context = getContext();
            if (context != null && error != null && !error.isEmpty()) {
                DebugLog.logHandledError("FavoriteTab", error);
                if (!DebugLog.isLikelyNetworkIssue(error)
                        && DebugLog.shouldShowToast("FavoriteTab:" + error, 2000L)) {
                    Toast.makeText(context, error, Toast.LENGTH_SHORT).show();
                }
                viewModel.clearError();
            }
        });
    }

    private void renderFavorites(@NonNull FavoriteAdapter adapter) {
        adapter.submitList(new ArrayList<>(latestFavorites));
        boolean empty = latestFavorites.isEmpty();
        binding.emptyContainer.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void runIfUiAlive(@NonNull Runnable action) {
        FragmentUiSafe.runIfUiAlive(this, binding, action);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
