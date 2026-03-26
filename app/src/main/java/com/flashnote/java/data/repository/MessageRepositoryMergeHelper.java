package com.flashnote.java.data.repository;

import androidx.annotation.Nullable;

import com.flashnote.java.DebugLog;
import com.flashnote.java.data.model.CardPayload;
import com.flashnote.java.data.model.Message;
import com.flashnote.java.data.model.PendingMessage;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

final class MessageRepositoryMergeHelper {
    private static final String MEDIA_TYPE_TEXT = "TEXT";
    private static final Gson GSON = new Gson();

    private MessageRepositoryMergeHelper() {
    }

    static List<Message> buildMergedMessages(List<Message> remoteMessages,
                                             List<PendingMessage> pendingMessages,
                                             java.util.Comparator<Message> comparator) {
        List<Message> merged = new ArrayList<>();
        HashSet<Long> remoteIds = new HashSet<>();
        HashSet<String> remoteClientRequestIds = new HashSet<>();
        List<Message> normalizedRemote = remoteMessages == null ? Collections.emptyList() : remoteMessages;
        for (Message remoteMessage : normalizedRemote) {
            if (remoteMessage == null) {
                continue;
            }
            if (remoteMessage.getId() != null) {
                if (!remoteIds.add(remoteMessage.getId())) {
                    continue;
                }
            }
            String clientRequestId = normalizeClientRequestId(remoteMessage.getClientRequestId());
            if (!clientRequestId.isEmpty()) {
                remoteClientRequestIds.add(clientRequestId);
            }
            merged.add(remoteMessage);
        }
        if (pendingMessages != null) {
            for (PendingMessage pendingMessage : pendingMessages) {
                if (pendingMessage == null || PendingMessageDispatcher.STATUS_SENT.equals(pendingMessage.getStatus())) {
                    continue;
                }
                Long serverMessageId = pendingMessage.getServerMessageId();
                if (serverMessageId != null && remoteIds.contains(serverMessageId)) {
                    continue;
                }
                String pendingClientRequestId = normalizeClientRequestId(pendingMessage.getClientRequestId());
                if (!pendingClientRequestId.isEmpty() && remoteClientRequestIds.contains(pendingClientRequestId)) {
                    continue;
                }
                if (matchesRemoteFingerprint(merged, pendingMessage)) {
                    continue;
                }
                merged.add(toUiMessage(pendingMessage));
            }
        }
        merged.sort(comparator);
        return merged;
    }

    static long messageSourceOrder(Message message) {
        return isPendingUiMessage(message) ? 1L : 0L;
    }

    static long messageSortTimestamp(Message message) {
        if (message == null) {
            return Long.MAX_VALUE;
        }
        Long localSortTimestamp = message.getLocalSortTimestamp();
        if (localSortTimestamp != null) {
            return localSortTimestamp;
        }
        LocalDateTime createdAt = message.getCreatedAt();
        if (createdAt == null) {
            return Long.MAX_VALUE;
        }
        return createdAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    static long messageStableTieBreaker(Message message) {
        if (message == null || message.getId() == null) {
            return Long.MAX_VALUE;
        }
        long id = message.getId();
        if (id < 0L) {
            return -id;
        }
        return id;
    }

    static boolean isPendingUiMessage(Message message) {
        return message != null && message.getId() != null && message.getId() < 0L;
    }

    static Message toUiMessage(PendingMessage pendingMessage) {
        Message message = new Message();
        message.setId(-pendingMessage.getLocalId());
        message.setFlashNoteId(pendingMessage.getFlashNoteId());
        message.setReceiverId(pendingMessage.getPeerUserId());
        message.setClientRequestId(pendingMessage.getClientRequestId());
        message.setContent(pendingMessage.getContent());
        message.setMediaType(pendingMessage.getMediaType());
        message.setMediaUrl(pendingMessage.getRemoteUrl() != null ? pendingMessage.getRemoteUrl() : pendingMessage.getLocalFilePath());
        message.setThumbnailUrl(pendingMessage.getThumbnailUrl() != null ? pendingMessage.getThumbnailUrl() : pendingMessage.getRemoteUrl());
        message.setFileName(pendingMessage.getFileName());
        message.setFileSize(pendingMessage.getFileSize());
        message.setMediaDuration(pendingMessage.getMediaDuration());
        message.setRole("user");
        message.setCreatedAt(LocalDateTime.ofInstant(Instant.ofEpochMilli(pendingMessage.getCreatedAt()), ZoneId.systemDefault()));
        message.setLocalSortTimestamp(pendingMessage.getCreatedAt());
        message.setUploading(!PendingMessageDispatcher.STATUS_FAILED.equals(pendingMessage.getStatus()));
        if (pendingMessage.getPayloadJson() != null && !pendingMessage.getPayloadJson().trim().isEmpty()) {
            try {
                message.setPayload(GSON.fromJson(pendingMessage.getPayloadJson(), CardPayload.class));
            } catch (JsonSyntaxException ex) {
                DebugLog.w("MessageRepo", "Failed to parse pending payloadJson: " + ex.getMessage());
            }
        }
        return message;
    }

    static String defaultMediaContent(String mediaType) {
        if ("IMAGE".equalsIgnoreCase(mediaType)) {
            return "[图片]";
        }
        if ("FILE".equalsIgnoreCase(mediaType)) {
            return "[文件]";
        }
        if ("VIDEO".equalsIgnoreCase(mediaType)) {
            return "[视频]";
        }
        if ("VOICE".equalsIgnoreCase(mediaType)) {
            return "[语音]";
        }
        if ("COMPOSITE".equalsIgnoreCase(mediaType)) {
            return "[卡片消息]";
        }
        return "[附件]";
    }

    private static boolean matchesRemoteFingerprint(List<Message> remoteMessages, PendingMessage pendingMessage) {
        if (remoteMessages == null || remoteMessages.isEmpty() || pendingMessage == null) {
            return false;
        }
        if (!MEDIA_TYPE_TEXT.equalsIgnoreCase(nullToEmpty(pendingMessage.getMediaType()))) {
            return false;
        }
        String pendingContent = nullToEmpty(pendingMessage.getContent()).trim();
        if (pendingContent.isEmpty()) {
            return false;
        }
        long pendingCreatedAt = pendingMessage.getCreatedAt();
        for (Message remoteMessage : remoteMessages) {
            if (remoteMessage == null) {
                continue;
            }
            if (!"user".equalsIgnoreCase(nullToEmpty(remoteMessage.getRole()))) {
                continue;
            }
            if (!MEDIA_TYPE_TEXT.equalsIgnoreCase(nullToEmpty(remoteMessage.getMediaType()))) {
                continue;
            }
            if (!pendingContent.equals(nullToEmpty(remoteMessage.getContent()).trim())) {
                continue;
            }
            long remoteTimestamp = messageSortTimestamp(remoteMessage);
            if (remoteTimestamp == Long.MAX_VALUE) {
                continue;
            }
            if (Math.abs(remoteTimestamp - pendingCreatedAt) <= 2_000L) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeClientRequestId(@Nullable String value) {
        return nullToEmpty(value).trim();
    }

    private static String nullToEmpty(@Nullable String value) {
        return value == null ? "" : value;
    }
}
