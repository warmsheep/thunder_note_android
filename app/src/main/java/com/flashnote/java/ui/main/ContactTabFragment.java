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

import com.flashnote.java.R;
import com.flashnote.java.data.model.ContactUser;
import com.flashnote.java.databinding.FragmentContactTabBinding;
import com.flashnote.java.ui.navigation.ShellNavigator;

import java.util.ArrayList;
import java.util.List;

public class ContactTabFragment extends Fragment {
    private FragmentContactTabBinding binding;
    private List<ContactUser> latestContacts = new ArrayList<>();

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
        ContactViewModel viewModel = new ViewModelProvider(this).get(ContactViewModel.class);
        ContactAdapter adapter = new ContactAdapter(contact -> {
            if (contact.getUserId() == null || contact.getUserId() <= 0L) {
                android.content.Context context = getContext();
                if (context != null) {
                    Toast.makeText(context, "联系人信息无效", Toast.LENGTH_SHORT).show();
                }
                return;
            }
            String displayName = contact.getNickname() != null && !contact.getNickname().isBlank()
                    ? contact.getNickname()
                    : contact.getUsername();
            if (getActivity() instanceof ShellNavigator navigator) {
                navigator.openContactChat(contact.getUserId(), displayName == null ? "联系人" : displayName);
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

        viewModel.getContacts().observe(getViewLifecycleOwner(), contacts -> {
            latestContacts = contacts == null ? new ArrayList<>() : new ArrayList<>(contacts);
            renderContacts(adapter);
        });

        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), error -> {
            android.content.Context context = getContext();
            if (context != null && error != null && !error.isEmpty()) {
                Toast.makeText(context, error, Toast.LENGTH_SHORT).show();
                viewModel.clearError();
            }
        });

        binding.refreshButton.setOnClickListener(v -> viewModel.refreshContacts());
    }

    private void renderContacts(@NonNull ContactAdapter adapter) {
        adapter.submitList(new ArrayList<>(latestContacts));
        boolean empty = latestContacts.isEmpty();
        binding.emptyContainer.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
