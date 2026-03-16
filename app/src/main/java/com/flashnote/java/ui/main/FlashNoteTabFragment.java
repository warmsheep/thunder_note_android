package com.flashnote.java.ui.main;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.flashnote.java.data.model.Collection;
import com.flashnote.java.data.model.FlashNote;
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
        FlashNoteViewModel viewModel = new ViewModelProvider(this).get(FlashNoteViewModel.class);

        adapter = new FlashNoteAdapter(new FlashNoteAdapter.OnItemActionListener() {
            @Override
            public void onOpenChat(FlashNote item) {
                if (requireActivity() instanceof ShellNavigator) {
                    ((ShellNavigator) requireActivity()).openChat(item.getId(), item.getTitle());
                } else {
                    Toast.makeText(requireContext(), item.getTitle(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onEdit(FlashNote item) {
                showNoteDialog(item, viewModel);
            }

            @Override
            public void onDelete(FlashNote item) {
                new AlertDialog.Builder(requireContext())
                        .setTitle("删除闪记")
                        .setMessage("确定要删除这条闪记吗？")
                        .setPositiveButton("删除", (dialog, which) -> viewModel.deleteNote(item.getId()))
                        .setNegativeButton("取消", null)
                        .show();
            }
        });
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerView.setAdapter(adapter);

        binding.addButton.setOnClickListener(v -> showNoteDialog(null, viewModel));
        binding.fabAdd.setOnClickListener(v -> showNoteDialog(null, viewModel));

        viewModel.getNotes().observe(getViewLifecycleOwner(), notes -> {
            adapter.submitList(notes);
            boolean empty = notes == null || notes.isEmpty();
            binding.emptyContainer.setVisibility(empty ? View.VISIBLE : View.GONE);
            binding.recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
        });

        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showNoteDialog(@Nullable FlashNote note, @NonNull FlashNoteViewModel viewModel) {
        DialogFlashNoteEditBinding dialogBinding = DialogFlashNoteEditBinding.inflate(LayoutInflater.from(requireContext()));
        if (note != null && note.getTitle() != null) {
            dialogBinding.nameInput.setText(note.getTitle());
        }
        if (note != null && note.getTags() != null) {
            dialogBinding.collectionInput.setText(note.getTags());
        }

        List<String> suggestions = buildCollectionSuggestions(viewModel.getCollections().getValue(), viewModel.getNotes().getValue());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, suggestions);
        dialogBinding.collectionInput.setAdapter(adapter);

        String[] selectedIcon = new String[]{resolveInitialIcon(note)};
        for (String icon : NOTE_ICONS) {
            Chip chip = new Chip(requireContext());
            chip.setText(icon);
            chip.setCheckable(true);
            chip.setClickable(true);
            chip.setCheckedIconVisible(false);
            chip.setEnsureMinTouchTargetSize(false);
            chip.setChecked(icon.equals(selectedIcon[0]));
            chip.setOnClickListener(v -> selectedIcon[0] = icon);
            dialogBinding.iconChipGroup.addView(chip);
        }

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
