package com.flashnote.java.ui.main;

import android.app.AlertDialog;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ArrayAdapter;
import android.widget.GridLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Rect;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.flashnote.java.DebugLog;
import com.flashnote.java.R;
import com.flashnote.java.FlashNoteApp;
import com.flashnote.java.data.model.Collection;
import com.flashnote.java.data.model.FlashNote;
import com.flashnote.java.data.model.FlashNoteSearchResult;
import com.flashnote.java.data.model.MatchedMessageInfo;
import com.flashnote.java.data.model.Message;
import com.flashnote.java.data.repository.FileRepository;
import com.flashnote.java.data.repository.MessageRepository;
import android.widget.PopupMenu;
import com.flashnote.java.databinding.DialogFlashNoteEditBinding;
import com.flashnote.java.databinding.FragmentFlashNoteTabBinding;
import com.flashnote.java.databinding.PopupFlashnoteActionsBinding;
import com.flashnote.java.databinding.PopupQuickCaptureActionsBinding;
import com.flashnote.java.ui.FragmentUiSafe;
import com.flashnote.java.ui.navigation.ShellNavigator;
import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class FlashNoteTabFragment extends Fragment {
    private static final long COLLECTION_BOX_NOTE_ID = -1L;
    private FragmentFlashNoteTabBinding binding;
    private FlashNoteAdapter adapter;
    private SearchResultAdapter searchResultAdapter;
    private FlashNoteViewModel viewModel;
    private List<FlashNote> latestNotes = new ArrayList<>();
    private List<FlashNoteSearchResult> latestSearchResults = new ArrayList<>();
    private List<FlashNoteSearchResult> latestMessageContentResults = new ArrayList<>();
    private String currentQuery = "";
    private final Rect swipeDeleteRect = new Rect();
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;
    private boolean suppressSearchWatcher;
    private MessageRepository messageRepository;
    private FileRepository fileRepository;
    private Uri cameraPhotoUri;

    private final ActivityResultLauncher<Intent> mediaPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        String mimeType = requireContext().getContentResolver().getType(uri);
                        handleCaptureMedia(uri, isVideoMimeType(mimeType) ? "VIDEO" : "IMAGE");
                    }
                }
            }
    );

    private final ActivityResultLauncher<Intent> filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        handleCaptureMedia(uri, "FILE");
                    }
                }
            }
    );

    private final ActivityResultLauncher<Intent> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && cameraPhotoUri != null) {
                    handleCaptureMedia(cameraPhotoUri, "IMAGE");
                }
            }
    );

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
        messageRepository = FlashNoteApp.getInstance().getMessageRepository();

        adapter = new FlashNoteAdapter(new FlashNoteAdapter.OnItemActionListener() {
            @Override
            public void onOpenChat(FlashNote item) {
                if (Boolean.TRUE.equals(item.getHidden()) && item.getId() != null && item.getId() > 0L) {
                    viewModel.setHidden(item.getId(), false);
                    item.setHidden(false);
                }
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
            public void onLongPress(FlashNote item, View anchor) {
                showFlashNoteActions(item, anchor);
            }

            @Override
            public void onDelete(FlashNote item) {
                if (Boolean.TRUE.equals(item.getInbox()) || (item.getId() != null && item.getId() == COLLECTION_BOX_NOTE_ID)) {
                    adapter.clearPendingDelete();
                    Context ctx = getContext();
                    if (ctx != null) {
                        new AlertDialog.Builder(ctx)
                                .setTitle(R.string.flashnote_clear_inbox_title)
                                .setMessage(R.string.flashnote_clear_inbox_message)
                                .setPositiveButton(R.string.action_clear, (dialog, which) -> viewModel.clearInboxMessages(() -> {
                                    if (!isAdded() || getContext() == null) {
                                        return;
                                    }
                                    requireActivity().runOnUiThread(() -> Toast.makeText(getContext(), R.string.flashnote_cleared, Toast.LENGTH_SHORT).show());
                                }))
                                .setNegativeButton(R.string.action_cancel, null)
                                .show();
                    }
                    return;
                }
                adapter.clearPendingDelete();
                Context ctx = getContext();
                if (ctx != null) {
                    new AlertDialog.Builder(ctx)
                            .setTitle(R.string.flashnote_delete_title)
                            .setMessage(R.string.flashnote_delete_message)
                            .setPositiveButton(R.string.action_delete, (dialog, which) -> viewModel.deleteNote(item.getId()))
                            .setNegativeButton(R.string.action_cancel, null)
                            .show();
                }
            }
        });
        Context ctx = getContext();
        if (ctx != null) {
            binding.recyclerView.setLayoutManager(new LinearLayoutManager(ctx));
        }
        binding.recyclerView.setAdapter(adapter);
        
        ItemTouchHelper.SimpleCallback swipeCallback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            private final ColorDrawable background = new ColorDrawable(Color.parseColor("#FF4444"));
            private final Drawable deleteIcon = androidx.core.content.ContextCompat.getDrawable(requireContext(), android.R.drawable.ic_menu_delete);
            private final int deleteAreaWidth = (int) (72 * requireContext().getResources().getDisplayMetrics().density);

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                 List<FlashNote> currentList = getDisplayedNotes();
                if (position >= 0 && position < currentList.size()) {
                    FlashNote note = currentList.get(position);
                    adapter.setPendingDeleteNoteId(note.getId());
                    adapter.notifyItemChanged(position);
                }
            }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
                View itemView = viewHolder.itemView;

                // Pass dX to super so item translates left, revealing red background underneath
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);

                if (dX < 0) {
                    float revealWidth = Math.min(Math.abs(dX), deleteAreaWidth);
                    if (revealWidth > 0f) {
                        View timeText = itemView.findViewById(R.id.timeText);
                        if (timeText != null) {
                            float progress = Math.min(1f, revealWidth / deleteAreaWidth);
                            timeText.setAlpha(1f - progress);
                        }
                        int left = itemView.getRight() - (int) revealWidth;
                        int top = itemView.getTop();
                        int bottom = itemView.getBottom();
                        background.setBounds(left, top, itemView.getRight(), bottom);
                        background.draw(c);

                        int iconSize = deleteIcon.getIntrinsicHeight();
                        int iconRightPadding = (int) (22 * requireContext().getResources().getDisplayMetrics().density);
                        int iconLeft = itemView.getRight() - iconRightPadding - iconSize;
                        int iconTop = top + (itemView.getHeight() - iconSize) / 2;
                        deleteIcon.setBounds(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize);
                        deleteIcon.setTint(Color.WHITE);
                        deleteIcon.draw(c);
                    }
                }
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                View timeText = viewHolder.itemView.findViewById(R.id.timeText);
                if (timeText != null) {
                    timeText.setAlpha(1f);
                }
            }
            
            @Override
            public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder viewHolder) {
                return 0.4f;
            }
        };
        
        new ItemTouchHelper(swipeCallback).attachToRecyclerView(binding.recyclerView);

        binding.recyclerView.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull android.view.MotionEvent e) {
                if (e.getAction() != android.view.MotionEvent.ACTION_UP) {
                    return false;
                }
                View child = rv.findChildViewUnder(e.getX(), e.getY());
                if (child == null) {
                    return false;
                }
                RecyclerView.ViewHolder holder = rv.getChildViewHolder(child);
                int position = holder.getBindingAdapterPosition();
                if (position == RecyclerView.NO_POSITION) {
                    return false;
                }
                View deleteOverlay = child.findViewById(R.id.deleteOverlay);
                if (deleteOverlay == null || deleteOverlay.getVisibility() != View.VISIBLE) {
                    return false;
                }
                deleteOverlay.getHitRect(swipeDeleteRect);
                if (swipeDeleteRect.contains((int) (e.getX() - child.getLeft()), (int) (e.getY() - child.getTop()))) {
                    deleteOverlay.performClick();
                    return true;
                }
                adapter.clearPendingDelete();
                return false;
            }
        });
        
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
        binding.fabAdd.setOnClickListener(this::showQuickCaptureMenu);
        binding.searchButton.setOnClickListener(v -> toggleSearchContainer());
        binding.searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (suppressSearchWatcher) {
                    return;
                }
                if (pendingSearch != null) {
                    searchHandler.removeCallbacks(pendingSearch);
                }
                pendingSearch = () -> performSearch(s.toString());
                searchHandler.postDelayed(pendingSearch, 300);
            }
        });
        binding.searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (FlashNoteSearchInputPolicy.shouldSubmitSearch(actionId, event)) {
                if (pendingSearch != null) {
                    searchHandler.removeCallbacks(pendingSearch);
                    pendingSearch = null;
                }
                performSearch(binding.searchInput.getText() == null ? "" : binding.searchInput.getText().toString());
                return true;
            }
            return false;
        });
        binding.searchCloseButton.setOnClickListener(v -> closeSearchContainer());
        resetSearchUiState("onViewCreated");

        viewModel.getNotes().observe(getViewLifecycleOwner(), notes -> {
            latestNotes = notes == null ? new ArrayList<>() : new ArrayList<>(notes);
            renderNotes();
        });

        viewModel.getCollections().observe(getViewLifecycleOwner(), collections -> {
            if (adapter != null) {
                renderNotes();
            }
        });

        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                DebugLog.logHandledError("FlashNoteTab", error);
                Context errorCtx = getContext();
                if (errorCtx != null && !DebugLog.isLikelyNetworkIssue(error)
                        && DebugLog.shouldShowToast("FlashNoteTab:" + error, 2000L)) {
                    Toast.makeText(errorCtx, error, Toast.LENGTH_SHORT).show();
                }
                viewModel.clearError();
            }
        });

        getParentFragmentManager().setFragmentResultListener("quick_capture_saved", getViewLifecycleOwner(), (requestKey, result) -> {
            String preview = result == null ? null : result.getString("inbox_preview");
            if (preview != null && !preview.isBlank()) {
                viewModel.updateInboxPreviewLocally(preview);
            }
        });

        viewModel.refreshIfNeeded();
    }

    private void renderNotes() {
        String recyclerAdapterName = binding.recyclerView.getAdapter() == null
                ? "null"
                : binding.recyclerView.getAdapter().getClass().getSimpleName();
        boolean searchContainerVisible = binding.searchContainer.getVisibility() == View.VISIBLE;
        if (FlashNoteSearchAdapterPolicy.shouldRenderSearchPane(searchContainerVisible, currentQuery)) {
            ensureSearchResultAdapter();
            boolean isShowingSearchAdapter = binding.recyclerView.getAdapter() == searchResultAdapter;
            if (FlashNoteSearchAdapterPolicy.shouldEnsureSearchAdapter(true, isShowingSearchAdapter)) {
                binding.recyclerView.setAdapter(searchResultAdapter);
            }
            if (searchResultAdapter != null) {
                searchResultAdapter.submitList(latestSearchResults, latestMessageContentResults);
            }
            boolean empty = (latestSearchResults == null || latestSearchResults.isEmpty()) 
                    && (latestMessageContentResults == null || latestMessageContentResults.isEmpty());
            binding.emptyContainer.setVisibility(empty ? View.VISIBLE : View.GONE);
            binding.recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
        } else {
            if (binding.recyclerView.getAdapter() != adapter) {
                binding.recyclerView.setAdapter(adapter);
            }
            List<FlashNote> visible = getVisibleNotes(latestNotes);
            adapter.submitList(visible);
            boolean empty = visible.isEmpty();
            binding.emptyContainer.setVisibility(empty ? View.VISIBLE : View.GONE);
            binding.recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
        }
    }

    @NonNull
    private List<FlashNote> getVisibleNotes(@Nullable List<FlashNote> source) {
        List<FlashNote> visible = new ArrayList<>();
        if (source == null) {
            return visible;
        }
        for (FlashNote note : source) {
            if (note == null) {
                continue;
            }
            if (Boolean.TRUE.equals(note.getHidden())) {
                continue;
            }
            visible.add(note);
        }
        return visible;
    }

    @NonNull
    private List<FlashNote> getDisplayedNotes() {
        return getVisibleNotes(latestNotes);
    }

    private void showFlashNoteActions(@NonNull FlashNote note, @NonNull View anchor) {
        if (!isAdded()) {
            return;
        }
        boolean isInbox = Boolean.TRUE.equals(note.getInbox()) || (note.getId() != null && note.getId() == COLLECTION_BOX_NOTE_ID);
        if (!isInbox) {
            PopupFlashnoteActionsBinding popupBinding = PopupFlashnoteActionsBinding.inflate(
                    LayoutInflater.from(requireContext()));
            PopupWindow popupWindow = new PopupWindow(
                    popupBinding.getRoot(),
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    true
            );
            popupWindow.setBackgroundDrawable(null);
            popupWindow.setOutsideTouchable(true);

            popupBinding.actionPinText.setText(
                    Boolean.TRUE.equals(note.getPinned()) ? R.string.flashnote_unpin : R.string.flashnote_pin);

            popupBinding.actionEdit.setOnClickListener(v -> {
                popupWindow.dismiss();
                showNoteDialog(note, viewModel);
            });
            popupBinding.actionHide.setOnClickListener(v -> {
                popupWindow.dismiss();
                if (note.getId() != null && note.getId() > 0L) {
                    viewModel.setHidden(note.getId(), true);
                }
            });
            popupBinding.actionPin.setOnClickListener(v -> {
                popupWindow.dismiss();
                if (note.getId() != null && note.getId() > 0L) {
                    viewModel.setPinned(note.getId(), !Boolean.TRUE.equals(note.getPinned()));
                }
            });
            showPopupNearAnchor(anchor, popupWindow, popupBinding.getRoot());
        }
    }

    private void toggleSearchContainer() {
        boolean isVisible = binding.searchContainer.getVisibility() == View.VISIBLE;
        if (isVisible) {
            closeSearchContainer();
        } else {
            binding.searchContainer.setVisibility(View.VISIBLE);
            binding.searchInput.requestFocus();
            ensureSearchResultAdapter();
            boolean isShowingSearchAdapter = binding.recyclerView.getAdapter() == searchResultAdapter;
            if (FlashNoteSearchAdapterPolicy.shouldAttachSearchAdapter(true, isShowingSearchAdapter)) {
                binding.recyclerView.setAdapter(searchResultAdapter);
            }
            renderNotes();
        }
    }

    private void resetSearchUiState(@NonNull String reason) {
        currentQuery = "";
        latestSearchResults = new ArrayList<>();
        latestMessageContentResults = new ArrayList<>();
        if (pendingSearch != null) {
            searchHandler.removeCallbacks(pendingSearch);
            pendingSearch = null;
        }
        suppressSearchWatcher = true;
        binding.searchInput.setText("");
        suppressSearchWatcher = false;
        binding.searchContainer.setVisibility(View.GONE);
        if (binding.recyclerView.getAdapter() != adapter) {
            binding.recyclerView.setAdapter(adapter);
        }
    }

    private void ensureSearchResultAdapter() {
        if (searchResultAdapter != null) {
            return;
        }
        searchResultAdapter = new SearchResultAdapter(new SearchResultAdapter.OnSearchResultClickListener() {
            @Override
            public void onResultClick(FlashNote flashNote, Long messageId) {
                if (getActivity() instanceof ShellNavigator navigator) {
                    if (messageId == null) {
                        navigator.openChat(flashNote.getId(), flashNote.getTitle());
                    } else {
                        navigator.openChat(flashNote.getId(), flashNote.getTitle(), messageId);
                    }
                }
            }
        });
        Context ctx = getContext();
        if (ctx != null && binding.recyclerView.getLayoutManager() == null) {
            binding.recyclerView.setLayoutManager(new LinearLayoutManager(ctx));
        }
    }

    private void closeSearchContainer() {
        binding.searchContainer.setVisibility(View.GONE);
        resetSearchUiState("closeSearchContainer");
        renderNotes();
    }

    private void performSearch(String query) {
        String normalizedQuery = normalizeQuery(query);
        if (normalizedQuery.isEmpty()) {
            currentQuery = "";
            latestSearchResults = new ArrayList<>();
            latestMessageContentResults = new ArrayList<>();
            renderNotes();
            return;
        }
        currentQuery = normalizedQuery;
        viewModel.searchNotes(normalizedQuery, new com.flashnote.java.data.repository.FlashNoteRepository.SearchCallback() {
            @Override
            public void onSuccess(List<FlashNoteSearchResult> noteNameResults, List<FlashNoteSearchResult> messageContentResults) {
                runIfUiAlive(() -> {
                    latestSearchResults = noteNameResults == null ? new ArrayList<>() : new ArrayList<>(noteNameResults);
                    latestMessageContentResults = messageContentResults == null ? new ArrayList<>() : new ArrayList<>(messageContentResults);
                    renderNotes();
                });
            }

            @Override
            public void onError(String message) {
                runIfUiAlive(() -> {
                    Context errorCtx = getContext();
                    if (errorCtx != null) {
                        Toast.makeText(errorCtx, message, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
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
        for (String icon : getResources().getStringArray(R.array.flashnote_icons)) {
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
                .setTitle(note == null ? R.string.flashnote_new_title : R.string.flashnote_edit_title)
                .setView(dialogBinding.getRoot())
                .setPositiveButton(note == null ? R.string.action_create : R.string.action_save, null)
                .setNegativeButton(R.string.action_cancel, null)
                .create();

        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String title = dialogBinding.nameInput.getText() == null ? "" : dialogBinding.nameInput.getText().toString().trim();
            String collectionName = dialogBinding.collectionInput.getText() == null ? "" : dialogBinding.collectionInput.getText().toString().trim();
            if (title.isEmpty()) {
                dialogBinding.nameInput.setError(getString(R.string.flashnote_name_required));
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
        String[] icons = getResources().getStringArray(R.array.flashnote_icons);
        return icons.length == 0 ? "💼" : icons[0];
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
        openChatBtn.setText(R.string.flashnote_open_chat_from_start);
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
            titleView.setText(info.getSnippet() != null ? info.getSnippet() : getString(R.string.flashnote_matched_message));
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
                .setTitle(getString(R.string.flashnote_match_dialog_title, item.getTitle()))
                .setView(scrollView)
                .setNegativeButton(R.string.action_cancel, null)
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

    private void showQuickCaptureMenu(@NonNull View anchor) {
        if (!isAdded()) {
            return;
        }
        PopupQuickCaptureActionsBinding popupBinding = PopupQuickCaptureActionsBinding.inflate(
                LayoutInflater.from(requireContext()));
        PopupWindow popupWindow = new PopupWindow(
                popupBinding.getRoot(),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
        );
        popupWindow.setBackgroundDrawable(null);
        popupWindow.setOutsideTouchable(true);

        popupBinding.actionText.setOnClickListener(v -> {
            popupWindow.dismiss();
            if (getActivity() instanceof ShellNavigator navigator) {
                navigator.openQuickCaptureTextEditor();
            }
        });
        popupBinding.actionImage.setOnClickListener(v -> {
            popupWindow.dismiss();
            openImagePicker();
        });
        popupBinding.actionFile.setOnClickListener(v -> {
            popupWindow.dismiss();
            openFilePicker();
        });
        popupBinding.actionCard.setOnClickListener(v -> {
            popupWindow.dismiss();
            if (getActivity() instanceof ShellNavigator navigator) {
                navigator.openCardEditor(COLLECTION_BOX_NOTE_ID, 0L, getString(R.string.flashnote_inbox_card_title));
            }
        });
        popupBinding.actionCamera.setOnClickListener(v -> {
            popupWindow.dismiss();
            openCamera();
        });

        showPopupNearAnchor(anchor, popupWindow, popupBinding.getRoot());
    }

    private void showPopupNearAnchor(@NonNull View anchor, @NonNull PopupWindow popupWindow, @NonNull View popupView) {
        popupView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int popupWidth = popupView.getMeasuredWidth();
        int popupHeight = popupView.getMeasuredHeight();

        int[] location = new int[2];
        anchor.getLocationOnScreen(location);
        int viewX = location[0];
        int viewY = location[1];
        int viewWidth = anchor.getWidth();
        int viewHeight = anchor.getHeight();

        float density = getResources().getDisplayMetrics().density;
        int gap = (int) (4 * density);
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        boolean showAbove = viewY > screenHeight / 2;

        int popupX = viewX + viewWidth / 2 - popupWidth / 2;
        int popupY = showAbove ? viewY - popupHeight - gap : viewY + viewHeight + gap;

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        if (popupX < 0) {
            popupX = 0;
        }
        if (popupX + popupWidth > screenWidth) {
            popupX = screenWidth - popupWidth;
        }
        if (popupY < 0) {
            popupY = 0;
        }
        popupWindow.showAtLocation(binding.getRoot(), Gravity.NO_GRAVITY, popupX, popupY);
    }

    private void openImagePicker() {
        registerExternalFlowForGestureUnlockSkip();
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
        mediaPickerLauncher.launch(intent);
    }

    private void openFilePicker() {
        registerExternalFlowForGestureUnlockSkip();
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        filePickerLauncher.launch(intent);
    }

    private void openCamera() {
        if (!isAdded()) {
            return;
        }
        try {
            File photoFile = new File(requireContext().getCacheDir(), "capture_" + System.currentTimeMillis() + ".jpg");
            cameraPhotoUri = FileProvider.getUriForFile(
                    requireContext(),
                    requireContext().getPackageName() + ".fileprovider",
                    photoFile
            );
            registerExternalFlowForGestureUnlockSkip();
            Intent intent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, cameraPhotoUri);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            cameraLauncher.launch(intent);
        } catch (Exception exception) {
            Context ctx = getContext();
            if (ctx != null) {
                Toast.makeText(ctx, R.string.flashnote_open_camera_failed, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void handleCaptureMedia(@NonNull Uri uri, @NonNull String mediaType) {
        if (!isAdded()) {
            return;
        }
        copyUriToTempFile(uri, mediaType.equals("FILE") ? "file" : "image", file -> {
            if (file == null) {
                Context ctx = getContext();
                if (ctx != null) {
                    Toast.makeText(ctx, R.string.flashnote_file_process_failed, Toast.LENGTH_SHORT).show();
                }
                return;
            }
            String originalName = getOriginalFileName(uri);
            String resolvedMediaType = resolveCaptureMediaType(mediaType);
            String fileName = originalName != null && !originalName.isBlank() ? originalName : file.getName();
            Integer mediaDuration = null;
            if ("VIDEO".equals(resolvedMediaType)) {
                mediaDuration = resolveVideoDurationSeconds(file.getAbsolutePath());
            }
            String preview = resolveInboxPreviewText(resolvedMediaType);
            viewModel.updateInboxPreviewLocally(preview);
            messageRepository.enqueueMedia(COLLECTION_BOX_NOTE_ID, 0L, resolvedMediaType, file, fileName, file.length(), mediaDuration, () -> runIfUiAlive(() -> {
                if (binding != null) {
                    playCaptureAnimation(binding.fabAdd);
                }
            }));
        });
    }

    @Nullable
    private Integer resolveVideoDurationSeconds(@NonNull String filePath) {
        try {
            android.media.MediaMetadataRetriever retriever = new android.media.MediaMetadataRetriever();
            retriever.setDataSource(filePath);
            String duration = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);
            retriever.release();
            if (duration != null) {
                return Integer.parseInt(duration) / 1000;
            }
        } catch (Exception exception) {
            DebugLog.w("FlashNoteTab", "Failed to resolve video duration");
        }
        return null;
    }

    @NonNull
    private String resolveInboxPreviewText(@NonNull String mediaType) {
        if ("VIDEO".equalsIgnoreCase(mediaType)) {
            return getString(R.string.chat_media_video_placeholder);
        }
        if ("FILE".equalsIgnoreCase(mediaType)) {
            return getString(R.string.chat_media_file_placeholder);
        }
        return getString(R.string.chat_media_image_placeholder);
    }

    private void runIfUiAlive(@NonNull Runnable action) {
        FragmentUiSafe.runIfUiAlive(this, binding, action);
    }

    private void registerExternalFlowForGestureUnlockSkip() {
        if (getActivity() instanceof ShellNavigator navigator) {
            navigator.registerExternalFlowForGestureUnlockSkip();
        }
    }

    @NonNull
    private String resolveCaptureMediaType(@NonNull String mediaType) {
        if ("FILE".equals(mediaType)) {
            return "FILE";
        }
        if ("VIDEO".equals(mediaType)) {
            return "VIDEO";
        }
        return "IMAGE";
    }

    private boolean isVideoMimeType(@Nullable String mimeType) {
        return mimeType != null && mimeType.startsWith("video/");
    }

    private interface TempFileCallback {
        void onReady(@Nullable File file);
    }

    private void copyUriToTempFile(@NonNull Uri uri, @NonNull String prefix, @NonNull TempFileCallback callback) {
        if (!isAdded()) {
            callback.onReady(null);
            return;
        }
        try {
            InputStream inputStream = requireContext().getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                callback.onReady(null);
                return;
            }
            String extension = getFileExtension(uri);
            File tempFile = new File(requireContext().getCacheDir(), prefix + "_" + System.currentTimeMillis() + "." + extension);
            try (inputStream; FileOutputStream fos = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
            }
            callback.onReady(tempFile);
        } catch (IOException exception) {
            callback.onReady(null);
        }
    }

    private void playCaptureAnimation(@NonNull View sourceView) {
        sourceView.animate()
                .scaleX(0.82f)
                .scaleY(0.82f)
                .alpha(0.65f)
                .setDuration(140)
                .withEndAction(() -> sourceView.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .alpha(1f)
                        .setDuration(180)
                        .start())
                .start();
    }

    @Nullable
    private String getOriginalFileName(@NonNull Uri uri) {
        if (!isAdded()) {
            return null;
        }
        try (android.database.Cursor cursor = requireContext().getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (nameIndex >= 0) {
                    return cursor.getString(nameIndex);
                }
            }
        } catch (Exception exception) {
            DebugLog.w("FlashNoteTab", "Failed to resolve original file name");
        }
        return null;
    }

    @NonNull
    private String getFileExtension(@NonNull Uri uri) {
        if (!isAdded()) {
            return "tmp";
        }
        String mimeType = requireContext().getContentResolver().getType(uri);
        String extension = mimeType == null ? null : android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
        if (extension == null || extension.isBlank()) {
            extension = android.webkit.MimeTypeMap.getFileExtensionFromUrl(uri.toString());
        }
        return extension == null || extension.isBlank() ? "tmp" : extension;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (binding != null && binding.searchContainer.getVisibility() != View.VISIBLE) {
            resetSearchUiState("onResume");
        }
        if (viewModel != null) {
            viewModel.refreshIfNeeded();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (pendingSearch != null) {
            searchHandler.removeCallbacks(pendingSearch);
            pendingSearch = null;
        }
        if (binding != null) {
            binding.recyclerView.setAdapter(null);
        }
        searchResultAdapter = null;
        adapter = null;
        binding = null;
    }
}
