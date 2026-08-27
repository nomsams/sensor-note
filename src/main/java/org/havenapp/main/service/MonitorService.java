/*
 * Copyright (c) 2017 Nathanial Freitas / Guardian Project
 *  * Licensed under the GPLv3 license.
 *
 * Copyright (c) 2013-2015 Marco Ziccardi, Luca Bonato
 * Licensed under the MIT license.
 */

package org.havenapp.main.service;


import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.telephony.SmsManager;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.havenapp.main.HavenApp;
import org.havenapp.main.MonitorActivity;
import org.havenapp.main.PreferenceManager;
import org.havenapp.main.R;
import org.havenapp.main.anomaly.AnomalyCalibrator;
import org.havenapp.main.anomaly.AnomalyDataStore;
import org.havenapp.main.anomaly.AnomalyPoint;
import org.havenapp.main.anomaly.HotellingPcaModel;
import org.havenapp.main.anomaly.RuntimeLogStore;
import org.havenapp.main.anomaly.SensorFeatureWindow;
import org.havenapp.main.database.HavenEventDB;
import org.havenapp.main.model.Event;
import org.havenapp.main.model.EventTrigger;
import org.havenapp.main.resources.ResourceManager;
import org.havenapp.main.sensors.AccelerometerMonitor;
import org.havenapp.main.sensors.AmbientLightMonitor;
import org.havenapp.main.sensors.BarometerMonitor;
import org.havenapp.main.sensors.BumpMonitor;
import org.havenapp.main.sensors.EmfMonitor;
import org.havenapp.main.sensors.MicrophoneMonitor;
import org.havenapp.main.sensors.PowerConnectionReceiver;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import kotlin.Pair;
import java.util.StringTokenizer;

@SuppressLint("HandlerLeak")
public class MonitorService extends Service {

    /**
     * Monitor instance
     */
    private static MonitorService sInstance;

    /**
     * To show a notification on service start
     */
    private final static String channelId = "monitor_id";
    private final static CharSequence channelName = "Haven notifications";
    private final static String channelDescription = "Important messages from Haven";

    /**
     * Object used to retrieve shared preferences
     */
     private PreferenceManager mPrefs = null;

    /**
     * Sensor Monitors
     */
    private AccelerometerMonitor mAccelManager = null;
    private BumpMonitor mBumpMonitor = null;
    private MicrophoneMonitor mMicMonitor = null;
    private BarometerMonitor mBaroMonitor = null;
    private AmbientLightMonitor mLightMonitor = null;
    private EmfMonitor mEmfMonitor = null;

    private PowerConnectionReceiver mPowerReceiver = null;
    private final SensorFusion sensorFusion = new SensorFusion();
    private final SensorFeatureWindow featureWindow =
            new SensorFeatureWindow(4, 1500L, 8, 512);
    private final AnomalyCalibrator anomalyCalibrator =
            new AnomalyCalibrator(featureWindow.getNames());
    private HotellingPcaModel anomalyModel = null;
    private org.havenapp.main.anomaly.AnomalyPoint latestAnomalyPoint;
    private HotellingPcaModel.InferenceResult anomalyResult;
    private AnomalyDataStore anomalyDataStore;

    private boolean mIsMonitoringActive = false;

    /**
     * Last Event instances
     */
    private Event mLastEvent;

    /**
     * Last sent notification time
     */
    private Date mLastNotification;
    private long sessionStartTime;

        /**
	 * Handler for incoming messages
	 */
    private class MessageHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {

