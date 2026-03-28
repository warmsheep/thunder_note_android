package com.flashnote.java.data.repository;

import com.flashnote.java.DebugLog;
import com.flashnote.java.data.local.FlashNoteLocalDao;
import com.flashnote.java.data.local.MessageLocalDao;
import com.flashnote.java.data.local.MessageLocalEntity;
import com.flashnote.java.data.model.CardPayload;
import com.flashnote.java.data.model.Message;
import com.flashnote.java.data.model.PendingMessage;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

class MessageRepositoryPersistenceHelper {
    private static final Gson GSON = new Gson();

    private final MessageLocalDao messageLocalDao;
    private final PendingMessageRepository pendingMessageRepository;
    private final ExecutorService pendingStorageExecutor;
    private final FlashNoteLocalDao flashNoteLocalDao;

    MessageRepositoryPersistenceHelper(MessageLocalDao messageLocalDao,
                                       PendingMessageRepository pendingMessageRepository,
                                       ExecutorService pendingStorageExecutor,
                                       FlashNoteLocalDao flashNoteLocalDao) {
        this.messageLocalDao = messageLocalDao;
        this.pendingMessageRepository = pendingMessageRepository;
        this.pendingStorageExecutor = pendingStorageExecutor;
        this.flashNoteLocalDao = flashNoteLocalDao;
    }

    void persistRemoteMessages(long conversationKey, List<Message> messages, boolean replaceConversation) {
        List<MessageLocalEntity> entities = toLocalMessageList(conversationKey, messages);
        pendingStorageExecutor.execute(() -> {
            Map<String, String> createdAtByClientRequestId = new HashMap<>();
            Map<Long, String> createdAtByMessageId = new HashMap<>();
            Map<String, Integer> durationByClientRequestId = new HashMap<>();
            Map<Long, Integer> durationByMessageId = new HashMap<>();
            if (replaceConversation) {
                List<MessageLocalEntity> existingEntities = messageLocalDao.getByConversationKeyNow(conversationKey);
                for (MessageLocalEntity existing : existingEntities) {
                    if (existing == null) {
                        continue;
                    }
                    if (existing.getClientRequestId() != null && !existing.getClientRequestId().trim().isEmpty() && existing.getCreatedAt() != null) {
                        createdAtByClientRequestId.put(existing.getClientRequestId(), existing.getCreatedAt());
                    }
                    if (existing.getId() != null && existing.getCreatedAt() != null) {
                        createdAtByMessageId.put(existing.getId(), existing.getCreatedAt());
                    }
                    if (existing.getClientRequestId() != null
                            && !existing.getClientRequestId().trim().isEmpty()
                            && existing.getMediaDuration() != null
                            && existing.getMediaDuration() > 0) {
                        durationByClientRequestId.put(existing.getClientRequestId(), existing.getMediaDuration());
                    }
                    if (existing.getId() != null && existing.getMediaDuration() != null && existing.getMediaDuration() > 0) {
                        durationByMessageId.put(existing.getId(), existing.getMediaDuration());
                    }
                }
            }
            for (MessageLocalEntity entity : entities) {
                if (entity == null) {
                    continue;
                }
                String preservedCreatedAt = null;
                if (entity.getClientRequestId() != null && !entity.getClientRequestId().trim().isEmpty()) {
                    preservedCreatedAt = createdAtByClientRequestId.get(entity.getClientRequestId());
                }
                if (preservedCreatedAt == null && entity.getId() != null) {
                    preservedCreatedAt = createdAtByMessageId.get(entity.getId());
                }
                if (preservedCreatedAt != null && !preservedCreatedAt.trim().isEmpty()) {
                    entity.setCreatedAt(preservedCreatedAt);
                }
                Integer preservedDuration = null;
                if (entity.getClientRequestId() != null && !entity.getClientRequestId().trim().isEmpty()) {
                    preservedDuration = durationByClientRequestId.get(entity.getClientRequestId());
                }
                if ((preservedDuration == null || preservedDuration <= 0) && entity.getId() != null) {
                    preservedDuration = durationByMessageId.get(entity.getId());
                }
                if ((entity.getMediaDuration() == null || entity.getMediaDuration() <= 0)
                        && preservedDuration != null
                        && preservedDuration > 0) {
                    entity.setMediaDuration(preservedDuration);
                }
            }
            if (replaceConversation) {
                messageLocalDao.clearConversation(conversationKey);
            }
            if (!entities.isEmpty()) {
                messageLocalDao.upsertAll(entities);
            }
        });
    }

    void persistSingleMessage(long conversationKey, Message message) {
        MessageLocalEntity entity = toLocalMessage(conversationKey, message);
        if (entity == null) {
            return;
        }
        pendingStorageExecutor.execute(() -> {
            messageLocalDao.upsert(entity);
            updateFlashNotePreviewLocally(message);
        });
    }

