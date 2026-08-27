package org.havenapp.main.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;

import org.havenapp.main.R;
import org.havenapp.main.anomaly.AnomalyDataStore;
import org.havenapp.main.anomaly.AnomalySummaryBucket;
import org.havenapp.main.service.MonitorService;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import kotlin.Unit;

public class AnomalyChartActivity extends AppCompatActivity {
    private BarChart chart;
    private SeekBar timeRangeSeek;
    private TextView rangeText;
    private AnomalyDataStore dataStore;
    private long sessionStartTime;
    private long sessionEndTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_anomaly_chart);
        
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.anomaly_chart_title);
        }

        chart = findViewById(R.id.anomaly_chart);
        timeRangeSeek = findViewById(R.id.time_range_seek);
        rangeText = findViewById(R.id.range_text);

        dataStore = new AnomalyDataStore(this);
        sessionStartTime = getIntent().getLongExtra("session_start", System.currentTimeMillis() - 3600_000L);
        sessionEndTime = getIntent().getLongExtra("session_end", System.currentTimeMillis());

        setupChart();
        setupTimeRangeSeek();
        loadChartData();
    }

    private void setupChart() {
        chart.getDescription().setEnabled(false);
        chart.setDrawGridBackground(false);
        chart.setBackgroundColor(Color.TRANSPARENT);
        chart.setPinchZoom(true);
        chart.setScaleEnabled(true);
        chart.setDrawBarShadow(false);
        chart.setDrawValueAboveBar(true);

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setValueFormatter(new IndexAxisValueFormatter() {
            private final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            public String getFormattedValue(float value, int index) {
                return sdf.format(new Date((long) value));
            }
        });

        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setAxisMinimum(0f);
        
        chart.getAxisRight().setEnabled(false);
        chart.getLegend().setEnabled(true);
    }

    private void setupTimeRangeSeek() {
        timeRangeSeek.setMax(100);
        timeRangeSeek.setProgress(100);
        timeRangeSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    long range = sessionEndTime - sessionStartTime;
                    long newStart = sessionEndTime - (range * progress / 100);
                    updateRangeText(newStart, sessionEndTime);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                long range = sessionEndTime - sessionStartTime;
                long newStart = sessionEndTime - (range * seekBar.getProgress() / 100);
                loadChartData(newStart, sessionEndTime);
            }
        });
        updateRangeText(sessionStartTime, sessionEndTime);
    }

    private void updateRangeText(long start, long end) {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM d HH:mm", Locale.getDefault());
        rangeText.setText(sdf.format(new Date(start)) + " - " + sdf.format(new Date(end)));
    }

    private void loadChartData() {
        loadChartData(sessionStartTime, sessionEndTime);
    }

    private void loadChartData(long start, long end) {
        int bucketCount = 50;
        dataStore.getSummaryBuckets(start, end, bucketCount, buckets -> {
            runOnUiThread(() -> {
                List<BarEntry> totalEntries = new ArrayList<>();
                List<BarEntry> anomalyEntries = new ArrayList<>();
                
                for (int i = 0; i < buckets.size(); i++) {
                    AnomalySummaryBucket bucket = buckets.get(i);
                    totalEntries.add(new BarEntry(bucket.timestamp, bucket.totalPoints));
                    anomalyEntries.add(new BarEntry(bucket.timestamp, bucket.anomalyCount));
                }
                
                BarDataSet totalSet = new BarDataSet(totalEntries, "Total Points");
                totalSet.setColor(Color.rgb(100, 100, 200));
                totalSet.setValueTextSize(10f);
                
                BarDataSet anomalySet = new BarDataSet(anomalyEntries, "Anomalies (Outside Zone)");
                anomalySet.setColor(Color.rgb(220, 50, 50));
                anomalySet.setValueTextSize(10f);
                
                BarData data = new BarData(totalSet, anomalySet);
                data.setBarWidth((end - start) / (bucketCount * 1.5f));
                chart.setData(data);
                chart.invalidate();
            });
            return Unit.INSTANCE;
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dataStore != null) {
            dataStore.close();
        }
    }
}
