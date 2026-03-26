package com.flashnote.java.ui.main;

import android.app.AlertDialog;
import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.flashnote.java.R;

final class ProfileEditHelper {

    interface BioSaveHandler {
        void onSave(@NonNull String bio);
    }

    interface ProfileSaveHandler {
        void onSave(@NonNull String nickname, @NonNull String bio);

        void onAvatarRequested();
    }

    void showEditBioDialog(@NonNull Context context,
                           @NonNull android.content.res.Resources resources,
                           String initialBio,
                           @NonNull BioSaveHandler handler) {
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
        int horizontalPadding = (int) (14 * resources.getDisplayMetrics().density);
        int verticalPadding = (int) (10 * resources.getDisplayMetrics().density);
        editText.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding);
        editText.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        editText.setVerticalScrollBarEnabled(false);
        if (initialBio != null) {
            editText.setText(initialBio);
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
        int margin = (int) (20 * resources.getDisplayMetrics().density);
        params.setMargins(margin, 0, margin, 0);
        editText.setLayoutParams(params);
        container.addView(editText);

        TextView title = new TextView(context);
        int horizontal = (int) (20 * resources.getDisplayMetrics().density);
        int top = (int) (18 * resources.getDisplayMetrics().density);
        int bottom = (int) (8 * resources.getDisplayMetrics().density);
        title.setPadding(horizontal, top, horizontal, bottom);
        title.setText("编辑简介");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        title.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL);
        title.setTextColor(resources.getColor(R.color.text_primary, null));

        new AlertDialog.Builder(context)
                .setCustomTitle(title)
                .setView(container)
                .setPositiveButton("保存", (dialog, which) -> handler.onSave(editText.getText().toString().trim()))
                .setNegativeButton("取消", null)
                .show();
    }

    void showEditProfileDialog(@NonNull Context context,
                               @NonNull android.content.res.Resources resources,
                               String initialNickname,
                               String initialBio,
                               @NonNull ProfileSaveHandler handler) {
        android.widget.LinearLayout container = new android.widget.LinearLayout(context);
        container.setOrientation(android.widget.LinearLayout.VERTICAL);
        int padding = (int) (18 * resources.getDisplayMetrics().density);
        container.setPadding(padding, padding, padding, padding);

        EditText nicknameInput = new EditText(context);
        nicknameInput.setHint("昵称");
        nicknameInput.setSingleLine(true);
        nicknameInput.setBackgroundResource(R.drawable.bg_input_rounded);
        if (initialNickname != null) {
            nicknameInput.setText(initialNickname);
        }
        container.addView(nicknameInput);

        EditText bioInput = new EditText(context);
        bioInput.setHint("简介");
        bioInput.setMinLines(2);
        bioInput.setMaxLines(5);
        bioInput.setBackgroundResource(R.drawable.bg_input_rounded);
        int topMargin = (int) (10 * resources.getDisplayMetrics().density);
        android.widget.LinearLayout.LayoutParams bioParams = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        bioParams.topMargin = topMargin;
        bioInput.setLayoutParams(bioParams);
        if (initialBio != null) {
            bioInput.setText(initialBio);
        }
        container.addView(bioInput);

        TextView avatarAction = new TextView(context);
        avatarAction.setText("修改头像");
        avatarAction.setTextColor(resources.getColor(R.color.primary, null));
        avatarAction.setPadding(0, topMargin, 0, 0);
        avatarAction.setOnClickListener(v -> handler.onAvatarRequested());
        container.addView(avatarAction);

        new AlertDialog.Builder(context)
                .setTitle("修改个人资料")
                .setView(container)
                .setPositiveButton("保存", (dialog, which) -> handler.onSave(
                        nicknameInput.getText() == null ? "" : nicknameInput.getText().toString().trim(),
                        bioInput.getText() == null ? "" : bioInput.getText().toString().trim()))
                .setNegativeButton("取消", null)
                .show();
    }
}
