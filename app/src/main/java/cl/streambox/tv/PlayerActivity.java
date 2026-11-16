package cl.streambox.tv;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
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
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Reproductor (diseño v1, pantalla 4): OSD con ruta, título, barra cian, «siguiente en
 * la carpeta» y línea técnica real; ▲ abre audio y subtítulos con las pistas de Media3.
 *
 * <p>Al terminar el video devuelve {@link #EXTRA_RESULT_ENDED} a la biblioteca, que decide si
 * sigue con el siguiente de la carpeta o vuelve a mostrar la biblioteca con el foco en este video.
 */
@OptIn(markerClass = UnstableApi.class)
public final class PlayerActivity extends Activity {
    static final String EXTRA_URI = "video_uri";
    static final String EXTRA_TITLE = "video_title";
    static final String EXTRA_DURATION = "video_duration";
    static final String EXTRA_RESUME_KEY = "video_resume_key";
    /** Posición de inicio elegida en Detalle; -1 = usar el avance guardado. */
    static final String EXTRA_START_MS = "video_start_ms";
    static final String EXTRA_RETENTION_MS = "video_retention_ms";
    static final String EXTRA_SERVER = "video_server";
    static final String EXTRA_FOLDER = "video_folder";
    static final String EXTRA_HOST = "video_host";
    static final String EXTRA_NEXT_TITLE = "video_next_title";
    static final String EXTRA_NEXT_DURATION = "video_next_duration";
    /** Resultado: clave de reanudado del video que se estaba viendo. */
    static final String EXTRA_RESULT_KEY = "video_result_key";
    /** Resultado: true si el video llegó al final (y no se cerró a mano). */
    static final String EXTRA_RESULT_ENDED = "video_result_ended";

    private static final String TAG = "VibeDlnaPlayer";
    /** Mismo ocultado automático que el OSD de VibeM3U (OVERLAY_TIMEOUT_MS). */
    private static final long OVERLAY_TIMEOUT_MS = 4_500L;
    private static final long SEEK_STEP_MS = 10_000L;
    private static final long SEEK_FAST_STEP_MS = 30_000L;
    private static final long RESUME_SAVE_INTERVAL_MS = 10_000L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService resumeExecutor = Executors.newSingleThreadExecutor();

    private PlayerView playerView;
    private FrameLayout overlay;
    private LinearLayout osd;
    private TextView eyebrow;
    private TextView title;
    private StraightProgressBar bar;
    private TextView time;
    private TextView next;
    private TextView help;
    private TextView clock;
    private TextView date;
    private TextView tech;
    private TextView techSeparator;
    private TextView from;
    private LinearLayout trackPanel;
    private LinearLayout trackColumns;

    private ExoPlayer player;
    private PlaybackResumeStore resumeStore;
    private String resumeKey;
    private long declaredDurationMs;
    private long startMs = -1L;
    private String server = "";
    private String folder = "";
    private String host = "";
    private String nextTitle = "";
    private long nextDurationMs;
    private long lastBackActionMs = -1_000L;
    private boolean finishing;

    private boolean trackPanelOpen;
    private int trackGroup;
    private final int[] trackIndex = new int[2];
    private final List<List<TrackChoice>> choices = new ArrayList<>();

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
                mainHandler.postDelayed(this, 500L);
            }
        }
    };

    /** Oculta todo el OSD junto: degradado, reloj, línea técnica y título. Con ▲ abierto, no. */
    private final Runnable hideOverlay = () -> {
        if (trackPanelOpen) return;
        overlay.setVisibility(View.GONE);
        mainHandler.removeCallbacks(updateProgress);
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        playerView = findViewById(R.id.player_view);
        overlay = findViewById(R.id.player_overlay);
        osd = findViewById(R.id.player_osd);
        eyebrow = findViewById(R.id.player_eyebrow);
        title = findViewById(R.id.player_title);
        bar = findViewById(R.id.player_bar);
        time = findViewById(R.id.player_time);
        next = findViewById(R.id.player_next);
        help = findViewById(R.id.player_help);
        clock = findViewById(R.id.player_clock);
        date = findViewById(R.id.player_date);
        tech = findViewById(R.id.player_tech);
        techSeparator = findViewById(R.id.player_tech_sep);
        from = findViewById(R.id.player_from);
        trackPanel = findViewById(R.id.player_tracks);
        trackColumns = findViewById(R.id.player_track_columns);
        registerBackCallback();
        enterImmersiveMode();

        String uriValue = getIntent().getStringExtra(EXTRA_URI);
        String titleValue = getIntent().getStringExtra(EXTRA_TITLE);
        declaredDurationMs = getIntent().getLongExtra(EXTRA_DURATION, 0L);
        resumeKey = getIntent().getStringExtra(EXTRA_RESUME_KEY);
        startMs = getIntent().getLongExtra(EXTRA_START_MS, -1L);
        long retention = getIntent().getLongExtra(EXTRA_RETENTION_MS, PlaybackResumeStore.RETENTION_MS);
        server = textExtra(EXTRA_SERVER);
        folder = textExtra(EXTRA_FOLDER);
        host = textExtra(EXTRA_HOST);
        nextTitle = textExtra(EXTRA_NEXT_TITLE);
        nextDurationMs = getIntent().getLongExtra(EXTRA_NEXT_DURATION, 0L);
        resumeStore = new PlaybackResumeStore(this, retention);
        if (uriValue == null || uriValue.isBlank()) {
            finish();
            return;
        }
        title.setText(titleValue == null || titleValue.isBlank() ? "Video" : titleValue);
        eyebrow.setText(DlnaMediaFormat.join(java.util.Arrays.asList(server, folder), " · ")
                .toUpperCase(Locale.ROOT));
        from.setText(PlaybackUiFormatter.fromLine(server, host));
        // Sin siguiente video la línea «Después» no se muestra: ni texto ni espacio.
        if (nextTitle.isEmpty()) {
            next.setVisibility(View.GONE);
        } else {
            next.setText(nextTitle + " · " + DlnaMediaFormat.duration(nextDurationMs));
            next.setVisibility(View.VISIBLE);
        }
        createPlayer(Uri.parse(uriValue));
        showOverlay();
    }

    /** Código HTTP de la causa del error, o -1 si no hubo respuesta HTTP. */
    private static int httpStatusOf(Throwable error) {
        Throwable cause = error;
        for (int depth = 0; cause != null && depth < 8; depth++) {
            if (cause instanceof HttpDataSource.InvalidResponseCodeException) {
                return ((HttpDataSource.InvalidResponseCodeException) cause).responseCode;
            }
            cause = cause.getCause();
        }
        return -1;
    }

    private String textExtra(String name) {
        String value = getIntent().getStringExtra(name);
        return value == null ? "" : value;
    }

    private void createPlayer(Uri uri) {
        DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent(ClientIdentity.forDevice(this));
        DefaultMediaSourceFactory sourceFactory = new DefaultMediaSourceFactory(
                new DefaultDataSource.Factory(this, httpFactory)
        );
        player = new ExoPlayer.Builder(this).setMediaSourceFactory(sourceFactory).build();
        player.setAudioAttributes(AudioAttributes.DEFAULT, true);
        player.setHandleAudioBecomingNoisy(true);
        playerView.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_READY) updatePlaybackUi();
                // Al terminar no queda en «en pausa»: se devuelve el resultado y la biblioteca decide.
                if (playbackState == Player.STATE_ENDED) mainHandler.post(() -> finishWithResult(true));
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                updatePlaybackUi();
                // Reproduciendo, el OSD se oculta a su tiempo; en pausa se queda.
                if (overlay.getVisibility() == View.VISIBLE) scheduleAutoHide();
            }

            @Override
            public void onTracksChanged(Tracks tracks) {
                if (trackPanelOpen) rebuildTracks();
                updateTechLine();
            }

            @Override
            public void onVideoSizeChanged(androidx.media3.common.VideoSize videoSize) {
                updateTechLine();
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                // El código técnico va al log; en pantalla, un mensaje para quien mira la TV.
                Log.w(TAG, "Error de reproducción: " + error.getErrorCodeName(), error);
                eyebrow.setText(PlaybackUiFormatter.playbackErrorMessage(
                        error.getErrorCodeName(), httpStatusOf(error)));
                showOverlay();
            }
        });
        player.setMediaItem(MediaItem.fromUri(uri));
        long resumePosition = 0L;
        if (startMs >= 0L) {
            resumePosition = startMs;
        } else {
            PlaybackResumeStore.ResumePoint saved = resumeKey == null
                    ? null
                    : resumeStore.get(resumeKey, System.currentTimeMillis());
            if (saved != null) {
                resumePosition = saved.positionMs;
                if (declaredDurationMs <= 0L && saved.durationMs > 0L) {
                    declaredDurationMs = saved.durationMs;
                }
            }
        }
        player.seekTo(resumePosition);
        player.prepare();
        player.play();
        mainHandler.removeCallbacks(saveResumePoint);
        mainHandler.postDelayed(saveResumePoint, RESUME_SAVE_INTERVAL_MS);
    }

    private void updateTechLine() {
        if (player == null) return;
        Format video = player.getVideoFormat();
        Format audio = player.getAudioFormat();
        List<String> parts = new ArrayList<>();
        if (video != null && video.width > 0 && video.height > 0) {
            parts.add(video.width + " × " + video.height);
        }
        String videoCodec = PlaybackUiFormatter.friendlyCodec(
                video == null ? null : video.sampleMimeType,
                video == null ? null : video.codecs
        );
        if (!"—".equals(videoCodec)) parts.add(videoCodec);
        if (audio != null) {
            String audioCodec = PlaybackUiFormatter.friendlyCodec(audio.sampleMimeType, audio.codecs);
            String channels = PlaybackUiFormatter.audioChannels(audio.channelCount);
            String audioText = ("—".equals(audioCodec) ? "" : audioCodec)
                    + (channels.isEmpty() ? "" : " " + channels);
            if (!audioText.trim().isEmpty()) parts.add(audioText.trim());
        }
        tech.setText(DlnaMediaFormat.join(parts, " · "));
        // El separador entre la técnica y «desde…» solo existe si hay técnica.
        techSeparator.setVisibility(parts.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /** Muestra el OSD completo y, si el video suena, lo programa para ocultarse. */
    private void showOverlay() {
        overlay.setVisibility(View.VISIBLE);
        mainHandler.removeCallbacks(updateProgress);
        updateProgress.run();
        scheduleAutoHide();
    }

    /** Oculta a los 4,5 s solo mientras el video se reproduce; en pausa el OSD se queda. */
    private void scheduleAutoHide() {
        mainHandler.removeCallbacks(hideOverlay);
        if (player != null && player.isPlaying() && !trackPanelOpen) {
            mainHandler.postDelayed(hideOverlay, OVERLAY_TIMEOUT_MS);
        }
    }

    private long playbackDuration() {
        if (player == null) return declaredDurationMs;
        long duration = player.getDuration();
        return duration <= 0L || duration == C.TIME_UNSET ? declaredDurationMs : duration;
    }

    private void updatePlaybackUi() {
        clock.setText(new SimpleDateFormat("HH:mm", new Locale("es")).format(new Date()));
        date.setText(new SimpleDateFormat("EEEE d 'de' MMMM", new Locale("es")).format(new Date())
                .toUpperCase(Locale.ROOT));
        if (player == null) return;
        long duration = playbackDuration();
        long position = Math.max(0L, player.getCurrentPosition());
        boolean playing = player.isPlaying();
        boolean known = duration > 0L && duration != C.TIME_UNSET;
        help.setText(PlaybackUiFormatter.helpLine(playing));
        time.setText(PlaybackUiFormatter.timeLine(position, known ? duration : 0L, playing));
        bar.setProgress(position, known ? duration : 0L);
        if (known) updateTechLine();
    }

    /** Medida en dp del OSD, sin el factor de escala de las pantallas de la biblioteca. */
    private int px(float dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                getResources().getDisplayMetrics()
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
        showOverlay();
    }

    // ---------------------------------------------------------------- audio y subtítulos

    private void openTracks() {
        if (player == null) return;
        trackPanelOpen = true;
        trackPanel.setVisibility(View.VISIBLE);
        rebuildTracks();
        showOverlay();
    }

    private void closeTracks() {
        trackPanelOpen = false;
        trackPanel.setVisibility(View.GONE);
        showOverlay();
    }

    private void rebuildTracks() {
        choices.clear();
        List<TrackChoice> audio = new ArrayList<>();
        List<TrackChoice> subs = new ArrayList<>();
        subs.add(TrackChoice.off(!player.getCurrentTracks().isTypeSelected(C.TRACK_TYPE_TEXT)));
        for (Tracks.Group group : player.getCurrentTracks().getGroups()) {
            int type = group.getType();
            if (type != C.TRACK_TYPE_AUDIO && type != C.TRACK_TYPE_TEXT) continue;
            for (int i = 0; i < group.getMediaTrackGroup().length; i++) {
                if (!group.isTrackSupported(i)) continue;
                Format format = group.getTrackFormat(i);
                TrackChoice choice = TrackChoice.track(group, i, format, group.isTrackSelected(i));
                if (type == C.TRACK_TYPE_AUDIO) audio.add(choice);
                else subs.add(choice);
            }
        }
        choices.add(audio);
        choices.add(subs);
        trackColumns.removeAllViews();
        trackColumns.addView(column("AUDIO", audio, 0));
        trackColumns.addView(column("SUBTÍTULOS", subs, 1));
        trackIndex[0] = Math.min(trackIndex[0], Math.max(0, audio.size() - 1));
        trackIndex[1] = Math.min(trackIndex[1], Math.max(0, subs.size() - 1));
        renderFocus();
    }

    private LinearLayout column(String label, List<TrackChoice> items, int group) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                px(240),
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.rightMargin = px(24);
        column.setLayoutParams(params);
        TextView head = plainText(label, 7.5f, TvUi.INK_3);
        head.setLetterSpacing(0.2f);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        head.setTag("head" + group);
        column.addView(head);
        for (int i = 0; i < items.size(); i++) {
            TrackChoice choice = items.get(i);
            TextView row = plainText(choice.label, 10f, TvUi.INK_2);
            row.setTag(group + ":" + i);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            rowParams.topMargin = px(6);
            row.setLayoutParams(rowParams);
            if (choice.selected) row.setText("●  " + choice.label);
            column.addView(row);
        }
        return column;
    }

    private TextView plainText(String value, float sp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        view.setSingleLine(true);
        return view;
    }

    /** Resalta la fila con foco de cada columna: blanco en negrita y línea. */
    private void renderFocus() {
        for (int g = 0; g < 2; g++) {
            LinearLayout column = (LinearLayout) trackColumns.getChildAt(g);
            if (column == null) continue;
            for (int i = 1; i < column.getChildCount(); i++) {
                TextView row = (TextView) column.getChildAt(i);
                boolean focused = g == trackGroup && (i - 1) == trackIndex[g];
                row.setTextColor(focused ? TvUi.WHITE : TvUi.INK_2);
                row.setTypeface(null, focused ? Typeface.BOLD : Typeface.NORMAL);
                row.setPaintFlags(focused ? row.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG
                        : row.getPaintFlags() & ~android.graphics.Paint.UNDERLINE_TEXT_FLAG);
            }
        }
    }

    private void moveTrack(int dx, int dy) {
        if (dx != 0) {
            trackGroup = dx > 0 ? 1 : 0;
        }
        if (dy != 0) {
            List<TrackChoice> list = choices.get(trackGroup);
            trackIndex[trackGroup] = Math.max(0, Math.min(list.size() - 1, trackIndex[trackGroup] + dy));
        }
        renderFocus();
    }

    private void chooseTrack() {
        List<TrackChoice> list = choices.get(trackGroup);
        if (list.isEmpty()) return;
        TrackChoice choice = list.get(trackIndex[trackGroup]);
        androidx.media3.common.TrackSelectionParameters.Builder builder =
                player.getTrackSelectionParameters().buildUpon();
        if (choice.off) {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT);
        } else if (choice.group.getType() == C.TRACK_TYPE_AUDIO) {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                    .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                    .setOverrideForType(new TrackSelectionOverride(
                            choice.group.getMediaTrackGroup(), choice.index));
        } else {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .setOverrideForType(new TrackSelectionOverride(
                            choice.group.getMediaTrackGroup(), choice.index));
        }
        player.setTrackSelectionParameters(builder.build());
        rebuildTracks();
    }

    // ---------------------------------------------------------------- teclas

    @Override
    @SuppressLint("GestureBackNavigation")
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();
            if (trackPanelOpen) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    moveTrack(-1, 0);
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    moveTrack(1, 0);
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    moveTrack(0, -1);
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    moveTrack(0, 1);
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                    if (event.getRepeatCount() == 0) chooseTrack();
                    return true;
                }
            }
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
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP && !trackPanelOpen) {
                openTracks();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                    || keyCode == KeyEvent.KEYCODE_ENTER) {
                if (player != null) {
                    if (player.isPlaying()) player.pause();
                    else player.play();
                    savePlaybackPosition();
                    // En pausa el OSD se queda para mostrar «en pausa»; al reproducir se oculta.
                    showOverlay();
                }
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_INFO) {
                showOverlay();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE && player != null) {
                player.pause();
                savePlaybackPosition();
                showOverlay();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY && player != null) {
                player.play();
                showOverlay();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE && player != null) {
                if (player.isPlaying()) player.pause();
                else player.play();
                savePlaybackPosition();
                showOverlay();
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

    /**
     * Atrás: cierra el panel de pistas; si el OSD está a la vista, lo oculta; y otra vez cierra
     * el video. Igual que VibeM3U: el primer Atrás oculta y el segundo sale.
     */
    private void handleBackAction() {
        long now = SystemClock.uptimeMillis();
        if (now - lastBackActionMs < 250L) return;
        lastBackActionMs = now;
        if (trackPanelOpen) {
            closeTracks();
            return;
        }
        if (overlay.getVisibility() == View.VISIBLE) {
            hideOverlay.run();
            return;
        }
        finishWithResult(false);
    }

    /**
     * Cierra el reproductor y devuelve a la biblioteca si el video terminó ({@code ended}) o si
     * se cerró a mano. La biblioteca lee la clave del video para decidir qué sigue.
     */
    private void finishWithResult(boolean ended) {
        if (finishing) return;
        finishing = true;
        savePlaybackPosition();
        Intent result = new Intent()
                .putExtra(EXTRA_RESULT_KEY, resumeKey == null ? "" : resumeKey)
                .putExtra(EXTRA_RESULT_ENDED, ended);
        setResult(RESULT_OK, result);
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
        long position = Math.max(0L, player.getCurrentPosition());
        long savedAt = System.currentTimeMillis();
        // Síncrono: al volver, la biblioteca lee el avance en onResume y no debe ir detrás de un hilo.
        resumeStore.save(resumeKey, position, duration, savedAt);
    }

    @Override
    protected void onDestroy() {
        releasePlayer();
        resumeExecutor.shutdown();
        super.onDestroy();
    }

    /** Una opción de audio o subtítulos; {@code off} es «Desactivados» en subtítulos. */
    private static final class TrackChoice {
        final String label;
        final Tracks.Group group;
        final int index;
        final boolean selected;
        final boolean off;

        private TrackChoice(String label, Tracks.Group group, int index, boolean selected, boolean off) {
            this.label = label;
            this.group = group;
            this.index = index;
            this.selected = selected;
            this.off = off;
        }

        static TrackChoice off(boolean selected) {
            return new TrackChoice("Desactivados", null, -1, selected, true);
        }

        static TrackChoice track(Tracks.Group group, int index, Format format, boolean selected) {
            String codec = PlaybackUiFormatter.friendlyCodec(format.sampleMimeType, format.codecs);
            String text;
            if (group.getType() == C.TRACK_TYPE_AUDIO) {
                text = PlaybackUiFormatter.audioTrackLabel(format.language, codec, format.channelCount);
            } else {
                String language = format.language == null || format.language.isEmpty()
                        || "und".equalsIgnoreCase(format.language)
                        ? "Pista " + (index + 1)
                        : PlaybackUiFormatter.languageName(format.language);
                text = DlnaMediaFormat.join(java.util.Arrays.asList(
                        language,
                        "—".equals(codec) ? "" : codec
                ), " · ");
            }
            return new TrackChoice(text, group, index, selected, false);
        }
    }
}
