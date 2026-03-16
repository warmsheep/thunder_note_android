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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.flashnote.java.R;
import com.flashnote.java.databinding.FragmentMainShellBinding;

public class MainShellFragment extends Fragment {
    private static final String KEY_SELECTED_TAB = "selected_tab";

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

        binding.bottomNav.setItemIconTintList(null);
        applyEmojiIcons();
        binding.bottomNav.setOnItemSelectedListener(this::onTabSelected);

        if (savedInstanceState != null) {
            selectedTabId = savedInstanceState.getInt(KEY_SELECTED_TAB, R.id.tab_flashnote);
        }

        binding.bottomNav.setSelectedItemId(selectedTabId);
    }

    private boolean onTabSelected(@NonNull MenuItem item) {
        selectedTabId = item.getItemId();
        switchContent(selectedTabId);
        return true;
    }

    private void switchContent(int tabId) {
        Fragment fragment;
        if (tabId == R.id.tab_collection) {
            fragment = new CollectionTabFragment();
        } else if (tabId == R.id.tab_favorite) {
            fragment = new FavoriteTabFragment();
        } else if (tabId == R.id.tab_profile) {
            fragment = new ProfileTabFragment();
        } else {
            fragment = new FlashNoteTabFragment();
        }

        if (!isAdded()) {
            return;
        }
        androidx.fragment.app.FragmentTransaction transaction = getChildFragmentManager().beginTransaction()
                .replace(R.id.mainContentContainer, fragment)
                .setReorderingAllowed(true);
        if (getChildFragmentManager().isStateSaved()) {
            transaction.commitAllowingStateLoss();
        } else {
            transaction.commit();
        }
    }

    private void applyEmojiIcons() {
        binding.bottomNav.getMenu().findItem(R.id.tab_flashnote).setIcon(R.drawable.ic_nav_flashnote);
        binding.bottomNav.getMenu().findItem(R.id.tab_collection).setIcon(R.drawable.ic_nav_collection);
        binding.bottomNav.getMenu().findItem(R.id.tab_favorite).setIcon(R.drawable.ic_nav_favorite);
        binding.bottomNav.getMenu().findItem(R.id.tab_profile).setIcon(R.drawable.ic_nav_profile);
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
