package org.havenapp.main.sensors;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import org.havenapp.main.PreferenceManager;
import org.havenapp.main.model.EventTrigger;
import org.havenapp.main.service.MonitorService;

import java.util.Locale;

public class EmfMonitor implements SensorEventListener {
    private static final String TAG = \"EmfMonitor\";
    
    private final SensorManager sensorManager;
    private final Sensor sensor;
    private final Context context;
    private final PreferenceManager prefs;
    private long lastUpdate = -1;
    private float[] lastValues;
    private boolean alert;
    private int remainingAlertPeriod;
    private static final int CHECK_INTERVAL = 500;
    private static final int MAX_ALERT_PERIOD = 20;
    private Messenger serviceMessenger;

    public EmfMonitor(Context context) {
        this.context = context;
        prefs = new PreferenceManager(context);
        
        // Bind to MonitorService for alert messaging
        context.bindService(new Intent(context, MonitorService.class),
                connection, Context.BIND_ABOVE_CLIENT);
        
        sensorManager = (SensorManager) context.getSystemService(AppCompatActivity.SENSOR_SERVICE);
        
        // Try uncalibrated magnetic field first, then calibrated
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED);
        if (sensor == null) {
            sensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        }
        
        if (sensor != null) {
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME);
        } else {
            Log.w(TAG, \"No magnetic field sensor available\");
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Sensor accuracy changed
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        long now = System.currentTimeMillis();
        if (now - lastUpdate < CHECK_INTERVAL) return;
        lastUpdate = now;
        
        float[] values = event.values.clone();
        float threshold;
        try {
            threshold = Float.parseFloat(prefs.getEmfSensitivity());
        } catch (Exception ignored) {
            threshold = 25f; // µT
        }
        
        if (lastValues != null) {
            float change = (float) Math.sqrt(
                    square(values[0] - lastValues[0]) +
                    square(values[1] - lastValues[1]) +
                    square(values[2] - lastValues[2]));
            
            if (change > threshold && !alert) {
                alert = true;
                remainingAlertPeriod = MAX_ALERT_PERIOD;
                
                // Send event to MonitorService (which handles anomaly detection)
                Message message = new Message();
                message.what = EventTrigger.EMF;
                message.getData().putString(MonitorService.KEY_PATH, 
                        String.format(Locale.US, \"%.2f µT\", change));
                if (serviceMessenger != null) {
                    try {
                        serviceMessenger.send(message);
                    } catch (RemoteException ignored) {
                    }
                }
                
                Log.i(TAG, String.format(Locale.US, \"EMF alert: %.2f µT (threshold: %.2f)\", change, threshold));
            }
        }
        
        if (alert && --remainingAlertPeriod <= 0) alert = false;
        lastValues = values;
    }

    private static float square(float value) {
        return value * value;
    }

    public void stop(Context context) {
        sensorManager.unregisterListener(this);
        try {
            context.unbindService(connection);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            serviceMessenger = new Messenger(service);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceMessenger = null;
        }
    };
    
    /**
     * Get current magnetic field reading
     */
    public float[] getCurrentValues() {
        return lastValues != null ? lastValues.clone() : new float[3];
    }
    
    /**
     * Check if sensor is available
     */
    public boolean isAvailable() {
        return sensor != null;
    }
    
    /**
     * Get sensor type name
     */
    public String getSensorName() {
        return sensor != null ? sensor.getName() : \"Unavailable\";
    }
}
