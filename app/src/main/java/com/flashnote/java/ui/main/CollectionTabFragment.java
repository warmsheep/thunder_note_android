package com.flashnote.java.ui.main;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.flashnote.java.R;
import com.flashnote.java.data.model.Collection;
import com.flashnote.java.databinding.FragmentCollectionTabBinding;

public class CollectionTabFragment extends Fragment {
    private FragmentCollectionTabBinding binding;
    private CollectionAdapter adapter;
    private CollectionViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentCollectionTabBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        viewModel = new ViewModelProvider(this).get(CollectionViewModel.class);
        
        adapter = new CollectionAdapter(new CollectionAdapter.OnCollectionClickListener() {
            @Override
            public void onEdit(Collection collection) {
                showEditDialog(collection);
            }

            @Override
            public void onDelete(Collection collection) {
                showDeleteDialog(collection);
            }
        });
        
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerView.setAdapter(adapter);

        binding.addButton.setOnClickListener(v -> showCreateDialog());
        binding.fabAdd.setOnClickListener(v -> showCreateDialog());

        viewModel.getCollections().observe(getViewLifecycleOwner(), collections -> {
            adapter.submitList(collections);
            boolean empty = collections == null || collections.isEmpty();
            binding.emptyContainer.setVisibility(empty ? View.VISIBLE : View.GONE);
            binding.recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
        });

        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showCreateDialog() {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_collection_edit, null);
        EditText nameInput = dialogView.findViewById(R.id.nameInput);
        EditText descriptionInput = dialogView.findViewById(R.id.descriptionInput);

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.dialog_create_collection)
                .setView(dialogView)
                .setPositiveButton(R.string.action_create, (dialog, which) -> {
                    String name = nameInput.getText().toString().trim();
                    String description = descriptionInput.getText().toString().trim();
                    if (!name.isEmpty()) {
                        viewModel.createCollection(name, description);
                    } else {
                        Toast.makeText(requireContext(), R.string.error_name_required, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void showEditDialog(Collection collection) {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_collection_edit, null);
        EditText nameInput = dialogView.findViewById(R.id.nameInput);
        EditText descriptionInput = dialogView.findViewById(R.id.descriptionInput);
        
        nameInput.setText(collection.getName());
        if (collection.getDescription() != null) {
            descriptionInput.setText(collection.getDescription());
        }

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.dialog_edit_collection)
                .setView(dialogView)
                .setPositiveButton(R.string.action_save, (dialog, which) -> {
                    String name = nameInput.getText().toString().trim();
                    String description = descriptionInput.getText().toString().trim();
                    if (!name.isEmpty()) {
                        viewModel.updateCollection(collection.getId(), name, description);
                    } else {
                        Toast.makeText(requireContext(), R.string.error_name_required, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void showDeleteDialog(Collection collection) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.dialog_delete_collection)
                .setMessage(R.string.dialog_delete_collection_message)
                .setPositiveButton(R.string.action_delete, (dialog, which) -> {
                    viewModel.deleteCollection(collection.getId());
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
