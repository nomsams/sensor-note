package org.havenapp.main.service;

import android.content.Context;
import android.content.pm.PackageManager;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.havenapp.main.PreferenceManager;

import java.io.File;
import java.util.concurrent.TimeUnit;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class TelegramSender {
    private static final String TAG = \"TelegramSender\";
    private static final String API_BASE = \"https://api.telegram.org/bot\";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    private TelegramSender() {
    }

    public static void sendMessage(Context context, String message, @Nullable File attachment) {
        PreferenceManager preferences = new PreferenceManager(context.getApplicationContext());
        if (!preferences.getTelegramEnabled() || !preferences.isTelegramConfigured()) {
            return;
        }

        // Check for POST_NOTIFICATIONS permission on Android 13+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, \"POST_NOTIFICATIONS permission not granted, skipping Telegram send\");
                return;
            }
        }

        String token = preferences.getTelegramBotToken();
        String chatId = preferences.getTelegramChatId();
        String text = TextUtils.isEmpty(message) ? \"Haven alert\" : message;

        EXECUTOR.execute(() -> {
            try (Response response = CLIENT.newCall(createRequest(token, chatId, text, attachment)).execute()) {
                if (!response.isSuccessful()) {
                    Log.w(TAG, \"Telegram delivery failed: \" + response.code());
                }
            } catch (IOException exception) {
                Log.w(TAG, \"Unable to deliver Telegram alert\", exception);
            }
        });
    }

    private static Request createRequest(
            String botToken,
            String chatId,
            String message,
            @Nullable File attachment
    ) {
        if (attachment != null && attachment.exists() && attachment.length() > 0) {
            boolean video = attachment.getName().toLowerCase(Locale.US).endsWith(\".mp4\");
            String method = video ? \"sendVideo\" : \"sendPhoto\";
            MultipartBody body = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(\"chat_id\", chatId)
                    .addFormDataPart(\"caption\", message)
                    .addFormDataPart(
                            video ? \"video\" : \"photo\",
                            attachment.getName(),
                            RequestBody.create(MediaType.parse(\"application/octet-stream\"), attachment)
                    )
                    .build();
            return new Request.Builder().url(API_BASE + botToken + \"/\" + method).post(body).build();
        }

        RequestBody body = RequestBody.create(
                MediaType.parse(\"application/json; charset=utf-8\"),
                \"{\\\"chat_id\\\":\\\"\" + escape(chatId) + \"\\\",\\\"text\\\":\\\"\" + escape(message) + \"\\\"}\"
        );
        return new Request.Builder()
                .url(API_BASE + botToken + \"/sendMessage\")
                .post(body)
                .build();
    }

    private static String escape(String value) {
        return value.replace(\"\\\\\", \"\\\\\\\\\").replace(\"\\\"\", \"\\\\\\\"\");
    }
}
