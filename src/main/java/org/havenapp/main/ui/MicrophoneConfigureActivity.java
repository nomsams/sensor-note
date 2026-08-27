package org.havenapp.main.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.TextView;


import org.havenapp.main.PreferenceManager;
import org.havenapp.main.R;
import org.havenapp.main.sensors.media.MicSamplerTask;
import org.havenapp.main.sensors.media.MicrophoneTaskFactory;

import java.util.LinkedList;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MicrophoneConfigureActivity extends AppCompatActivity implements MicSamplerTask.MicListener {

    private MicSamplerTask microphone;
    private TextView mTextLevel;
    private com.google.android.material.slider.Slider mNumberTrigger;
    private PreferenceManager mPrefManager;
    private SimpleWaveformExtended mWaveform;
    private LinkedList<Integer> mWaveAmpList;
    private static final int MAX_SLIDER_VALUE = 120;

    private double maxAmp = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_microphone_configure);
        mPrefManager = new PreferenceManager(this.getApplicationContext());

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        setTitle("");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mTextLevel = findViewById(R.id.text_display_level);
        mNumberTrigger = findViewById(R.id.number_trigger_level);
        mWaveform = findViewById(R.id.simplewaveform);
        mWaveform.setMaxVal(100);

        mNumberTrigger.setValueFrom(0);
        mNumberTrigger.setValueTo(MAX_SLIDER_VALUE);

        try {
            mNumberTrigger.setValue(Float.parseFloat(mPrefManager.getMicrophoneSensitivity()));
        } catch (NumberFormatException ignored) {
            mNumberTrigger.setValue(60);
        }

        mNumberTrigger.addOnChangeListener((slider, value, fromUser) -> {
            mWaveform.setThreshold((int) value);
            mPrefManager.setMicrophoneSensitivity(String.valueOf((int) value));
        });


        initWave();
        startMic();
    }

    private void initWave ()
    {
        mWaveform.init();

        mWaveAmpList = new LinkedList<>();

        mWaveform.setDataList(mWaveAmpList);

        mWaveform.setMaxVal(100);

    }

    private void startMic() {
        String permission = Manifest.permission.RECORD_AUDIO;
        int requestCode = 999;
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {

                //This is called if user has denied the permission before
                //In this case I am just asking the permission again
                ActivityCompat.requestPermissions(this, new String[]{permission}, requestCode);

            } else {

                ActivityCompat.requestPermissions(this, new String[]{permission}, requestCode);
            }
        } else {

            try {
                microphone = MicrophoneTaskFactory.makeSampler(this);
                microphone.setMicListener(this);
                microphone.execute();
            } catch (MicrophoneTaskFactory.RecordLimitExceeded e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case 999:
                startMic();
                break;

        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (microphone != null)
            microphone.cancel(true);

    }

    @Override
    public void onSignalReceived(short[] signal) {
        /*
		 * We do and average of the 512 samples
		 */
        int total = 0;
        int count = 0;
        for (short peak : signal) {
            //Log.i("MicrophoneFragment", "Sampled values are: "+peak);
            if (peak != 0) {
                total += Math.abs(peak);
                count++;
            }
        }
        //  Log.i("MicrophoneFragment", "Total value: " + total);
        int average = 0;
        if (count > 0) average = total / count;
		/*
		 * We compute a value in decibels
		 */
        double averageDB = 0.0;
        if (average != 0) {
            averageDB = 20 * Math.log10(Math.abs(average));
        }

        if (averageDB > maxAmp) {
            maxAmp = averageDB + 5d; //add 5db buffer
            mNumberTrigger.setValue((float) Math.min(maxAmp, MAX_SLIDER_VALUE));
        }

        int perc = (int)((averageDB/120d)*100d)-10;
        mWaveAmpList.addFirst(perc);

            if (mWaveAmpList.size() > 100) {
            mWaveAmpList.removeLast();
        }

        mWaveform.refresh();
        mTextLevel.setText(getString(R.string.current_noise_base).concat(" ").concat(Integer.toString((int) averageDB)).concat("db"));

    }

    @Override
    public void onMicError() {

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
