package org.havenapp.main.ui;

import android.os.Bundle;
import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.havenapp.main.PreferenceManager;
import org.havenapp.main.R;
import org.havenapp.main.audio.AudioFilterEngine;

import java.util.Arrays;

public class AudioFilterTunerActivity extends AppCompatActivity {
    private PreferenceManager preferences;
    private AudioFilterEngine.FilterProfile profile = new AudioFilterEngine.FilterProfile();
    private TextView statusText;
    private ImageView spectrogramView;
    private static final int REQUEST_RECORD_AUDIO = 4101;
    private static final int FFT_SIZE = 1024;
    private static final int HOP_SIZE = 256;
    private Handler uiHandler = new Handler(Looper.getMainLooper());
    private boolean isPreviewRunning = false;
    private Thread previewThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio_filter_tuner);
        setSupportActionBar(findViewById(R.id.toolbar));
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        preferences = new PreferenceManager(this);
        loadProfile();

        bindSwitch(R.id.filter_high_pass, profile.highPassEnabled, value -> profile.highPassEnabled = value);
        bindSwitch(R.id.filter_low_pass, profile.lowPassEnabled, value -> profile.lowPassEnabled = value);
        bindSwitch(R.id.filter_notch, profile.notchEnabled, value -> profile.notchEnabled = value);
        bindSwitch(R.id.filter_band_pass, profile.bandPassEnabled, value -> profile.bandPassEnabled = value);
        bindText(R.id.filter_high_pass_hz, profile.highPassHz);
        bindText(R.id.filter_low_pass_hz, profile.lowPassHz);
        bindText(R.id.filter_notch_hz, profile.notchHz);
        bindText(R.id.filter_band_low_hz, profile.bandLowHz);
        bindText(R.id.filter_band_high_hz, profile.bandHighHz);
        bindText(R.id.filter_order, profile.order);

        Button preview = findViewById(R.id.button_preview);
        Button save = findViewById(R.id.button_save);
        Button reset = findViewById(R.id.button_reset);
        Button livePreview = findViewById(R.id.button_live_preview);
        statusText = findViewById(R.id.filter_status);
        spectrogramView = findViewById(R.id.spectrogram_view);

        preview.setOnClickListener(view -> startPreviewWithPermission());
        livePreview.setOnClickListener(view -> toggleLivePreview());
        reset.setOnClickListener(view -> {
            profile = new AudioFilterEngine.FilterProfile();
            recreate();
        });
        save.setOnClickListener(view -> saveProfile());
    }

    private void runPreview() {
        readProfile();
        statusText.setText(getString(R.string.audio_filter_recording));
        previewThread = new Thread(() -> {
            try {
                short[] raw = AudioFilterEngine.recordPreview(15);
                double inputLevel = AudioFilterEngine.levelDb(raw);
                short[] filtered = AudioFilterEngine.filter(raw, profile);
                double outputLevel = AudioFilterEngine.levelDb(filtered);
                
                // Generate spectrogram for raw audio
                double[][] specRaw = AudioFilterEngine.generateSpectrogram(raw, FFT_SIZE, HOP_SIZE);
                double[][] specFiltered = AudioFilterEngine.generateSpectrogram(filtered, FFT_SIZE, HOP_SIZE);
                
                // Show spectrogram
                Bitmap specBitmap = createSpectrogramBitmap(specFiltered);
                uiHandler.post(() -> {
                    spectrogramView.setImageBitmap(specBitmap);
                    spectrogramView.setVisibility(View.VISIBLE);
                });
                
                uiHandler.post(() -> statusText.setText(getString(
                        R.string.audio_filter_preview_status, inputLevel, outputLevel)));
                Thread.sleep(250);
                AudioFilterEngine.play(filtered);
            } catch (Exception exception) {
                uiHandler.post(() -> Toast.makeText(this,
                        exception.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
        previewThread.start();
    }

    private void toggleLivePreview() {
        if (isPreviewRunning) {
            stopLivePreview();
        } else {
            startLivePreview();
        }
    }

    private void startLivePreview() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
            return;
        }
        
        isPreviewRunning = true;
        readProfile();
        statusText.setText(\"Live preview running...\");
        ((Button) findViewById(R.id.button_live_preview)).setText(\"Stop Live Preview\");
        
        previewThread = new Thread(() -> {
            int minBuffer = android.media.AudioRecord.getMinBufferSize(
                    AudioFilterEngine.SAMPLE_RATE,
                    android.media.AudioFormat.CHANNEL_IN_MONO,
                    android.media.AudioFormat.ENCODING_PCM_16BIT);
            android.media.AudioRecord recorder = new android.media.AudioRecord(
                    android.media.MediaRecorder.AudioSource.MIC,
                    AudioFilterEngine.SAMPLE_RATE,
                    android.media.AudioFormat.CHANNEL_IN_MONO,
                    android.media.AudioFormat.ENCODING_PCM_16BIT,
                    minBuffer);
            
            if (recorder.getState() == android.media.AudioRecord.STATE_INITIALIZED) {
                recorder.startRecording();
                short[] buffer = new short[FFT_SIZE];
                
                while (isPreviewRunning) {
                    int read = recorder.read(buffer, 0, buffer.length);
                    if (read > 0) {
                        // Apply filter
                        short[] filtered = AudioFilterEngine.filter(Arrays.copyOf(buffer, read), profile);
                        
                        // Generate spectrogram
                        double[][] spec = AudioFilterEngine.generateSpectrogram(filtered, FFT_SIZE, HOP_SIZE);
                        if (spec.length > 0 && spec[0].length > 0) {
                            Bitmap bitmap = createSpectrogramBitmap(spec);
                            final Bitmap finalBitmap = bitmap;
                            uiHandler.post(() -> {
                                if (isPreviewRunning) {
                                    spectrogramView.setImageBitmap(finalBitmap);
                                    spectrogramView.setVisibility(View.VISIBLE);
                                }
                            });
                        }
                        
                        // Update level
                        double level = AudioFilterEngine.levelDb(filtered);
                        uiHandler.post(() -> statusText.setText(
                                String.format(\"Live level: %.1f dB\", level)));
                    }
                    
                    try { Thread.sleep(100); } catch (InterruptedException e) { break; }
                }
                recorder.stop();
            }
            recorder.release();
        });
        previewThread.start();
    }

    private void stopLivePreview() {
        isPreviewRunning = false;
        if (previewThread != null) {
            previewThread.interrupt();
            try { previewThread.join(1000); } catch (InterruptedException ignored) {}
        }
        statusText.setText(\"Live preview stopped\");
        ((Button) findViewById(R.id.button_live_preview)).setText(\"Start Live Preview\");
    }

    private Bitmap createSpectrogramBitmap(double[][] spectrogram) {
        int width = spectrogram[0].length;
        int height = spectrogram.length;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        
        // Find min/max for normalization
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (double[] row : spectrogram) {
            for (double val : row) {
                if (val < min) min = val;
                if (val > max) max = val;
            }
        }
        double range = max - min;
        if (range < 1e-6) range = 1;
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double normalized = (spectrogram[y][x] - min) / range;
                int color = hsvToRgb((float) (0.6 * (1.0 - normalized)), 1f, 1f);
                paint.setColor(color);
                canvas.drawPoint(x, height - 1 - y, paint);
            }
        }
        
        // Draw frequency labels
        paint.setColor(Color.WHITE);
        paint.setTextSize(20);
        for (int i = 0; i <= 4; i++) {
            int y = height * i / 4;
            float freq = (AudioFilterEngine.SAMPLE_RATE / 2.0f) * (1.0f - (float) y / height);
            canvas.drawText(String.format(\"%.0f Hz\", freq), 5, y + 5, paint);
        }
        
        return bitmap;
    }

    private int hsvToRgb(float h, float s, float v) {
        int i = (int) (h * 6);
        float f = h * 6 - i;
        float p = v * (1 - s);
        float q = v * (1 - f * s);
        float t = v * (1 - (1 - f) * s);
        switch (i % 6) {
            case 0: return Color.rgb((int)(v*255), (int)(t*255), (int)(p*255));
            case 1: return Color.rgb((int)(q*255), (int)(v*255), (int)(p*255));
            case 2: return Color.rgb((int)(p*255), (int)(v*255), (int)(t*255));
            case 3: return Color.rgb((int)(p*255), (int)(q*255), (int)(v*255));
            case 4: return Color.rgb((int)(t*255), (int)(p*255), (int)(v*255));
            case 5: return Color.rgb((int)(v*255), (int)(p*255), (int)(q*255));
        }
        return Color.BLACK;
    }

    private void startPreviewWithPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
        } else {
            runPreview();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO &&
                grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            runPreview();
        } else if (requestCode == REQUEST_RECORD_AUDIO) {
            Toast.makeText(this, R.string.microphone_permission_required, Toast.LENGTH_LONG).show();
        }
    }

    private void loadProfile() {
        profile.highPassEnabled = preferences.appSharedPrefs.getBoolean(\"filter_hp_enabled\", false);
        profile.highPassHz = parse(preferences.appSharedPrefs.getString(\"filter_hp_hz\", \"100\"), 100);
        profile.lowPassEnabled = preferences.appSharedPrefs.getBoolean(\"filter_lp_enabled\", false);
        profile.lowPassHz = parse(preferences.appSharedPrefs.getString(\"filter_lp_hz\", \"8000\"), 8000);
        profile.notchEnabled = preferences.appSharedPrefs.getBoolean(\"filter_notch_enabled\", true);
        profile.notchHz = parse(preferences.appSharedPrefs.getString(\"filter_notch_hz\", \"50\"), 50);
        profile.bandPassEnabled = preferences.appSharedPrefs.getBoolean(\"filter_bp_enabled\", false);
        profile.bandLowHz = parse(preferences.appSharedPrefs.getString(\"filter_bp_low\", \"300\"), 300);
        profile.bandHighHz = parse(preferences.appSharedPrefs.getString(\"filter_bp_high\", \"3400\"), 3400);
        profile.order = (int) parse(preferences.appSharedPrefs.getString(\"filter_order\", \"2\"), 2);
    }

    private void readProfile() {
        profile.highPassEnabled = ((Switch) findViewById(R.id.filter_high_pass)).isChecked();
        profile.highPassHz = parse(text(R.id.filter_high_pass_hz), 100);
        profile.lowPassEnabled = ((Switch) findViewById(R.id.filter_low_pass)).isChecked();
        profile.lowPassHz = parse(text(R.id.filter_low_pass_hz), 8000);
        profile.notchEnabled = ((Switch) findViewById(R.id.filter_notch)).isChecked();
        profile.notchHz = parse(text(R.id.filter_notch_hz), 50);
        profile.bandPassEnabled = ((Switch) findViewById(R.id.filter_band_pass)).isChecked();
        profile.bandLowHz = parse(text(R.id.filter_band_low_hz), 300);
        profile.bandHighHz = parse(text(R.id.filter_band_high_hz), 3400);
        profile.order = (int) Math.max(1, Math.min(6, parse(text(R.id.filter_order), 2)));
    }

    private void saveProfile() {
        readProfile();
        preferences.appSharedPrefs.edit()
                .putBoolean(\"filter_hp_enabled\", profile.highPassEnabled)
                .putString(\"filter_hp_hz\", String.valueOf(profile.highPassHz))
                .putBoolean(\"filter_lp_enabled\", profile.lowPassEnabled)
                .putString(\"filter_lp_hz\", String.valueOf(profile.lowPassHz))
                .putBoolean(\"filter_notch_enabled\", profile.notchEnabled)
                .putString(\"filter_notch_hz\", String.valueOf(profile.notchHz))
                .putBoolean(\"filter_bp_enabled\", profile.bandPassEnabled)
                .putString(\"filter_bp_low\", String.valueOf(profile.bandLowHz))
                .putString(\"filter_bp_high\", String.valueOf(profile.bandHighHz))
                .putString(\"filter_order\", String.valueOf(profile.order))
                .apply();
        Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show();
    }

    private interface SwitchSetter {
        void set(boolean value);
    }

    private void bindSwitch(int id, boolean value, SwitchSetter setter) {
        Switch view = findViewById(id);
        view.setChecked(value);
    }

    private void bindText(int id, Number value) {
        EditText view = findViewById(id);
        view.setText(String.valueOf(value.doubleValue()));
    }

    private String text(int id) {
        return ((EditText) findViewById(id)).getText().toString().trim();
    }

    private double parse(String value, double fallback) {
        try {
            return Double.parseDouble(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopLivePreview();
    }
}
