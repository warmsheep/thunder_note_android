package com.flashnote.java.ui.main;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.MenuItem;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.view.MenuItemCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.flashnote.java.DebugLog;
import com.flashnote.java.R;
import com.flashnote.java.databinding.FragmentMainShellBinding;

public class MainShellFragment extends Fragment {
    private static final String KEY_SELECTED_TAB = "selected_tab";
    private static final String TAG_FLASHNOTE = "tab_flashnote";
    private static final String TAG_COLLECTION = "tab_collection";
    private static final String TAG_CONTACT = "tab_contact";
    private static final String TAG_FAVORITE = "tab_favorite";
    private static final String TAG_PROFILE = "tab_profile";

    private FragmentMainShellBinding binding;
    private int selectedTabId = R.id.tab_flashnote;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentMainShellBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        try {
            binding.bottomNav.setItemActiveIndicatorEnabled(false);
            applyEmojiIcons();
            binding.bottomNav.post(this::disableBottomNavLongPressHints);
            binding.bottomNav.setOnItemSelectedListener(this::onTabSelected);

            if (savedInstanceState != null) {
                selectedTabId = savedInstanceState.getInt(KEY_SELECTED_TAB, R.id.tab_flashnote);
            }

            ContactViewModel contactViewModel = new ViewModelProvider(this).get(ContactViewModel.class);
            contactViewModel.getPendingRequestCount().observe(getViewLifecycleOwner(), count ->
                    updateContactBadge(count != null && count > 0));
            contactViewModel.refreshContacts();

            binding.bottomNav.setSelectedItemId(selectedTabId);
        } catch (RuntimeException exception) {
            DebugLog.e("MainShell", "MainShellFragment onViewCreated failed", exception);
            throw exception;
        }
    }

    public void updateContactBadge(boolean show) {
        com.google.android.material.badge.BadgeDrawable badge = binding.bottomNav.getOrCreateBadge(R.id.tab_contact);
        badge.setVisible(show);
        if (show) {
            badge.clearNumber();
            badge.setBackgroundColor(getResources().getColor(R.color.danger, null));
        }
    }

    private boolean onTabSelected(@NonNull MenuItem item) {
        selectedTabId = item.getItemId();
        switchContent(selectedTabId);
        return true;
    }

    private void switchContent(int tabId) {
        if (!isAdded()) {
            return;
        }

        androidx.fragment.app.FragmentManager fragmentManager = getChildFragmentManager();
        String targetTag = resolveTabTag(tabId);
        Fragment targetFragment = fragmentManager.findFragmentByTag(targetTag);
        if (targetFragment == null) {
            targetFragment = createTabFragment(tabId);
        }

        androidx.fragment.app.FragmentTransaction transaction = fragmentManager.beginTransaction()
                .setReorderingAllowed(true);

        java.util.List<Fragment> fragments = fragmentManager.getFragments();
        for (Fragment fragment : fragments) {
            if (fragment == null) {
                continue;
            }
            if (fragment == targetFragment) {
                continue;
            }
            transaction.hide(fragment);
        }

        if (targetFragment.isAdded()) {
            transaction.show(targetFragment);
        } else {
            transaction.add(R.id.mainContentContainer, targetFragment, targetTag);
        }

        if (getChildFragmentManager().isStateSaved()) {
            transaction.commitAllowingStateLoss();
        } else {
            transaction.commit();
        }
    }

    @NonNull
    private Fragment createTabFragment(int tabId) {
        if (tabId == R.id.tab_collection) {
            return new CollectionTabFragment();
        }
        if (tabId == R.id.tab_contact) {
            return new ContactTabFragment();
        }
        if (tabId == R.id.tab_favorite) {
            return new FavoriteTabFragment();
        }
        if (tabId == R.id.tab_profile) {
            return new ProfileTabFragment();
        }
        return new FlashNoteTabFragment();
    }

    @NonNull
    private String resolveTabTag(int tabId) {
        if (tabId == R.id.tab_collection) {
            return TAG_COLLECTION;
        }
        if (tabId == R.id.tab_contact) {
            return TAG_CONTACT;
        }
        if (tabId == R.id.tab_favorite) {
            return TAG_FAVORITE;
        }
        if (tabId == R.id.tab_profile) {
            return TAG_PROFILE;
        }
        return TAG_FLASHNOTE;
    }

    private void applyEmojiIcons() {
        binding.bottomNav.getMenu().findItem(R.id.tab_flashnote).setIcon(R.drawable.ic_nav_flashnote);
        binding.bottomNav.getMenu().findItem(R.id.tab_collection).setIcon(R.drawable.ic_nav_collection);
        binding.bottomNav.getMenu().findItem(R.id.tab_contact).setIcon(R.drawable.ic_nav_contact);
        binding.bottomNav.getMenu().findItem(R.id.tab_favorite).setIcon(R.drawable.ic_nav_favorite);
        binding.bottomNav.getMenu().findItem(R.id.tab_profile).setIcon(R.drawable.ic_nav_profile);
    }

    private void disableBottomNavLongPressHints() {
        int count = binding.bottomNav.getMenu().size();
        for (int i = 0; i < count; i++) {
            MenuItem item = binding.bottomNav.getMenu().getItem(i);
            item.setTooltipText(null);
            MenuItemCompat.setTooltipText(item, null);
            View itemView = binding.bottomNav.findViewById(item.getItemId());
            if (itemView != null) {
                itemView.setOnLongClickListener(v -> true);
                itemView.setLongClickable(true);
                itemView.setHapticFeedbackEnabled(false);
            }
        }
    }

    private void setEmojiIcon(int itemId, @NonNull String emoji) {
        MenuItem item = binding.bottomNav.getMenu().findItem(itemId);
        if (item == null) {
            return;
        }
        item.setIcon(createEmojiIcon(emoji));
    }

    @NonNull
    private BitmapDrawable createEmojiIcon(@NonNull String emoji) {
        int sizePx = dp(24);
        Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextSize(sp(18));

        Paint.FontMetrics metrics = paint.getFontMetrics();
        float x = sizePx / 2f;
        float y = sizePx / 2f - (metrics.ascent + metrics.descent) / 2f;
        canvas.drawText(emoji, x, y, paint);

        return new BitmapDrawable(getResources(), bitmap);
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        );
    }

    private float sp(int value) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                value,
                getResources().getDisplayMetrics()
        );
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_SELECTED_TAB, selectedTabId);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
