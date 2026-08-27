package org.havenapp.main.ui;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.TextView;


import org.havenapp.main.PreferenceManager;
import org.havenapp.main.R;

import java.util.LinkedList;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class AccelConfigureActivity extends AppCompatActivity implements SensorEventListener {

    private TextView mTextLevel;
    private com.google.android.material.slider.Slider mNumberTrigger;
    private PreferenceManager mPrefManager;
    private SimpleWaveformExtended mWaveform;
    private LinkedList<Integer> mWaveAmpList;

    private static final int MAX_SLIDER_VALUE = 100;

    private double maxAmp = 0;

    /**
     * Last update of the accelerometer
     */
    private long lastUpdate = -1;

    /**
     * Current accelerometer values
     */
    private float accel_values[];

    /**
     * Last accelerometer values
     */
    private float last_accel_values[];


    private float mAccelCurrent =  SensorManager.GRAVITY_EARTH;
    private float mAccelLast = SensorManager.GRAVITY_EARTH;
    private float mAccel = 0.00f;


    /**
     * Text showing accelerometer values
     */
    private int maxAlertPeriod = 30;
    private int remainingAlertPeriod = 0;
    private boolean alert = false;
    private final static int CHECK_INTERVAL = 100;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_accel_configure);
        mPrefManager = new PreferenceManager(this.getApplicationContext());

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        setTitle("");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mTextLevel = findViewById(R.id.text_display_level);
        mNumberTrigger = findViewById(R.id.number_trigger_level);
        mWaveform = findViewById(R.id.simplewaveform);
        mWaveform.setMaxVal(MAX_SLIDER_VALUE);

        mNumberTrigger.setValueFrom(0);
        mNumberTrigger.setValueTo(MAX_SLIDER_VALUE);

        try {
            mNumberTrigger.setValue(Float.parseFloat(mPrefManager.getAccelerometerSensitivity()));
        } catch (NumberFormatException ignored) {
            mNumberTrigger.setValue(50);
        }

        mNumberTrigger.addOnChangeListener((slider, value, fromUser) -> {
            mWaveform.setThreshold((int) value);
            mPrefManager.setAccelerometerSensitivity(String.valueOf((int) value));
        });




        initWave();
        startAccel();
    }

    private void initWave ()
    {
        mWaveform.init();

        mWaveAmpList = new LinkedList<>();

        mWaveform.setDataList(mWaveAmpList);

        mWaveform.setMaxVal(MAX_SLIDER_VALUE);

        mWaveform.refresh();
    }
    private void startAccel () {

            try {

                SensorManager sensorMgr = (SensorManager) getSystemService(AppCompatActivity.SENSOR_SERVICE);
                Sensor sensor = sensorMgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

                if (sensor == null) {
                    Log.i("AccelerometerFrament", "Warning: no accelerometer");
                } else {
                    sensorMgr.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);

                }


            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

    }

    public void onSensorChanged(SensorEvent event) {
        long curTime = System.currentTimeMillis();
        // only allow one update every 100ms.
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            if ((curTime - lastUpdate) > CHECK_INTERVAL) {
                long diffTime = (curTime - lastUpdate);
                lastUpdate = curTime;

                accel_values = event.values.clone();

                if (alert && remainingAlertPeriod > 0) {
                    remainingAlertPeriod = remainingAlertPeriod - 1;
                } else {
                    alert = false;
                }

                if (last_accel_values != null) {

                    mAccelLast = mAccelCurrent;
                    mAccelCurrent =(float)Math.sqrt(accel_values[0]* accel_values[0] + accel_values[1]*accel_values[1]
                            + accel_values[2]*accel_values[2]);
                    float delta = mAccelCurrent - mAccelLast;
                    mAccel = (mAccel * 0.9f + delta);

                    double averageDB = 0.0;
                    if (mAccel != 0) {
                        averageDB = 20 * Math.log10(Math.abs(mAccel));
                    }

                    if (averageDB > maxAmp) {
                        maxAmp = averageDB + 5d; //add 5db buffer
                    }

                    mWaveAmpList.addFirst((int)mAccel);

                    if (mWaveAmpList.size() > 100) {
                        mWaveAmpList.removeLast();
                    }

                    mWaveform.refresh();
                    mTextLevel.setText(getString(R.string.current_accel_base) + " " + (int)mAccel);


                }
                last_accel_values = accel_values.clone();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }


    @Override
    protected void onDestroy() {
        super.onDestroy();

    }




    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                break;
        }
        return true;
    }

    /**
     * When user closes the activity
     */
    @Override
    public void onBackPressed() {
        finish();
    }
}
