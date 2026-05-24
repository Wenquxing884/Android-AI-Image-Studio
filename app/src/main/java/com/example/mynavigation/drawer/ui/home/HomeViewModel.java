package com.example.mynavigation.drawer.ui.home;

import android.app.Application;
import android.content.SharedPreferences;
import android.graphics.Bitmap;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.mynavigation.drawer.ui.aigc.AigcApiService;

public class HomeViewModel extends AndroidViewModel {

    private static final String PREFS_NAME = "aigc_settings";
    private static final String KEY_BASE_URL = "base_url";
    private static final String KEY_API_KEY = "api_key";
    private static final String KEY_MODEL = "model";
    private static final String KEY_SIZE = "size";
    private static final String KEY_QUALITY = "quality";

    private final AigcApiService apiService;
    private final MutableLiveData<Boolean> generating = new MutableLiveData<>(false);
    private final MutableLiveData<ChatMessage> newMessage = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private int currentGenerationId = 0;

    public HomeViewModel(@NonNull Application application) {
        super(application);
        apiService = new AigcApiService();
    }

    public LiveData<Boolean> isGenerating() { return generating; }
    public LiveData<ChatMessage> getNewMessage() { return newMessage; }
    public LiveData<String> getError() { return error; }

    /**
     * 重置所有状态（新建会话时调用，取消旧的生成回调）
     */
    public void resetState() {
        currentGenerationId++;
        generating.setValue(false);
        newMessage.setValue(null);
        error.setValue(null);
    }

    public void generateFromText(String prompt) {
        String[] config = loadConfig();
        String baseUrl = config[0];
        String apiKey = config[1];
        String model = config[2];
        String size = config[3];
        String quality = config[4];

        if (apiKey == null || apiKey.isEmpty()) {
            error.setValue("请先在设置中配置 API Key");
            return;
        }

        generating.setValue(true);
        final int genId = currentGenerationId;

        apiService.textToImage(baseUrl, apiKey, model, prompt, size, quality,
                new AigcApiService.ImageCallback() {
                    @Override
                    public void onSuccess(Bitmap bitmap) {
                        if (genId != currentGenerationId) return;
                        generating.postValue(false);
                        newMessage.postValue(ChatMessage.aiImage(bitmap));
                    }

                    @Override
                    public void onUrlReceived(String url) {
                        if (genId != currentGenerationId) return;
                        generating.postValue(false);
                        newMessage.postValue(ChatMessage.aiImageFromUrl(url));
                    }

                    @Override
                    public void onError(String errMsg) {
                        if (genId != currentGenerationId) return;
                        generating.postValue(false);
                        newMessage.postValue(ChatMessage.system("错误: " + errMsg));
                    }
                });
    }

    public void generateFromImage(String prompt, Bitmap image) {
        String[] config = loadConfig();
        String baseUrl = config[0];
        String apiKey = config[1];
        String model = config[2];
        String size = config[3];
        String quality = config[4];

        if (apiKey == null || apiKey.isEmpty()) {
            error.setValue("请先在设置中配置 API Key");
            return;
        }

        generating.setValue(true);
        final int genId = currentGenerationId;

        apiService.imageToImage(baseUrl, apiKey, model, prompt, image, size, quality,
                new AigcApiService.ImageCallback() {
                    @Override
                    public void onSuccess(Bitmap bitmap) {
                        if (genId != currentGenerationId) return;
                        generating.postValue(false);
                        newMessage.postValue(ChatMessage.aiImage(bitmap));
                    }

                    @Override
                    public void onUrlReceived(String url) {
                        if (genId != currentGenerationId) return;
                        generating.postValue(false);
                        newMessage.postValue(ChatMessage.aiImageFromUrl(url));
                    }

                    @Override
                    public void onError(String errMsg) {
                        if (genId != currentGenerationId) return;
                        generating.postValue(false);
                        newMessage.postValue(ChatMessage.system("错误: " + errMsg));
                    }
                });
    }

    private String[] loadConfig() {
        SharedPreferences prefs = getApplication().getSharedPreferences(PREFS_NAME, Application.MODE_PRIVATE);
        return new String[]{
                prefs.getString(KEY_BASE_URL, "https://api.openai.com"),
                prefs.getString(KEY_API_KEY, ""),
                prefs.getString(KEY_MODEL, "dall-e-3"),
                prefs.getString(KEY_SIZE, "1024x1024"),
                prefs.getString(KEY_QUALITY, "standard")
        };
    }
}