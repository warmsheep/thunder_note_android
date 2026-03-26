package com.flashnote.java.data.repository;

import com.flashnote.java.data.local.CollectionLocalEntity;
import com.flashnote.java.data.model.Collection;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

final class CollectionLocalMapper {

    List<CollectionLocalEntity> toLocalList(List<Collection> collections) {
        List<CollectionLocalEntity> result = new ArrayList<>();
        if (collections == null) {
            return result;
        }
        for (Collection collection : collections) {
            CollectionLocalEntity entity = toLocal(collection);
            if (entity != null) {
                result.add(entity);
            }
        }
        return result;
    }

    CollectionLocalEntity toLocal(Collection collection) {
        if (collection == null || collection.getId() == null) {
            return null;
        }
        CollectionLocalEntity entity = new CollectionLocalEntity();
        entity.setId(collection.getId());
        entity.setUserId(collection.getUserId());
        entity.setName(collection.getName());
        entity.setDescription(collection.getDescription());
        entity.setCreatedAt(collection.getCreatedAt() == null ? null : collection.getCreatedAt().toString());
        entity.setUpdatedAt(collection.getUpdatedAt() == null ? null : collection.getUpdatedAt().toString());
        return entity;
    }

    List<Collection> toModelList(List<CollectionLocalEntity> entities) {
        List<Collection> result = new ArrayList<>();
        if (entities == null) {
            return result;
        }
        for (CollectionLocalEntity entity : entities) {
            Collection collection = new Collection();
            collection.setId(entity.getId());
            collection.setUserId(entity.getUserId());
            collection.setName(entity.getName());
            collection.setDescription(entity.getDescription());
            if (entity.getCreatedAt() != null && !entity.getCreatedAt().trim().isEmpty()) {
                collection.setCreatedAt(LocalDateTime.parse(entity.getCreatedAt()));
            }
            if (entity.getUpdatedAt() != null && !entity.getUpdatedAt().trim().isEmpty()) {
                collection.setUpdatedAt(LocalDateTime.parse(entity.getUpdatedAt()));
            }
            result.add(collection);
        }
        return result;
    }
}
