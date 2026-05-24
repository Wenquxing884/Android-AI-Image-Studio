package com.example.mynavigation.drawer.ui.history;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.mynavigation.drawer.R;
import com.example.mynavigation.drawer.databinding.FragmentHistoryBinding;

import java.util.List;

public class HistoryFragment extends Fragment {

    private FragmentHistoryBinding binding;
    private ChatHistoryStore historyStore;
    private HistoryAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHistoryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        historyStore = new ChatHistoryStore(requireContext());
        adapter = new HistoryAdapter();

        binding.rvHistory.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvHistory.setAdapter(adapter);

        // 点击会话 -> 先保存当前进行中的会话到历史，再恢复选中的会话
        adapter.setOnSessionClickListener(session -> {
            // 1. 将当前进行中的会话存入历史
            ChatSession currentSession = historyStore.loadCurrentSession();
            if (currentSession != null && !currentSession.getMessages().isEmpty()) {
                historyStore.saveSession(currentSession);
                historyStore.clearCurrentSession();
            }
            // 2. 通知 HomeFragment 恢复选中的会话
            Bundle result = new Bundle();
            result.putString("restore_session_id", session.getSessionId());
            getParentFragmentManager().setFragmentResult("restore_chat", result);
            // 3. 导航到首页
            Navigation.findNavController(view).navigate(R.id.nav_home);
        });

        // 长按删除
        adapter.setOnSessionDeleteListener((session, position) -> {
            new AlertDialog.Builder(requireContext())
                    .setTitle("删除会话")
                    .setMessage("确定要删除「" + session.getTitle() + "」吗？")
                    .setPositiveButton("删除", (d, w) -> {
                        historyStore.deleteSession(session.getSessionId());
                        adapter.removeSession(position);
                        updateEmptyState();
                        Toast.makeText(requireContext(), "已删除", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });

        loadSessions();
    }

    @Override
    public void onResume() {
        super.onResume();
        // 每次可见时刷新
        if (adapter != null) {
            loadSessions();
        }
    }

    private void loadSessions() {
        List<ChatSession> sessions = historyStore.loadAllSessions();
        adapter.setSessions(sessions);
        updateEmptyState();
    }

    private void updateEmptyState() {
        if (adapter.getItemCount() == 0) {
            binding.tvEmpty.setVisibility(View.VISIBLE);
            binding.rvHistory.setVisibility(View.GONE);
        } else {
            binding.tvEmpty.setVisibility(View.GONE);
            binding.rvHistory.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}