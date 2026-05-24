package com.example.mynavigation.drawer.ui.history;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mynavigation.drawer.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

    public interface OnSessionClickListener {
        void onSessionClick(ChatSession session);
    }

    public interface OnSessionDeleteListener {
        void onSessionDelete(ChatSession session, int position);
    }

    private final List<ChatSession> sessions = new ArrayList<>();
    private OnSessionClickListener clickListener;
    private OnSessionDeleteListener deleteListener;
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());

    public void setOnSessionClickListener(OnSessionClickListener listener) {
        this.clickListener = listener;
    }

    public void setOnSessionDeleteListener(OnSessionDeleteListener listener) {
        this.deleteListener = listener;
    }

    public void setSessions(List<ChatSession> newSessions) {
        sessions.clear();
        sessions.addAll(newSessions);
        notifyDataSetChanged();
    }

    public void removeSession(int position) {
        if (position >= 0 && position < sessions.size()) {
            sessions.remove(position);
            notifyItemRemoved(position);
            notifyItemRangeChanged(position, sessions.size());
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_history_session, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ChatSession session = sessions.get(position);

        // 标题
        String title = session.getTitle();
        if (title == null || title.isEmpty()) {
            title = holder.itemView.getContext().getString(R.string.ai_new_chat);
        }
        holder.tvTitle.setText(title);

        // 时间
        holder.tvTime.setText(dateFormat.format(new Date(session.getCreatedAt())));

        // 消息数（排除系统消息和加载消息）
        int msgCount = 0;
        for (ChatSession.MessageEntry entry : session.getMessages()) {
            if (entry.getType() == ChatSession.MessageEntry.TYPE_USER_TEXT
                    || entry.getType() == ChatSession.MessageEntry.TYPE_USER_IMAGE
                    || entry.getType() == ChatSession.MessageEntry.TYPE_AI_IMAGE) {
                msgCount++;
            }
        }
        holder.tvCount.setText(msgCount + " 条消息");

        // 点击事件
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onSessionClick(session);
            }
        });

        // 删除按钮
        holder.btnDelete.setOnClickListener(v -> {
            if (deleteListener != null) {
                deleteListener.onSessionDelete(session, holder.getAdapterPosition());
            }
        });
    }

    @Override
    public int getItemCount() {
        return sessions.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvTime, tvCount;
        ImageView btnDelete;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_session_title);
            tvTime = itemView.findViewById(R.id.tv_session_time);
            tvCount = itemView.findViewById(R.id.tv_session_count);
            btnDelete = itemView.findViewById(R.id.btn_delete_session);
        }
    }
}