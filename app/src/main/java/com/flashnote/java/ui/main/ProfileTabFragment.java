package com.flashnote.java.ui.main;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.Editable;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.flashnote.java.FlashNoteApp;
import com.flashnote.java.TokenManager;
import com.flashnote.java.data.model.FavoriteItem;
import com.flashnote.java.data.model.UserProfile;
import com.flashnote.java.data.repository.FileRepository;
import com.flashnote.java.data.repository.MessageRepository;
import com.flashnote.java.data.repository.UserRepository;
import com.flashnote.java.R;
import com.flashnote.java.ui.media.MediaUrlResolver;
import com.flashnote.java.ui.auth.AuthViewModel;
import com.flashnote.java.ui.navigation.ShellNavigator;
import androidx.lifecycle.ViewModelProvider;

import com.yalantis.ucrop.UCrop;

import java.io.File;
import java.util.List;

public class ProfileTabFragment extends Fragment {
    private static final String PREFS_PROFILE = "profile_tab";
    private static final String PREF_KEY_RECORD_COUNT = "record_count";
    private com.flashnote.java.databinding.FragmentProfileTabBinding binding;
    private UserRepository userRepository;
    private FileRepository fileRepository;
    private TokenManager tokenManager;
    private UserProfile currentProfile;
    
    private static final String[] AVATAR_EMOJIS = {"💼", "📚", "❤️", "🌟", "🎯", "🚀", "🎨", "🎵", "📷", "🍕", "⚽", "😊"};
    private static final int AVATAR_MAX_SIZE = 512;

