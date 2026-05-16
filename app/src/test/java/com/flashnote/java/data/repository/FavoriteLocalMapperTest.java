package com.flashnote.java.data.repository;

import com.flashnote.java.data.local.FavoriteLocalEntity;
import com.flashnote.java.data.model.CardItem;
import com.flashnote.java.data.model.CardPayload;
import com.flashnote.java.data.model.FavoriteItem;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class FavoriteLocalMapperTest {
    @Test
    public void toLocalAndModel_preservesCompositePayload() {
        CardItem image = new CardItem();
        image.setType("IMAGE");
        image.setUrl("u1/image.jpg");

        CardPayload payload = new CardPayload();
        payload.setCardType("COMPOSITE_CARD");
        payload.setTitle("卡片");
        payload.setItems(List.of(image));

        FavoriteItem item = new FavoriteItem();
        item.setId(1L);
        item.setMessageId(10L);
        item.setMediaType("COMPOSITE");
        item.setPayload(payload);

        FavoriteLocalMapper mapper = new FavoriteLocalMapper();
        FavoriteLocalEntity entity = mapper.toLocal(item, 1001L);
        List<FavoriteItem> models = mapper.toModelList(List.of(entity));

        assertNotNull(entity.getPayloadJson());
        assertTrue(entity.getPayloadJson().contains("u1/image.jpg"));
        assertEquals(1, models.size());
        assertNotNull(models.get(0).getPayload());
        assertEquals("卡片", models.get(0).getPayload().getTitle());
        assertEquals("IMAGE", models.get(0).getPayload().getItems().get(0).getType());
        assertEquals("u1/image.jpg", models.get(0).getPayload().getItems().get(0).getUrl());
    }
}
