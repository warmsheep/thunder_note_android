package com.flashnote.java.ui.main;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.flashnote.java.DebugLog;
import com.flashnote.java.data.model.ContactSearchUser;
import com.flashnote.java.data.model.ContactUser;
import com.flashnote.java.data.model.FriendRequest;
import com.flashnote.java.databinding.FragmentContactTabBinding;
import com.flashnote.java.ui.FragmentUiSafe;
import com.flashnote.java.ui.navigation.ShellNavigator;

import java.util.ArrayList;
import java.util.List;

public class ContactTabFragment extends Fragment {
    private FragmentContactTabBinding binding;
    private ContactViewModel viewModel;
    private ContactAdapter contactAdapter;
    private ContactSearchAdapter searchAdapter;
    private FriendRequestAdapter requestAdapter;
    private boolean showingRequests = false;
    private boolean isSearchVisible = false;
    private String currentSearchQuery = "";
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;
    private boolean suppressSearchTextWatcher = false;
    private List<ContactUser> latestContacts = new ArrayList<>();
    private List<FriendRequest> latestRequests = new ArrayList<>();
    private List<ContactSearchUser> latestSearchResults = new ArrayList<>();

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
            if (!"FRIEND".equals(contact.getRelationStatus())) {
                toast("等待对方同意后即可开始聊天");
                return;
            }
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
        binding.searchButton.setOnClickListener(v -> toggleSearchContainer());
        binding.searchCloseButton.setOnClickListener(v -> closeSearchContainer());
        binding.searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (suppressSearchTextWatcher || !isSearchVisible) {
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
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                if (pendingSearch != null) {
                    searchHandler.removeCallbacks(pendingSearch);
                }
                performSearch(binding.searchInput.getText().toString());
                return true;
            }
            return false;
        });

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

        viewModel.getSearchResults().observe(getViewLifecycleOwner(), users -> {
            latestSearchResults = users == null ? new ArrayList<>() : new ArrayList<>(users);
            if (!isSearchVisible) {
                return;
            }
            showSearchResults(latestSearchResults);
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
                DebugLog.logHandledError("ContactTab", error);
                if (!DebugLog.isLikelyNetworkIssue(error)
                        && DebugLog.shouldShowToast("ContactTab:" + error, 2000L)) {
                    toast(error);
                }
                viewModel.clearError();
            }
        });

        switchTab(false);
        viewModel.refreshContacts();
    }

    @Override
    public void onResume() {
        super.onResume();
        reconcileVisibleState();
    }

    private void attachContactSwipeDelete() {
        contactAdapter.setOnDeleteClickListener((contact, action) -> {
            String title, message, confirmText;
            if ("PENDING_SENT".equals(action)) {
                title = "取消好友申请";
                message = "确定要取消该好友申请吗？";
                confirmText = "确定";
            } else if ("PENDING_RECEIVED".equals(action)) {
                title = "删除好友请求";
                message = "确定要删除该好友请求吗？";
                confirmText = "删除";
            } else {
                title = "删除联系人";
                message = "确定删除该联系人吗？";
                confirmText = "删除";
            }
            new android.app.AlertDialog.Builder(requireContext())
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton(confirmText, (dialog, which) -> viewModel.deleteContact(contact.getUserId()))
                    .setNegativeButton("取消", (dialog, which) -> {
                        contactAdapter.clearPendingDelete();
                        renderCurrentTab();
                    })
                    .setOnCancelListener(dialog -> {
                        contactAdapter.clearPendingDelete();
                        renderCurrentTab();
                    })
                    .show();
        });

        ItemTouchHelper.SimpleCallback callback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            private final ColorDrawable background = new ColorDrawable(Color.parseColor("#FF4444"));
            private final Drawable deleteIcon = androidx.core.content.ContextCompat.getDrawable(requireContext(), android.R.drawable.ic_menu_delete);
            private final int deleteAreaWidth = (int) (72 * requireContext().getResources().getDisplayMetrics().density);

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getBindingAdapterPosition();
                if (showingRequests || position < 0 || position >= latestContacts.size()) {
                    requestAdapter.notifyItemChanged(viewHolder.getBindingAdapterPosition());
                    return;
                }
                ContactUser contact = latestContacts.get(position);
                String status = contact.getRelationStatus();
                contactAdapter.setPendingDeleteContactId(contact.getUserId(), status);
            }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
                View itemView = viewHolder.itemView;
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);

                if (dX < 0) {
                    float revealWidth = Math.min(Math.abs(dX), deleteAreaWidth);
                    if (revealWidth > 0f) {
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
            }

            @Override
            public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder viewHolder) {
                return 0.4f;
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
            binding.emptyIcon.setImageResource(com.flashnote.java.R.drawable.ic_person_empty);
            binding.emptyIcon.setColorFilter(getResources().getColor(com.flashnote.java.R.color.text_secondary, null));
            binding.emptyTitleText.setText("暂无新的好友请求");
            binding.emptyContainer.setVisibility(empty ? View.VISIBLE : View.GONE);
            binding.recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
            return;
        }
        binding.recyclerView.setAdapter(contactAdapter);
        contactAdapter.submitList(new ArrayList<>(latestContacts));
        boolean empty = latestContacts.isEmpty();
        binding.emptyIcon.setImageResource(com.flashnote.java.R.drawable.ic_nav_contact);
        binding.emptyIcon.setColorFilter(getResources().getColor(com.flashnote.java.R.color.primary, null));
        binding.emptyTitleText.setText("暂无联系人");
        binding.emptyContainer.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void toggleSearchContainer() {
        if (isSearchVisible) {
            closeSearchContainer();
        } else {
            isSearchVisible = true;
            binding.searchContainer.setVisibility(View.VISIBLE);
            binding.searchInput.requestFocus();
            ensureSearchAdapter();
            binding.recyclerView.setAdapter(searchAdapter);
            if (!currentSearchQuery.isEmpty()) {
                showSearchResults(latestSearchResults);
            }
        }
    }

    private void closeSearchContainer() {
        if (pendingSearch != null) {
            searchHandler.removeCallbacks(pendingSearch);
            pendingSearch = null;
        }
        isSearchVisible = false;
        currentSearchQuery = "";
        latestSearchResults = new ArrayList<>();
        binding.searchContainer.setVisibility(View.GONE);
        suppressSearchTextWatcher = true;
        binding.searchInput.setText("");
        suppressSearchTextWatcher = false;
        if (searchAdapter != null) {
            searchAdapter.submitList(new ArrayList<>());
        }
        if (binding.recyclerView.getAdapter() != contactAdapter && binding.recyclerView.getAdapter() != requestAdapter) {
            binding.recyclerView.setAdapter(showingRequests ? requestAdapter : contactAdapter);
        }
        renderCurrentTab();
    }

    private void performSearch(String query) {
        if (binding == null || !isAdded() || getContext() == null) {
            return;
        }
        String keyword = query == null ? "" : query.trim();
        currentSearchQuery = keyword;
        if (!isSearchVisible) {
            return;
        }
        if (keyword.isEmpty()) {
            if (searchAdapter != null) {
                searchAdapter.submitList(new ArrayList<>());
            }
            binding.emptyContainer.setVisibility(View.GONE);
            binding.recyclerView.setVisibility(View.VISIBLE);
            return;
        }
        viewModel.searchContacts(keyword);
    }

    private void showSearchResults(List<ContactSearchUser> users) {
        if (!isSearchVisible) {
            return;
        }
        FragmentUiSafe.runIfUiAlive(this, binding, () -> {
            if (!isSearchVisible) {
                return;
            }
            ensureSearchAdapter();
            binding.searchContainer.setVisibility(View.VISIBLE);
            if (binding.recyclerView.getAdapter() != searchAdapter) {
                binding.recyclerView.setAdapter(searchAdapter);
            }
            if (searchAdapter != null) {
                searchAdapter.submitList(users);
            }
            boolean empty = users == null || users.isEmpty();
            binding.emptyContainer.setVisibility(empty ? View.VISIBLE : View.GONE);
            binding.emptyIcon.setImageResource(android.R.drawable.ic_menu_search);
            binding.emptyIcon.setColorFilter(getResources().getColor(com.flashnote.java.R.color.text_secondary, null));
            binding.emptyTitleText.setText("未找到相关用户");
            binding.recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
        });
    }

    private void ensureSearchAdapter() {
        if (searchAdapter != null) {
            return;
        }
        searchAdapter = new ContactSearchAdapter(user -> {
            viewModel.sendFriendRequest(user.getUserId());
            if (!currentSearchQuery.isEmpty()) {
                viewModel.searchContacts(currentSearchQuery);
            }
        });
    }

    private void reconcileVisibleState() {
        if (binding == null) {
            return;
        }
        if (isSearchVisible) {
            if (currentSearchQuery == null || currentSearchQuery.trim().isEmpty()) {
                closeSearchContainer();
                return;
            }
            showSearchResults(latestSearchResults);
            return;
        }
        binding.searchContainer.setVisibility(View.GONE);
        renderCurrentTab();
    }

    private void toast(String text) {
        if (getContext() != null) {
            Toast.makeText(getContext(), text, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroyView() {
        if (pendingSearch != null) {
            searchHandler.removeCallbacks(pendingSearch);
            pendingSearch = null;
        }
        super.onDestroyView();
        binding = null;
    }
}
