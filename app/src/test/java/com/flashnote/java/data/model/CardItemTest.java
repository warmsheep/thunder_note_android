package com.flashnote.java.data.model;

import com.google.gson.Gson;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CardItemTest {
    @Test
    public void gson_acceptsServiceFieldNames() {
        CardItem item = new Gson().fromJson("{\"type\":\"IMAGE\",\"url\":\"u1/a.jpg\"}", CardItem.class);

        assertEquals("IMAGE", item.getType());
        assertEquals("u1/a.jpg", item.getUrl());
    }

    @Test
    public void gson_acceptsClientFieldNamesFromIosCompositeRequest() {
        CardItem item = new Gson().fromJson("{\"mediaType\":\"VIDEO\",\"mediaUrl\":\"u1/v.mp4\",\"thumbnailUrl\":\"u1/t.jpg\"}", CardItem.class);

        assertEquals("VIDEO", item.getType());
        assertEquals("u1/v.mp4", item.getUrl());
        assertEquals("u1/t.jpg", item.getThumbnailUrl());
    }
}
