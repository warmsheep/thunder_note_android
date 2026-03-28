package com.flashnote.java.ui;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

/**
 * UI 线程安全执行工具。统一 Fragment 生命周期边界判断，避免每个 Fragment 重复实现。
 * <p>
 * 标准判断顺序：isAdded() → getContext() != null → binding != null（如果传入了 binding 引用）。
 * 满足条件后，通过 Handler 切换到主线程执行。
 */
public final class FragmentUiSafe {

    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private FragmentUiSafe() {}

    /**
     * 在 Fragment UI 仍存活时安全执行 action。
     * 自动切换到主线程，且仅在 Fragment 未 detached 时执行。
     *
     * @param fragment Fragment 引用
     * @param action   要执行的操作
     */
    public static void runIfUiAlive(@NonNull Fragment fragment, @NonNull Runnable action) {
        if (!fragment.isAdded() || fragment.getContext() == null) {
            return;
        }
        MAIN_HANDLER.post(action);
    }

    /**
     * 同上，但额外检查 binding 是否非空（适用于 ViewBinding 子类）。
     *
     * @param fragment Fragment 引用
     * @param binding  ViewBinding 引用（通常传 this.binding）
     * @param action   要执行的操作
     */
    public static <T> void runIfUiAlive(@NonNull Fragment fragment, T binding, @NonNull Runnable action) {
        if (!fragment.isAdded() || fragment.getContext() == null || binding == null) {
            return;
        }
        MAIN_HANDLER.post(action);
    }
}
