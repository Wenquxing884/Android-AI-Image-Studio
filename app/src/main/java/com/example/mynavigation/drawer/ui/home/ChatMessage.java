package com.example.mynavigation.drawer.ui.home;

import android.graphics.Bitmap;

public class ChatMessage {

    public static final int TYPE_USER_TEXT = 0;
    public static final int TYPE_AI_IMAGE = 1;
    public static final int TYPE_SYSTEM = 2;
    public static final int TYPE_LOADING = 3;
    public static final int TYPE_USER_IMAGE = 4;
    private final int type;
    private final String content;
    private final Bitmap imageBitmap;
    private final String imageUrl;
    private final long timestamp;

    private ChatMessage(int type, String content, Bitmap imageBitmap, String imageUrl) {
        this.type = type;
        this.content = content;
        this.imageBitmap = imageBitmap;
        this.imageUrl = imageUrl;
        this.timestamp = System.currentTimeMillis();
    }

    public static ChatMessage userText(String prompt) {
        return new ChatMessage(TYPE_USER_TEXT, prompt, null, null);
    }

    public static ChatMessage userImage(String prompt, Bitmap bitmap) {
        return new ChatMessage(TYPE_USER_IMAGE, prompt, bitmap, null);
    }

    public static ChatMessage aiImage(Bitmap bitmap) {
        return new ChatMessage(TYPE_AI_IMAGE, null, bitmap, null);
    }

    public static ChatMessage aiImageFromUrl(String url) {
        return new ChatMessage(TYPE_AI_IMAGE, null, null, url);
    }

    public static ChatMessage system(String text) {
        return new ChatMessage(TYPE_SYSTEM, text, null, null);
    }

    public static ChatMessage loading() {
        return new ChatMessage(TYPE_LOADING, null, null, null);
    }

    public int getType() { return type; }
    public String getContent() { return content; }
    public Bitmap getImageBitmap() { return imageBitmap; }
    public String getImageUrl() { return imageUrl; }
    public long getTimestamp() { return timestamp; }
}