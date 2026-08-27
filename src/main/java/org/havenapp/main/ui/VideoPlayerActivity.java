package org.havenapp.main.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.MediaController;
import android.widget.VideoView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import org.havenapp.main.R;

public class VideoPlayerActivity extends AppCompatActivity {

    private VideoView player;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_player);
        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));

        player = findViewById(R.id.player);
        MediaController controls = new MediaController(this);
        controls.setAnchorView(player);
        player.setMediaController(controls);
        player.setOnErrorListener((mediaPlayer, what, extra) -> true);
        Intent intent = getIntent();
        if (intent != null && intent.getData() != null) {
            player.setVideoURI(intent.getData());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        player.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        player.pause();
    }
}