		    //only accept alert if monitor is running
        if (mIsMonitoringActive)
		        alert(msg.what,msg.getData().getString(KEY_PATH));
		}
	}

	public final static String KEY_PATH = "path";

	/**
	 * Messenger interface used by clients to interact
	 */
	private final Messenger messenger = new Messenger(new MessageHandler());

    /**
     * Helps keep the service awake when screen is off
     */
    private PowerManager.WakeLock wakeLock;

    /**
     * Application
     */
    private HavenApp mApp = null;

	/**
	 * Called on service creation, sends a notification
	 */
    @Override
    public void onCreate() {

        sInstance = this;

        mApp = (HavenApp)getApplication();

        mPrefs = new PreferenceManager(this);
        sessionStartTime = System.currentTimeMillis();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setupNotificationChannel();
            startForeground(1, buildNotification().build());
        } else {
            showNotification();
        }

        // Initialize wake lock properly - only once
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (wakeLock == null) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Haven::MonitorService");
            wakeLock.setReferenceCounted(false);
        }

        // Initialize anomaly data store
        anomalyDataStore = new AnomalyDataStore(this);

        startSensors();

        showNotification();

      //  startCamera();
    }

    /**
     * Proper onStartCommand with START_STICKY for restart handling
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire();
        }
        return START_STICKY;
    }

	/**
	 * Called on service destruction, stops sensors and removes notification
	 */
    @Override
    public void onDestroy() {
        super.onDestroy();
        stopSensors();
        if (mPowerReceiver != null) {
            unregisterReceiver(mPowerReceiver);
        }
        // Safe release of wake lock with null and isHeld checks
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        if (anomalyDataStore != null) {
            anomalyDataStore.close();
        }
        sInstance = null;
    }

    /**
     * Get the instance of the service
     */
    public static MonitorService getInstance() {
        return sInstance;
    }

    /**
     * Start all enabled sensors
     */
    private void startSensors() {
        mIsMonitoringActive = true;
        mAccelManager = new AccelerometerMonitor(this);
        mBumpMonitor = new BumpMonitor(this);
        mMicMonitor = new MicrophoneMonitor(this);
        mBaroMonitor = new BarometerMonitor(this);
        mLightMonitor = new AmbientLightMonitor(this);
        mEmfMonitor = new EmfMonitor(this);
        mPowerReceiver = new PowerConnectionReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        registerReceiver(mPowerReceiver, filter);

        // Start anomaly calibration
        anomalyCalibrator.clear();
        anomalyModel = null;
        latestAnomalyPoint = null;
        anomalyResult = null;
    }

    /**
     * Stop all sensors
     */
    private void stopSensors() {
        mIsMonitoringActive = false;
        if (mAccelManager != null) {
            mAccelManager.stop(this);
            mAccelManager = null;
        }
        if (mBumpMonitor != null) {
            mBumpMonitor.stop(this);
            mBumpMonitor = null;
        }
        if (mMicMonitor != null) {
            mMicMonitor.stop(this);
            mMicMonitor = null;
        }
        if (mBaroMonitor != null) {
            mBaroMonitor.stop(this);
            mBaroMonitor = null;
        }
        if (mLightMonitor != null) {
            mLightMonitor.stop(this);
            mLightMonitor = null;
        }
        if (mEmfMonitor != null) {
            mEmfMonitor.stop(this);
            mEmfMonitor = null;
        }
    }

    /**
     * Show notification
     */
    private void showNotification() {
        NotificationCompat.Builder builder = buildNotification();
        startForeground(1, builder.build());
    }

    private NotificationCompat.Builder buildNotification() {
        Intent intent = new Intent(this, MonitorActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
            .setContentTitle("Haven")
            .setContentText("Monitoring active")
            .setSmallIcon(R.drawable.ic_stat_haven)
            .setContentIntent(pendingIntent)
            .setOngoing(true);

        return builder;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void setupNotificationChannel() {
        android.app.NotificationChannel channel = new android.app.NotificationChannel(
            channelId, channelName, android.app.NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(channelDescription);
        android.app.NotificationManager notificationManager =
            (android.app.NotificationManager) getSystemService(android.content.Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.createNotificationChannel(channel);
        }
    }

    /**
     * Alert handling with anomaly detection integration
     */
    public void alert(int type, String path) {
        // Process sensor data through anomaly detection
        long timestamp = System.currentTimeMillis();
        double value = 0;
        try {
            value = Double.parseDouble(path.split(" ")[0]);
        } catch (Exception ignored) {}

        // Add to feature window
        int sensorIndex = -1;
        switch (type) {
            case EventTrigger.ACCELEROMETER: sensorIndex = 0; break;
            case EventTrigger.MICROPHONE: sensorIndex = 1; break;
            case EventTrigger.EMF: sensorIndex = 3; break;
            case EventTrigger.PRESSURE: sensorIndex = 2; break;
        }

        if (sensorIndex >= 0) {
            featureWindow.add(sensorIndex, value);

            SensorFeatureWindow.FeatureVector vector =
                featureWindow.observe(timestamp, sensorIndex, value);

            if (vector != null && anomalyCalibrator != null) {
                anomalyCalibrator.add(vector.getValues());

                // Calibrate model if ready
                if (anomalyCalibrator.isReady() && anomalyModel == null) {
                    anomalyModel = anomalyCalibrator.calibrate();
                }

                // Run inference if model exists
                if (anomalyModel != null) {
                    anomalyResult = anomalyModel.infer(vector.getValues());

                    // Create anomaly point
                    Pair<Double, Double> coords = anomalyModel.firstTwoComponentCoordinates(vector.getValues());
                    latestAnomalyPoint = new AnomalyPoint(
                        timestamp,
                        coords.getFirst(),
                        coords.getSecond(),
                        anomalyResult.getTSquared(),
                        anomalyResult.getAnomaly()
                    );

                    // Save to persistent storage
                    if (anomalyDataStore != null) {
                        anomalyDataStore.savePoint(latestAnomalyPoint);
                    }

                    // Log to runtime log
                    RuntimeLogStore.INSTANCE.info("Anomaly",
                            "T^2=" + String.format("%.3f", anomalyResult.getTSquared()) +
                                    " anomaly=" + anomalyResult.getAnomaly());
                }
            }
        }

        // Original alert logic
        SensorFusion.Result fusionResult = sensorFusion.observe(type);

        // Log event
        RuntimeLogStore.INSTANCE.info("MonitorService", "Alert: type=" + type + " path=" + path +
            " fusion_score=" + fusionResult.score);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return messenger.getBinder();
    }

    /**
     * Get anomaly data store for external access
     */
    public AnomalyDataStore getAnomalyDataStore() {
        return anomalyDataStore;
    }

    /**
     * Get session start time
     */
    public long getSessionStartTime() {
        return sessionStartTime;
    }

    public boolean isRunning() {
        return mIsMonitoringActive;
    }
}
