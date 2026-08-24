package org.havenapp.main.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import org.havenapp.main.R;

final class ArmEvidenceNotification {
    private static final String CHANNEL_ID = "haven_secure_evidence";
    private static final int ID = 2006;

    private ArmEvidenceNotification() {
    }

    static Notification build(Context context) {
        createChannel(context);
        return new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_haven)
                .setContentTitle(context.getString(R.string.secure_evidence_title))
                .setContentText(context.getString(R.string.secure_evidence_text))
                .setOngoing(true)
                .setSilent(true)
                .setLowPriority(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .build();
    }

    private static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                context.getString(R.string.secure_evidence_title), NotificationManager.IMPORTANCE_LOW);
        channel.setSound(null, null);
        channel.enableVibration(false);
        channel.enableLights(false);
        channel.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
        ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE))
                .createNotificationChannel(channel);
    }
}
