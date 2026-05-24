package com.example.mynavigation.drawer.ui.home;

import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.mynavigation.drawer.R;

import java.util.ArrayList;
import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatViewHolder> {

    public interface OnReferenceEditListener {
        void onReferenceEdit(Bitmap bitmap);
    }

    public interface OnImageClickListener {
        void onImageClick(Bitmap bitmap);
    }

    public interface OnDownloadClickListener {
        void onDownloadClick(Bitmap bitmap);
    }

    private final List<ChatMessage> messages = new ArrayList<>();
    private OnReferenceEditListener referenceEditListener;
    private OnImageClickListener imageClickListener;
    private OnDownloadClickListener downloadClickListener;

    public void setOnReferenceEditListener(OnReferenceEditListener listener) {
        this.referenceEditListener = listener;
    }

    public void setOnImageClickListener(OnImageClickListener listener) {
        this.imageClickListener = listener;
    }

    public void setOnDownloadClickListener(OnDownloadClickListener listener) {
        this.downloadClickListener = listener;
    }

    public void addMessage(ChatMessage message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    public void removeLastMessage() {
        if (!messages.isEmpty()) {
            int last = messages.size() - 1;
            messages.remove(last);
            notifyItemRemoved(last);
        }
    }

    public int getMessageCount() {
        return messages.size();
    }

    public List<ChatMessage> getMessages() {
        return new ArrayList<>(messages);
    }

    public void setMessages(List<ChatMessage> newMessages) {
        int oldSize = messages.size();
        messages.clear();
        notifyItemRangeRemoved(0, oldSize);
        messages.addAll(newMessages);
        notifyItemRangeInserted(0, messages.size());
    }

    public void clearMessages() {
        int size = messages.size();
        messages.clear();
        notifyItemRangeRemoved(0, size);
    }

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat_message, parent, false);
        return new ChatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        ChatMessage msg = messages.get(position);

        // 隐藏所有视图
        holder.cardUser.setVisibility(View.GONE);
        holder.ivUserImage.setVisibility(View.GONE);
        holder.layoutAi.setVisibility(View.GONE);
        holder.tvSystem.setVisibility(View.GONE);
        holder.layoutLoading.setVisibility(View.GONE);

        switch (msg.getType()) {
            case ChatMessage.TYPE_USER_TEXT:
                holder.cardUser.setVisibility(View.VISIBLE);
                holder.tvUserMessage.setText(msg.getContent());
                break;

            case ChatMessage.TYPE_USER_IMAGE:
                holder.cardUser.setVisibility(View.VISIBLE);
                holder.tvUserMessage.setText(msg.getContent());
                if (msg.getImageBitmap() != null) {
                    holder.ivUserImage.setVisibility(View.VISIBLE);
                    Glide.with(holder.ivUserImage.getContext())
                            .load(msg.getImageBitmap())
                            .into(holder.ivUserImage);
                    // 点击图片放大预览
                    holder.ivUserImage.setOnClickListener(v -> {
                        if (imageClickListener != null) {
                            imageClickListener.onImageClick(msg.getImageBitmap());
                        }
                    });
                }
                break;

            case ChatMessage.TYPE_AI_IMAGE:
                holder.layoutAi.setVisibility(View.VISIBLE);
                if (msg.getImageBitmap() != null) {
                    Glide.with(holder.ivAiImage.getContext())
                            .load(msg.getImageBitmap())
                            .into(holder.ivAiImage);
                    holder.btnReferenceEdit.setVisibility(View.VISIBLE);
                    holder.btnDownload.setVisibility(View.VISIBLE);
                    holder.btnReferenceEdit.setOnClickListener(v -> {
                        if (referenceEditListener != null) {
                            referenceEditListener.onReferenceEdit(msg.getImageBitmap());
                        }
                    });
                    holder.btnDownload.setOnClickListener(v -> {
                        if (downloadClickListener != null) {
                            downloadClickListener.onDownloadClick(msg.getImageBitmap());
                        }
                    });
                    // 点击图片放大预览
                    holder.ivAiImage.setOnClickListener(v -> {
                        if (imageClickListener != null) {
                            imageClickListener.onImageClick(msg.getImageBitmap());
                        }
                    });
                } else if (msg.getImageUrl() != null) {
                    Glide.with(holder.ivAiImage.getContext())
                            .load(msg.getImageUrl())
                            .into(holder.ivAiImage);
                    // URL图片暂时不支持引用，隐藏引用按钮
                    holder.btnReferenceEdit.setVisibility(View.GONE);
                    holder.btnDownload.setVisibility(View.GONE);
                }
                break;

            case ChatMessage.TYPE_SYSTEM:
                holder.tvSystem.setVisibility(View.VISIBLE);
                holder.tvSystem.setText(msg.getContent());
                break;

            case ChatMessage.TYPE_LOADING:
                holder.layoutLoading.setVisibility(View.VISIBLE);
                break;
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class ChatViewHolder extends RecyclerView.ViewHolder {
        View cardUser;
        TextView tvUserMessage;
        ImageView ivUserImage;
        LinearLayout layoutAi;
        ImageView ivAiImage;
        View btnReferenceEdit;
        View btnDownload;
        TextView tvSystem;
        LinearLayout layoutLoading;
        ChatViewHolder(@NonNull View itemView) {
            super(itemView);
            cardUser = itemView.findViewById(R.id.card_user);
            tvUserMessage = itemView.findViewById(R.id.tv_user_message);
            ivUserImage = itemView.findViewById(R.id.iv_user_image);
            layoutAi = itemView.findViewById(R.id.layout_ai);
            ivAiImage = itemView.findViewById(R.id.iv_ai_image);
            btnReferenceEdit = itemView.findViewById(R.id.btn_reference_edit);
            btnDownload = itemView.findViewById(R.id.btn_download);
            tvSystem = itemView.findViewById(R.id.tv_system);
            layoutLoading = itemView.findViewById(R.id.layout_loading);
        }
    }
}