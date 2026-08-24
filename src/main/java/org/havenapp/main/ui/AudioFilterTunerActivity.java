package org.havenapp.main.ui;

import android.os.Bundle;
import android.Manifest;
import android.content.pm.PackageManager;
import android.widget.Button;
import android.widget.EditText;
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

public class AudioFilterTunerActivity extends AppCompatActivity {
    private PreferenceManager preferences;
    private AudioFilterEngine.FilterProfile profile = new AudioFilterEngine.FilterProfile();
    private TextView statusText;
    private static final int REQUEST_RECORD_AUDIO = 4101;

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
        statusText = findViewById(R.id.filter_status);

        preview.setOnClickListener(view -> startPreviewWithPermission());
        reset.setOnClickListener(view -> {
            profile = new AudioFilterEngine.FilterProfile();
            recreate();
        });
        save.setOnClickListener(view -> saveProfile());
    }

    private void runPreview() {
        readProfile();
        Toast.makeText(this, R.string.audio_filter_recording, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                short[] raw = AudioFilterEngine.recordPreview(15);
                double inputLevel = AudioFilterEngine.levelDb(raw);
                short[] filtered = AudioFilterEngine.filter(raw, profile);
                double outputLevel = AudioFilterEngine.levelDb(filtered);
                runOnUiThread(() -> statusText.setText(getString(
                        R.string.audio_filter_preview_status, inputLevel, outputLevel)));
                Thread.sleep(250);
                AudioFilterEngine.play(filtered);
            } catch (Exception exception) {
                runOnUiThread(() -> Toast.makeText(this,
                        exception.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
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
        profile.highPassEnabled = preferences.appSharedPrefs.getBoolean("filter_hp_enabled", false);
        profile.highPassHz = parse(preferences.appSharedPrefs.getString("filter_hp_hz", "100"), 100);
        profile.lowPassEnabled = preferences.appSharedPrefs.getBoolean("filter_lp_enabled", false);
        profile.lowPassHz = parse(preferences.appSharedPrefs.getString("filter_lp_hz", "8000"), 8000);
        profile.notchEnabled = preferences.appSharedPrefs.getBoolean("filter_notch_enabled", true);
        profile.notchHz = parse(preferences.appSharedPrefs.getString("filter_notch_hz", "50"), 50);
        profile.bandPassEnabled = preferences.appSharedPrefs.getBoolean("filter_bp_enabled", false);
        profile.bandLowHz = parse(preferences.appSharedPrefs.getString("filter_bp_low", "300"), 300);
        profile.bandHighHz = parse(preferences.appSharedPrefs.getString("filter_bp_high", "3400"), 3400);
        profile.order = (int) parse(preferences.appSharedPrefs.getString("filter_order", "2"), 2);
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
                .putBoolean("filter_hp_enabled", profile.highPassEnabled)
                .putString("filter_hp_hz", String.valueOf(profile.highPassHz))
                .putBoolean("filter_lp_enabled", profile.lowPassEnabled)
                .putString("filter_lp_hz", String.valueOf(profile.lowPassHz))
                .putBoolean("filter_notch_enabled", profile.notchEnabled)
                .putString("filter_notch_hz", String.valueOf(profile.notchHz))
                .putBoolean("filter_bp_enabled", profile.bandPassEnabled)
                .putString("filter_bp_low", String.valueOf(profile.bandLowHz))
                .putString("filter_bp_high", String.valueOf(profile.bandHighHz))
                .putString("filter_order", String.valueOf(profile.order))
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
}
