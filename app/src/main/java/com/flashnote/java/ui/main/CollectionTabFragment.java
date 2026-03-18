package com.flashnote.java.ui.main;

import android.app.AlertDialog;
import android.os.Bundle;
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
import com.flashnote.java.data.model.Collection;
import com.flashnote.java.data.model.FlashNote;
import com.flashnote.java.databinding.FragmentCollectionTabBinding;
import com.flashnote.java.ui.navigation.ShellNavigator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CollectionTabFragment extends Fragment {
    private FragmentCollectionTabBinding binding;
    private CollectionAdapter adapter;
    private CollectionViewModel viewModel;
    private FlashNoteViewModel flashNoteViewModel;
    private List<Collection> latestCollections = new ArrayList<>();
    private List<FlashNote> latestNotes = new ArrayList<>();

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
        flashNoteViewModel = new ViewModelProvider(this).get(FlashNoteViewModel.class);
        
        adapter = new CollectionAdapter(new CollectionAdapter.OnFlashNoteClickListener() {
            @Override
            public void onOpenFlashNote(FlashNote item) {
                if (getActivity() instanceof ShellNavigator navigator) {
                    navigator.openChat(item.getId(), item.getTitle());
                }
            }

            @Override
            public void onEditCollection(CollectionAdapter.CollectionGroup group) {
                showRenameDialog(group);
            }

            @Override
            public void onDeleteCollection(CollectionAdapter.CollectionGroup group) {
                showDeleteDialog(group);
            }
        });
        
        android.content.Context ctx = getContext();
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
        binding.addButton.setVisibility(View.GONE);
        binding.fabAdd.setVisibility(View.GONE);

        viewModel.getCollections().observe(getViewLifecycleOwner(), collections -> {
            latestCollections = collections == null ? new ArrayList<>() : new ArrayList<>(collections);
            renderGroups();
        });

        flashNoteViewModel.getNotes().observe(getViewLifecycleOwner(), notes -> {
            latestNotes = notes == null ? new ArrayList<>() : new ArrayList<>(notes);
            renderGroups();
        });

        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), error -> {
            android.content.Context context = getContext();
            if (context != null && error != null && !error.isEmpty()) {
                Toast.makeText(context, error, Toast.LENGTH_SHORT).show();
                viewModel.clearError();
            }
        });
    }

    private void renderGroups() {
        List<CollectionAdapter.CollectionGroup> groups = buildGroups(latestCollections, latestNotes);
        adapter.submitList(groups);
        boolean empty = groups.isEmpty();
        binding.emptyContainer.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private List<CollectionAdapter.CollectionGroup> buildGroups(List<Collection> collections, List<FlashNote> notes) {
        Map<String, List<FlashNote>> grouped = new LinkedHashMap<>();
        for (Collection collection : collections) {
            String name = normalizeName(collection.getName());
            if (name != null && !grouped.containsKey(name)) {
                grouped.put(name, new ArrayList<>());
            }
        }

        List<FlashNote> uncategorized = new ArrayList<>();
        for (FlashNote note : notes) {
            String collectionName = normalizeName(note.getTags());
            if (collectionName == null) {
                uncategorized.add(note);
                continue;
            }
            grouped.computeIfAbsent(collectionName, key -> new ArrayList<>()).add(note);
        }

        List<CollectionAdapter.CollectionGroup> result = new ArrayList<>();
        for (Map.Entry<String, List<FlashNote>> entry : grouped.entrySet()) {
            result.add(new CollectionAdapter.CollectionGroup(findCollectionByName(entry.getKey()), entry.getKey(), entry.getValue()));
        }
        if (!uncategorized.isEmpty()) {
            result.add(new CollectionAdapter.CollectionGroup(null, "未分类", uncategorized));
        }
        return result;
    }

    private String normalizeName(String rawName) {
        if (rawName == null) {
            return null;
        }
        String trimmed = rawName.trim().replaceAll("\\s+", " ");
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void showRenameDialog(CollectionAdapter.CollectionGroup group) {
        if (!isAdded()) {
            return;
        }
        android.content.Context ctx = getContext();
        if (ctx == null) {
            return;
        }
        EditText input = new EditText(ctx);
        input.setText(group.getName());
        input.setSelection(group.getName().length());
        int padding = (int) (16 * ctx.getResources().getDisplayMetrics().density);
        input.setPadding(padding, padding, padding, padding);

        Collection resolvedTarget = group.getSource();
        if (resolvedTarget == null) {
            resolvedTarget = findCollectionByName(group.getName());
        }
        if (resolvedTarget == null) {
            android.content.Context context = getContext();
            if (context != null) {
                Toast.makeText(context, "未找到合集记录，暂不可编辑", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        final Collection target = resolvedTarget;

        new AlertDialog.Builder(ctx)
                .setTitle("重命名合集")
                .setView(input)
                .setPositiveButton("保存", (dialog, which) -> {
                    String newName = normalizeName(input.getText().toString());
                    if (newName == null) {
                        android.content.Context context = getContext();
                        if (context != null) {
                            Toast.makeText(context, "请输入合集名称", Toast.LENGTH_SHORT).show();
                        }
                        return;
                    }
                    String oldName = group.getName();
                    viewModel.updateCollection(target.getId(), newName, target.getDescription(), () -> {
                        if (!isAdded() || getActivity() == null) {
                            return;
                        }
                        getActivity().runOnUiThread(() -> {
                            if (!isAdded()) {
                                return;
                            }
                            flashNoteViewModel.renameCollectionLocally(oldName, newName);
                            flashNoteViewModel.refresh();
                            renderGroups();
                            android.content.Context context = getContext();
                            if (context != null) {
                                Toast.makeText(context, "合集已重命名", Toast.LENGTH_SHORT).show();
                            }
                        });
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showDeleteDialog(CollectionAdapter.CollectionGroup group) {
        if (!isAdded()) {
            return;
        }
        android.content.Context ctx = getContext();
        if (ctx == null) {
            return;
        }
        Collection resolvedTarget = group.getSource();
        if (resolvedTarget == null) {
            resolvedTarget = findCollectionByName(group.getName());
        }
        if (resolvedTarget == null) {
            android.content.Context context = getContext();
            if (context != null) {
                Toast.makeText(context, "未找到合集记录，暂不可删除", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        final Collection target = resolvedTarget;

        new AlertDialog.Builder(ctx)
                .setTitle("删除合集")
                .setMessage("删除后，归属到该合集的闪记会变为未分类。确定继续吗？")
                .setPositiveButton("删除", (dialog, which) -> viewModel.deleteCollection(target.getId(), () -> {
                    if (!isAdded() || getActivity() == null) {
                        return;
                    }
                    getActivity().runOnUiThread(() -> {
                        if (!isAdded()) {
                            return;
                        }
                        flashNoteViewModel.clearCollectionLocally(group.getName());
                        flashNoteViewModel.refresh();
                        renderGroups();
                        android.content.Context context = getContext();
                        if (context != null) {
                            Toast.makeText(context, "合集已删除", Toast.LENGTH_SHORT).show();
                        }
                    });
                }))
                .setNegativeButton("取消", null)
                .show();
    }

    @Nullable
    private Collection findCollectionByName(String name) {
        String normalizedTarget = normalizeName(name);
        if (normalizedTarget == null) {
            return null;
        }
        for (Collection collection : latestCollections) {
            String normalized = normalizeName(collection.getName());
            if (normalizedTarget.equalsIgnoreCase(normalized)) {
                return collection;
            }
        }
        return null;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
