package org.havenapp.main.ui;

import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import org.havenapp.main.R;
import org.havenapp.main.anomaly.AnomalyDataStore;
import org.havenapp.main.anomaly.AnomalyPoint;
import org.havenapp.main.anomaly.AnomalySummaryBucket;
import org.havenapp.main.service.MonitorService;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AnomalyMapActivity extends AppCompatActivity {
    private final Handler handler = new Handler();
    private AnomalyEllipseView ellipseView;
    private TextView details;
    private List<AnomalyPoint> points = new ArrayList<>();
    private boolean playing;
    private int playbackIndex;
    private AnomalyDataStore dataStore;
    private long sessionStartTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_anomaly_map);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        ellipseView = findViewById(R.id.ellipse_view);
        details = findViewById(R.id.point_details);
        Button previous = findViewById(R.id.button_previous);
        Button next = findViewById(R.id.button_next);
        Button play = findViewById(R.id.button_play);
        SeekBar threshold = findViewById(R.id.threshold_seek);

        // Initialize data store
        dataStore = new AnomalyDataStore(this);
        
        // Get session start time from intent or use current session
        sessionStartTime = getIntent().getLongExtra(\"session_start\", System.currentTimeMillis() - 3600_000L);
        
        // Load real anomaly data
        loadAnomalyData();

        previous.setOnClickListener(view -> seek(playbackIndex - 1));
        next.setOnClickListener(view -> seek(playbackIndex + 1));
        play.setOnClickListener(view -> {
            playing = !playing;
            play.setText(playing ? R.string.pause : R.string.play);
            if (playing) handler.postDelayed(this::advance, 250L);
        });
        threshold.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int value, boolean fromUser) {
                ellipseView.setThresholdScale(0.25f + value / 100f * 4.75f);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
    }

    private void loadAnomalyData() {
        long endTime = System.currentTimeMillis();
        dataStore.getPoints(sessionStartTime, endTime, loadedPoints -> {
            runOnUiThread(() -> {
                points = loadedPoints;
                if (points.isEmpty()) {
                    // Fallback to demo points if no real data
                    points = loadDemoPoints();
                }
                ellipseView.setPoints(points);
                ellipseView.onPointSelected = this::showDetails;
                seek(points.size() - 1);
            });
        });
    }

    private void advance() {
        if (!playing || points.isEmpty()) return;
        if (playbackIndex + 1 >= points.size()) {
            playing = false;
            runOnUiThread(() -> {
                Button play = findViewById(R.id.button_play);
                play.setText(R.string.play);
            });
            return;
        }
        seek(playbackIndex + 1);
        handler.postDelayed(this::advance, 250L);
    }

    private void seek(int index) {
        if (points.isEmpty()) return;
        playbackIndex = Math.max(0, Math.min(index, points.size() - 1));
        ellipseView.seek(playbackIndex);
    }

    private void showDetails(AnomalyPoint point) {
        if (point == null) return;
        String time = SimpleDateFormat.getDateTimeInstance().format(new Date(point.timestamp));
        runOnUiThread(() -> {
            details.setText(String.format(Locale.US,
                    \"%s\nT² %.3f\n%s\", time, point.tSquared,
                    getString(point.anomaly ? R.string.outside_safe_zone : R.string.inside_safe_zone)));
        });
    }

    private List<AnomalyPoint> loadDemoPoints() {
        List<AnomalyPoint> result = new ArrayList<>();
        long now = System.currentTimeMillis() - 60_000L;
        for (int index = 0; index < 80; index++) {
            double angle = index / 6.0;
            double radius = index % 13 == 0 ? 2.4 : 0.8;
            double x = Math.cos(angle) * radius;
            double y = Math.sin(angle) * radius;
            double distance = Math.sqrt(x * x + y * y);
            result.add(new AnomalyPoint(now + index * 700L, x, y, distance, distance > 1.5));
        }
        return result;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dataStore != null) {
            dataStore.close();
        }
    }
}
