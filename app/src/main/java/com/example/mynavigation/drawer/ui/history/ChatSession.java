package com.example.mynavigation.drawer.ui.history;

import java.util.ArrayList;
import java.util.List;

/**
 * 历史会话数据模型
 */
public class ChatSession {

    private String sessionId;
    private String title;
    private long createdAt;
    private List<MessageEntry> messages;

    public ChatSession() {
        this.messages = new ArrayList<>();
    }

    public ChatSession(String sessionId, String title, long createdAt) {
        this.sessionId = sessionId;
        this.title = title;
        this.createdAt = createdAt;
        this.messages = new ArrayList<>();
    }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public List<MessageEntry> getMessages() { return messages; }
    public void setMessages(List<MessageEntry> messages) { this.messages = messages; }

    public void addMessage(MessageEntry entry) {
        messages.add(entry);
    }

    /**
     * 单条消息记录（可序列化）
     */
    public static class MessageEntry {
        public static final int TYPE_USER_TEXT = 0;
        public static final int TYPE_AI_IMAGE = 1;
        public static final int TYPE_SYSTEM = 2;
        public static final int TYPE_USER_IMAGE = 4;

        private int type;
        private String content;
        private String imagePath;  // AI生成图或用户引用图的本地路径
        private String imageUrl;   // AI返回的URL图片

        public MessageEntry() {}

        public MessageEntry(int type, String content, String imagePath, String imageUrl) {
            this.type = type;
            this.content = content;
            this.imagePath = imagePath;
            this.imageUrl = imageUrl;
        }

        public int getType() { return type; }
        public void setType(int type) { this.type = type; }

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }

        public String getImagePath() { return imagePath; }
        public void setImagePath(String imagePath) { this.imagePath = imagePath; }

        public String getImageUrl() { return imageUrl; }
        public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    }
}