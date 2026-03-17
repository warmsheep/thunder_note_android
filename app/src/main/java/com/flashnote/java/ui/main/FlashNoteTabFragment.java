package com.flashnote.java.ui.main;

import android.app.AlertDialog;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ArrayAdapter;
import android.widget.GridLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.flashnote.java.R;
import com.flashnote.java.data.model.Collection;
import com.flashnote.java.data.model.FlashNote;
import com.flashnote.java.data.model.FlashNoteSearchResult;
import com.flashnote.java.data.model.MatchedMessageInfo;
import com.flashnote.java.data.model.Message;
import com.flashnote.java.databinding.DialogFlashNoteEditBinding;
import com.flashnote.java.databinding.FragmentFlashNoteTabBinding;
import com.flashnote.java.ui.navigation.ShellNavigator;
import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class FlashNoteTabFragment extends Fragment {
    private static final String[] NOTE_ICONS = new String[]{"💼", "📚", "❤️", "🍀", "🌟", "🎯", "🚀", "🎨", "🎵", "📷", "📝", "💡"};

    private FragmentFlashNoteTabBinding binding;
    private FlashNoteAdapter adapter;
    private FlashNoteViewModel viewModel;
    private List<FlashNote> latestNotes = new ArrayList<>();
    private List<FlashNote> searchedNotes = new ArrayList<>();
    private List<FlashNoteSearchResult> latestSearchResults = new ArrayList<>();
    private String currentQuery = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentFlashNoteTabBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(FlashNoteViewModel.class);

        adapter = new FlashNoteAdapter(new FlashNoteAdapter.OnItemActionListener() {
            @Override
            public void onOpenChat(FlashNote item) {
                if (!currentQuery.isEmpty() && !latestSearchResults.isEmpty()) {
                    for (FlashNoteSearchResult result : latestSearchResults) {
                        if (result.getFlashNote() != null && result.getFlashNote().getId() != null 
                            && result.getFlashNote().getId().equals(item.getId())
                            && result.getMatchedMessages() != null && !result.getMatchedMessages().isEmpty()) {
                            showMatchedMessagesDialog(item, result.getMatchedMessages());
                            return;
                        }
                    }
                }
                if (getActivity() instanceof ShellNavigator navigator) {
                    navigator.openChat(item.getId(), item.getTitle());
                } else {
                    Context ctx = getContext();
                    if (ctx != null) {
                        Toast.makeText(ctx, item.getTitle(), Toast.LENGTH_SHORT).show();
                    }
                }
            }

            @Override
            public void onEdit(FlashNote item) {
                showNoteDialog(item, viewModel);
            }

            @Override
            public void onDelete(FlashNote item) {
                Context ctx = getContext();
                if (ctx != null) {
                    new AlertDialog.Builder(ctx)
                            .setTitle("删除闪记")
                            .setMessage("确定要删除这条闪记吗？")
                            .setPositiveButton("删除", (dialog, which) -> viewModel.deleteNote(item.getId()))
                            .setNegativeButton("取消", null)
                            .show();
                }
            }
        });
        Context ctx = getContext();
        if (ctx != null) {
            binding.recyclerView.setLayoutManager(new LinearLayoutManager(ctx));
        }
        binding.recyclerView.setAdapter(adapter);
        if (ctx != null) {
            androidx.recyclerview.widget.DividerItemDecoration divider = new androidx.recyclerview.widget.DividerItemDecoration(
                ctx, androidx.recyclerview.widget.DividerItemDecoration.VERTICAL);
            android.graphics.drawable.ShapeDrawable dividerDrawable = new android.graphics.drawable.ShapeDrawable();
            dividerDrawable.setIntrinsicHeight((int) (0.5f * ctx.getResources().getDisplayMetrics().density));
            dividerDrawable.getPaint().setColor(ctx.getColor(R.color.border));
            divider.setDrawable(dividerDrawable);
            binding.recyclerView.addItemDecoration(divider);
        }

        binding.addButton.setOnClickListener(v -> showNoteDialog(null, viewModel));
        binding.fabAdd.setOnClickListener(v -> showNoteDialog(null, viewModel));
        binding.searchButton.setOnClickListener(v -> showSearchDialog());

        viewModel.getNotes().observe(getViewLifecycleOwner(), notes -> {
            latestNotes = notes == null ? new ArrayList<>() : new ArrayList<>(notes);
            renderNotes();
        });

        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                Context errorCtx = getContext();
                if (errorCtx != null) {
                    Toast.makeText(errorCtx, error, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void renderNotes() {
        List<FlashNote> displayed = currentQuery.isEmpty() ? latestNotes : searchedNotes;
        adapter.submitList(displayed);
        boolean empty = displayed == null || displayed.isEmpty();
        binding.emptyContainer.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void showSearchDialog() {
        Context ctx = getContext();
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

        new AlertDialog.Builder(ctx)
                .setCustomTitle(createDialogTitle(ctx, R.string.dialog_search_flashnote))
                .setView(container)
                .setPositiveButton(R.string.action_search, (dialog, which) -> {
                    String query = normalizeQuery(input.getText() == null ? "" : input.getText().toString());
                    if (query.isEmpty()) {
                        currentQuery = "";
                        searchedNotes = new ArrayList<>();
                        renderNotes();
                        return;
                    }
                    viewModel.searchNotes(query, new com.flashnote.java.data.repository.FlashNoteRepository.SearchCallback() {
                        @Override
                        public void onSuccess(List<FlashNoteSearchResult> results) {
                            if (!isAdded() || getActivity() == null) {
                                return;
                            }
                            getActivity().runOnUiThread(() -> {
                                currentQuery = query;
                                latestSearchResults = results == null ? new ArrayList<>() : new ArrayList<>(results);
                                searchedNotes = new ArrayList<>();
                                for (FlashNoteSearchResult result : latestSearchResults) {
                                    if (result.getFlashNote() != null) {
                                        searchedNotes.add(result.getFlashNote());
                                    }
                                }
                                renderNotes();
                            });
                        }

                        @Override
                        public void onError(String message) {
                            if (!isAdded() || getActivity() == null) {
                                return;
                            }
                            getActivity().runOnUiThread(() -> {
                                Context errorCtx = getContext();
                                if (errorCtx != null) {
                                    Toast.makeText(errorCtx, message, Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    });
                })
                .setNeutralButton(R.string.action_clear, (dialog, which) -> {
                    currentQuery = "";
                    searchedNotes = new ArrayList<>();
                    latestSearchResults = new ArrayList<>();
                    renderNotes();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void showNoteDialog(@Nullable FlashNote note, @NonNull FlashNoteViewModel viewModel) {
        if (!isAdded()) return;
        Context ctx = getContext();
        if (ctx == null) return;
        
        DialogFlashNoteEditBinding dialogBinding = DialogFlashNoteEditBinding.inflate(LayoutInflater.from(ctx));
        if (note != null && note.getTitle() != null) {
            dialogBinding.nameInput.setText(note.getTitle());
        }
        if (note != null && note.getTags() != null) {
            dialogBinding.collectionInput.setText(note.getTags());
        }

        List<String> suggestions = buildCollectionSuggestions(viewModel.getCollections().getValue(), viewModel.getNotes().getValue());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(ctx, android.R.layout.simple_dropdown_item_1line, suggestions);
        dialogBinding.collectionInput.setAdapter(adapter);

        String[] selectedIcon = new String[]{resolveInitialIcon(note)};
        updateDialogPreview(dialogBinding, note, selectedIcon[0]);
        dialogBinding.nameInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateDialogPreview(dialogBinding, note, selectedIcon[0]);
            }
        });
        for (String icon : NOTE_ICONS) {
            Chip chip = new Chip(ctx);
            chip.setText(icon);
            chip.setCheckable(true);
            chip.setClickable(true);
            chip.setCheckedIconVisible(false);
            chip.setEnsureMinTouchTargetSize(false);
            chip.setGravity(Gravity.CENTER);
            chip.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            chip.setChipStartPadding(0f);
            chip.setChipEndPadding(0f);
            chip.setTextStartPadding(0f);
            chip.setTextEndPadding(0f);
            chip.setIconStartPadding(0f);
            chip.setIconEndPadding(0f);
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            chip.setMinHeight((int) (36 * ctx.getResources().getDisplayMetrics().density));
            chip.setChecked(icon.equals(selectedIcon[0]));
            chip.setOnClickListener(v -> {
                selectedIcon[0] = icon;
                updateDialogPreview(dialogBinding, note, icon);
                syncChipSelection(dialogBinding.iconChipGroup, chip);
            });
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            int margin = (int) (4 * ctx.getResources().getDisplayMetrics().density);
            params.setMargins(margin, margin, margin, margin);
            chip.setLayoutParams(params);
            dialogBinding.iconChipGroup.addView(chip);
        }

        AlertDialog dialog = new AlertDialog.Builder(ctx)
                .setTitle(note == null ? "新建闪记" : "编辑闪记")
                .setView(dialogBinding.getRoot())
                .setPositiveButton(note == null ? "创建" : "保存", null)
                .setNegativeButton("取消", null)
                .create();

        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String title = dialogBinding.nameInput.getText() == null ? "" : dialogBinding.nameInput.getText().toString().trim();
            String collectionName = dialogBinding.collectionInput.getText() == null ? "" : dialogBinding.collectionInput.getText().toString().trim();
            if (title.isEmpty()) {
                dialogBinding.nameInput.setError("请输入闪记名称");
                return;
            }
            String normalizedCollection = collectionName.isEmpty() ? null : collectionName;
            if (note == null) {
                viewModel.createNote(title, selectedIcon[0], normalizedCollection);
            } else {
                viewModel.updateNote(note.getId(), title, note.getContent(), selectedIcon[0], normalizedCollection);
            }
            dialog.dismiss();
        }));
        dialog.show();
    }

    private List<String> buildCollectionSuggestions(@Nullable List<Collection> collections,
                                                    @Nullable List<FlashNote> notes) {
        Set<String> names = new LinkedHashSet<>();
        if (collections != null) {
            for (Collection collection : collections) {
                String name = normalize(collection.getName());
                if (name != null) {
                    names.add(name);
                }
            }
        }
        if (notes != null) {
            for (FlashNote item : notes) {
                String name = normalize(item.getTags());
                if (name != null) {
                    names.add(name);
                }
            }
        }
        return new ArrayList<>(names);
    }

    private String normalize(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String resolveInitialIcon(@Nullable FlashNote note) {
        if (note != null) {
            String icon = note.getIcon();
            if (icon != null && !icon.trim().isEmpty()) {
                return icon;
            }
        }
        return NOTE_ICONS[0];
    }

    private void updateDialogPreview(@NonNull DialogFlashNoteEditBinding dialogBinding,
                                     @Nullable FlashNote note,
                                     @NonNull String selectedIcon) {
        dialogBinding.iconPreviewText.setText(selectedIcon);
        String inputTitle = dialogBinding.nameInput.getText() == null ? "" : dialogBinding.nameInput.getText().toString().trim();
        if (!inputTitle.isEmpty()) {
            dialogBinding.previewTitleText.setText(inputTitle);
            return;
        }
        if (note != null && note.getTitle() != null && !note.getTitle().trim().isEmpty()) {
            dialogBinding.previewTitleText.setText(note.getTitle().trim());
            return;
        }
        dialogBinding.previewTitleText.setText(getString(R.string.flashnote_dialog_preview_title));
    }

    @NonNull
    private String normalizeQuery(@Nullable String query) {
        return query == null ? "" : query.trim();
    }

    private void showMatchedMessagesDialog(FlashNote item, List<MatchedMessageInfo> matchedMessages) {
        Context ctx = getContext();
        if (ctx == null || matchedMessages == null || matchedMessages.isEmpty()) return;

        float density = ctx.getResources().getDisplayMetrics().density;
        int pagePadding = (int) (16 * density);
        int itemPadding = (int) (12 * density);
        int smallPadding = (int) (8 * density);
        int tinyPadding = (int) (4 * density);

        android.widget.ScrollView scrollView = new android.widget.ScrollView(ctx);
        scrollView.setFillViewport(true);
        android.widget.LinearLayout container = new android.widget.LinearLayout(ctx);
        container.setOrientation(android.widget.LinearLayout.VERTICAL);
        container.setPadding(pagePadding, pagePadding, pagePadding, pagePadding);
        scrollView.addView(container, new android.widget.ScrollView.LayoutParams(
                android.widget.ScrollView.LayoutParams.MATCH_PARENT,
                android.widget.ScrollView.LayoutParams.WRAP_CONTENT
        ));

        TextView openChatBtn = new TextView(ctx);
        openChatBtn.setText("打开聊天（从头开始）");
        openChatBtn.setTextColor(ctx.getColor(R.color.primary));
        openChatBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        openChatBtn.setPadding(itemPadding, smallPadding, itemPadding, smallPadding);
        openChatBtn.setBackgroundResource(R.drawable.bg_placeholder_card);
        openChatBtn.setOnClickListener(v -> {
            if (getActivity() instanceof ShellNavigator navigator) {
                navigator.openChat(item.getId(), item.getTitle());
            }
        });
        container.addView(openChatBtn, new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        View divider = new View(ctx);
        divider.setBackgroundColor(ctx.getColor(R.color.border));
        android.widget.LinearLayout.LayoutParams dividerParams = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                (int) density
        );
        dividerParams.topMargin = smallPadding;
        dividerParams.bottomMargin = smallPadding;
        container.addView(divider, dividerParams);

        for (MatchedMessageInfo info : matchedMessages) {
            android.widget.LinearLayout card = new android.widget.LinearLayout(ctx);
            card.setOrientation(android.widget.LinearLayout.VERTICAL);
            card.setBackgroundResource(R.drawable.bg_placeholder_card);
            card.setPadding(itemPadding, smallPadding, itemPadding, smallPadding);

            TextView titleView = new TextView(ctx);
            titleView.setText(info.getSnippet() != null ? info.getSnippet() : "匹配的消息");
            titleView.setTextColor(ctx.getColor(R.color.text_primary));
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            titleView.setTypeface(titleView.getTypeface(), android.graphics.Typeface.BOLD);
            titleView.setMaxLines(2);
            titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            card.addView(titleView);

            List<Message> contextMessages = info.getContextMessages();
            if (contextMessages != null && !contextMessages.isEmpty()) {
                for (Message message : contextMessages) {
                    TextView msgView = new TextView(ctx);
                    boolean isMatched = message.getId() != null && message.getId().equals(info.getMessageId());
                    String role = message.getRole();
                    String prefix = ("assistant".equals(role) || "ai".equals(role)) ? "🤖 " : "😊 ";
                    String content = message.getContent() == null ? "" : message.getContent();
                    msgView.setText(prefix + content);
                    msgView.setTextSize(TypedValue.COMPLEX_UNIT_SP, isMatched ? 13 : 12);
                    msgView.setTextColor(ctx.getColor(isMatched ? R.color.text_primary : R.color.text_secondary));
                    msgView.setBackgroundColor(ctx.getColor(isMatched ? R.color.search_highlight : R.color.background));
                    msgView.setPadding(smallPadding, tinyPadding, smallPadding, tinyPadding);
                    msgView.setMaxLines(3);
                    msgView.setEllipsize(android.text.TextUtils.TruncateAt.END);

                    android.widget.LinearLayout.LayoutParams msgParams = new android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    );
                    msgParams.topMargin = tinyPadding;
                    card.addView(msgView, msgParams);
                }
            }

            Long matchedMessageId = info.getMessageId();
            card.setOnClickListener(v -> {
                if (getActivity() instanceof ShellNavigator navigator) {
                    if (matchedMessageId != null) {
                        navigator.openChat(item.getId(), item.getTitle(), matchedMessageId);
                    } else {
                        navigator.openChat(item.getId(), item.getTitle());
                    }
                }
            });

            android.widget.LinearLayout.LayoutParams cardParams = new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            );
            cardParams.topMargin = smallPadding;
            container.addView(card, cardParams);
        }

        new AlertDialog.Builder(ctx)
                .setTitle("\"" + item.getTitle() + "\" 中的匹配")
                .setView(scrollView)
                .setNegativeButton("关闭", null)
                .show();
    }

    private void syncChipSelection(@NonNull GridLayout chipGroup, @NonNull Chip selectedChip) {
        for (int index = 0; index < chipGroup.getChildCount(); index++) {
            View child = chipGroup.getChildAt(index);
            if (child instanceof Chip chip) {
                chip.setChecked(chip == selectedChip);
            }
        }
    }

    @NonNull
    private TextView createDialogTitle(@NonNull Context context, int textResId) {
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
