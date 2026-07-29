package cl.streambox.tv;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.SeekBar;
import android.widget.TextView;
import android.window.OnBackInvokedDispatcher;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class PlayerActivity extends Activity {
    static final String EXTRA_URI = "video_uri";
    static final String EXTRA_TITLE = "video_title";
    static final String EXTRA_DURATION = "video_duration";
    static final String EXTRA_RESUME_KEY = "video_resume_key";
    private static final long OVERLAY_TIMEOUT_MS = 4_500L;
    private static final long SEEK_STEP_MS = 10_000L;
    private static final long SEEK_FAST_STEP_MS = 30_000L;
    private static final long RESUME_SAVE_INTERVAL_MS = 10_000L;
    private static final int PROGRESS_MAX = 1_000;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private PlayerView playerView;
    private View overlay;
    private View clockPanel;
    private TextView clock;
    private TextView endingTime;
    private TextView title;
    private TextView diagnostics;
    private TextView seekTime;
    private SeekBar seekBar;
    private ExoPlayer player;
    private PlaybackResumeStore resumeStore;
    private String resumeKey;
    private long declaredDurationMs;
    private boolean seekControlsRequested;
    private long lastBackActionMs = -1_000L;

    private final Runnable saveResumePoint = new Runnable() {
        @Override
        public void run() {
            savePlaybackPosition();
            if (player != null) mainHandler.postDelayed(this, RESUME_SAVE_INTERVAL_MS);
        }
    };

    private final Runnable updateProgress = new Runnable() {
        @Override
        public void run() {
            updatePlaybackUi();
            if (overlay.getVisibility() == View.VISIBLE) {
                mainHandler.postDelayed(this, 250L);
            }
        }
    };

    private final Runnable hideOverlay = () -> {
        overlay.setVisibility(View.GONE);
        seekBar.setVisibility(View.GONE);
        seekTime.setVisibility(View.GONE);
        seekControlsRequested = false;
        clockPanel.setVisibility(View.GONE);
        mainHandler.removeCallbacks(updateProgress);
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        playerView = findViewById(R.id.player_view);
        overlay = findViewById(R.id.player_overlay);
        clockPanel = findViewById(R.id.player_clock_panel);
        clock = findViewById(R.id.player_clock);
        endingTime = findViewById(R.id.player_ending_time);
        title = findViewById(R.id.player_title);
        diagnostics = findViewById(R.id.player_diagnostics);
        seekTime = findViewById(R.id.player_seek_time);
        seekBar = findViewById(R.id.player_seek_bar);
        registerBackCallback();
        enterImmersiveMode();

        String uriValue = getIntent().getStringExtra(EXTRA_URI);
        String titleValue = getIntent().getStringExtra(EXTRA_TITLE);
        declaredDurationMs = getIntent().getLongExtra(EXTRA_DURATION, 0L);
        resumeKey = getIntent().getStringExtra(EXTRA_RESUME_KEY);
        resumeStore = new PlaybackResumeStore(this);
        if (uriValue == null || uriValue.isBlank()) {
            finish();
            return;
        }
        title.setText(titleValue == null || titleValue.isBlank() ? "Video" : titleValue);
        createPlayer(Uri.parse(uriValue));
        showOverlay(false, false);
    }

    private void createPlayer(Uri uri) {
        player = new ExoPlayer.Builder(this).build();
        player.setAudioAttributes(AudioAttributes.DEFAULT, true);
        player.setHandleAudioBecomingNoisy(true);
        playerView.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                updateDiagnostics();
                if (playbackState == Player.STATE_READY) updatePlaybackUi();
                if (playbackState == Player.STATE_ENDED) savePlaybackPosition();
            }

            @Override
            public void onVideoSizeChanged(VideoSize videoSize) {
                updateDiagnostics();
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                diagnostics.setText(error.getErrorCodeName());
                showOverlay(true, false);
            }
        });
        player.setMediaItem(MediaItem.fromUri(uri));
        PlaybackResumeStore.ResumePoint saved = resumeStore == null || resumeKey == null
                ? null
                : resumeStore.get(resumeKey, System.currentTimeMillis());
        if (saved != null) {
            long resumePosition = saved.positionMs;
            if (declaredDurationMs <= 0L && saved.durationMs > 0L) {
                declaredDurationMs = saved.durationMs;
            }
            player.seekTo(resumePosition);
        } else {
            player.seekTo(0L);
        }
        player.prepare();
        player.play();
        mainHandler.removeCallbacks(saveResumePoint);
        mainHandler.postDelayed(saveResumePoint, RESUME_SAVE_INTERVAL_MS);
    }

    @OptIn(markerClass = UnstableApi.class)
    private void updateDiagnostics() {
        if (player == null) return;
        Format video = player.getVideoFormat();
        Format audio = player.getAudioFormat();
        String resolution = video != null && video.width > 0 && video.height > 0
                ? video.width + " \u00d7 " + video.height
                : "Resoluci\u00f3n pendiente";
        String videoCodec = PlaybackUiFormatter.friendlyCodec(
                video == null ? null : video.sampleMimeType,
                video == null ? null : video.codecs
        );
        String audioCodec = PlaybackUiFormatter.friendlyCodec(
                audio == null ? null : audio.sampleMimeType,
                audio == null ? null : audio.codecs
        );
        diagnostics.setText(resolution + "\n" + videoCodec + " \u00b7 " + audioCodec);
    }

    private void showOverlay(boolean keepVisible, boolean showSeekControls) {
        seekControlsRequested = showSeekControls;
        overlay.setVisibility(View.VISIBLE);
        clockPanel.setVisibility(View.VISIBLE);
        seekBar.setVisibility(showSeekControls ? View.VISIBLE : View.GONE);
        seekTime.setVisibility(showSeekControls ? View.VISIBLE : View.GONE);
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

    private void updatePlaybackUi() {
        clock.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
        if (player == null) {
            endingTime.setVisibility(View.GONE);
            seekTime.setVisibility(View.GONE);
            return;
        }
        long duration = playbackDuration();
        long position = Math.max(0L, player.getCurrentPosition());
        if (duration <= 0L || duration == C.TIME_UNSET) {
            endingTime.setVisibility(View.GONE);
            seekBar.setVisibility(View.GONE);
            seekTime.setVisibility(View.GONE);
            return;
        }
        seekBar.setProgress(PlaybackMath.progress(position, duration, PROGRESS_MAX));
        seekTime.setText(PlaybackUiFormatter.positionAndDuration(position, duration));
        if (seekControlsRequested && overlay.getVisibility() == View.VISIBLE) {
            seekBar.setVisibility(View.VISIBLE);
            seekTime.setVisibility(View.VISIBLE);
        }
        endingTime.setText(PlaybackUiFormatter.endingAt(
                System.currentTimeMillis(),
                position,
                duration,
                Locale.getDefault()
        ));
        endingTime.setVisibility(
                overlay.getVisibility() == View.VISIBLE ? View.VISIBLE : View.GONE
        );
        clockPanel.setVisibility(
                overlay.getVisibility() == View.VISIBLE ? View.VISIBLE : View.GONE
        );
    }

    private void seekBy(long deltaMs) {
        if (player == null) return;
        long target = PlaybackMath.clampSeekPosition(
                player.getCurrentPosition(),
                deltaMs,
                playbackDuration()
        );
        player.seekTo(target);
        showOverlay(false, true);
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
                showOverlay(false, false);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE && player != null) {
                player.pause();
                savePlaybackPosition();
                showOverlay(false, true);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY && player != null) {
                player.play();
                showOverlay(false, true);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE && player != null) {
                if (player.isPlaying()) player.pause();
                else player.play();
                savePlaybackPosition();
                showOverlay(false, true);
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
        long now = SystemClock.uptimeMillis();
        if (now - lastBackActionMs < 250L) return;
        lastBackActionMs = now;
        if (overlay.getVisibility() == View.VISIBLE || clockPanel.getVisibility() == View.VISIBLE) {
            hideOverlay.run();
            return;
        }
        savePlaybackPosition();
        releasePlayer();
        finish();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus && player != null) {
            player.pause();
            savePlaybackPosition();
        }
    }

    @Override
    protected void onPause() {
        if (player != null) {
            player.pause();
            savePlaybackPosition();
        }
        super.onPause();
    }

    @Override
    protected void onStop() {
        releasePlayer();
        super.onStop();
        if (!isFinishing() && !isChangingConfigurations()) finish();
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

    private void releasePlayer() {
        mainHandler.removeCallbacksAndMessages(null);
        if (player == null) return;
        savePlaybackPosition();
        player.pause();
        player.stop();
        playerView.setPlayer(null);
        player.release();
        player = null;
    }

    private void savePlaybackPosition() {
        if (resumeStore == null || resumeKey == null || resumeKey.isBlank() || player == null) {
            return;
        }
        long duration = playbackDuration();
        resumeStore.save(
                resumeKey,
                Math.max(0L, player.getCurrentPosition()),
                duration,
                System.currentTimeMillis()
        );
    }

    @Override
    protected void onDestroy() {
        releasePlayer();
        super.onDestroy();
    }
}
