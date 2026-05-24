package com.example.mynavigation.drawer.ui.home;

import android.Manifest;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.mynavigation.drawer.R;
import com.example.mynavigation.drawer.databinding.FragmentHomeBinding;
import com.example.mynavigation.drawer.ui.history.ChatHistoryStore;
import com.example.mynavigation.drawer.ui.history.ChatSession;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private HomeViewModel viewModel;
    private ChatAdapter chatAdapter;
    private Bitmap selectedImageBitmap;
    private ChatHistoryStore historyStore;
    private String currentSessionId;
    private final ActivityResultLauncher<String> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    onImagePicked(uri);
                }
            });

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    imagePickerLauncher.launch("image/*");
                } else {
                    Toast.makeText(requireContext(), "需要存储权限来选择图片", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        historyStore = new ChatHistoryStore(requireContext());

        setupRecyclerView();
        setupInputArea();
        observeViewModel();

        // 优先从 ViewModel 恢复（切换页面后返回时）
        if (!viewModel.getSavedMessages().isEmpty()) {
            currentSessionId = viewModel.getSavedSessionId();
            chatAdapter.setMessages(viewModel.getSavedMessages());
            selectedImageBitmap = viewModel.getSavedSelectedImage();
            if (selectedImageBitmap != null) {
                binding.layoutPreview.setVisibility(View.VISIBLE);
                binding.ivPreview.setImageBitmap(selectedImageBitmap);
            }
            scrollToBottom();
        } else {
            // 否则从存储恢复（冷启动时）
            ChatSession currentSession = historyStore.loadCurrentSession();
            if (currentSession != null && !currentSession.getMessages().isEmpty()) {
                currentSessionId = currentSession.getSessionId();
                restoreSessionFromData(currentSession);
            } else {
                currentSessionId = ChatHistoryStore.generateSessionId();
                chatAdapter.addMessage(ChatMessage.system("欢迎使用 AI 生图！输入提示词即可生成图片，也可上传或引用图片进行修图"));
            }
        }

        // 监听新建会话请求：将当前会话存入历史，然后清空
        getParentFragmentManager().setFragmentResultListener("new_chat", this, (key, bundle) -> {
            saveCurrentSessionToHistory();
            historyStore.clearCurrentSession();
            chatAdapter.clearMessages();
            selectedImageBitmap = null;
            binding.layoutPreview.setVisibility(View.GONE);
            currentSessionId = ChatHistoryStore.generateSessionId();
            chatAdapter.addMessage(ChatMessage.system("新会话已开始，输入提示词即可生成图片"));
        });

        // 监听恢复历史会话请求
        getParentFragmentManager().setFragmentResultListener("restore_chat", this, (key, bundle) -> {
            String sessionId = bundle.getString("restore_session_id");
            if (sessionId != null) {
                restoreSession(sessionId);
            }
        });

        return root;
    }

    private void setupRecyclerView() {
        chatAdapter = new ChatAdapter();
        LinearLayoutManager lm = new LinearLayoutManager(requireContext());
        lm.setStackFromEnd(true);
        binding.rvChat.setLayoutManager(lm);
        binding.rvChat.setAdapter(chatAdapter);

        // 设置引用修图回调
        chatAdapter.setOnReferenceEditListener(bitmap -> {
            selectedImageBitmap = bitmap;
            binding.layoutPreview.setVisibility(View.VISIBLE);
            binding.ivPreview.setImageBitmap(bitmap);
            binding.tvPreviewLabel.setText("💬 引用聊天图片");
            binding.tvPreviewName.setText("来自 AI 生成的图片，将用于修图");
            scrollToBottom();
        });

        // 点击图片放大预览
        chatAdapter.setOnImageClickListener(bitmap -> {
            binding.ivFullscreenImage.setImageBitmap(bitmap);
            binding.layoutFullscreenPreview.setVisibility(View.VISIBLE);
        });

        // 点击蒙层关闭预览
        binding.layoutFullscreenPreview.setOnClickListener(v -> {
            binding.layoutFullscreenPreview.setVisibility(View.GONE);
        });

        // 下载图片
        chatAdapter.setOnDownloadClickListener(this::saveImageToGallery);
    }

    private void setupInputArea() {
        // 发送按钮
        binding.btnSend.setOnClickListener(v -> doSend());

        // 键盘发送
        binding.etPrompt.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                doSend();
                return true;
            }
            return false;
        });

        // 图片选择（始终显示）
        binding.btnPickImage.setOnClickListener(v -> pickImage());

        // 移除预览
        binding.btnRemovePreview.setOnClickListener(v -> {
            selectedImageBitmap = null;
            binding.layoutPreview.setVisibility(View.GONE);
        });
    }

    private void doSend() {
        String prompt = binding.etPrompt.getText().toString().trim();
        if (prompt.isEmpty()) {
            Toast.makeText(requireContext(), R.string.ai_error_empty_prompt, Toast.LENGTH_SHORT).show();
            return;
        }

        // 有引用图片则添加带图片的用户消息，否则添加纯文本用户消息
        if (selectedImageBitmap != null) {
            Bitmap imageToUse = selectedImageBitmap;
            chatAdapter.addMessage(ChatMessage.userImage(prompt, imageToUse));
            scrollToBottom();

            // 添加加载消息
            chatAdapter.addMessage(ChatMessage.loading());
            scrollToBottom();

            // 清空输入
            binding.etPrompt.setText("");

            // 使用后清空引用
            selectedImageBitmap = null;
            binding.layoutPreview.setVisibility(View.GONE);
            viewModel.generateFromImage(prompt, imageToUse);
        } else {
            chatAdapter.addMessage(ChatMessage.userText(prompt));
            scrollToBottom();

            // 添加加载消息
            chatAdapter.addMessage(ChatMessage.loading());
            scrollToBottom();

            // 清空输入
            binding.etPrompt.setText("");

            viewModel.generateFromText(prompt);
        }
    }

    private void pickImage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED) {
                imagePickerLauncher.launch("image/*");
            } else {
                requestPermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES);
            }
        } else {
            imagePickerLauncher.launch("image/*");
        }
    }

    private void onImagePicked(Uri uri) {
        try {
            Bitmap bitmap;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.Source source = ImageDecoder.createSource(
                        requireContext().getContentResolver(), uri);
                bitmap = ImageDecoder.decodeBitmap(source, (decoder, info, src) -> {
                    decoder.setMutableRequired(true);
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                });
            } else {
                bitmap = MediaStore.Images.Media.getBitmap(
                        requireContext().getContentResolver(), uri);
            }
            selectedImageBitmap = bitmap;
            binding.layoutPreview.setVisibility(View.VISIBLE);
            binding.ivPreview.setImageBitmap(bitmap);
            binding.tvPreviewLabel.setText("🖼️ 相册图片");
            binding.tvPreviewName.setText("已从相册选择，将用于修图");
        } catch (Exception e) {
            Toast.makeText(requireContext(), "读取图片失败: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void observeViewModel() {
        viewModel.getNewMessage().observe(getViewLifecycleOwner(), message -> {
            if (message != null) {
                // 移除加载消息
                removeLoadingMessage();
                chatAdapter.addMessage(message);
                scrollToBottom();
            }
        });

        viewModel.isGenerating().observe(getViewLifecycleOwner(), generating -> {
            if (Boolean.TRUE.equals(generating)) {
                binding.btnSend.setEnabled(false);
                binding.etPrompt.setEnabled(false);
            } else {
                binding.btnSend.setEnabled(true);
                binding.etPrompt.setEnabled(true);
            }
        });

        viewModel.getError().observe(getViewLifecycleOwner(), errMsg -> {
            if (errMsg != null && !errMsg.isEmpty()) {
                Toast.makeText(requireContext(), errMsg, Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * 公开方法：供 MainActivity 调用，开始新会话
     */
    public void startNewChat() {
        saveCurrentSessionToHistory();
        historyStore.clearCurrentSession();
        resetToNewChat();
    }

    /**
     * 公开方法：供 MainActivity 调用，退出时保存当前会话（不进入历史）
     */
    public void saveSessionOnExit() {
        saveCurrentSession();
    }

    /**
     * 彻底重置为全新会话界面
     */
    private void resetToNewChat() {
        // 重置 ViewModel（取消旧的生成回调）
        viewModel.resetState();

        // 清空聊天列表
        chatAdapter.clearMessages();

        // 重置图片引用
        selectedImageBitmap = null;
        binding.layoutPreview.setVisibility(View.GONE);

        // 清空输入框
        binding.etPrompt.setText("");

        // 关闭全屏预览
        binding.layoutFullscreenPreview.setVisibility(View.GONE);

        // 重新启用按钮
        binding.btnSend.setEnabled(true);
        binding.etPrompt.setEnabled(true);

        // 生成新会话ID
        currentSessionId = ChatHistoryStore.generateSessionId();

        // 添加欢迎消息
        chatAdapter.addMessage(ChatMessage.system("新会话已开始，输入提示词即可生成图片"));
    }
    /**
     * 保存当前会话到"进行中"状态（不进入历史列表）- 异步执行
     */
    private void saveCurrentSession() {
        // 在主线程获取消息列表的快照
        List<ChatMessage> messages = chatAdapter.getMessages();
        if (messages == null || messages.isEmpty()) return;
        String sid = currentSessionId;

        // 后台线程执行所有I/O操作
        new Thread(() -> {
            ChatSession session = buildSessionFromMessages(messages, sid);
            if (session != null) {
                historyStore.saveCurrentSession(session);
            }
        }).start();
    }

    /**
     * 将当前会话保存到历史列表（新建会话时调用）- 异步执行
     */
    private void saveCurrentSessionToHistory() {
        List<ChatMessage> messages = chatAdapter.getMessages();
        if (messages == null || messages.isEmpty()) return;
        String sid = currentSessionId;

        new Thread(() -> {
            ChatSession session = buildSessionFromMessages(messages, sid);
            if (session != null) {
                historyStore.saveSession(session);
            }
        }).start();
    }

    /**
     * 从消息列表构建 ChatSession（在后台线程调用）
     */
    private ChatSession buildSessionFromMessages(List<ChatMessage> messages, String sessionId) {
        boolean hasRealContent = false;
        for (ChatMessage msg : messages) {
            if (msg.getType() == ChatMessage.TYPE_USER_TEXT
                    || msg.getType() == ChatMessage.TYPE_USER_IMAGE
                    || msg.getType() == ChatMessage.TYPE_AI_IMAGE) {
                hasRealContent = true;
                break;
            }
        }
        if (!hasRealContent) return null;

        String title = "新会话";
        for (ChatMessage msg : messages) {
            if (msg.getType() == ChatMessage.TYPE_USER_TEXT && msg.getContent() != null) {
                title = msg.getContent().length() > 20
                        ? msg.getContent().substring(0, 20) + "…"
                        : msg.getContent();
                break;
            }
        }

        ChatSession session = new ChatSession(sessionId, title, System.currentTimeMillis());

        for (ChatMessage msg : messages) {
            switch (msg.getType()) {
                case ChatMessage.TYPE_USER_TEXT:
                    session.addMessage(new ChatSession.MessageEntry(
                            ChatSession.MessageEntry.TYPE_USER_TEXT,
                            msg.getContent(), null, null));
                    break;

                case ChatMessage.TYPE_USER_IMAGE:
                    String userImgPath = null;
                    if (msg.getImageBitmap() != null) {
                        userImgPath = historyStore.saveImage(msg.getImageBitmap(), sessionId);
                    }
                    session.addMessage(new ChatSession.MessageEntry(
                            ChatSession.MessageEntry.TYPE_USER_IMAGE,
                            msg.getContent(), userImgPath, null));
                    break;

                case ChatMessage.TYPE_AI_IMAGE:
                    if (msg.getImageBitmap() != null) {
                        String aiImgPath = historyStore.saveImage(msg.getImageBitmap(), sessionId);
                        session.addMessage(new ChatSession.MessageEntry(
                                ChatSession.MessageEntry.TYPE_AI_IMAGE,
                                null, aiImgPath, null));
                    } else if (msg.getImageUrl() != null) {
                        session.addMessage(new ChatSession.MessageEntry(
                                ChatSession.MessageEntry.TYPE_AI_IMAGE,
                                null, null, msg.getImageUrl()));
                    }
                    break;

                case ChatMessage.TYPE_SYSTEM:
                    String content = msg.getContent();
                    if (content != null && !content.startsWith("欢迎使用")) {
                        session.addMessage(new ChatSession.MessageEntry(
                                ChatSession.MessageEntry.TYPE_SYSTEM,
                                content, null, null));
                    }
                    break;
            }
        }

        return session;
    }

    /**
     * 从 ChatSession 数据恢复会话到界面
     */
    private void restoreSessionFromData(ChatSession session) {
        chatAdapter.clearMessages();

        for (ChatSession.MessageEntry entry : session.getMessages()) {
            switch (entry.getType()) {
                case ChatSession.MessageEntry.TYPE_USER_TEXT:
                    chatAdapter.addMessage(ChatMessage.userText(entry.getContent()));
                    break;

                case ChatSession.MessageEntry.TYPE_USER_IMAGE:
                    Bitmap userBmp = historyStore.loadImage(entry.getImagePath());
                    if (userBmp != null) {
                        chatAdapter.addMessage(ChatMessage.userImage(entry.getContent(), userBmp));
                    } else {
                        chatAdapter.addMessage(ChatMessage.userText(entry.getContent()));
                    }
                    break;

                case ChatSession.MessageEntry.TYPE_AI_IMAGE:
                    if (entry.getImagePath() != null) {
                        Bitmap aiBmp = historyStore.loadImage(entry.getImagePath());
                        if (aiBmp != null) {
                            chatAdapter.addMessage(ChatMessage.aiImage(aiBmp));
                        }
                    } else if (entry.getImageUrl() != null) {
                        chatAdapter.addMessage(ChatMessage.aiImageFromUrl(entry.getImageUrl()));
                    }
                    break;

                case ChatSession.MessageEntry.TYPE_SYSTEM:
                    chatAdapter.addMessage(ChatMessage.system(entry.getContent()));
                    break;
            }
        }

        scrollToBottom();
    }

    /**
     * 从历史记录恢复会话
     */
    private void restoreSession(String sessionId) {
        java.util.List<ChatSession> sessions = historyStore.loadAllSessions();
        ChatSession target = null;
        for (ChatSession s : sessions) {
            if (s.getSessionId().equals(sessionId)) {
                target = s;
                break;
            }
        }
        if (target == null) {
            Toast.makeText(requireContext(), "会话不存在", Toast.LENGTH_SHORT).show();
            return;
        }

        selectedImageBitmap = null;
        binding.layoutPreview.setVisibility(View.GONE);
        currentSessionId = sessionId;

        restoreSessionFromData(target);
        Toast.makeText(requireContext(), "已恢复会话", Toast.LENGTH_SHORT).show();
    }
    private void removeLoadingMessage() {
        int count = chatAdapter.getMessageCount();
        if (count > 0) {
            chatAdapter.removeLastMessage();
        }
    }

    private void scrollToBottom() {
        int count = chatAdapter.getMessageCount();
        if (count > 0) {
            binding.rvChat.smoothScrollToPosition(count - 1);
        }
    }

    private void saveImageToGallery(Bitmap bitmap) {
        String fileName = "AIGC_" + System.currentTimeMillis() + ".png";
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ 使用 MediaStore
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                values.put(MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/AIGC");

                Uri uri = requireContext().getContentResolver().insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    OutputStream os = requireContext().getContentResolver().openOutputStream(uri);
                    if (os != null) {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, os);
                        os.close();
                    }
                    Toast.makeText(requireContext(),
                            getString(R.string.ai_download_success), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(requireContext(),
                            getString(R.string.ai_download_failed), Toast.LENGTH_SHORT).show();
                }
            } else {
                // Android 9 及以下
                String savedPath = MediaStore.Images.Media.insertImage(
                        requireContext().getContentResolver(),
                        bitmap, fileName, "AI Generated Image");
                if (savedPath != null) {
                    Toast.makeText(requireContext(),
                            getString(R.string.ai_download_success), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(requireContext(),
                            getString(R.string.ai_download_failed), Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            Toast.makeText(requireContext(),
                    getString(R.string.ai_download_failed) + ": " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroyView() {
        // 保存到 ViewModel（快速恢复用）
        viewModel.setSavedMessages(chatAdapter.getMessages());
        viewModel.setSavedSessionId(currentSessionId);
        viewModel.setSavedSelectedImage(selectedImageBitmap);

        // 同时保存到存储（冷启动恢复用）
        saveCurrentSession();
        super.onDestroyView();
        binding = null;
    }
}