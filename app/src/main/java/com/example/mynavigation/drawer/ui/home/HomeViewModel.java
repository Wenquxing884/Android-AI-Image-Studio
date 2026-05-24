package com.example.mynavigation.drawer.ui.home;

import android.app.Application;
import android.content.SharedPreferences;
import android.graphics.Bitmap;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.mynavigation.drawer.ui.aigc.AigcApiService;

import java.util.ArrayList;
import java.util.List;
public class HomeViewModel extends AndroidViewModel {

    private static final String PREFS_NAME = "aigc_settings";
    private static final String KEY_BASE_URL = "base_url";
    private static final String KEY_API_KEY = "api_key";
    private static final String KEY_MODEL = "model";

    private final AigcApiService apiService;
    private final MutableLiveData<Boolean> generating = new MutableLiveData<>(false);
    private final MutableLiveData<ChatMessage> newMessage = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private int currentGenerationId = 0;

    // 保存聊天消息，防止切换页面时丢失
    private List<ChatMessage> savedMessages = new ArrayList<>();
    private String savedSessionId = null;
    private Bitmap savedSelectedImage = null;

    public HomeViewModel(@NonNull Application application) {
        super(application);
        apiService = new AigcApiService();
    }

    public LiveData<Boolean> isGenerating() { return generating; }
    public LiveData<ChatMessage> getNewMessage() { return newMessage; }
    public LiveData<String> getError() { return error; }

    // 保存/恢复聊天状态
    public List<ChatMessage> getSavedMessages() { return savedMessages; }
    public void setSavedMessages(List<ChatMessage> messages) { this.savedMessages = messages; }
    public String getSavedSessionId() { return savedSessionId; }
    public void setSavedSessionId(String sessionId) { this.savedSessionId = sessionId; }
    public Bitmap getSavedSelectedImage() { return savedSelectedImage; }
    public void setSavedSelectedImage(Bitmap image) { this.savedSelectedImage = image; }

    /**
     * 重置所有状态（新建会话时调用，取消旧的生成回调）
     */
    public void resetState() {
        currentGenerationId++;
        generating.setValue(false);
        newMessage.setValue(null);
        error.setValue(null);
        savedMessages.clear();
        savedSessionId = null;
        savedSelectedImage = null;
    }

    public void generateFromText(String prompt) {
        String[] config = loadConfig();
        String baseUrl = config[0];
        String apiKey = config[1];
        String model = config[2];

        if (apiKey == null || apiKey.isEmpty()) {
            error.setValue("请先在设置中配置 API Key");
            return;
        }

        generating.setValue(true);
        final int genId = currentGenerationId;

        apiService.textToImage(baseUrl, apiKey, model, prompt, "auto", "auto",
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

        if (apiKey == null || apiKey.isEmpty()) {
            error.setValue("请先在设置中配置 API Key");
            return;
        }

        generating.setValue(true);
        final int genId = currentGenerationId;

        apiService.imageToImage(baseUrl, apiKey, model, prompt, image, "auto", "auto",
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
                prefs.getString(KEY_MODEL, "gpt-image-1")
        };
    }
}