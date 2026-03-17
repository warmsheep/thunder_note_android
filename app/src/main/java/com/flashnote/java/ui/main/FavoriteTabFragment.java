package com.flashnote.java.ui.main;

import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.flashnote.java.R;
import com.flashnote.java.data.model.FavoriteItem;
import com.flashnote.java.data.model.FlashNoteSearchResult;
import com.flashnote.java.data.repository.FavoriteRepository;
import com.flashnote.java.databinding.FragmentFavoriteTabBinding;
import com.flashnote.java.ui.navigation.ShellNavigator;

import java.util.List;

public class FavoriteTabFragment extends Fragment {
    private FragmentFavoriteTabBinding binding;
    private FlashNoteViewModel flashNoteViewModel;
    private java.util.List<FavoriteItem> latestFavorites = new java.util.ArrayList<>();
    private java.util.Set<Long> matchedNoteIds = new java.util.HashSet<>();
    private String currentQuery = "";

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
        flashNoteViewModel = new ViewModelProvider(this).get(FlashNoteViewModel.class);
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
                    String title = item.getFlashNoteTitle() == null ? "已收藏消息" : item.getFlashNoteTitle();
                    navigator.openChat(item.getFlashNoteId(), title, item.getMessageId());
                }
            }

            @Override
            public void onRemove(FavoriteItem item) {
                viewModel.removeFavorite(item.getMessageId(), new FavoriteRepository.ActionCallback() {
                    @Override
                    public void onSuccess(String message) {
                        if (!isAdded() || getActivity() == null) {
                            return;
                        }
                        getActivity().runOnUiThread(() -> {
                            android.content.Context context = getContext();
                            if (isAdded() && context != null) {
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }

                    @Override
                    public void onError(String message, int code) {
                        if (!isAdded() || getActivity() == null) {
                            return;
                        }
                        getActivity().runOnUiThread(() -> {
                            android.content.Context context = getContext();
                            if (isAdded() && context != null) {
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
        binding.searchButton.setOnClickListener(v -> showSearchDialog(adapter));

        viewModel.getFavorites().observe(getViewLifecycleOwner(), favorites -> {
            latestFavorites = favorites == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(favorites);
            renderFavorites(adapter);
        });

        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), error -> {
            android.content.Context context = getContext();
            if (context != null && error != null && !error.isEmpty()) {
                Toast.makeText(context, error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showSearchDialog(@NonNull FavoriteAdapter adapter) {
        android.content.Context ctx = getContext();
        if (ctx == null) {
            return;
        }
        EditText input = new EditText(ctx);
        input.setHint(R.string.hint_search_flashnote);
        input.setText(currentQuery);
        input.setSelection(input.getText().length());
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        int padding = (int) (10 * ctx.getResources().getDisplayMetrics().density);
        input.setPadding(padding, padding, padding, padding);
        input.setBackgroundResource(R.drawable.bg_input_rounded);

        android.widget.FrameLayout container = new android.widget.FrameLayout(ctx);
        android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT);
        int horizontalMargin = (int) (20 * ctx.getResources().getDisplayMetrics().density);
        params.setMargins(horizontalMargin, 0, horizontalMargin, 0);
        input.setLayoutParams(params);
        container.addView(input);

        new android.app.AlertDialog.Builder(ctx)
                .setCustomTitle(createDialogTitle(ctx, R.string.dialog_search_flashnote))
                .setView(container)
                .setPositiveButton(R.string.action_search, (dialog, which) -> {
                    String query = normalizeQuery(input.getText() == null ? "" : input.getText().toString());
                    if (query.isEmpty()) {
                        currentQuery = "";
                        matchedNoteIds.clear();
                        renderFavorites(adapter);
                        return;
                    }
                    flashNoteViewModel.searchNotes(query, new com.flashnote.java.data.repository.FlashNoteRepository.SearchCallback() {
                        @Override
                        public void onSuccess(List<FlashNoteSearchResult> results) {
                            if (!isAdded() || getActivity() == null) {
                                return;
                            }
                            getActivity().runOnUiThread(() -> {
                                currentQuery = query;
                                matchedNoteIds.clear();
                                if (results != null) {
                                    for (FlashNoteSearchResult result : results) {
                                        if (result.getFlashNote() != null && result.getFlashNote().getId() != null) {
                                            matchedNoteIds.add(result.getFlashNote().getId());
                                        }
                                    }
                                }
                                renderFavorites(adapter);
                            });
                        }

                        @Override
                        public void onError(String message) {
                            if (!isAdded() || getActivity() == null) {
                                return;
                            }
                            getActivity().runOnUiThread(() -> {
                                android.content.Context context = getContext();
                                if (context != null) {
                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    });
                })
                .setNeutralButton(R.string.action_clear, (dialog, which) -> {
                    currentQuery = "";
                    matchedNoteIds.clear();
                    renderFavorites(adapter);
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void renderFavorites(@NonNull FavoriteAdapter adapter) {
        java.util.List<FavoriteItem> filtered = filterFavorites();
        adapter.submitList(filtered);
        boolean empty = filtered.isEmpty();
        binding.emptyContainer.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    @NonNull
    private java.util.List<FavoriteItem> filterFavorites() {
        if (currentQuery.isEmpty()) {
            return new java.util.ArrayList<>(latestFavorites);
        }
        java.util.List<FavoriteItem> filtered = new java.util.ArrayList<>();
        for (FavoriteItem item : latestFavorites) {
            if (item.getFlashNoteId() != null && matchedNoteIds.contains(item.getFlashNoteId())) {
                filtered.add(item);
            }
        }
        return filtered;
    }

    @NonNull
    private String normalizeQuery(@Nullable String query) {
        return query == null ? "" : query.trim();
    }

    @NonNull
    private TextView createDialogTitle(@NonNull android.content.Context context, int textResId) {
        TextView titleView = new TextView(context);
        int horizontal = (int) (20 * context.getResources().getDisplayMetrics().density);
        int top = (int) (18 * context.getResources().getDisplayMetrics().density);
        int bottom = (int) (6 * context.getResources().getDisplayMetrics().density);
        titleView.setPadding(horizontal, top, horizontal, bottom);
        titleView.setText(textResId);
        titleView.setTextColor(getResources().getColor(R.color.text_primary));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        return titleView;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
