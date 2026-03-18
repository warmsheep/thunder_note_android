package com.flashnote.java.ui.main;

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
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.flashnote.java.data.model.ContactSearchUser;
import com.flashnote.java.data.model.ContactUser;
import com.flashnote.java.data.model.FriendRequest;
import com.flashnote.java.databinding.FragmentContactTabBinding;
import com.flashnote.java.ui.navigation.ShellNavigator;

import java.util.ArrayList;
import java.util.List;

public class ContactTabFragment extends Fragment {
    private FragmentContactTabBinding binding;
    private ContactViewModel viewModel;
    private ContactAdapter contactAdapter;
    private FriendRequestAdapter requestAdapter;
    private boolean showingRequests = false;
    private List<ContactUser> latestContacts = new ArrayList<>();
    private List<FriendRequest> latestRequests = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentContactTabBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(ContactViewModel.class);

        contactAdapter = new ContactAdapter(contact -> {
            if (contact.getUserId() == null || contact.getUserId() <= 0L) {
                toast("联系人信息无效");
                return;
            }
            String displayName = contact.getNickname() != null && !contact.getNickname().isBlank()
                    ? contact.getNickname()
                    : contact.getUsername();
            if (getActivity() instanceof ShellNavigator navigator) {
                navigator.openContactChat(contact.getUserId(), displayName == null ? "联系人" : displayName);
            }
        });

        requestAdapter = new FriendRequestAdapter(new FriendRequestAdapter.OnActionListener() {
            @Override
            public void onAccept(FriendRequest request) {
                viewModel.acceptRequest(request.getRequestId());
            }

            @Override
            public void onReject(FriendRequest request) {
                viewModel.rejectRequest(request.getRequestId());
            }
        });

        binding.recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerView.setAdapter(contactAdapter);
        attachContactSwipeDelete();

        binding.tabContacts.setOnClickListener(v -> switchTab(false));
        binding.tabRequestsWrap.setOnClickListener(v -> switchTab(true));
        binding.refreshButton.setOnClickListener(v -> viewModel.refreshContacts());
        binding.searchButton.setOnClickListener(v -> showSearchDialog());

        viewModel.getContacts().observe(getViewLifecycleOwner(), contacts -> {
            latestContacts = contacts == null ? new ArrayList<>() : new ArrayList<>(contacts);
            if (!showingRequests) {
                renderCurrentTab();
            }
        });

        viewModel.getFriendRequests().observe(getViewLifecycleOwner(), requests -> {
            latestRequests = requests == null ? new ArrayList<>() : new ArrayList<>(requests);
            if (showingRequests) {
                renderCurrentTab();
            }
        });

        viewModel.getPendingRequestCount().observe(getViewLifecycleOwner(), count -> {
            boolean showDot = count != null && count > 0;
            binding.tabRequestDot.setVisibility(showDot ? View.VISIBLE : View.GONE);
            if (getParentFragment() instanceof MainShellFragment mainShellFragment) {
                mainShellFragment.updateContactBadge(showDot);
            }
        });

        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                toast(error);
                viewModel.clearError();
            }
        });

        switchTab(false);
        viewModel.refreshContacts();
    }

    private void attachContactSwipeDelete() {
        ItemTouchHelper.SimpleCallback callback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                if (showingRequests) {
                    requestAdapter.notifyItemChanged(viewHolder.getBindingAdapterPosition());
                    return;
                }
                int position = viewHolder.getBindingAdapterPosition();
                if (position < 0 || position >= latestContacts.size()) {
                    return;
                }
                ContactUser contact = latestContacts.get(position);
                new android.app.AlertDialog.Builder(requireContext())
                        .setTitle("删除联系人")
                        .setMessage("确定删除该联系人吗？")
                        .setPositiveButton("删除", (dialog, which) -> viewModel.deleteContact(contact.getUserId()))
                        .setNegativeButton("取消", (dialog, which) -> renderCurrentTab())
                        .setOnCancelListener(dialog -> renderCurrentTab())
                        .show();
            }
        };
        new ItemTouchHelper(callback).attachToRecyclerView(binding.recyclerView);
    }

    private void switchTab(boolean requests) {
        showingRequests = requests;
        binding.tabContacts.setTextColor(getResources().getColor(requests ? com.flashnote.java.R.color.text_secondary : com.flashnote.java.R.color.primary, null));
        binding.tabRequests.setTextColor(getResources().getColor(requests ? com.flashnote.java.R.color.primary : com.flashnote.java.R.color.text_secondary, null));
        renderCurrentTab();
    }

    private void renderCurrentTab() {
        if (showingRequests) {
            binding.recyclerView.setAdapter(requestAdapter);
            requestAdapter.submitList(latestRequests);
            boolean empty = latestRequests.isEmpty();
            binding.emptyContainer.setVisibility(empty ? View.VISIBLE : View.GONE);
            binding.recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
            return;
        }
        binding.recyclerView.setAdapter(contactAdapter);
        contactAdapter.submitList(new ArrayList<>(latestContacts));
        boolean empty = latestContacts.isEmpty();
        binding.emptyContainer.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void showSearchDialog() {
        if (!isAdded() || getContext() == null) {
            return;
        }
        EditText input = new EditText(getContext());
        input.setHint("搜索联系人");
        new android.app.AlertDialog.Builder(requireContext())
                .setTitle("搜索联系人")
                .setView(input)
                .setPositiveButton("搜索", (dialog, which) -> {
                    String keyword = input.getText() == null ? "" : input.getText().toString().trim();
                    viewModel.searchContacts(keyword);
                    viewModel.getSearchResults().observe(getViewLifecycleOwner(), this::showSearchResultDialog);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showSearchResultDialog(List<ContactSearchUser> users) {
        if (!isAdded() || getContext() == null || users == null) {
            return;
        }
        RecyclerView recyclerView = new RecyclerView(requireContext());
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        ContactSearchAdapter adapter = new ContactSearchAdapter(user -> viewModel.sendFriendRequest(user.getUserId()));
        adapter.submitList(users);
        recyclerView.setAdapter(adapter);
        new android.app.AlertDialog.Builder(requireContext())
                .setTitle("搜索结果")
                .setView(recyclerView)
                .setNegativeButton("关闭", null)
                .show();
    }

    private void toast(String text) {
        if (getContext() != null) {
            Toast.makeText(getContext(), text, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
