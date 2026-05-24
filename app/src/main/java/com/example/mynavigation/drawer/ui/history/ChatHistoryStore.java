package com.example.mynavigation.drawer.ui.history;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 聊天历史持久化存储
 * 使用 SharedPreferences 存储会话列表索引
 * 图片保存到内部存储文件
 */
public class ChatHistoryStore {

    private static final String PREFS_NAME = "chat_history";
    private static final String KEY_SESSIONS = "sessions";
    private static final String KEY_CURRENT_SESSION = "current_session";
    private static final String IMAGE_DIR = "chat_images";
    private final SharedPreferences prefs;
    private final Gson gson = new Gson();
    private final File imageDir;

    public ChatHistoryStore(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        // 使用外部存储私有目录：/storage/emulated/0/Android/data/{package}/files/chat_images/
        File externalDir = context.getExternalFilesDir(null);
        if (externalDir != null) {
            imageDir = new File(externalDir, IMAGE_DIR);
        } else {
            // 外部存储不可用时回退到内部存储
            imageDir = new File(context.getFilesDir(), IMAGE_DIR);
        }
        if (!imageDir.exists()) {
            imageDir.mkdirs();
        }
    }

    /**
     * 保存一个会话
     */
    public void saveSession(ChatSession session) {
        List<ChatSession> sessions = loadAllSessions();

        // 如果已存在同ID会话则替换，否则添加
        boolean found = false;
        for (int i = 0; i < sessions.size(); i++) {
            if (sessions.get(i).getSessionId().equals(session.getSessionId())) {
                sessions.set(i, session);
                found = true;
                break;
            }
        }
        if (!found) {
            sessions.add(session);
        }

        // 只保存索引（不含图片数据），最多保留50条
        List<ChatSession> index = new ArrayList<>();
        for (ChatSession s : sessions) {
            ChatSession idx = new ChatSession(s.getSessionId(), s.getTitle(), s.getCreatedAt());
            idx.setMessages(s.getMessages()); // messages里的imagePath是文件路径，可序列化
            index.add(idx);
        }

        // 按时间倒序，保留最近50条
        Collections.sort(index, (a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));
        if (index.size() > 50) {
            // 删除超出的会话图片
            for (int i = 50; i < index.size(); i++) {
                deleteSessionImages(index.get(i));
            }
            index = new ArrayList<>(index.subList(0, 50));
        }

        prefs.edit().putString(KEY_SESSIONS, gson.toJson(index)).apply();
    }

    /**
     * 加载所有会话（按时间倒序）
     */
    public List<ChatSession> loadAllSessions() {
        String json = prefs.getString(KEY_SESSIONS, null);
        if (json == null) return new ArrayList<>();
        try {
            Type type = new TypeToken<List<ChatSession>>() {}.getType();
            List<ChatSession> sessions = gson.fromJson(json, type);
            return sessions != null ? sessions : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * 删除指定会话
     */
    public void deleteSession(String sessionId) {
        List<ChatSession> sessions = loadAllSessions();
        ChatSession toRemove = null;
        for (ChatSession s : sessions) {
            if (s.getSessionId().equals(sessionId)) {
                toRemove = s;
                break;
            }
        }
        if (toRemove != null) {
            deleteSessionImages(toRemove);
            sessions.remove(toRemove);
            prefs.edit().putString(KEY_SESSIONS, gson.toJson(sessions)).apply();
        }
    }

    /**
     * 保存图片到内部存储，返回文件路径
     */
    public String saveImage(Bitmap bitmap, String sessionId) {
        String fileName = sessionId + "_" + System.currentTimeMillis() + ".png";
        File file = new File(imageDir, fileName);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, fos);
            return file.getAbsolutePath();
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 从文件路径加载图片
     */
    public Bitmap loadImage(String path) {
        if (path == null || path.isEmpty()) return null;
        File file = new File(path);
        if (!file.exists()) return null;
        return BitmapFactory.decodeFile(path);
    }

    /**
     * 删除会话关联的图片文件
     */
    private void deleteSessionImages(ChatSession session) {
        for (ChatSession.MessageEntry entry : session.getMessages()) {
            if (entry.getImagePath() != null && !entry.getImagePath().isEmpty()) {
                File file = new File(entry.getImagePath());
                if (file.exists()) {
                    file.delete();
                }
            }
        }
    }
    /**
     * 保存当前进行中的会话（不进入历史列表）
     */
    public void saveCurrentSession(ChatSession session) {
        prefs.edit().putString(KEY_CURRENT_SESSION, gson.toJson(session)).apply();
    }

    /**
     * 加载当前进行中的会话
     */
    public ChatSession loadCurrentSession() {
        String json = prefs.getString(KEY_CURRENT_SESSION, null);
        if (json == null) return null;
        try {
            return gson.fromJson(json, ChatSession.class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 清除当前进行中的会话
     */
    public void clearCurrentSession() {
        prefs.edit().remove(KEY_CURRENT_SESSION).apply();
    }

    /**
     * 生成新的会话ID
     */
    public static String generateSessionId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}