package org.havenapp.main.ui;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import org.havenapp.main.R;
import org.havenapp.main.anomaly.RuntimeLogStore;

public class RuntimeLogActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_runtime_log);
        setSupportActionBar(findViewById(R.id.toolbar));
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        StringBuilder text = new StringBuilder();
        for (RuntimeLogStore.Entry entry : RuntimeLogStore.snapshot()) {
            text.append(RuntimeLogStore.format(entry)).append('\n');
        }
        ((TextView) findViewById(R.id.log_text)).setText(text.toString());
    }
}
