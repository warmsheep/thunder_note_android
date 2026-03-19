package com.flashnote.java.ui.chat;

import android.content.Context;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.flashnote.java.data.model.CardItem;
import com.flashnote.java.data.model.CardPayload;
import com.flashnote.java.data.model.Message;
import com.flashnote.java.databinding.ActivityCardDetailBinding;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;

public class CardDetailActivity extends AppCompatActivity {
    private static final String EXTRA_TITLE = "title";
    private static final String EXTRA_PAYLOAD_JSON = "payloadJson";
    private static final String EXTRA_USER_AVATAR = "userAvatar";
    private static final String EXTRA_USER_AVATAR_URL = "userAvatarUrl";
    private static final String EXTRA_PEER_AVATAR = "peerAvatar";

    private ActivityCardDetailBinding binding;
    private MessageAdapter adapter;
    private List<Message> messageList = new ArrayList<>();

    public static void start(Context context, String title, CardPayload payload) {
        start(context, title, payload, null, null, null);
    }

    public static void start(Context context,
                             String title,
                             CardPayload payload,
                             @Nullable String userAvatar,
                             @Nullable String userAvatarUrl,
                             @Nullable String peerAvatar) {
        if (payload == null) {
            return;
        }
        Intent intent = new Intent(context, CardDetailActivity.class);
        intent.putExtra(EXTRA_TITLE, title);
        intent.putExtra(EXTRA_PAYLOAD_JSON, new Gson().toJson(payload));
        intent.putExtra(EXTRA_USER_AVATAR, userAvatar);
        intent.putExtra(EXTRA_USER_AVATAR_URL, userAvatarUrl);
        intent.putExtra(EXTRA_PEER_AVATAR, peerAvatar);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCardDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        String title = getIntent().getStringExtra(EXTRA_TITLE);
        String payloadJson = getIntent().getStringExtra(EXTRA_PAYLOAD_JSON);

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(title != null ? title : "卡片消息详情");
        }
        binding.toolbar.setTitleTextColor(getColor(com.flashnote.java.R.color.text_primary));
        if (binding.toolbar.getNavigationIcon() != null) {
            binding.toolbar.getNavigationIcon().mutate().setColorFilter(getColor(com.flashnote.java.R.color.text_primary), PorterDuff.Mode.SRC_IN);
        }

        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(this, DividerItemDecoration.VERTICAL);
        binding.recyclerView.addItemDecoration(dividerItemDecoration);

        adapter = new MessageAdapter(null);
        adapter.setUserAvatar(getIntent().getStringExtra(EXTRA_USER_AVATAR));
        adapter.setUserAvatarUrl(getIntent().getStringExtra(EXTRA_USER_AVATAR_URL), this);
        adapter.setPeerAvatar(getIntent().getStringExtra(EXTRA_PEER_AVATAR));
        binding.recyclerView.setAdapter(adapter);

        loadMessages(payloadJson);
    }

    private void loadMessages(String payloadJson) {
        if (payloadJson == null || payloadJson.isEmpty()) {
            return;
        }
        
        try {
            Gson gson = new Gson();
            CardPayload payload = gson.fromJson(payloadJson, CardPayload.class);
            if (payload != null && payload.getItems() != null) {
                List<Message> detailMessages = new ArrayList<>();
                for (CardItem item : payload.getItems()) {
                    detailMessages.add(toDetailMessage(item));
                }
                messageList.clear();
                messageList.addAll(detailMessages);
                adapter.submitList(new ArrayList<>(detailMessages));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Message toDetailMessage(CardItem item) {
        Message message = new Message();
        message.setMediaType(item.getType());
        message.setContent(item.getContent());
        message.setMediaUrl(item.getUrl());
        message.setThumbnailUrl(item.getThumbnailUrl());
        message.setFileName(item.getFileName());
        message.setFileSize(item.getFileSize());
        message.setRole(item.getRole());
        message.setSenderId(item.getSenderId());
        return message;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
