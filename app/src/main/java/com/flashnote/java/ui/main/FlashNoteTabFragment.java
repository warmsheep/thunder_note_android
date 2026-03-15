package com.flashnote.java.ui.main;

import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.flashnote.java.ui.navigation.ShellNavigator;
import com.flashnote.java.databinding.FragmentFlashNoteTabBinding;

public class FlashNoteTabFragment extends Fragment {
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
            public void onOpenChat(com.flashnote.java.data.model.FlashNote item) {
                if (requireActivity() instanceof ShellNavigator) {
                    ((ShellNavigator) requireActivity()).openChat(item.getId(), item.getTitle());
                } else {
                    Toast.makeText(requireContext(), item.getTitle(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onEdit(com.flashnote.java.data.model.FlashNote item) {
                showNoteDialog(item, viewModel);
            }

            @Override
            public void onDelete(com.flashnote.java.data.model.FlashNote item) {
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

    private void showNoteDialog(@Nullable com.flashnote.java.data.model.FlashNote note,
                                @NonNull FlashNoteViewModel viewModel) {
        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (16 * requireContext().getResources().getDisplayMetrics().density);
        container.setPadding(padding, padding, padding, 0);

        EditText titleInput = new EditText(requireContext());
        titleInput.setHint("闪记标题");
        if (note != null && note.getTitle() != null) {
            titleInput.setText(note.getTitle());
        }

        EditText contentInput = new EditText(requireContext());
        contentInput.setHint("记录内容");
        contentInput.setMinLines(3);
        if (note != null && note.getContent() != null) {
            contentInput.setText(note.getContent());
        }

        container.addView(titleInput);
        container.addView(contentInput);

        new AlertDialog.Builder(requireContext())
                .setTitle(note == null ? "新建闪记" : "编辑闪记")
                .setView(container)
                .setPositiveButton(note == null ? "创建" : "保存", (dialog, which) -> {
                    String title = titleInput.getText().toString().trim();
                    String content = contentInput.getText().toString().trim();
                    if (title.isEmpty()) {
                        title = "未命名闪记";
                    }
                    if (note == null) {
                        viewModel.createNote(title, content);
                    } else {
                        viewModel.updateNote(note.getId(), title, content);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
