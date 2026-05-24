package com.example.mynavigation.drawer.ui.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.mynavigation.drawer.R;
import com.example.mynavigation.drawer.databinding.FragmentSettingsBinding;

public class SettingsFragment extends Fragment {

    private static final String PREFS_NAME = "aigc_settings";
    private static final String KEY_BASE_URL = "base_url";
    private static final String KEY_API_KEY = "api_key";
    private static final String KEY_MODEL = "model";

    private FragmentSettingsBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        loadSettings();

        binding.btnSave.setOnClickListener(v -> saveSettings());
    }

    private void loadSettings() {
        SharedPreferences prefs = requireContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        binding.etBaseUrl.setText(prefs.getString(KEY_BASE_URL, "https://api.openai.com"));
        binding.etApiKey.setText(prefs.getString(KEY_API_KEY, ""));
        binding.etModel.setText(prefs.getString(KEY_MODEL, "gpt-image-1"));
    }

    private void saveSettings() {
        String baseUrl = binding.etBaseUrl.getText().toString().trim();
        String apiKey = binding.etApiKey.getText().toString().trim();
        String model = binding.etModel.getText().toString().trim();

        if (baseUrl.isEmpty()) {
            binding.etBaseUrl.setError("请输入 API 地址");
            return;
        }
        if (apiKey.isEmpty()) {
            binding.etApiKey.setError("请输入 API Key");
            return;
        }
        if (model.isEmpty()) {
            binding.etModel.setError("请输入模型名称");
            return;
        }

        SharedPreferences prefs = requireContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_BASE_URL, baseUrl)
                .putString(KEY_API_KEY, apiKey)
                .putString(KEY_MODEL, model)
                .apply();

        Toast.makeText(requireContext(), R.string.settings_saved, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}