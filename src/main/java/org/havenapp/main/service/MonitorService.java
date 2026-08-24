
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

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.havenapp.main.HavenApp;
import org.havenapp.main.MonitorActivity;
import org.havenapp.main.PreferenceManager;
import org.havenapp.main.R;
import org.havenapp.main.anomaly.AnomalyCalibrator;
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
import java.util.StringTokenizer;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

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
    private final static String channelDescription= "Important messages from Haven";
	
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
    private final SensorFeatureWindow featureWindow = new SensorFeatureWindow(4);
    private final AnomalyCalibrator anomalyCalibrator = new AnomalyCalibrator(featureWindow.names);
    private HotellingPcaModel anomalyModel = null;
    private org.havenapp.main.anomaly.AnomalyPoint latestAnomalyPoint;
    private HotellingPcaModel.InferenceResult anomalyResult;

    private boolean mIsMonitoringActive = false;

    /**
     * Last Event instances
     */
    private Event mLastEvent;

    /**
     * Last sent notification time
     */
    private Date mLastNotification;

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setupNotificationChannel();
            startForeground(1, buildNotification());
        }

        startSensors();

        showNotification();

      //  startCamera();

        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (wakeLock == null || !wakeLock.isHeld()) {
            wakeLock = powerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK,
                "haven:MyWakelockTag");
            wakeLock.acquire();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Handle service restart
        return START_STICKY;
    }


    @RequiresApi(api = Build.VERSION_CODES.O)
    private void setupNotificationChannel ()
    {
        android.app.NotificationManager manager = (android.app.NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        android.app.NotificationChannel channel;
        channel = new android.app.NotificationChannel(channelId, channelName,
                android.app.NotificationManager.IMPORTANCE_HIGH); // Fixed: removed duplicate IMPORTANCE_MIN
        channel.setDescription(channelDescription);
        channel.setLightColor(Color.RED);
        if (mPrefs != null && mPrefs.getSilentOperations()) {
            channel.setImportance(android.app.NotificationManager.IMPORTANCE_MIN);
            channel.setSound(null, null);
            channel.enableVibration(false);
            channel.enableLights(false);
        }
        // channel.setImportance(android.app.NotificationManager.IMPORTANCE_MIN); // BUG FIX: This was overriding IMPORTANCE_HIGH
        manager.createNotificationChannel(channel);
    }

    public static MonitorService getInstance ()
    {
        return sInstance;
    }
    
    /**
     * Called on service destroy, cancels persistent notification
     * and shows a toast
     */
    @Override
    public void onDestroy() {

        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        stopSensors();
		stopForeground(true);

    }
	
    /**
     * When binding to the service, we return an interface to our messenger
     * for sending messages to the service.
     */
    @Override
    public IBinder onBind(Intent intent) {
        return messenger.getBinder();
    }
    
    /**
     * Show a notification while this service is running.
     */
    private void showNotification() {
        CharSequence text = getText(R.string.secure_service_started);
        NotificationCompat.Builder mBuilder =
                notificationBuilder(text).setSilent(mPrefs.getSilentOperations());
		startForeground(1, mBuilder.build());

    }

    private NotificationCompat.Builder notificationBuilder(CharSequence text) {
        Intent toLaunch = new Intent(getApplicationContext(), MonitorActivity.class)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent resultPendingIntent = PendingIntent.getActivity(
                this,
                0,
                toLaunch,
                Build.VERSION.SDK_INT >= 23
                        ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                        : PendingIntent.FLAG_UPDATE_CURRENT
        );
        return new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_stat_haven)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setContentIntent(resultPendingIntent)
                .setWhen(System.currentTimeMillis())
                .setVisibility(NotificationCompat.VISIBILITY_SECRET);
    }

    private android.app.Notification buildNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setupNotificationChannel();
        }
        return notificationBuilder(getText(R.string.secure_service_started)).build();
    }

    public boolean isRunning ()
    {
        return mIsMonitoringActive;

    }

    private void startSensors ()
    {
        mIsMonitoringActive = true;

        // set current event start date in prefs
        mPrefs.setCurrentSession(new Date(System.currentTimeMillis()));

        if (!mPrefs.getAccelerometerSensitivity().equals(PreferenceManager.OFF)) {
            mAccelManager = new AccelerometerMonitor(this);
            if(Build.VERSION.SDK_INT>=18) {
                mBumpMonitor = new BumpMonitor(this);
            }
        }

        if (!mPrefs.isSensorEnabled(EventTrigger.ACCELEROMETER, true)) {
            if (mAccelManager != null) {
                mAccelManager.stop(this);
                mAccelManager = null;
            }
            if (Build.VERSION.SDK_INT >= 18 && mBumpMonitor != null) {
                mBumpMonitor.stop(this);
                mBumpMonitor = null;
            }
        }

        if (mPrefs.isSensorEnabled(EventTrigger.PRESSURE, true)) {
            mBaroMonitor = new BarometerMonitor(this);
        }
        if (mPrefs.isSensorEnabled(EventTrigger.LIGHT, true)) {
            mLightMonitor = new AmbientLightMonitor(this);
        }
        if (mPrefs.isSensorEnabled(EventTrigger.EMF, true)) {
            mEmfMonitor = new EmfMonitor(this);
        }

        mPrefs.activateMonitorService(true);

        if (mPrefs.getHeartbeatActive()){
            SignalSender sender = SignalSender.getInstance(this, mPrefs.getSignalUsername());
            sender.startHeartbeatTimer(mPrefs.getHeartbeatNotificationTimeMs());
        }

        // && !mPrefs.getVideoMonitoringActive()

        if (!mPrefs.getMicrophoneSensitivity().equals(PreferenceManager.OFF)
                && mPrefs.isSensorEnabled(EventTrigger.MICROPHONE, true))
            mMicMonitor = new MicrophoneMonitor(this);

        mPowerReceiver = new PowerConnectionReceiver();
        // register our power status receivers
        IntentFilter powerConnectedFilter = new IntentFilter(Intent.ACTION_POWER_CONNECTED);
        registerReceiver(mPowerReceiver, powerConnectedFilter);

        IntentFilter powerDisconnectedFilter = new IntentFilter(Intent.ACTION_POWER_DISCONNECTED);
        registerReceiver(mPowerReceiver, powerDisconnectedFilter);
    }

    private void stopSensors ()
    {
        mIsMonitoringActive = false;
        //this will never be false:
        // -you can't use ==, != for string comparisons, use equals() instead
        // -Value is never set to OFF in the first place
        if (!mPrefs.getAccelerometerSensitivity().equals(PreferenceManager.OFF) && mAccelManager != null) {
            mAccelManager.stop(this);
            if(Build.VERSION.SDK_INT>=18 && mBumpMonitor != null) {
                mBumpMonitor.stop(this);
            }
        }

        //moving these out of the accelerometer pref, but need to enable off prefs for them too
        if (mBaroMonitor != null) {
            mBaroMonitor.stop(this);
        }
        if (mLightMonitor != null) {
            mLightMonitor.stop(this);
        }
        if (mEmfMonitor != null) {
            mEmfMonitor.stop(this);
        }

        // && !mPrefs.getVideoMonitoringActive())

        if (!mPrefs.getMicrophoneSensitivity().equals(PreferenceManager.OFF) && mMicMonitor != null)
            mMicMonitor.stop(this);

        if (mPrefs.getMonitorServiceActive()) {
            mPrefs.activateMonitorService(false);
            if (mPrefs.getHeartbeatActive()) {
                SignalSender sender = SignalSender.getInstance(this, mPrefs.getSignalUsername());
                sender.stopHeartbeatTimer();
            }
        }
        
        unregisterReceiver(mPowerReceiver);
    }

    /**
    * Sends an alert according to type of connectivity
    */
    public void alert(int alertType, String value) {

        Date now = new Date();
        boolean doNotification = false;
        SensorFusion.Result fusionResult = sensorFusion.observe(alertType);

        //for the UI visual
        Intent iEvent = new Intent("event");
        iEvent.putExtra("type",alertType);
        LocalBroadcastManager.getInstance(this).sendBroadcast(iEvent);

        if (TextUtils.isEmpty(value))
            return;

        observeSensorValue(alertType, value);

        if (alertType == EventTrigger.EMF) {
            try {
                if (Float.parseFloat(value) <= 0f) return;
            } catch (NumberFormatException ignored) {
            }
        }

        if (mLastEvent == null) {
            mLastEvent = new Event();
            long eventId = HavenEventDB.getDatabase(getApplicationContext())
                    .getEventDAO().insert(mLastEvent);
            mLastEvent.setId(eventId);
            doNotification = true;
        }
        else if (mPrefs.getNotificationTimeMs() == 0)
        {
            doNotification = true;
        }
        else if (mPrefs.getNotificationTimeMs() > 0 && mLastNotification != null)
        {
            //check if time window is within configured notification time window
            doNotification = ((now.getTime()-mLastNotification.getTime())>mPrefs.getNotificationTimeMs());
        }

        if (doNotification)
        {
            doNotification = !(mPrefs.getVideoMonitoringActive() && alertType == EventTrigger.CAMERA);
        }

        if (fusionResult.highPriority) {
            doNotification = true;
        }

        EventTrigger eventTrigger = new EventTrigger();
        eventTrigger.setType(alertType);
        eventTrigger.setPath(value);

        mLastEvent.addEventTrigger(eventTrigger);

        //we don't need to resave the event, only the trigger
        long eventTriggerId = HavenEventDB.getDatabase(getApplicationContext())
                .getEventTriggerDAO().insert(eventTrigger);
        eventTrigger.setId(eventTriggerId);

        if (doNotification) {

            mLastNotification = new Date();
            /*
             * If SMS mode is on we send an SMS or Signal alert to the specified
             * number
             */
            StringBuilder alertMessage = new StringBuilder();
            alertMessage.append(getString(R.string.intrusion_detected,
                    eventTrigger.getStringType(new ResourceManager(this))));

        if (mPrefs.getSilentOperations()) {
                alertMessage.append(" [S").append(fusionResult.score).append(']');
            } else {
                alertMessage.append(" [score ").append(fusionResult.score).append(']');
            }

            if (fusionResult.tripleCorrelation) {
                SecureCaptureService.startEvidenceCapture(this);
            }

            RuntimeLogStore.log(RuntimeLogStore.Level.WARNING, "SensorAlert",
                    eventTrigger.getStringType(new ResourceManager(this)) +
                            " fusion=" + fusionResult.score);

            if (!mPrefs.getOperatingMode().equals(OperatingMode.FULL_SIGNALS)) {
                return;
            }

            if (mPrefs.isRemoteNotificationActive() && mPrefs.isSignalVerified()) {
                //since this is a secure channel, we can add the Onion address
                if (mPrefs.getRemoteAccessActive() && (!TextUtils.isEmpty(mPrefs.getRemoteAccessOnion()))) {
                    alertMessage.append(" http://").append(mPrefs.getRemoteAccessOnion())
                            .append(':').append(WebServer.LOCAL_PORT);
                }

                SignalSender sender = SignalSender.getInstance(this, mPrefs.getSignalUsername());
                ArrayList<String> recips = new ArrayList<>();
                StringTokenizer st = new StringTokenizer(mPrefs.getRemotePhoneNumber(), ",");
                while (st.hasMoreTokens())
                    recips.add(st.nextToken());

                String attachment = null;
                if (eventTrigger.getType() == EventTrigger.CAMERA) {
                    attachment = eventTrigger.getPath();
                } else if (eventTrigger.getType() == EventTrigger.MICROPHONE) {
                    attachment = eventTrigger.getPath();
                }
                else if (eventTrigger.getType() == EventTrigger.CAMERA_VIDEO) {
                    attachment = eventTrigger.getPath();
                }

                sender.sendMessage(recips, alertMessage.toString(), attachment, null);
            }

            if (mPrefs.getTelegramEnabled() && mPrefs.isTelegramConfigured()) {
                File mediaFile = TextUtils.isEmpty(eventTrigger.getPath())
                        ? null : new File(eventTrigger.getPath());
                TelegramSender.sendMessage(this, alertMessage.toString(), mediaFile);
            }
        }

        if (anomalyResult != null && anomalyResult.anomaly) {
            SecureCaptureService.startEvidenceCapture(this);
        }

    }

    private void observeSensorValue(int alertType, String value) {
        anomalyResult = null;
        int channel = switch (alertType) {
            case EventTrigger.ACCELEROMETER, EventTrigger.BUMP -> 0;
            case EventTrigger.MICROPHONE -> 1;
            case EventTrigger.LIGHT -> 2;
            case EventTrigger.EMF -> 3;
            default -> -1;
        };
        if (channel < 0) return;
        try {
            double numericValue = Double.parseDouble(value.replaceAll("[^0-9.E+-]", ""));
            SensorFeatureWindow.FeatureVector vector =
                    featureWindow.observe(System.currentTimeMillis(), channel, numericValue);
            if (vector == null) return;

            if (anomalyModel == null) {
                anomalyCalibrator.add(vector.values);
                if (anomalyCalibrator.isReady()) {
                    anomalyModel = anomalyCalibrator.calibrate();
                    RuntimeLogStore.info("Anomaly", "Calibrated model from " +
                            (anomalyModel == null ? 0 : anomalyModel.sampleCount) + " samples");
                }
                return;
            }

            HotellingPcaModel.InferenceResult result = anomalyModel.infer(vector.values);
            anomalyResult = result;
            RuntimeLogStore.log(result.anomaly ? RuntimeLogStore.Level.ERROR : RuntimeLogStore.Level.DEBUG,
                    "PCA", "T2=" + result.tSquared);
            latestAnomalyPoint = new org.havenapp.main.anomaly.AnomalyPoint(
                    vector.timestamp,
                    anomalyModel.firstTwoComponentCoordinates(vector.values).getFirst(),
                    anomalyModel.firstTwoComponentCoordinates(vector.values).getSecond(),
                    result.tSquared,
                    result.anomaly);
            if (result.anomaly) SecureCaptureService.startEvidenceCapture(this);
        } catch (Exception exception) {
            RuntimeLogStore.warning("PCA", "Unable to process value: " + value);
        }
    }




}
