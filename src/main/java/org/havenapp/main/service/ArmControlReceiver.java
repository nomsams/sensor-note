package org.havenapp.main.service;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;

import androidx.core.app.NotificationCompat;
import androidx.core.app.RemoteInput;

import org.havenapp.main.PreferenceManager;
import org.havenapp.main.R;

public class ArmControlReceiver extends BroadcastReceiver {
    public static final String ACTION_DISARM = \"org.havenapp.main.ACTION_DISARM\";
    public static final String ACTION_ARM = \"org.havenapp.main.ACTION_ARM\";
    public static final String EXTRA_CODE = \"code\";
    private static final String CHANNEL_ID = \"haven_arm_control\";
    private static final int NOTIFICATION_ID = 2001;

    @Override
    public void onReceive(Context context, Intent intent) {
        PreferenceManager preferences = new PreferenceManager(context);
        String action = intent.getAction();

        if (ACTION_DISARM.equals(action)) {
            // Null-safe RemoteInput handling
            String provided = \"\";
            if (intent.getExtras() != null) {
                CharSequence remoteInput = RemoteInput.getResultsFromIntent(intent).getCharSequence(EXTRA_CODE);
                if (remoteInput != null) {
                    provided = remoteInput.toString();
                }
            }

            // Verify panic code (constant-time comparison with hash)
            if (preferences.verifyPanicCode(provided)) {
                preferences.setPendingArmRequest(false);
                SecureCaptureService.startEvidenceCapture(context);
                schedule(context, armPendingIntent(context), 3_000L);
                updateNotification(context, false, context.getString(R.string.panic_disarmed));
                sendPanicAlert(context);
                return;
            }

            // Verify disarm code (constant-time comparison with hash)
            // Check if disarm code is configured by attempting verification with empty string
            boolean hasDisarmCode = !preferences.verifyDisarmCode(\"__HAS_CODE_CHECK__\");
            if (!preferences.verifyDisarmCode(provided)) {
                SecureCaptureService.startEvidenceCapture(context);
                updateNotification(context, false,
                        context.getString(hasDisarmCode
                                ? R.string.disarm_invalid_code
                                : R.string.disarm_code_not_configured));
                return;
            }
            preferences.setPendingArmRequest(true);
            context.stopService(new Intent(context, MonitorService.class));
            SecureCaptureService.startEvidenceCapture(context);
            schedule(context, armPendingIntent(context), 15_000L);
            updateNotification(context, false,
                    context.getString(R.string.disarm_sequence_running).replace(\"Disarmed.\", \"Disarmed!\"));
        } else if (ACTION_ARM.equals(action)) {
            preferences.setPendingArmRequest(false);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(new Intent(context, MonitorService.class));
            } else {
                context.startService(new Intent(context, MonitorService.class));
            }
            schedule(context, newClusterPendingIntent(context), 30_000L);
            updateNotification(context, true, context.getString(R.string.arming_in_30_seconds));
        }
    }

    public static void show(Context context) {
        createChannel(context);
        updateNotification(context,
                !new PreferenceManager(context).getMonitorServiceActive(),
                context.getString(R.string.arm_control_ready));
    }

    private static void schedule(Context context, PendingIntent pendingIntent, long delayMillis) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+ exact alarm permission required
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                            SystemClock.elapsedRealtime() + delayMillis, pendingIntent);
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                            SystemClock.elapsedRealtime() + delayMillis, pendingIntent);
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        SystemClock.elapsedRealtime() + delayMillis, pendingIntent);
            }
        }
    }

    private static PendingIntent armPendingIntent(Context context) {
        Intent intent = new Intent(context, ArmControlReceiver.class).setAction(ACTION_ARM);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }
        return PendingIntent.getBroadcast(context, 2002, intent, flags);
    }

    private static PendingIntent newClusterPendingIntent(Context context) {
        Intent intent = new Intent(context, MonitorService.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // For foreground service on Android 12+
            return PendingIntent.getForegroundService(context, 2003, intent, flags);
        } else {
            return PendingIntent.getService(context, 2003, intent, flags);
        }
    }

    private static void updateNotification(Context context, boolean armed, String message) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_haven)
                .setContentTitle(\"Haven \" + (armed ? \"ARMED\" : \"DISARMED\"))
                .setContentText(message)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        if (new PreferenceManager(context).getSilentOperations()) {
            builder.setSilent(true).setPublicVersion(new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_haven)
                    .setContentTitle(\"Sync\")
                    .setContentText(\"Completed\")
                    .build());
        }

        if (armed) {
            RemoteInput remoteInput = new RemoteInput.Builder(EXTRA_CODE)
                    .setLabel(context.getString(R.string.disarm_action)).build();
            Intent intent = new Intent(context, ArmControlReceiver.class).setAction(ACTION_DISARM);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags |= PendingIntent.FLAG_MUTABLE;
            }
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 2004, intent, flags);
            NotificationCompat.Action action = new NotificationCompat.Action.Builder(
                    0, context.getString(R.string.disarm_action), pendingIntent)
                    .addRemoteInput(remoteInput)
                    .build();
            builder.addAction(action);
        } else {
            builder.addAction(0, context.getString(R.string.arm_action), armPendingIntent(context));
        }

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, builder.build());
        }
    }

    private static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, \"Arm control\", NotificationManager.IMPORTANCE_HIGH);
        channel.setSound(null, null);
        channel.enableVibration(false);
        channel.enableLights(false);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    private static void sendPanicAlert(Context context) {
        PreferenceManager preferences = new PreferenceManager(context);
        String message = context.getString(R.string.panic_alert_message);
        if (preferences.isTelegramConfigured()) {
            TelegramSender.sendMessage(context, message, null);
        }
        if (preferences.isRemoteNotificationActive()) {
            SignalSender sender = SignalSender.getInstance(context, preferences.getSignalUsername());
            java.util.ArrayList<String> recipients = new java.util.ArrayList<>();
            recipients.add(preferences.getRemotePhoneNumber());
            sender.sendMessage(recipients, message, null, null);
        }
    }
}
