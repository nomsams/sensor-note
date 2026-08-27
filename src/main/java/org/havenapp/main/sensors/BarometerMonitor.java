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

import org.havenapp.main.PreferenceManager;
import org.havenapp.main.model.EventTrigger;
import org.havenapp.main.service.MonitorService;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

/**
 * Barometer monitor with anomaly detection integration
 */
public class BarometerMonitor implements SensorEventListener {

    private static final String TAG = "BarometerMonitor";

    // For pressure change detection.
    private SensorManager sensorMgr;
    private Sensor sensor;
    private long lastUpdate = -1;
    private float pressure_values[];
    private float last_pressure_values[];
    private PreferenceManager prefs;

    private int maxAlertPeriod = 30;
    private int remainingAlertPeriod = 0;
    private boolean alert = false;
    private final static int CHECK_INTERVAL = 1000;
    private float CHANGE_THRESHOLD = 0.20f; // hPa / mbar

    private Messenger serviceMessenger;

    public BarometerMonitor(Context context) {
        prefs = new PreferenceManager(context);
        CHANGE_THRESHOLD = prefs.getPressureSensitivity();

        context.bindService(new Intent(context,
                MonitorService.class), mConnection, Context.BIND_ABOVE_CLIENT);

        sensorMgr = (SensorManager) context.getSystemService(AppCompatActivity.SENSOR_SERVICE);
        sensor = sensorMgr.getDefaultSensor(Sensor.TYPE_PRESSURE);

        if (sensor == null) {
            Log.i(TAG, "Warning: no barometer sensor");
        } else {
            sensorMgr.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Safe not to implement
    }

    public void onSensorChanged(SensorEvent event) {
        long curTime = System.currentTimeMillis();

        // only allow one update every 1000ms (1 second).
        if (event.sensor.getType() == Sensor.TYPE_PRESSURE) {

            if ((curTime - lastUpdate) > CHECK_INTERVAL) {
                long diffTime = (curTime - lastUpdate);
                lastUpdate = curTime;

                pressure_values = event.values.clone();

                if (alert && remainingAlertPeriod > 0) {
                    remainingAlertPeriod = remainingAlertPeriod - 1;
                } else {
                    alert = false;
                }

                if (last_pressure_values != null) {
                    float diffValue = Math.abs(pressure_values[0] - last_pressure_values[0]);
                    Log.d(TAG, "diff: " + diffValue + " hPa");
                    boolean logit = (diffValue > CHANGE_THRESHOLD);

                    if (logit) {
                        // Send Alert
                        alert = true;
                        remainingAlertPeriod = maxAlertPeriod;

                        Message message = new Message();
                        message.what = EventTrigger.PRESSURE;
                        message.getData().putString(MonitorService.KEY_PATH,
                                String.format(Locale.US, "%.3f hPa", diffValue));

                        try {
                            if (serviceMessenger != null) {
                                serviceMessenger.send(message);
                            }
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }

                        Log.i(TAG, String.format(Locale.US, "Barometer alert: %.3f hPa (threshold: %.3f)", diffValue, CHANGE_THRESHOLD));
                    }
                }
                last_pressure_values = pressure_values.clone();
            }
        }
    }

    public void stop(Context context) {
        sensorMgr.unregisterListener(this);
        context.unbindService(mConnection);
    }

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.i(TAG, "SERVICE CONNECTED");
            serviceMessenger = new Messenger(service);
        }

        public void onServiceDisconnected(ComponentName arg0) {
            Log.i(TAG, "SERVICE DISCONNECTED");
            serviceMessenger = null;
        }
    };
    
    /**
     * Update pressure sensitivity threshold
     */
    public void setThreshold(float threshold) {
        CHANGE_THRESHOLD = threshold;
        prefs.setPressureSensitivity(String.valueOf(threshold));
    }
    
    /**
     * Get current pressure reading
     */
    public float getCurrentPressure() {
        return pressure_values != null ? pressure_values[0] : 0f;
    }
    
    /**
     * Check if sensor is available
     */
    public boolean isAvailable() {
        return sensor != null;
    }
    
    /**
     * Get sensor name
     */
    public String getSensorName() {
        return sensor != null ? sensor.getName() : "Unavailable";
    }
}
