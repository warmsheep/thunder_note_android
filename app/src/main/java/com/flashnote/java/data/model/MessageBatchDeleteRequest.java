package com.flashnote.java.data.model;

import java.util.List;

public class MessageBatchDeleteRequest {
    private List<Long> ids;

    public List<Long> getIds() {
        return ids;
    }

    public void setIds(List<Long> ids) {
        this.ids = ids;
    }
}
