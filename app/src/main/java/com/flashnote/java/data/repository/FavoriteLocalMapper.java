package com.flashnote.java.data.repository;

import com.flashnote.java.data.local.FavoriteLocalEntity;
import com.flashnote.java.data.model.FavoriteItem;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

final class FavoriteLocalMapper {

    List<FavoriteLocalEntity> toLocalList(List<FavoriteItem> items, long currentUserId) {
        List<FavoriteLocalEntity> result = new ArrayList<>();
        if (items == null) {
            return result;
        }
        for (FavoriteItem item : items) {
            FavoriteLocalEntity entity = toLocal(item, currentUserId);
            if (entity != null) {
                result.add(entity);
            }
        }
        return result;
    }

    FavoriteLocalEntity toLocal(FavoriteItem item, long currentUserId) {
        if (item == null || item.getId() == null) {
            return null;
        }
        FavoriteLocalEntity entity = new FavoriteLocalEntity();
        entity.setId(item.getId());
        entity.setMessageId(item.getMessageId());
        entity.setUserId(currentUserId);
        entity.setFlashNoteId(item.getFlashNoteId());
        entity.setFlashNoteTitle(item.getFlashNoteTitle());
        entity.setRole(item.getRole());
        entity.setContent(item.getContent());
        entity.setFlashNoteIcon(item.getFlashNoteIcon());
        entity.setMediaType(item.getMediaType());
        entity.setMediaUrl(item.getMediaUrl());
        entity.setFileName(item.getFileName());
        entity.setFileSize(item.getFileSize());
        entity.setMediaDuration(item.getMediaDuration());
        entity.setFavoritedAt(item.getFavoritedAt() == null ? null : item.getFavoritedAt().toString());
        entity.setMessageCreatedAt(item.getMessageCreatedAt() == null ? null : item.getMessageCreatedAt().toString());
        return entity;
    }

    List<FavoriteItem> toModelList(List<FavoriteLocalEntity> entities) {
        List<FavoriteItem> result = new ArrayList<>();
        if (entities == null) {
            return result;
        }
        for (FavoriteLocalEntity entity : entities) {
            FavoriteItem item = new FavoriteItem();
            item.setId(entity.getId());
            item.setMessageId(entity.getMessageId());
            item.setFlashNoteId(entity.getFlashNoteId());
            item.setFlashNoteTitle(entity.getFlashNoteTitle());
            item.setRole(entity.getRole());
            item.setContent(entity.getContent());
            item.setFlashNoteIcon(entity.getFlashNoteIcon());
            item.setMediaType(entity.getMediaType());
            item.setMediaUrl(entity.getMediaUrl());
            item.setFileName(entity.getFileName());
            item.setFileSize(entity.getFileSize());
            item.setMediaDuration(entity.getMediaDuration());
            if (entity.getFavoritedAt() != null && !entity.getFavoritedAt().trim().isEmpty()) {
                item.setFavoritedAt(LocalDateTime.parse(entity.getFavoritedAt()));
            }
            if (entity.getMessageCreatedAt() != null && !entity.getMessageCreatedAt().trim().isEmpty()) {
                item.setMessageCreatedAt(LocalDateTime.parse(entity.getMessageCreatedAt()));
            }
            result.add(item);
        }
        return result;
    }
}
