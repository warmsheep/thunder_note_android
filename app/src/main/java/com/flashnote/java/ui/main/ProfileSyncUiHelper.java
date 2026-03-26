package com.flashnote.java.ui.main;

final class ProfileSyncUiHelper {

    String buildSyncHintText(boolean syncInProgress, int pendingSyncCount) {
        if (syncInProgress) {
            return "正在同步中，请稍候...";
        }
        if (pendingSyncCount <= 0) {
            return "已同步，点击可手动刷新";
        }
        if (pendingSyncCount == 1) {
            return "有 1 条待同步记录";
        }
        return "有 " + pendingSyncCount + " 条待同步记录";
    }

    String buildSyncBadgeText(int pendingSyncCount) {
        return pendingSyncCount > 99 ? "99+" : String.valueOf(pendingSyncCount);
    }
}