    // Activity result launchers
    private final ActivityResultLauncher<Intent> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    if (imageUri != null) {
                        startCrop(imageUri);
                    }
                }
            }
    );

    private final ActivityResultLauncher<Intent> ucropLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri resultUri = UCrop.getOutput(result.getData());
                    if (resultUri != null) {
                        handleCropResult(resultUri);
                    }
                }
            }
    );

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = com.flashnote.java.databinding.FragmentProfileTabBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        FlashNoteApp app = FlashNoteApp.getInstance();
        userRepository = app.getUserRepository();
        fileRepository = app.getFileRepository();
        tokenManager = app.getTokenManager();

        String username = tokenManager.getUsername();
        if (username != null) {
            binding.usernameText.setText(username);
        } else {
            binding.usernameText.setText("未知用户");
        }

        userRepository.getProfile().observe(getViewLifecycleOwner(), profile -> {
            if (profile != null) {
                currentProfile = profile;
                updateProfileUI(profile);
            }
        });

        loadCachedRecordCount();
        loadLocalAvatar();

        fetchProfile();
        setupClickActions();
    }

    private void setupClickActions() {
        if (binding == null) {
            return;
        }
        binding.avatarContainer.setOnClickListener(v -> openEditProfile());
        binding.menuChangePassword.setOnClickListener(v -> openChangePassword());
        binding.menuSettings.setOnClickListener(v -> openSettings());
        binding.menuDebug.setOnClickListener(v -> openDebug());
        binding.menuLogout.setOnClickListener(v -> logout());
    }

    private void openEditProfile() {
        if (!isAdded() || getActivity() == null) {
            return;
        }
        if (getActivity() instanceof ShellNavigator navigator) {
            navigator.openEditProfile();
        }
    }

    private void loadLocalAvatar() {
        if (!isAdded() || getContext() == null || binding == null) {
            return;
        }
        
        File avatarFile = new File(getContext().getFilesDir(), "avatar.jpg");
        if (avatarFile.exists()) {
            binding.avatarText.setVisibility(View.GONE);
            binding.avatarImage.setVisibility(View.VISIBLE);
            Glide.with(this)
                    .load(avatarFile)
                    .circleCrop()
                    .into(binding.avatarImage);
        }
    }

    private void loadStats() {
        FlashNoteApp app = FlashNoteApp.getInstance();
        
        List<?> notes = app.getFlashNoteRepository().getNotes().getValue();
        binding.flashNoteCount.setText(String.valueOf(notes != null ? notes.size() : 0));
        
        List<?> favorites = app.getFavoriteRepository().getFavorites().getValue();
        binding.favoriteCount.setText(String.valueOf(favorites != null ? favorites.size() : 0));
        
        app.getMessageRepository().countMessages(new MessageRepository.CountCallback() {
            @Override
            public void onSuccess(long count) {
                if (isAdded() && getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        binding.recordCount.setText(String.valueOf(count));
                        persistRecordCount(count);
                    });
                }
            }

            @Override
            public void onError(String message, int code) {
                if (isAdded() && getActivity() != null) {
                    getActivity().runOnUiThread(() -> binding.recordCount.setText("0"));
                }
            }
        });
    }

    private void fetchProfile() {
        userRepository.fetchProfile(new UserRepository.ProfileCallback() {
            @Override
            public void onSuccess(UserProfile profile) {
                if (isAdded() && getActivity() != null) {
                    getActivity().runOnUiThread(() -> loadStats());
                }
            }

            @Override
            public void onError(String message, int code) {
                if (!isAdded() || getActivity() == null) {
                    return;
                }
                getActivity().runOnUiThread(() -> {
                    android.content.Context context = getContext();
                    if (isAdded() && context != null) {
                        Toast.makeText(context, "获取资料失败：" + message, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void updateProfileUI(UserProfile profile) {
        if (!isAdded()) {
            return;
        }

        String nickname = profile.getNickname();
        if (nickname != null && !nickname.isBlank()) {
            binding.usernameText.setText(nickname.trim());
        } else {
            String username = tokenManager.getUsername();
            binding.usernameText.setText(username == null ? "未知用户" : username);
        }
        
        String bio = profile.getBio();
        binding.bioText.setText(bio != null && !bio.isEmpty() ? bio : "暂无简介");
        
        String avatar = profile.getAvatar();
        if (avatar != null && !avatar.isEmpty()) {
            if (avatar.startsWith("http") || avatar.contains("/")) {
                binding.avatarText.setVisibility(View.GONE);
                binding.avatarImage.setVisibility(View.VISIBLE);
                Glide.with(this)
                        .load(buildAvatarGlideSource(avatar))
                        .placeholder(R.drawable.bg_avatar_circle)
                        .error(R.drawable.bg_avatar_circle)
                        .circleCrop()
                        .into(binding.avatarImage);
            } else {
                binding.avatarImage.setVisibility(View.GONE);
                binding.avatarText.setVisibility(View.VISIBLE);
                binding.avatarText.setText(avatar);
            }
        } else {
            String username = tokenManager.getUsername();
            if (username != null && !username.isEmpty()) {
                binding.avatarImage.setVisibility(View.GONE);
                binding.avatarText.setVisibility(View.VISIBLE);
                binding.avatarText.setText(String.valueOf(username.charAt(0)));
            }
        }
    }

    private void showEditBioDialog() {
        if (!isAdded() || getContext() == null) {
            return;
        }

        android.content.Context context = getContext();
        EditText editText = new EditText(context);
        editText.setHint("请输入简介");
        editText.setMinLines(1);
        editText.setMaxLines(6);
        editText.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        editText.setSingleLine(false);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        editText.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL);
        editText.setBackgroundResource(R.drawable.bg_input_rounded);
        int horizontalPadding = (int) (14 * getResources().getDisplayMetrics().density);
        int verticalPadding = (int) (10 * getResources().getDisplayMetrics().density);
        editText.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding);
        editText.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        editText.setVerticalScrollBarEnabled(false);
        if (currentProfile != null && currentProfile.getBio() != null) {
            editText.setText(currentProfile.getBio());
            editText.setSelection(editText.getText().length());
        }
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                editText.post(() -> {
                    if (editText.getLayout() == null) {
                        return;
                    }
                    int lineCount = Math.max(1, Math.min(6, editText.getLineCount()));
                    editText.setLines(lineCount);
                });
            }
        });

        android.widget.FrameLayout container = new android.widget.FrameLayout(context);
        android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        );
        int margin = (int) (20 * getResources().getDisplayMetrics().density);
        params.setMargins(margin, 0, margin, 0);
        editText.setLayoutParams(params);
        container.addView(editText);

        TextView title = new TextView(context);
        int horizontal = (int) (20 * getResources().getDisplayMetrics().density);
        int top = (int) (18 * getResources().getDisplayMetrics().density);
        int bottom = (int) (8 * getResources().getDisplayMetrics().density);
        title.setPadding(horizontal, top, horizontal, bottom);
        title.setText("编辑简介");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        title.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL);
        title.setTextColor(getResources().getColor(R.color.text_primary, null));

        new AlertDialog.Builder(context)
            .setCustomTitle(title)
            .setView(container)
            .setPositiveButton("保存", (dialog, which) -> {
                String newBio = editText.getText().toString().trim();
                updateBio(newBio);
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void showEditProfileDialog() {
        if (!isAdded() || getContext() == null) {
            return;
        }
        android.content.Context context = getContext();
        android.widget.LinearLayout container = new android.widget.LinearLayout(context);
        container.setOrientation(android.widget.LinearLayout.VERTICAL);
        int padding = (int) (18 * getResources().getDisplayMetrics().density);
        container.setPadding(padding, padding, padding, padding);

        EditText nicknameInput = new EditText(context);
        nicknameInput.setHint("昵称");
        nicknameInput.setSingleLine(true);
        nicknameInput.setBackgroundResource(R.drawable.bg_input_rounded);
        String initialNickname = currentProfile == null ? null : currentProfile.getNickname();
        if (initialNickname != null) {
            nicknameInput.setText(initialNickname);
        }
        container.addView(nicknameInput);

        EditText bioInput = new EditText(context);
        bioInput.setHint("简介");
        bioInput.setMinLines(2);
        bioInput.setMaxLines(5);
        bioInput.setBackgroundResource(R.drawable.bg_input_rounded);
        int topMargin = (int) (10 * getResources().getDisplayMetrics().density);
        android.widget.LinearLayout.LayoutParams bioParams = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        bioParams.topMargin = topMargin;
        bioInput.setLayoutParams(bioParams);
        if (currentProfile != null && currentProfile.getBio() != null) {
            bioInput.setText(currentProfile.getBio());
        }
        container.addView(bioInput);

        TextView avatarAction = new TextView(context);
        avatarAction.setText("修改头像");
        avatarAction.setTextColor(getResources().getColor(R.color.primary, null));
        avatarAction.setPadding(0, topMargin, 0, 0);
        avatarAction.setOnClickListener(v -> showAvatarPicker());
        container.addView(avatarAction);

        new AlertDialog.Builder(context)
                .setTitle("修改个人资料")
                .setView(container)
                .setPositiveButton("保存", (dialog, which) -> {
                    if (currentProfile == null) {
                        currentProfile = new UserProfile();
                    }
                    currentProfile.setNickname(nicknameInput.getText() == null ? "" : nicknameInput.getText().toString().trim());
                    currentProfile.setBio(bioInput.getText() == null ? "" : bioInput.getText().toString().trim());
                    userRepository.updateProfile(currentProfile, new UserRepository.ProfileCallback() {
                        @Override
                        public void onSuccess(UserProfile profile) {
                            if (!isAdded() || getActivity() == null) {
                                return;
                            }
                            getActivity().runOnUiThread(() -> {
                                currentProfile = profile;
                                updateProfileUI(profile);
                                android.content.Context ctx = getContext();
                                if (ctx != null) {
                                    Toast.makeText(ctx, "资料已保存", Toast.LENGTH_SHORT).show();
                                }
                            });
                        }

                        @Override
                        public void onError(String message, int code) {
                            if (!isAdded() || getActivity() == null) {
                                return;
                            }
                            getActivity().runOnUiThread(() -> {
                                android.content.Context ctx = getContext();
                                if (ctx != null) {
                                    Toast.makeText(ctx, "保存失败：" + message, Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void updateBio(String bio) {
        if (userRepository == null) {
            return;
        }
        
        if (currentProfile == null) {
            currentProfile = new UserProfile();
        }
        currentProfile.setBio(bio);

        userRepository.updateProfile(currentProfile, new UserRepository.ProfileCallback() {
            @Override
            public void onSuccess(UserProfile profile) {
                if (!isAdded() || getActivity() == null) {
                    return;
                }
                getActivity().runOnUiThread(() -> {
                    if (!isAdded() || binding == null) {
                        return;
                    }
                    binding.bioText.setText(bio.isEmpty() ? "暂无简介" : bio);
                    android.content.Context context = getContext();
                    if (context != null) {
                        Toast.makeText(context, "简介已更新", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String message, int code) {
                if (!isAdded() || getActivity() == null) {
                    return;
                }
                getActivity().runOnUiThread(() -> {
                    android.content.Context context = getContext();
                    if (isAdded() && context != null) {
                        Toast.makeText(context, "更新失败：" + message, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void showAvatarPicker() {
        if (!isAdded() || getContext() == null) {
            return;
        }
        
        String[] options = {"从相册选择图片", "选择emoji头像"};
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getContext());
        builder.setTitle("选择头像");
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) {
                openGalleryPicker();
            } else if (which == 1) {
                showEmojiPicker();
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void openGalleryPicker() {
        if (!isAdded() || getContext() == null) {
            return;
        }
        
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        galleryLauncher.launch(intent);
    }

    private void startCrop(Uri sourceUri) {
        if (!isAdded() || getContext() == null) {
            return;
        }
        
        try {
            File outputDir = getContext().getCacheDir();
            File outputFile = new File(outputDir, "avatar_crop.jpg");
            
            UCrop.Options options = new UCrop.Options();
            options.setCircleDimmedLayer(true);
            options.setShowCropFrame(false);
            options.setShowCropGrid(false);
            options.setCompressionQuality(90);
            options.setToolbarTitle("裁剪头像");
            
            Intent ucropIntent = UCrop.of(sourceUri, Uri.fromFile(outputFile))
                    .withAspectRatio(1, 1)
                    .withMaxResultSize(AVATAR_MAX_SIZE, AVATAR_MAX_SIZE)
                    .withOptions(options)
                    .getIntent(getContext());
            
            ucropLauncher.launch(ucropIntent);
        } catch (Exception e) {
            android.content.Context context = getContext();
            if (context != null) {
                Toast.makeText(context, "无法打开图片裁剪", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void handleCropResult(Uri resultUri) {
        if (!isAdded() || getContext() == null) {
            return;
        }
        
        android.content.Context context = getContext();
        
        try {
            File avatarFile = new File(context.getFilesDir(), "avatar.jpg");

            try (java.io.InputStream inputStream = context.getContentResolver().openInputStream(resultUri);
                 java.io.FileOutputStream fos = new java.io.FileOutputStream(avatarFile)) {
                if (inputStream == null) {
                    Toast.makeText(context, "裁剪后的图片读取失败", Toast.LENGTH_SHORT).show();
                    return;
                }
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
            }
            
            uploadAvatar(avatarFile);
            
        } catch (Exception e) {
            Toast.makeText(context, "保存头像失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void uploadAvatar(File avatarFile) {
        if (!isAdded() || getContext() == null || fileRepository == null) {
            return;
        }

        fileRepository.upload(avatarFile, new FileRepository.FileCallback() {
            @Override
            public void onSuccess(String avatarUrl) {
                updateAvatarToServer(avatarUrl);
            }

            @Override
            public void onError(String message, int code) {
                runIfUiAlive(() -> {
                    android.content.Context ctx = getContext();
                    if (ctx != null) {
                        Toast.makeText(ctx, "上传头像失败: " + message, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void updateAvatarToServer(String avatarUrl) {
        if (userRepository == null || !isAdded()) {
            return;
        }
        
        userRepository.updateAvatar(avatarUrl, new UserRepository.ProfileCallback() {
            @Override
            public void onSuccess(UserProfile profile) {
                currentProfile = profile;
                runIfUiAlive(() -> {
                    if (binding != null) {
                        loadAvatarImage(avatarUrl);
                        Toast.makeText(getContext(), "头像已更新", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String message, int code) {
                runIfUiAlive(() -> {
                    android.content.Context ctx = getContext();
                    if (ctx != null) {
                        Toast.makeText(ctx, "更新头像失败: " + message, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void loadAvatarImage(String avatarUrl) {
        if (!isAdded() || getContext() == null || binding == null) {
            return;
        }
        
        if (avatarUrl != null && (avatarUrl.startsWith("http") || avatarUrl.contains("/"))) {
            binding.avatarText.setVisibility(View.GONE);
            binding.avatarImage.setVisibility(View.VISIBLE);
            Glide.with(this)
                    .load(buildAvatarGlideSource(avatarUrl))
                    .placeholder(R.drawable.bg_avatar_circle)
                    .error(R.drawable.bg_avatar_circle)
                    .circleCrop()
                    .into(binding.avatarImage);
        } else {
            binding.avatarImage.setVisibility(View.GONE);
            binding.avatarText.setVisibility(View.VISIBLE);
        }
    }

    private void showEmojiPicker() {
        if (!isAdded() || getContext() == null) {
            return;
        }
        
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getContext());
        builder.setTitle("选择头像");

        GridLayout gridLayout = new GridLayout(getContext());
        gridLayout.setColumnCount(4);
        gridLayout.setPadding(32, 32, 32, 32);
        final String[] selectedEmoji = {null};
        final TextView[] selectedView = {null};
        
        for (String emoji : AVATAR_EMOJIS) {
            TextView textView = new TextView(getContext());
            textView.setText(emoji);
            textView.setTextSize(28f);
            textView.setGravity(android.view.Gravity.CENTER);
            
            int size = (int) (56 * getResources().getDisplayMetrics().density);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = size;
            params.height = size;
            params.setMargins(8, 8, 8, 8);
            textView.setLayoutParams(params);
            
            textView.setOnClickListener(v -> {
                selectedEmoji[0] = emoji;
                if (selectedView[0] != null) {
                    selectedView[0].setBackground(null);
                }
                selectedView[0] = textView;
                textView.setBackgroundResource(R.drawable.bg_logo_primary_soft);
            });
            
            gridLayout.addView(textView);
        }
        
        builder.setView(gridLayout);
        builder.setPositiveButton("确定", (dialog, which) -> {
            if (selectedEmoji[0] == null) {
                android.content.Context context = getContext();
                if (context != null) {
                    Toast.makeText(context, "请先选择一个头像", Toast.LENGTH_SHORT).show();
                }
                return;
            }
            updateAvatarWithEmoji(selectedEmoji[0]);
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private Object buildAvatarGlideSource(String avatarValue) {
        String resolved = MediaUrlResolver.resolve(avatarValue);
        String token = tokenManager.getAccessToken();
        if (token == null || token.isEmpty()) {
            return resolved;
        }
        return new GlideUrl(resolved, new LazyHeaders.Builder()
                .addHeader("Authorization", "Bearer " + token)
                .build());
    }

    private void loadCachedRecordCount() {
        if (!isAdded() || getContext() == null || binding == null) {
            return;
        }
        long cached = getContext().getSharedPreferences(PREFS_PROFILE, android.content.Context.MODE_PRIVATE)
                .getLong(PREF_KEY_RECORD_COUNT, -1L);
        if (cached >= 0) {
            binding.recordCount.setText(String.valueOf(cached));
        }
    }

    private void persistRecordCount(long count) {
        if (!isAdded() || getContext() == null) {
            return;
        }
        getContext().getSharedPreferences(PREFS_PROFILE, android.content.Context.MODE_PRIVATE)
                .edit()
                .putLong(PREF_KEY_RECORD_COUNT, count)
                .apply();
    }

    private void updateAvatarWithEmoji(String emoji) {
        if (userRepository == null || !isAdded()) {
            return;
        }
        
        userRepository.updateAvatar(emoji, new UserRepository.ProfileCallback() {
            @Override
            public void onSuccess(UserProfile profile) {
                currentProfile = profile;
                runIfUiAlive(() -> {
                    if (binding != null) {
                        clearLocalAvatarCache();
                        currentProfile.setAvatar(emoji);
                        binding.avatarImage.setVisibility(View.GONE);
                        binding.avatarText.setVisibility(View.VISIBLE);
                        binding.avatarText.setText(emoji);
                        Toast.makeText(getContext(), "头像已更新", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String message, int code) {
                runIfUiAlive(() -> {
                    android.content.Context context = getContext();
                    if (isAdded() && context != null) {
                        Toast.makeText(context, "更新头像失败：" + message, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void runIfUiAlive(@NonNull Runnable action) {
        if (!isAdded() || getActivity() == null) {
            return;
        }
        getActivity().runOnUiThread(action);
    }

    private void clearLocalAvatarCache() {
        if (!isAdded() || getContext() == null) {
            return;
        }
        File avatarFile = new File(getContext().getFilesDir(), "avatar.jpg");
        if (avatarFile.exists()) {
            avatarFile.delete();
        }
    }

    private void openChangePassword() {
        if (!isAdded() || getActivity() == null) {
            return;
        }
        if (getActivity() instanceof ShellNavigator navigator) {
            navigator.openChangePassword();
        }
    }

    private void openSettings() {
        if (!isAdded() || getActivity() == null) {
            return;
        }
        if (getActivity() instanceof ShellNavigator navigator) {
            navigator.openSettings();
        }
    }

    private void openDebug() {
        if (!isAdded() || getActivity() == null) {
            return;
        }
        if (getActivity() instanceof ShellNavigator navigator) {
            navigator.openDebug();
        }
    }

    private void logout() {
        if (!isAdded() || getActivity() == null || getActivity().isFinishing()) {
            return;
        }
        AuthViewModel authViewModel = new ViewModelProvider(getActivity()).get(AuthViewModel.class);
        authViewModel.logout();
        if (getActivity() instanceof ShellNavigator navigator) {
            navigator.logoutToLogin();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (binding != null) {
            loadStats();
            fetchProfile();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