    static void applyPendingMetadataToServerMessage(Message serverMessage, PendingMessage pendingMessage) {
        if (serverMessage == null || pendingMessage == null) {
            return;
        }
        if (serverMessage.getFlashNoteId() == null) {
            serverMessage.setFlashNoteId(pendingMessage.getFlashNoteId());
        }
        serverMessage.setLocalSortTimestamp(pendingMessage.getCreatedAt());
        if (pendingMessage.getCreatedAt() > 0L) {
            serverMessage.setCreatedAt(LocalDateTime.ofInstant(Instant.ofEpochMilli(pendingMessage.getCreatedAt()), ZoneId.systemDefault()));
        }
        if (serverMessage.getMediaType() == null) {
            serverMessage.setMediaType(pendingMessage.getMediaType());
        }
        if (serverMessage.getMediaUrl() == null) {
            serverMessage.setMediaUrl(pendingMessage.getRemoteUrl());
        }
        if (serverMessage.getThumbnailUrl() == null) {
            serverMessage.setThumbnailUrl(pendingMessage.getThumbnailUrl() != null ? pendingMessage.getThumbnailUrl() : pendingMessage.getRemoteUrl());
        }
        if (serverMessage.getMediaDuration() == null) {
            serverMessage.setMediaDuration(pendingMessage.getMediaDuration());
        }
        if (serverMessage.getFileName() == null) {
            serverMessage.setFileName(pendingMessage.getFileName());
        }
        if (serverMessage.getFileSize() == null) {
            serverMessage.setFileSize(pendingMessage.getFileSize());
        }
        if (serverMessage.getPayload() == null && pendingMessage.getPayloadJson() != null && !pendingMessage.getPayloadJson().trim().isEmpty()) {
            try {
                serverMessage.setPayload(GSON.fromJson(pendingMessage.getPayloadJson(), CardPayload.class));
            } catch (JsonSyntaxException ex) {
                DebugLog.w("MessageRepo", "Failed to apply pending payloadJson: " + ex.getMessage());
            }
        }
    }

    List<MessageLocalEntity> toLocalMessageList(long conversationKey, List<Message> messages) {
        List<MessageLocalEntity> entities = new ArrayList<>();
        if (messages == null) {
            return entities;
        }
        for (Message message : messages) {
            MessageLocalEntity entity = toLocalMessage(conversationKey, message);
            if (entity != null) {
                entities.add(entity);
            }
        }
        return entities;
    }

    MessageLocalEntity toLocalMessage(long conversationKey, Message message) {
        if (message == null || message.getId() == null) {
            return null;
        }
        MessageLocalEntity entity = new MessageLocalEntity();
        entity.setId(message.getId());
        entity.setConversationKey(conversationKey);
        entity.setSenderId(message.getSenderId());
        entity.setReceiverId(message.getReceiverId());
        entity.setFlashNoteId(message.getFlashNoteId());
        entity.setClientRequestId(message.getClientRequestId());
        entity.setContent(message.getContent());
        entity.setReadStatus(message.getReadStatus());
        entity.setRole(message.getRole());
        entity.setCreatedAt(message.getCreatedAt() == null ? null : message.getCreatedAt().toString());
        entity.setMediaType(message.getMediaType());
        entity.setMediaUrl(message.getMediaUrl());
        entity.setMediaDuration(message.getMediaDuration());
        entity.setThumbnailUrl(message.getThumbnailUrl());
        entity.setFileName(message.getFileName());
        entity.setFileSize(message.getFileSize());
        entity.setPayloadJson(message.getPayload() == null ? null : GSON.toJson(message.getPayload()));
        return entity;
    }

    List<Message> toMessageList(List<MessageLocalEntity> entities) {
        List<Message> result = new ArrayList<>();
        if (entities == null) {
            return result;
        }
        for (MessageLocalEntity entity : entities) {
            Message message = new Message();
            message.setId(entity.getId());
            message.setSenderId(entity.getSenderId());
            message.setReceiverId(entity.getReceiverId());
            message.setFlashNoteId(entity.getFlashNoteId());
            message.setClientRequestId(entity.getClientRequestId());
            message.setContent(entity.getContent());
            message.setReadStatus(entity.getReadStatus());
            message.setRole(entity.getRole());
            if (entity.getCreatedAt() != null && !entity.getCreatedAt().trim().isEmpty()) {
                message.setCreatedAt(LocalDateTime.parse(entity.getCreatedAt()));
            }
            message.setMediaType(entity.getMediaType());
            message.setMediaUrl(entity.getMediaUrl());
            message.setMediaDuration(entity.getMediaDuration());
            message.setThumbnailUrl(entity.getThumbnailUrl());
            message.setFileName(entity.getFileName());
            message.setFileSize(entity.getFileSize());
            if (entity.getPayloadJson() != null && !entity.getPayloadJson().trim().isEmpty()) {
                try {
                    message.setPayload(GSON.fromJson(entity.getPayloadJson(), CardPayload.class));
                } catch (JsonSyntaxException ex) {
                    DebugLog.w("MessageRepo", "Failed to parse payloadJson: " + ex.getMessage());
                }
            }
            result.add(message);
        }
        return result;
    }

    private void updateFlashNotePreviewLocally(Message message) {
        if (flashNoteLocalDao == null || message == null || message.getFlashNoteId() == null) {
            return;
        }
        String preview = buildLatestMessagePreview(message);
        if (preview == null || preview.trim().isEmpty()) {
            return;
        }
        String updatedAt = message.getCreatedAt() == null
                ? LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                : message.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        flashNoteLocalDao.updateLatestMessage(message.getFlashNoteId(), preview, updatedAt);
    }

    private String buildLatestMessagePreview(Message message) {
        if (message == null) {
            return null;
        }
        String content = message.getContent();
        if (content != null) {
            String trimmed = content.trim();
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        String mediaType = message.getMediaType();
        if (mediaType == null) {
            return null;
        }
        switch (mediaType) {
            case "IMAGE":
                return "[图片]";
            case "VIDEO":
                return "[视频]";
            case "VOICE":
                return "[语音]";
            case "FILE":
                return "[文件]";
            case "COMPOSITE":
                return "[卡片]";
            default:
                return null;
        }
    }
}
