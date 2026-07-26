package cl.streambox.tv;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.TextView;
import android.window.OnBackInvokedDispatcher;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import android.widget.SeekBar;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class PlayerActivity extends Activity {
    static final String EXTRA_URI = "video_uri";
    static final String EXTRA_TITLE = "video_title";
    static final String EXTRA_DURATION = "video_duration";
    private static final long OVERLAY_TIMEOUT_MS = 4_500L;
    private static final long SEEK_STEP_MS = 10_000L;
    private static final long SEEK_FAST_STEP_MS = 30_000L;
    private static final int PROGRESS_MAX = 1_000;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private PlayerView playerView;
    private View overlay;
    private TextView clock;
    private TextView title;
    private TextView diagnostics;
    private SeekBar seekBar;
    private ExoPlayer player;
    private long declaredDurationMs;

    private final Runnable updateProgress = new Runnable() {
        @Override
        public void run() {
            updateSeekProgress();
            if (overlay.getVisibility() == View.VISIBLE) {
                mainHandler.postDelayed(this, 250L);
            }
        }
    };

    private final Runnable hideOverlay = () -> {
        overlay.setVisibility(View.GONE);
        seekBar.setVisibility(View.GONE);
        clock.setVisibility(View.GONE);
        mainHandler.removeCallbacks(updateProgress);
    };

    private final Runnable updateClock = new Runnable() {
        @Override
        public void run() {
            clock.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
            mainHandler.postDelayed(this, 30_000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        playerView = findViewById(R.id.player_view);
        overlay = findViewById(R.id.player_overlay);
        clock = findViewById(R.id.player_clock);
        title = findViewById(R.id.player_title);
        diagnostics = findViewById(R.id.player_diagnostics);
        seekBar = findViewById(R.id.player_seek_bar);
        registerBackCallback();
        enterImmersiveMode();

        String uriValue = getIntent().getStringExtra(EXTRA_URI);
        String titleValue = getIntent().getStringExtra(EXTRA_TITLE);
        declaredDurationMs = getIntent().getLongExtra(EXTRA_DURATION, 0L);
        if (uriValue == null || uriValue.isBlank()) {
            finish();
            return;
        }
        title.setText(titleValue == null || titleValue.isBlank() ? "Video" : titleValue);
        updateClock.run();
        createPlayer(Uri.parse(uriValue));
        showOverlay(true);
    }

    private void createPlayer(Uri uri) {
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                updateDiagnostics();
                if (playbackState == Player.STATE_READY) showOverlay(false);
            }

            @Override
            public void onVideoSizeChanged(VideoSize videoSize) {
                updateDiagnostics();
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                diagnostics.setText(error.getErrorCodeName());
                showOverlay(true);
            }
        });
        player.setMediaItem(MediaItem.fromUri(uri));
        player.seekTo(0);
        player.prepare();
        player.play();
    }

    @OptIn(markerClass = UnstableApi.class)
    private void updateDiagnostics() {
        if (player == null) return;
        Format video = player.getVideoFormat();
        Format audio = player.getAudioFormat();
        String resolution = video != null && video.width > 0 && video.height > 0
                ? video.width + " × " + video.height
                : "Resolución pendiente";
        String videoCodec = codec(video == null ? null : video.sampleMimeType);
        String audioCodec = codec(audio == null ? null : audio.sampleMimeType);
        diagnostics.setText(resolution + "\n" + videoCodec + " · " + audioCodec);
    }

    private static String codec(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) return "—";
        int slash = mimeType.indexOf('/');
        return (slash >= 0 ? mimeType.substring(slash + 1) : mimeType)
                .toUpperCase(Locale.ROOT);
    }

    private void showOverlay(boolean keepVisible) {
        overlay.setVisibility(View.VISIBLE);
        seekBar.setVisibility(View.VISIBLE);
        clock.setVisibility(View.VISIBLE);
        mainHandler.removeCallbacks(hideOverlay);
        mainHandler.removeCallbacks(updateProgress);
        updateProgress.run();
        if (!keepVisible) mainHandler.postDelayed(hideOverlay, OVERLAY_TIMEOUT_MS);
    }

    private long playbackDuration() {
        if (player == null) return declaredDurationMs;
        long duration = player.getDuration();
        return duration <= 0L || duration == C.TIME_UNSET ? declaredDurationMs : duration;
    }

    private void updateSeekProgress() {
        if (player == null) return;
        seekBar.setProgress(PlaybackMath.progress(
                Math.max(0L, player.getCurrentPosition()),
                playbackDuration(),
                PROGRESS_MAX
        ));
    }

    private void seekBy(long deltaMs) {
        if (player == null) return;
        long target = PlaybackMath.clampSeekPosition(
                player.getCurrentPosition(),
                deltaMs,
                playbackDuration()
        );
        player.seekTo(target);
        showOverlay(false);
    }

    @Override
    @SuppressLint("GestureBackNavigation")
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                    || keyCode == KeyEvent.KEYCODE_MEDIA_REWIND) {
                long step = event.getRepeatCount() >= 5 ? SEEK_FAST_STEP_MS : SEEK_STEP_MS;
                seekBy(-step);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                    || keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) {
                long step = event.getRepeatCount() >= 5 ? SEEK_FAST_STEP_MS : SEEK_STEP_MS;
                seekBy(step);
                return true;
            }
            if (event.getRepeatCount() != 0) return super.dispatchKeyEvent(event);
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                handleBackAction();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                    || keyCode == KeyEvent.KEYCODE_ENTER
                    || keyCode == KeyEvent.KEYCODE_INFO) {
                showOverlay(false);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE && player != null) {
                if (player.isPlaying()) player.pause();
                else player.play();
                showOverlay(false);
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void registerBackCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    this::handleBackAction
            );
        }
    }

    private void handleBackAction() {
        finish();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        enterImmersiveMode();
    }

    private void enterImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.systemBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                );
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            );
        }
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }
}
