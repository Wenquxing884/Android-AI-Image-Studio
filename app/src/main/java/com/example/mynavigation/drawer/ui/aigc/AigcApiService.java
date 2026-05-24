package com.example.mynavigation.drawer.ui.aigc;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AigcApiService {
    private static final String TAG = "AigcApiService";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final OkHttpClient client;
    private final Gson gson;

    public AigcApiService() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .callTimeout(600, TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();
    }

    // ==================== 文生图 ====================
    public void textToImage(String baseUrl, String apiKey, String model, String prompt,
                            String size, String quality, ImageCallback callback) {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("prompt", prompt);
        body.addProperty("size", size);
        body.addProperty("quality", quality);
        body.addProperty("n", 1);
        if (!baseUrl.endsWith("/")) baseUrl += "/";
        String url = baseUrl + "v1/images/generations";
        Request req = new Request.Builder().url(url)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(gson.toJson(body), JSON)).build();
        client.newCall(req).enqueue(new Callback() {
            @Override
            public void onFailure(Call c, IOException e) {
                callback.onError("网络请求失败: " + e.getMessage());
            }
            @Override
            public void onResponse(Call c, Response r) throws IOException {
                parseImageResponse(r, callback);
            }
        });
    }

    // ==================== 图生图（5步策略链）====================
    // ① edits + raw_base64  256px
    // ② edits + data_uri    256px
    // ③ edits + raw_base64  512px
    // ④ edits + multipart   512px
    // ⑤ Responses API       256px

    public void imageToImage(String baseUrl, String apiKey, String model, String prompt,
                             Bitmap imageBitmap, String size, String quality, ImageCallback callback) {
        if (!baseUrl.endsWith("/")) baseUrl += "/";
        String editsUrl = baseUrl + "v1/images/edits";
        String responsesUrl = baseUrl + "v1/responses";
        byte[] smallBytes = compressToBytes(imageBitmap, 256, 70);
        byte[] mediumBytes = compressToBytes(imageBitmap, 512, 80);
        String smallB64 = Base64.encodeToString(smallBytes, Base64.NO_WRAP);
        String mediumB64 = Base64.encodeToString(mediumBytes, Base64.NO_WRAP);
        Log.d(TAG, "edits:" + editsUrl + " responses:" + responsesUrl
                + " | 256px=" + (smallBytes.length / 1024) + "KB 512px=" + (mediumBytes.length / 1024) + "KB");

        // ① edits + raw base64 (256px)
        Log.d(TAG, "① edits+raw_b64(256px)");
        JsonObject body1 = buildEditsBody(model, prompt, size, quality, smallB64, false);
        doChain(editsUrl, apiKey, gson.toJson(body1), model, prompt, size, quality,
                editsUrl, responsesUrl, smallBytes, mediumBytes, smallB64, mediumB64, 1, callback, "开始");
    }

    private void doChain(String url, String apiKey, String jsonBody,
                         String model, String prompt, String size, String quality,
                         String editsUrl, String responsesUrl,
                         byte[] smallBytes, byte[] mediumBytes,
                         String smallB64, String mediumB64,
                         int step, ImageCallback callback, String lastErr) {
        String sName = stepName(step);
        Log.d(TAG, "→ " + sName + " POST " + url);
        Request req = new Request.Builder().url(url)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(jsonBody, JSON)).build();
        client.newCall(req).enqueue(new Callback() {
            @Override
            public void onFailure(Call c, IOException e) {
                String err = sName + " 网络错误: " + e.getMessage();
                Log.w(TAG, err);
                goNext(model, prompt, size, quality, editsUrl, responsesUrl, apiKey,
                        smallBytes, mediumBytes, smallB64, mediumB64, step, callback, err);
            }
            @Override
            public void onResponse(Call c, Response r) throws IOException {
                if (r.isSuccessful()) {
                    Log.d(TAG, "✓ " + sName + " 成功!");
                    if (step == 5) parseResponsesApi(r, callback);
                    else parseImageResponse(r, callback);
                } else {
                    String errBody = r.body() != null ? r.body().string() : "";
                    String err = sName + " HTTP" + r.code() + ": " + errBody;
                    Log.w(TAG, err);
                    goNext(model, prompt, size, quality, editsUrl, responsesUrl, apiKey,
                            smallBytes, mediumBytes, smallB64, mediumB64, step, callback, err);
                }
            }
        });
    }
    private void goNext(String model, String prompt, String size, String quality,
                        String editsUrl, String responsesUrl, String apiKey,
                        byte[] smallBytes, byte[] mediumBytes,
                        String smallB64, String mediumB64,
                        int step, ImageCallback callback, String lastErr) {
        switch (step) {
            case 1: {
                Log.d(TAG, "② edits+data_uri(256px)");
                JsonObject body = buildEditsBody(model, prompt, size, quality, smallB64, true);
                doChain(editsUrl, apiKey, gson.toJson(body), model, prompt, size, quality,
                        editsUrl, responsesUrl, smallBytes, mediumBytes, smallB64, mediumB64, 2, callback, lastErr);
                break;
            }
            case 2: {
                Log.d(TAG, "③ edits+raw_b64(512px)");
                JsonObject body = buildEditsBody(model, prompt, size, quality, mediumB64, false);
                doChain(editsUrl, apiKey, gson.toJson(body), model, prompt, size, quality,
                        editsUrl, responsesUrl, smallBytes, mediumBytes, smallB64, mediumB64, 3, callback, lastErr);
                break;
            }
            case 3: {
                Log.d(TAG, "④ edits+multipart(512px)");
                doMultipart(editsUrl, apiKey, model, prompt, size, quality, mediumBytes,
                        editsUrl, responsesUrl, smallBytes, smallB64, mediumB64, callback, lastErr);
                break;
            }
            case 4: {
                Log.d(TAG, "⑤ ResponsesAPI(256px)");
                JsonObject body = buildResponsesBody(model, prompt, smallB64, size, quality);
                doChain(responsesUrl, apiKey, gson.toJson(body), model, prompt, size, quality,
                        editsUrl, responsesUrl, smallBytes, mediumBytes, smallB64, mediumB64, 5, callback, lastErr);
                break;
            }
            default:
                callback.onError("全部5步均失败，最后错误:\n" + lastErr);
                break;
        }
    }

    private void doMultipart(String url, String apiKey, String model, String prompt,
                             String size, String quality, byte[] imageBytes,
                             String editsUrl, String responsesUrl,
                             byte[] smallBytes, String smallB64, String mediumB64,
                             ImageCallback callback, String lastErr) {
        RequestBody multipart = new MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("model", model).addFormDataPart("prompt", prompt)
                .addFormDataPart("size", size).addFormDataPart("quality", quality)
                .addFormDataPart("n", "1")
                .addFormDataPart("image", "image.jpg",
                        RequestBody.create(imageBytes, MediaType.get("image/jpeg"))).build();
        new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS).readTimeout(180, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS).build()
                .newCall(new Request.Builder().url(url)
                        .addHeader("Authorization", "Bearer " + apiKey)
                        .post(multipart).build())
                .enqueue(new Callback() {
                    @Override
                    public void onFailure(Call c, IOException e) {
                        String err = "④multipart网络错误: " + e.getMessage();
                        Log.w(TAG, err);
                        goNext(model, prompt, size, quality, editsUrl, responsesUrl, apiKey,
                                smallBytes, null, smallB64, mediumB64, 4, callback, err);
                    }
                    @Override
                    public void onResponse(Call c, Response r) throws IOException {
                        if (r.isSuccessful()) {
                            Log.d(TAG, "✓ ④multipart成功!");
                            parseImageResponse(r, callback);
                        } else {
                            String eb = r.body() != null ? r.body().string() : "";
                            String err = "④multipart HTTP" + r.code() + ": " + eb;
                            Log.w(TAG, err);
                            goNext(model, prompt, size, quality, editsUrl, responsesUrl, apiKey,
                                    smallBytes, null, smallB64, mediumB64, 4, callback, err);
                        }
                    }
                });
    }

    // ==================== 辅助方法 ====================
    private String stepName(int s) {
        switch (s) {
            case 1: return "①edits+raw_b64(256px)";
            case 2: return "②edits+data_uri(256px)";
            case 3: return "③edits+raw_b64(512px)";
            case 4: return "④edits+multipart(512px)";
            case 5: return "⑤ResponsesAPI";
            default: return "Step" + s;
        }
    }
    private byte[] compressToBytes(Bitmap src, int maxSize, int quality) {
        Bitmap scaled = scaleBitmap(src, maxSize);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos);
        if (scaled != src) scaled.recycle();
        return baos.toByteArray();
    }
    private Bitmap scaleBitmap(Bitmap src, int maxSize) {
        int w = src.getWidth(), h = src.getHeight();
        if (w <= maxSize && h <= maxSize) return src;
        float ratio = Math.min((float) maxSize / w, (float) maxSize / h);
        return Bitmap.createScaledBitmap(src, Math.round(w * ratio), Math.round(h * ratio), true);
    }
    private JsonObject buildEditsBody(String model, String prompt, String size, String quality,
                                      String b64, boolean dataUri) {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("prompt", prompt);
        body.addProperty("size", size);
        body.addProperty("quality", quality);
        body.addProperty("n", 1);
        JsonArray arr = new JsonArray();
        JsonObject img = new JsonObject();
        img.addProperty("image_url", dataUri ? ("data:image/jpeg;base64," + b64) : b64);
        arr.add(img);
        body.add("images", arr);
        return body;
    }
    private JsonObject buildResponsesBody(String model, String prompt, String b64,
                                          String size, String quality) {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        JsonArray input = new JsonArray();
        JsonObject msg = new JsonObject();
        msg.addProperty("role", "user");
        JsonArray content = new JsonArray();
        JsonObject tp = new JsonObject();
        tp.addProperty("type", "input_text");
        tp.addProperty("text", prompt);
        content.add(tp);
        JsonObject ip = new JsonObject();
        ip.addProperty("type", "input_image");
        ip.addProperty("image_url", "data:image/jpeg;base64," + b64);
        content.add(ip);
        msg.add("content", content);
        input.add(msg);
        body.add("input", input);
        JsonArray tools = new JsonArray();
        JsonObject tool = new JsonObject();
        tool.addProperty("type", "image_generation");
        tool.addProperty("quality", quality);
        tool.addProperty("size", size);
        tools.add(tool);
        body.add("tools", tools);
        return body;
    }
    private void parseImageResponse(Response response, ImageCallback callback) {
        try {
            if (!response.isSuccessful()) {
                String err = response.body() != null ? response.body().string() : "Unknown";
                callback.onError("API错误(" + response.code() + "): " + err);
                return;
            }
            String body = response.body().string();
            JsonObject json = gson.fromJson(body, JsonObject.class);
            if (json.has("data")) {
                JsonArray data = json.getAsJsonArray("data");
                if (data.size() > 0) {
                    JsonObject item = data.get(0).getAsJsonObject();
                    if (item.has("b64_json")) {
                        byte[] bytes = Base64.decode(item.get("b64_json").getAsString(), Base64.DEFAULT);
                        callback.onSuccess(BitmapFactory.decodeByteArray(bytes, 0, bytes.length));
                    } else if (item.has("url")) {
                        callback.onUrlReceived(item.get("url").getAsString());
                    }
                } else {
                    callback.onError("API返回数据为空");
                }
            } else {
                callback.onError("API响应格式错误");
            }
        } catch (Exception e) {
            callback.onError("解析失败: " + e.getMessage());
        }
    }
    private void parseResponsesApi(Response response, ImageCallback callback) {
        try {
            String body = response.body().string();
            Log.d(TAG, "Responses: " + body.substring(0, Math.min(500, body.length())));
            JsonObject json = gson.fromJson(body, JsonObject.class);
            if (json.has("output")) {
                for (com.google.gson.JsonElement el : json.getAsJsonArray("output")) {
                    JsonObject item = el.getAsJsonObject();
                    String type = item.has("type") ? item.get("type").getAsString() : "";
                    if ("image_generation_call".equals(type) && item.has("result")) {
                        byte[] bytes = Base64.decode(item.get("result").getAsString(), Base64.DEFAULT);
                        callback.onSuccess(BitmapFactory.decodeByteArray(bytes, 0, bytes.length));
                        return;
                    }
                }
                callback.onError("Responses API 未返回图片");
            } else {
                callback.onError("Responses: " + body.substring(0, Math.min(200, body.length())));
            }
        } catch (Exception e) {
            callback.onError("解析Responses失败: " + e.getMessage());
        }
    }
    // ==================== 下载图片 ====================
    public void downloadImage(String imageUrl, ImageCallback callback) {
        client.newCall(new Request.Builder().url(imageUrl).build()).enqueue(new Callback() {
            @Override
            public void onFailure(Call c, IOException e) {
                callback.onError("下载失败: " + e.getMessage());
            }
            @Override
            public void onResponse(Call c, Response r) throws IOException {
                if (!r.isSuccessful()) {
                    callback.onError("下载失败: " + r.code());
                    return;
                }
                try {
                    byte[] bytes = r.body().bytes();
                    Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    if (bmp != null) callback.onSuccess(bmp);
                    else callback.onError("图片解码失败");
                } catch (Exception e) {
                    callback.onError("处理失败: " + e.getMessage());
                }
            }
        });
    }
    public interface ImageCallback {
        void onSuccess(Bitmap bitmap);
        void onUrlReceived(String url);
        void onError(String error);
    }
}