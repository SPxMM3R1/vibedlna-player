package cl.streambox.tv;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.window.OnBackInvokedDispatcher;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pantallas del Player 0.4.0 (diseño v1): Servidores, Biblioteca, Detalle y
 * Opciones. Todo vive en esta actividad; el Player de video es {@link PlayerActivity}.
 */
public final class MainActivity extends Activity {
    private static final String TAG = "VibeDlnaMain";
    private static final String EXTRA_DEBUG_SERVER_URL = "debug_server_url";
    private static final String UP_ROW_ID = "__arriba__";
    private static final long CLOCK_TICK_MS = 15_000L;
    private static final long SEARCH_RETRY_MS = 15_000L;
    private static final int REQUEST_PLAYER = 41;
    private static final String INCOMPLETE_NOTICE = "No se pudo leer todo: faltan algunos elementos";

    private enum Screen { SERVERS, LIBRARY, DETAIL, OPTIONS }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService dlnaExecutor = Executors.newFixedThreadPool(2);
    private final ExecutorService updateExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService thumbnailCacheExecutor = Executors.newSingleThreadExecutor();

    private final BrowseStack levels = new BrowseStack();
    private final List<List<VideoItem>> folderListStack = new ArrayList<>();
    private final List<DlnaServer> online = new ArrayList<>();
    private List<VideoItem> folders = new ArrayList<>();
    private List<VideoItem> videos = new ArrayList<>();

    private LibraryPreferences library;
    private PlayerSettings settings;
    private DlnaDiscovery discovery;
    private DlnaContentRepository contentRepository;
    private ThumbnailRepository thumbnailRepository;
    private PlaybackResumeStore resumeStore;
    private VideoAdapter adapter;
    private AppUpdater appUpdater;
    private OptionsScreen optionsScreen;
    private boolean supportedMimesLoaded;
    private java.util.Set<String> supportedMimes;

    private DlnaServer currentServer;
    private VideoItem selectedFolder;
    /** Clave del video que se reproduce; al volver del reproductor, la biblioteca lo busca. */
    private String playingKey = "";
    /** Tarjeta que debe recuperar el foco al volver a la biblioteca (null si no hace falta). */
    private String pendingFocusKey;
    private VideoItem detailEntry;
    private PlaybackResumeStore.ResumePoint detailPoint;
    private Screen screen = Screen.SERVERS;
    private Screen returnScreen = Screen.LIBRARY;
    /** URL de depuración (solo debug). Se conserva hasta registrarla con éxito. */
    private volatile String debugUrl;
    private volatile boolean debugRegistered;
    private boolean searching;
    private int discoveryGeneration;
    private int browseGeneration;
    private int backdropFlip;
    private String backdropToken = "";

    private FrameLayout root;
    private ImageView backdropA;
    private ImageView backdropB;
    private View focusLine;
    private View focusRing;
    private FrameLayout screenServers;
    private FrameLayout screenLibrary;
    private FrameLayout screenDetail;
    private FrameLayout screenOptions;
    private final List<TextView> clocks = new ArrayList<>();
    private LinearLayout serversList;
    private LinearLayout serversSpinner;
    private View[] spinnerDots;
    private TextView serversHint;
    private TextView libraryCrumbs;
    private LinearLayout folderList;
    private TextView heroEyebrow;
    private TextView heroTitle;
    private TextView heroMeta;
    private TextView gridLabel;
    private TextView gridMessage;
    private TextView libraryNotice;
    private RecyclerView grid;
    private TextView detailCrumbs;
    private TextView detailEyebrow;
    private TextView detailTitle;
    private LinearLayout detailFacts;
    private View detailBar;
    private View detailBarTrack;
    private TextView detailProgress;
    private TextView detailResume;
    private TextView detailFromStart;
    private TextView detailBack;
    private TextView detailFile;
    private Dialog exitDialog;
    private Dialog clearDialog;

    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            updateClocks();
            mainHandler.postDelayed(this, CLOCK_TICK_MS);
        }
    };

    private final Runnable spinnerTick = new Runnable() {
        private int phase;

        @Override
        public void run() {
            if (spinnerDots == null) return;
            for (int i = 0; i < spinnerDots.length; i++) {
                spinnerDots[i].setAlpha(i == phase ? 1f : 0.3f);
            }
            phase = (phase + 1) % spinnerDots.length;
            mainHandler.postDelayed(this, 320L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        library = new LibraryPreferences(this);
        settings = new PlayerSettings(this);
        resumeStore = new PlaybackResumeStore(this, settings.resumeRetentionMs());
        discovery = new DlnaDiscovery(this);
        contentRepository = new DlnaContentRepository(discovery);
        thumbnailRepository = new ThumbnailRepository(this, mainHandler);
        adapter = new VideoAdapter(thumbnailRepository, resumeStore, new VideoAdapter.Listener() {
            @Override
            public void onTileFocused(VideoItem entry, View thumbFrame) {
                // Una tarjeta de la lista anterior (que aún se dibuja mientras se reconstruye la
                // columna) no debe cambiar el detalle ni el marco de la carpeta nueva.
                if (screen != Screen.LIBRARY || indexOfVideo(entry.stableKey()) < 0) return;
                TvUi.hide(focusLine);
                TvUi.ring(focusRing, thumbFrame, root, MainActivity.this);
                setHero(entry);
            }

            @Override
            public void onTileSelected(VideoItem entry) {
                openEntry(entry);
            }
        });
        TvUi.configureScale(this);
        buildRoot();
        registerBackCallback();
        enterImmersiveMode();
        mainHandler.postDelayed(clockTick, CLOCK_TICK_MS);
        updateClocks();

        if (isDebuggable()) {
            debugUrl = getIntent().getStringExtra(EXTRA_DEBUG_SERVER_URL);
        }
        appUpdater = new AppUpdater(this, updateExecutor, mainHandler);
        appUpdater.checkForUpdates();
        discoverServers(true);
    }

    // ---------------------------------------------------------------- construcción

    private void buildRoot() {
        root = new FrameLayout(this);
        root.setBackgroundColor(TvUi.PAGE_BG);
        setContentView(root);

        backdropA = new ImageView(this);
        backdropB = new ImageView(this);
        for (ImageView backdrop : new ImageView[]{backdropA, backdropB}) {
            backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
            backdrop.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
            ));
            root.addView(backdrop);
        }
        backdropB.setAlpha(0f);

        // Velo oscuro al 66 % sobre el fondo ambiente (spec 02): el fondo no compite con las filas.
        View veil = new View(this);
        veil.setBackgroundColor(0xA8000000);
        root.addView(veil, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        root.addView(scrim(new int[]{0xDB000000, 0x99000000, 0x1A000000, 0x00000000},
                GradientDrawable.Orientation.LEFT_RIGHT));
        root.addView(scrim(new int[]{0xE6000000, 0x9E000000, 0x00000000},
                GradientDrawable.Orientation.BOTTOM_TOP));

        screenServers = screen();
        screenLibrary = screen();
        screenDetail = screen();
        screenOptions = screen();
        root.addView(screenServers);
        root.addView(screenLibrary);
        root.addView(screenDetail);
        root.addView(screenOptions);

        buildServers();
        buildLibrary();
        buildDetail();
        buildOptions();

        focusLine = new View(this);
        focusLine.setBackgroundColor(Color.WHITE);
        focusLine.setAlpha(0f);
        root.addView(focusLine, new FrameLayout.LayoutParams(
                TvUi.dp(this, 100),
                TvUi.dp(this, 2),
                Gravity.TOP | Gravity.START
        ));
        GradientDrawable ringShape = new GradientDrawable();
        ringShape.setStroke(TvUi.dp(this, 2), Color.WHITE);
        ringShape.setCornerRadius(TvUi.dp(this, 7));
        ringShape.setColor(Color.TRANSPARENT);
        focusRing = new View(this);
        focusRing.setBackground(ringShape);
        focusRing.setAlpha(0f);
        root.addView(focusRing, new FrameLayout.LayoutParams(
                TvUi.dp(this, 100),
                TvUi.dp(this, 100),
                Gravity.TOP | Gravity.START
        ));
        focusLine.bringToFront();
        focusRing.bringToFront();
        // Un solo foco a la vez: el marco de la tarjeta se va al salir de la grilla.
        root.getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus, newFocus) -> {
            boolean inGrid = newFocus != null && grid != null
                    && grid.findContainingViewHolder(newFocus) != null;
            if (inGrid) {
                // En la grilla la línea de la columna no se queda debajo de «‹ Inicio».
                TvUi.hide(focusLine);
            } else {
                TvUi.hide(focusRing);
            }
        });
    }

    private FrameLayout screen() {
        FrameLayout layout = new FrameLayout(this);
        layout.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        layout.setVisibility(View.GONE);
        return layout;
    }

    private View scrim(int[] colors, GradientDrawable.Orientation orientation) {
        View view = new View(this);
        view.setBackground(new GradientDrawable(orientation, colors));
        view.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        return view;
    }

    /** Logo, texto de ruta y reloj en la franja superior (left 64, top 44). */
    private TextView header(FrameLayout parent, String eyebrow) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams rowParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        rowParams.leftMargin = TvUi.dp(this, 64);
        rowParams.rightMargin = TvUi.dp(this, 64);
        rowParams.topMargin = TvUi.dp(this, 44);
        row.setLayoutParams(rowParams);

        View logo = new View(this);
        GradientDrawable mark = new GradientDrawable();
        mark.setCornerRadius(TvUi.dp(this, 4));
        mark.setColor(0xFFE7399C);
        logo.setBackground(mark);
        row.addView(logo, new LinearLayout.LayoutParams(TvUi.dp(this, 16), TvUi.dp(this, 16)));

        TextView crumbs = TvUi.text(this, eyebrow, 12f, 0x99FFFFFF, false);
        crumbs.setLetterSpacing(0.2f);
        crumbs.setAllCaps(true);
        LinearLayout.LayoutParams crumbsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        crumbsParams.leftMargin = TvUi.dp(this, 12);
        row.addView(crumbs, crumbsParams);

        TextView clock = TvUi.text(this, "", 15f, TvUi.INK_2, false);
        LinearLayout.LayoutParams clockParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        clockParams.weight = 1f;
        clockParams.leftMargin = TvUi.dp(this, 12);
        clock.setGravity(Gravity.END);
        row.addView(clock, clockParams);
        clocks.add(clock);

        parent.addView(row);
        return crumbs;
    }

    private void buildServers() {
        header(screenServers, "VibeDLNA");

        TextView eyebrow = TvUi.text(this, "EN ESTA RED", 12f, 0x99FFFFFF, false);
        eyebrow.setLetterSpacing(0.2f);
        eyebrow.setAllCaps(true);
        place(screenServers, eyebrow, 64, 170);

        TextView title = TvUi.text(this, "¿Qué quieres ver?", 44f, TvUi.WHITE, true);
        place(screenServers, title, 64, 196);

        serversList = new LinearLayout(this);
        serversList.setOrientation(LinearLayout.VERTICAL);
        serversList.setClipChildren(false);
        place(screenServers, serversList, 64, 290);

        serversSpinner = new LinearLayout(this);
        serversSpinner.setOrientation(LinearLayout.HORIZONTAL);
        serversSpinner.setGravity(Gravity.CENTER_VERTICAL);
        spinnerDots = new View[3];
        for (int i = 0; i < spinnerDots.length; i++) {
            View dot = new View(this);
            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.OVAL);
            shape.setColor(TvUi.NOW);
            dot.setBackground(shape);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    TvUi.dp(this, 7),
                    TvUi.dp(this, 7)
            );
            params.rightMargin = TvUi.dp(this, 6);
            serversSpinner.addView(dot, params);
            spinnerDots[i] = dot;
        }
        serversHint = TvUi.text(this, "Buscando más servidores en la red", 13f, TvUi.INK_3, false);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        hintParams.leftMargin = TvUi.dp(this, 8);
        serversSpinner.addView(serversHint, hintParams);
        place(screenServers, serversSpinner, 64, 600);
    }

    private void buildLibrary() {
        libraryCrumbs = header(screenLibrary, "");

        TextView label = TvUi.text(this, "CARPETAS", 11f, TvUi.INK_3, false);
        label.setLetterSpacing(0.2f);
        place(screenLibrary, label, 64, 118);

        folderList = new LinearLayout(this);
        folderList.setOrientation(LinearLayout.VERTICAL);
        folderList.setClipChildren(false);
        // Ancho fijo: el texto con foco crece en negrita y no debe cortarse en el borde.
        place(screenLibrary, folderList, 64, 150, 340);

        heroEyebrow = TvUi.text(this, "", 12f, 0x99FFFFFF, false);
        heroEyebrow.setLetterSpacing(0.2f);
        heroEyebrow.setAllCaps(true);
        place(screenLibrary, heroEyebrow, 420, 118);

        heroTitle = TvUi.text(this, "", 34f, TvUi.WHITE, true);
        heroTitle.setSingleLine(false);
        heroTitle.setMaxLines(2);
        heroTitle.setEllipsize(TextUtils.TruncateAt.END);
        place(screenLibrary, heroTitle, 420, 146, 700);

        heroMeta = TvUi.text(this, "", 13f, TvUi.INK_3, false);
        place(screenLibrary, heroMeta, 420, 236);

        gridLabel = TvUi.text(this, "", 11f, TvUi.INK_3, false);
        gridLabel.setLetterSpacing(0.2f);
        place(screenLibrary, gridLabel, 420, 288);

        gridMessage = TvUi.text(this, "", 13f, TvUi.INK_3, false);
        place(screenLibrary, gridMessage, 420, 340);

        libraryNotice = TvUi.text(this, INCOMPLETE_NOTICE, 11f, TvUi.INK_3, false);
        libraryNotice.setVisibility(View.GONE);
        place(screenLibrary, libraryNotice, 420, 262);

        grid = new RecyclerView(this);
        grid.setLayoutManager(new RowGridLayoutManager(this, 3));
        // La grilla recorta en el borde del área de contenido (los títulos de una fila que sale no
        // quedan sobre el encabezado) y deja margen arriba y abajo: la tarjeta con foco, ya
        // crecida y con su marco, queda entera.
        grid.setClipToPadding(true);
        grid.setClipChildren(true);
        grid.setPadding(0, TvUi.dp(this, 16), 0, TvUi.dp(this, 24));
        grid.setItemAnimator(null);
        grid.setAdapter(adapter);
        grid.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                                       @NonNull RecyclerView parent,
                                       @NonNull RecyclerView.State state) {
                int horizontal = TvUi.dp(MainActivity.this, 10);
                int vertical = TvUi.dp(MainActivity.this, 13);
                outRect.set(horizontal, vertical, horizontal, vertical);
            }
        });
        FrameLayout.LayoutParams gridParams = new FrameLayout.LayoutParams(
                TvUi.dp(this, 648),
                TvUi.dp(this, 400),
                Gravity.TOP | Gravity.START
        );
        gridParams.leftMargin = TvUi.dp(this, 410);
        gridParams.topMargin = TvUi.dp(this, 316);
        grid.setLayoutParams(gridParams);
        screenLibrary.addView(grid);
    }

    private void buildDetail() {
        detailCrumbs = header(screenDetail, "");

        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams blockParams = new FrameLayout.LayoutParams(
                TvUi.dp(this, 760),
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.START
        );
        blockParams.leftMargin = TvUi.dp(this, 64);
        blockParams.bottomMargin = TvUi.dp(this, 64);
        block.setLayoutParams(blockParams);
        screenDetail.addView(block);

        detailEyebrow = TvUi.text(this, "", 12f, 0x99FFFFFF, false);
        detailEyebrow.setLetterSpacing(0.2f);
        detailEyebrow.setAllCaps(true);
        block.addView(detailEyebrow);

        detailTitle = TvUi.text(this, "", 46f, TvUi.WHITE, true);
        detailTitle.setSingleLine(false);
        detailTitle.setMaxLines(2);
        detailTitle.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        titleParams.topMargin = TvUi.dp(this, 12);
        block.addView(detailTitle, titleParams);

        detailFacts = new LinearLayout(this);
        detailFacts.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams factsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        factsParams.topMargin = TvUi.dp(this, 26);
        block.addView(detailFacts, factsParams);

        LinearLayout progressRow = new LinearLayout(this);
        progressRow.setOrientation(LinearLayout.HORIZONTAL);
        progressRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        progressParams.topMargin = TvUi.dp(this, 28);
        block.addView(progressRow, progressParams);

        FrameLayout track = new FrameLayout(this);
        detailBarTrack = track;
        track.setBackgroundColor(0x38FFFFFF);
        progressRow.addView(track, new LinearLayout.LayoutParams(TvUi.dp(this, 300), TvUi.dp(this, 4)));
        detailBar = new View(this);
        detailBar.setBackgroundColor(TvUi.NOW);
        track.addView(detailBar, new FrameLayout.LayoutParams(0, TvUi.dp(this, 4)));

        detailProgress = TvUi.text(this, "", 14f, TvUi.INK_2, false);
        LinearLayout.LayoutParams progressTextParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        progressTextParams.leftMargin = TvUi.dp(this, 18);
        progressRow.addView(detailProgress, progressTextParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setClipChildren(false);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        actionsParams.topMargin = TvUi.dp(this, 26);
        block.addView(actions, actionsParams);
        detailResume = actionText(actions, "Reanudar", 34);
        detailFromStart = actionText(actions, "Desde el inicio", 34);
        detailBack = actionText(actions, "Volver a la carpeta", 0);
        TvUi.clickable(detailResume, () -> startPlayer(detailEntry, detailStart()));
        TvUi.clickable(detailFromStart, () -> startPlayer(detailEntry, 0L));
        TvUi.clickable(detailBack, () -> showLibrary(true));

        detailFile = TvUi.text(this, "", 12f, TvUi.INK_3, false);
        detailFile.setTypeface(Typeface.MONOSPACE);
        LinearLayout.LayoutParams fileParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        fileParams.topMargin = TvUi.dp(this, 22);
        block.addView(detailFile, fileParams);
    }

    private TextView actionText(LinearLayout row, String label, int marginDp) {
        TextView view = TvUi.text(this, label, 18f, TvUi.INK_2, false);
        view.setFocusable(true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.rightMargin = TvUi.dp(this, marginDp);
        row.addView(view, params);
        view.setOnFocusChangeListener((v, focused) -> {
            TvUi.setFocusedText(view, focused);
            if (focused) TvUi.underline(focusLine, view, root, this);
        });
        return view;
    }

    private void buildOptions() {
        TextView eyebrow = TvUi.text(this, "OPCIONES", 12f, 0x99FFFFFF, false);
        eyebrow.setLetterSpacing(0.2f);
        eyebrow.setAllCaps(true);
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams tabsParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START
        );
        tabsParams.leftMargin = TvUi.dp(this, 64);
        tabsParams.topMargin = TvUi.dp(this, 44);
        tabs.setLayoutParams(tabsParams);
        tabs.addView(eyebrow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ) {{
            rightMargin = TvUi.dp(MainActivity.this, 30);
        }});
        screenOptions.addView(tabs);

        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        place(screenOptions, rows, 64, 132, 600);

        // Velo fuerte desde la izquierda, como el mockup.
        screenOptions.addView(scrim(new int[]{0xEB000000, 0xC7000000, 0x33000000, 0x00000000},
                GradientDrawable.Orientation.LEFT_RIGHT), 0);

        optionsScreen = new OptionsScreen(this, settings, new OptionsScreen.Host() {
            @Override
            public void onSortChanged() {
                renderGrid();
            }

            @Override
            public void onRetentionChanged() {
                resumeStore = new PlaybackResumeStore(MainActivity.this, settings.resumeRetentionMs());
                adapter.setResume(resumeStore);
            }

            @Override
            public void onClearThumbnails() {
                showClearThumbnailsDialog();
            }

            @Override
            public void onOpenServersRequested() {
                closeOptions();
                startDiscovery(false);
            }

            @Override
            public void onCheckUpdatesRequested() {
                appUpdater.checkForUpdates();
            }
        }, "0.4.2", tabs, rows);
    }

    // ---------------------------------------------------------------- utilidades de vista

    private void place(FrameLayout parent, View view, int leftDp, int topDp) {
        place(parent, view, leftDp, topDp, -1);
    }

    private void place(FrameLayout parent, View view, int leftDp, int topDp, int widthDp) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                widthDp < 0 ? FrameLayout.LayoutParams.WRAP_CONTENT : TvUi.dp(this, widthDp),
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        params.leftMargin = TvUi.dp(this, leftDp);
        params.topMargin = TvUi.dp(this, topDp);
        view.setLayoutParams(params);
        parent.addView(view);
    }

    private void showScreen(Screen next) {
        screen = next;
        if (next != Screen.SERVERS) mainHandler.removeCallbacks(retrySearch);
        screenServers.setVisibility(next == Screen.SERVERS ? View.VISIBLE : View.GONE);
        // Con Opciones abiertas la biblioteca se oculta: solo queda el fondo de miniatura.
        screenLibrary.setVisibility(next == Screen.LIBRARY ? View.VISIBLE : View.GONE);
        screenDetail.setVisibility(next == Screen.DETAIL ? View.VISIBLE : View.GONE);
        screenOptions.setVisibility(next == Screen.OPTIONS ? View.VISIBLE : View.GONE);
        // Opciones es pantalla completa: ni línea ni marco de la biblioteca quedan detrás.
        TvUi.hide(focusLine);
        TvUi.hide(focusRing);
        if (next == Screen.SERVERS) {
            mainHandler.removeCallbacks(spinnerTick);
            if (serversSpinner.getVisibility() == View.VISIBLE) mainHandler.post(spinnerTick);
        } else {
            mainHandler.removeCallbacks(spinnerTick);
        }
    }

    private void updateClocks() {
        String text = new SimpleDateFormat("EEEE d · HH:mm", new Locale("es")).format(new Date());
        for (TextView clock : clocks) clock.setText(text);
    }

    private void setLoadingServers(boolean loading) {
        serversSpinner.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) mainHandler.post(spinnerTick);
        else mainHandler.removeCallbacks(spinnerTick);
    }

    // ---------------------------------------------------------------- servidores

    private void discoverServers(boolean automatic) {
        startDiscovery(automatic);
    }

    /**
     * Busca servidores. Con {@code automatic}, entra directo al último si es el
     * único en línea y la preferencia lo pide; si no, muestra la lista.
     */
    private void startDiscovery(boolean automatic) {
        int generation = ++discoveryGeneration;
        searching = true;
        mainHandler.removeCallbacks(retrySearch);
        showScreen(Screen.SERVERS);
        setLoadingServers(true);
        String url = debugUrl;
        boolean registerUrl = url != null && !debugRegistered;
        dlnaExecutor.submit(() -> {
            if (registerUrl) {
                try {
                    discovery.registerDebugServer(url);
                    debugRegistered = true;
                    Log.i(TAG, "Servidor de depuración registrado: " + url);
                } catch (Exception failure) {
                    Log.w(TAG, "No se pudo registrar el servidor de depuración " + url, failure);
                }
            }
            List<DlnaServer> found = discovery.discover(5_000L);
            Log.i(TAG, "Búsqueda: " + found.size() + " servidor(es) en línea");
            for (DlnaServer server : found) {
                server.setSummary(contentRepository.rootSummary(server));
            }
            mainHandler.post(() -> {
                if (generation != discoveryGeneration || isFinishing()) return;
                searching = false;
                online.clear();
                online.addAll(found);
                long now = System.currentTimeMillis();
                for (DlnaServer server : online) settings.markSeen(server.getUdn(), now);
                DlnaServer saved = findOnline(library.getServerUdn());
                if (automatic && online.size() == 1 && saved != null
                        && settings.openAtLastServer()) {
                    enterServer(saved);
                    return;
                }
                renderServers(online, false);
                // Sin salida: mientras la pantalla siga abierta se vuelve a buscar.
                if (screen == Screen.SERVERS) {
                    mainHandler.postDelayed(retrySearch, SEARCH_RETRY_MS);
                }
            });
        });
    }

    /** Vuelve a buscar servidores cada cierto tiempo mientras la pantalla de Servidores esté abierta. */
    private final Runnable retrySearch = () -> {
        if (screen == Screen.SERVERS && !searching) startDiscovery(false);
    };

    private DlnaServer findOnline(String udn) {
        if (udn == null || udn.isBlank()) return null;
        for (DlnaServer server : online) {
            if (udn.equals(server.getUdn())) return server;
        }
        return null;
    }

    private void renderServers(List<DlnaServer> servers, boolean searching) {
        // Al redibujar se conserva la fila que tenía el foco.
        int focusedIndex = -1;
        for (int i = 0; i < serversList.getChildCount(); i++) {
            if (serversList.getChildAt(i).hasFocus()) focusedIndex = i;
        }
        serversList.removeAllViews();
        for (DlnaServer server : servers) {
            serversList.addView(serverRow(server.getFriendlyName(), server.subtitle(), true,
                    () -> enterServer(server)));
        }
        String savedUdn = library.getServerUdn();
        // Mientras busca no se marca como caído al guardado: aún puede aparecer.
        if (!searching && findOnline(savedUdn) == null && !savedUdn.isBlank()) {
            long seen = settings.lastSeen(savedUdn);
            String since = seen > 0L
                    ? new SimpleDateFormat("HH:mm", new Locale("es")).format(new Date(seen))
                    : "";
            String detail = since.isEmpty()
                    ? "no responde"
                    : "no responde desde las " + since;
            serversList.addView(serverRow(library.getServerName(), detail, false, () -> {
            }));
        }
        if (serversList.getChildCount() > 0) {
            int target = focusedIndex < 0
                    ? 0
                    : Math.min(focusedIndex, serversList.getChildCount() - 1);
            serversList.post(() -> {
                View row = serversList.getChildAt(target);
                if (row != null) row.requestFocus();
            });
        }
        showScreen(Screen.SERVERS);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (!isDebuggable()) return;
        String url = intent.getStringExtra(EXTRA_DEBUG_SERVER_URL);
        if (url == null) return;
        if (!url.equals(debugUrl)) {
            debugUrl = url;
            debugRegistered = false;
        }
        startDiscovery(false);
    }

    private View serverRow(String name, String subtitle, boolean online, Runnable onSelect) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setFocusable(true);
        row.setClipChildren(false);
        // Ancho fijo de columna: el nombre con foco crece sin cortarse ni tapar el punto.
        row.setMinimumWidth(TvUi.dp(this, 620));
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        rowParams.bottomMargin = TvUi.dp(this, 22);
        row.setLayoutParams(rowParams);

        LinearLayout nameLine = new LinearLayout(this);
        nameLine.setOrientation(LinearLayout.HORIZONTAL);
        nameLine.setGravity(Gravity.CENTER_VERTICAL);
        nameLine.setClipChildren(false);
        TextView nameView = TvUi.text(this, name, 26f, online ? TvUi.INK_2 : TvUi.INK_4, false);
        nameLine.addView(nameView);
        if (online) {
            View dot = new View(this);
            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.OVAL);
            shape.setColor(TvUi.LIVE);
            dot.setBackground(shape);
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(
                    TvUi.dp(this, 7),
                    TvUi.dp(this, 7)
            );
            dotParams.leftMargin = TvUi.dp(this, 8);
            nameLine.addView(dot, dotParams);
        }
        row.addView(nameLine);

        TextView sub = TvUi.text(this, subtitle, 13f, online ? TvUi.INK_3 : TvUi.INK_4, false);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        // Línea de foco a 4 dp bajo el nombre (2 dp de grosor) y 4 dp de aire antes de este texto.
        subParams.topMargin = TvUi.dp(this, 10);
        row.addView(sub, subParams);

        TvUi.clickable(row, onSelect);
        row.setOnFocusChangeListener((view, focused) -> {
            boolean lit = focused && online;
            nameView.setTextColor(lit ? TvUi.WHITE : (online ? TvUi.INK_2 : TvUi.INK_4));
            nameView.setTypeface(null, lit ? Typeface.BOLD : Typeface.NORMAL);
            // El nombre y su punto crecen juntos desde la izquierda: el punto nunca pisa el texto.
            nameLine.setPivotX(0f);
            nameLine.animate()
                    .scaleX(lit ? 1.07f : 1f)
                    .scaleY(lit ? 1.07f : 1f)
                    .setDuration(350L)
                    .setInterpolator(TvUi.SPRING)
                    .start();
            // La línea mide el ancho final del nombre (crecido, si tiene foco).
            if (focused) TvUi.underline(focusLine, nameView, root, this, 4, lit ? 1.07f : 1f);
            else if (online) TvUi.hide(focusLine);
        });
        return row;
    }

    // ---------------------------------------------------------------- biblioteca

    private void enterServer(DlnaServer server) {
        if (!server.getUdn().equals(library.getServerUdn())) {
            library.selectServer(server);
        }
        currentServer = server;
        selectedFolder = null;
        levels.reset();
        folderListStack.clear();
        folders = new ArrayList<>();
        videos = new ArrayList<>();
        showScreen(Screen.LIBRARY);
        gridMessage.setText("Cargando carpeta remota…");
        gridMessage.setVisibility(View.VISIBLE);
        libraryNotice.setVisibility(View.GONE);
        clearHero();
        renderFolders();
        renderGrid();
        browseAsync(server, BrowseStack.ROOT_ID, result -> {
            if (result == null) {
                showLibraryError(null);
                return;
            }
            folders = containersSorted(result.entries);
            libraryNotice.setVisibility(result.incomplete ? View.VISIBLE : View.GONE);
            renderFolders();
            String savedId = library.getContainerId();
            if (savedId != null && !savedId.isBlank() && !BrowseStack.ROOT_ID.equals(savedId)) {
                VideoItem saved = VideoItem.container(
                        server.getUdn(),
                        savedId,
                        library.getParentId(),
                        library.getContainerName()
                );
                // Si la carpeta guardada ya no existe (el PC comparte otra carpeta, o se borró),
                // se olvida y se entra de nuevo desde la raíz, en vez de mostrar el error.
                Runnable forget = () -> {
                    if (server != currentServer) return;
                    Log.i(TAG, "La carpeta guardada " + savedId + " ya no está en el servidor: se vuelve a la raíz");
                    library.forgetContainer();
                    enterServer(server);
                };
                restoreChain(server, savedChain(), 0, saved, forget);
            } else if (!folders.isEmpty()) {
                // Al entrar se abre la primera carpeta; si solo tiene subcarpetas, la primera de ellas.
                selectFolder(folders.get(0), true, true);
            } else {
                selectedFolder = null;
                videos = new ArrayList<>();
                renderGrid();
            }
        });
    }

    /**
     * Niveles por donde se llega a la carpeta guardada, sin la raíz. Una versión anterior guardaba
     * solo el nivel padre: en ese caso ese es el único nivel.
     */
    private List<BrowseStack.Level> savedChain() {
        List<BrowseStack.Level> chain = new ArrayList<>();
        for (BrowseStack.Level level : BrowseStack.decode(library.getContainerPath())) {
            if (!BrowseStack.ROOT_ID.equals(level.id)) chain.add(level);
        }
        if (chain.isEmpty()) {
            String parentId = library.getParentId();
            if (parentId != null && !parentId.isBlank() && !BrowseStack.ROOT_ID.equals(parentId)) {
                chain.add(new BrowseStack.Level(parentId, library.getParentName()));
            }
        }
        return chain;
    }

    /**
     * Restaura la columna nivel por nivel, desde la raíz, hasta dejar abierta la carpeta guardada.
     * Así cada «‹» lleva al padre real. Si un nivel ya no existe, se olvida y se vuelve a la raíz.
     */
    private void restoreChain(DlnaServer server, List<BrowseStack.Level> chain, int index,
                              VideoItem target, Runnable onMissing) {
        if (server != currentServer) return;
        if (index >= chain.size()) {
            selectFolder(target, false, false, onMissing, null);
            return;
        }
        BrowseStack.Level level = chain.get(index);
        VideoItem entry = VideoItem.container(server.getUdn(), level.id, "", level.name);
        selectFolder(entry, true, false, onMissing, () -> {
            // Sin subcarpetas no hay nivel que abrir: la ruta guardada ya no coincide.
            if (!level.id.equals(levels.top().id)) {
                onMissing.run();
                return;
            }
            restoreChain(server, chain, index + 1, target, onMissing);
        });
    }

    /** @param folderName carpeta que no se pudo leer; el detalle queda solo con su nombre. */
    private void showLibraryError(String folderName) {
        folders = new ArrayList<>();
        videos = new ArrayList<>();
        libraryNotice.setVisibility(View.GONE);
        renderFolders();
        submitGrid(videos);
        gridMessage.setText("No se pudo leer la carpeta del servidor DLNA.");
        gridMessage.setVisibility(View.VISIBLE);
        renderHeader();
        clearHero();
        if (folderName != null) heroTitle.setText(folderName);
    }

    /** Carga una carpeta desde la columna: sus videos van a la grilla y, si tiene subcarpetas, a la columna. */
    private void selectFolder(VideoItem folder, boolean descend) {
        selectFolder(folder, descend, false);
    }

    /**
     * @param autoOpen al entrar al servidor: si la carpeta no tiene videos propios,
     *                 abre su primera subcarpeta para que la grilla no quede vacía.
     */
    private void selectFolder(VideoItem folder, boolean descend, boolean autoOpen) {
        selectFolder(folder, descend, autoOpen, null, null);
    }

    /**
     * @param descend  si tiene subcarpetas, la columna pasa a mostrarlas; la carpeta abierta queda
     *                 arriba de ellas, como nivel actual.
     * @param autoOpen al entrar: el foco va a la primera tarjeta. Si no, el foco queda en la
     *                 carpeta que se abrió.
     * @param onMissing si no es null, se llama en vez de mostrar el error cuando la carpeta no se puede leer.
     * @param onLoaded  si no es null, se llama al terminar en vez de mover el foco.
     */
    private void selectFolder(VideoItem folder, boolean descend, boolean autoOpen,
                              Runnable onMissing, Runnable onLoaded) {
        DlnaServer server = currentServer;
        if (server == null) return;
        // El detalle describe el video de la carpeta anterior: se quita al cambiar de carpeta.
        clearHero();
        browseAsync(server, folder.getId(), result -> {
            if (server != currentServer) return;
            if (result == null) {
                if (onMissing != null) onMissing.run();
                else showLibraryError(folder.getName());
                return;
            }
            selectedFolder = folder;
            // El nivel donde estaba la columna es el padre de la carpeta elegida (respaldo para
            // versiones anteriores); la ruta completa se guarda después de abrir sus subcarpetas.
            String parentId = levels.top().id;
            String parentName = levels.top().name;
            List<VideoItem> children = containersSorted(result.entries);
            if (descend && !children.isEmpty()) {
                folderListStack.add(new ArrayList<>(folders));
                levels.push(folder.getId(), folder.getName());
                folders = children;
            }
            library.selectContainer(folder.getId(), folder.getName(), parentId, parentName,
                    BrowseStack.encode(levels.path()));
            // Primero los videos: la columna cuenta la carpeta abierta con ellos.
            videos = arrange(result.entries);
            libraryNotice.setVisibility(result.incomplete ? View.VISIBLE : View.GONE);
            renderFolders();
            renderGrid();
            // Al llegar el resultado el detalle muestra el primer video, o el nombre de la carpeta.
            if (videos.isEmpty()) setFolderHero(folder, children.size());
            else setHero(videos.get(0));
            if (onLoaded != null) {
                onLoaded.run();
                return;
            }
            if (autoOpen && videos.isEmpty() && !children.isEmpty()) {
                selectFolder(children.get(0), true, true);
                return;
            }
            if (autoOpen) focusLibraryEntry();
            else focusFolderRow(folder.getId());
        });
    }

    /** Foco inicial de la biblioteca: la primera tarjeta, o la carpeta si no hay videos. */
    private void focusLibraryEntry() {
        grid.post(() -> {
            RecyclerView.ViewHolder holder = grid.findViewHolderForAdapterPosition(0);
            if (holder != null) holder.itemView.requestFocus();
            else focusFolderRow(selectedFolder == null ? null : selectedFolder.getId());
        });
    }

    private void popLevel() {
        BrowseStack.Level left = levels.pop();
        if (left == null) return;
        if (!folderListStack.isEmpty()) {
            folders = folderListStack.remove(folderListStack.size() - 1);
        }
        renderFolders();
        focusFolderRow(left.id);
    }

    private void browseAsync(DlnaServer server, String containerId, BrowseCallback callback) {
        int generation = ++browseGeneration;
        dlnaExecutor.submit(() -> {
            DlnaContentRepository.BrowseResult loaded;
            try {
                loaded = contentRepository.browse(server, containerId);
            } catch (Exception error) {
                Log.w(TAG, "No se pudo leer la carpeta DLNA " + containerId, error);
                loaded = null;
            }
            DlnaContentRepository.BrowseResult result = loaded;
            mainHandler.post(() -> {
                if (generation != browseGeneration || isFinishing()) return;
                callback.onBrowsed(result);
            });
        });
    }

    private interface BrowseCallback {
        void onBrowsed(DlnaContentRepository.BrowseResult result);
    }

    private List<VideoItem> containersSorted(List<VideoItem> entries) {
        List<VideoItem> list = new ArrayList<>();
        for (VideoItem entry : entries) {
            if (entry.isContainer()) list.add(entry);
        }
        Collections.sort(list, (left, right) -> left.getName().toLowerCase(Locale.ROOT)
                .compareTo(right.getName().toLowerCase(Locale.ROOT)));
        return list;
    }

    /** Videos de la carpeta: sin los que la TV no puede abrir (si la opción lo pide) y en el orden elegido. */
    private List<VideoItem> arrange(List<VideoItem> entries) {
        List<VideoItem> list = new ArrayList<>();
        java.util.Set<String> decoders = null;
        if (!settings.showUnplayable()) decoders = supportedMimes();
        for (VideoItem entry : entries) {
            if (entry.isContainer()) continue;
            if (decoders != null && !DlnaMediaFormat.unsupportedCodecs(
                    entry.getMedia().codecs, decoders).isEmpty()) {
                continue;
            }
            list.add(entry);
        }
        final int order = settings.sortOrder();
        Collections.sort(list, (left, right) -> {
            switch (order) {
                case PlayerSettings.SORT_RECENT: {
                    int compare = right.getMedia().date.compareTo(left.getMedia().date);
                    return compare != 0 ? compare : left.getName().compareToIgnoreCase(right.getName());
                }
                case PlayerSettings.SORT_SIZE:
                    return Long.compare(right.getMedia().sizeBytes, left.getMedia().sizeBytes);
                default:
                    return left.getName().compareToIgnoreCase(right.getName());
            }
        });
        return list;
    }

    private java.util.Set<String> supportedMimes() {
        if (!supportedMimesLoaded) {
            supportedMimes = DlnaMediaFormat.supportedDecoderMimes();
            supportedMimesLoaded = true;
        }
        return supportedMimes;
    }

    private void renderFolders() {
        folderList.removeAllViews();
        if (!levels.isRoot() && levels.parent() != null) {
            folderList.addView(folderRow(UP_ROW_ID, "‹ " + levels.parent().name, null, false, () -> popLevel()));
            // Nivel abierto: va arriba de sus subcarpetas. Con el punto verde, si es la carpeta
            // que muestra la grilla; y con el foco al abrirla desde la columna.
            BrowseStack.Level top = levels.top();
            boolean openTop = selectedFolder != null && top.id.equals(selectedFolder.getId());
            folderList.addView(folderRow(top.id, top.name,
                    openTop ? String.valueOf(videos.size()) : null, openTop,
                    () -> selectFolder(VideoItem.container(currentServer.getUdn(), top.id, "", top.name),
                            false)));
        }
        for (VideoItem folder : folders) {
            boolean open = selectedFolder != null && folder.getId().equals(selectedFolder.getId());
            folderList.addView(folderRow(folder.getId(), folder.getName(),
                    open ? String.valueOf(videos.size()) : null, open,
                    () -> selectFolder(folder, true)));
        }
        if (currentServer != null) {
            TextView change = TvUi.text(this, "Cambiar de servidor", 14f, TvUi.INK_2, false);
            change.setFocusable(true);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            params.topMargin = TvUi.dp(this, 14);
            folderList.addView(change, params);
            TvUi.clickable(change, () -> startDiscovery(false));
            change.setOnFocusChangeListener((view, focused) -> {
                TvUi.setFocusedText(change, focused);
                if (focused) TvUi.underline(focusLine, change, root, this);
            });
        }
    }

    private View folderRow(String id, String name, String count, boolean open, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setClipChildren(false);
        row.setTag(id);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        rowParams.bottomMargin = TvUi.dp(this, id.equals(UP_ROW_ID) ? 16 : 16);
        row.setLayoutParams(rowParams);

        TextView label = TvUi.text(this, name, id.equals(UP_ROW_ID) ? 14f : 17f,
                open ? TvUi.WHITE : TvUi.INK_2, open);
        row.addView(label);
        if (open) {
            View dot = new View(this);
            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.OVAL);
            shape.setColor(TvUi.LIVE);
            dot.setBackground(shape);
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(
                    TvUi.dp(this, 7),
                    TvUi.dp(this, 7)
            );
            dotParams.leftMargin = TvUi.dp(this, 10);
            row.addView(dot, dotParams);
        }
        if (count != null) {
            TextView countView = TvUi.text(this, count, 13f, TvUi.INK_3, false);
            LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            countParams.leftMargin = TvUi.dp(this, 10);
            row.addView(countView, countParams);
        }
        TvUi.clickable(row, action);
        row.setOnFocusChangeListener((view, focused) -> {
            TvUi.setFocusedText(label, focused);
            // Sin foco el texto va al 62 %; la carpeta abierta se marca con el punto verde.
            label.setTextColor(focused ? TvUi.WHITE : TvUi.INK_2);
            if (focused) TvUi.underline(focusLine, label, root, this);
            else TvUi.hide(focusLine);
        });
        return row;
    }

    private void focusFolderRow(String id) {
        folderList.post(() -> {
            View target = null;
            for (int i = 0; i < folderList.getChildCount(); i++) {
                View child = folderList.getChildAt(i);
                if (id != null && id.equals(child.getTag())) target = child;
            }
            if (target == null && folderList.getChildCount() > 0) {
                target = folderList.getChildAt(folderList.getChildCount() > 1 ? 1 : 0);
            }
            if (target != null) target.requestFocus();
        });
    }

    private void renderHeader() {
        String server = currentServer == null ? "" : currentServer.getFriendlyName();
        String folder = selectedFolder == null ? "" : selectedFolder.getName();
        libraryCrumbs.setText(DlnaMediaFormat.join(
                java.util.Arrays.asList(server, folder), " · ").toUpperCase(Locale.ROOT));
    }

    private void renderGrid() {
        renderHeader();
        String folder = selectedFolder == null ? "" : selectedFolder.getName();
        String order = OptionsScreen.sortLabel(settings.sortOrder()).toUpperCase(Locale.ROOT);
        int count = videos.size();
        // Sin nombre de carpeta, ese tramo se omite (no queda un «·» suelto al inicio).
        gridLabel.setText(DlnaMediaFormat.join(java.util.Arrays.asList(
                folder.trim().toUpperCase(Locale.ROOT),
                count + (count == 1 ? " VIDEO" : " VIDEOS"),
                order), " · "));
        submitGrid(videos);
        if (videos.isEmpty()) {
            if (selectedFolder != null) {
                gridMessage.setText("Esta carpeta no contiene videos.");
                gridMessage.setVisibility(View.VISIBLE);
            }
        } else {
            gridMessage.setVisibility(View.GONE);
        }
        grid.setVisibility(View.VISIBLE);
    }

    /**
     * Cambia el contenido de la grilla. La tarjeta enfocada deja de existir o cambia de sitio:
     * su marco se esconde aquí, para que no quede suelto sobre el encabezado.
     */
    private void submitGrid(List<VideoItem> list) {
        TvUi.hide(focusRing);
        adapter.submit(list);
    }

    /** El detalle se limpia al cambiar de carpeta: no muestra el video de la carpeta anterior. */
    private void clearHero() {
        heroEyebrow.setText("");
        heroTitle.setText("");
        heroMeta.setText("");
    }

    /** Carpeta sin videos: el bloque muestra su nombre y cuántas subcarpetas tiene. */
    private void setFolderHero(VideoItem folder, int subfolders) {
        heroEyebrow.setText("");
        heroTitle.setText(folder.getName());
        heroMeta.setText(subfolders <= 0
                ? ""
                : subfolders == 1 ? "1 subcarpeta" : subfolders + " subcarpetas");
    }

    /** Lleva el foco a la tarjeta de un video, con la grilla desplazada para que se vea entera. */
    private void focusGridEntry(String key) {
        int index = indexOfVideo(key);
        if (index < 0) return;
        grid.scrollToPosition(index);
        focusGridPosition(index, 10);
    }

    /** La grilla se dibuja después de desplazarse: se reintenta unas veces hasta encontrar la tarjeta. */
    private void focusGridPosition(int index, int attemptsLeft) {
        grid.post(() -> {
            RecyclerView.ViewHolder holder = grid.findViewHolderForAdapterPosition(index);
            if (holder != null) {
                View tile = holder.itemView;
                tile.requestFocus();
                // El foco puede llegar antes del layout: cuando la tarjeta ya tiene posición, se
                // repite su camino de foco (marco y negrita), igual que un foco normal.
                tile.post(() -> VideoAdapter.replayFocus(tile));
            } else if (attemptsLeft > 1) {
                grid.postDelayed(() -> focusGridPosition(index, attemptsLeft - 1), 30L);
            }
        });
    }

    private int indexOfVideo(String key) {
        if (key == null || key.isEmpty()) return -1;
        for (int i = 0; i < videos.size(); i++) {
            if (key.equals(videos.get(i).stableKey())) return i;
        }
        return -1;
    }

    /** Video que sigue al dado en la carpeta, o null si era el último. */
    private VideoItem videoAfter(String key) {
        int index = indexOfVideo(key);
        return index >= 0 && index + 1 < videos.size() ? videos.get(index + 1) : null;
    }

    private void showLibrary(boolean focusGrid) {
        showScreen(Screen.LIBRARY);
        renderHeader();
        if (focusGrid && !videos.isEmpty()) {
            grid.post(() -> {
                RecyclerView.ViewHolder holder = grid.findViewHolderForAdapterPosition(0);
                if (holder != null) holder.itemView.requestFocus();
            });
        } else {
            focusFolderRow(selectedFolder == null ? null : selectedFolder.getId());
        }
    }

    private void setHero(VideoItem entry) {
        MediaInfo media = entry.getMedia();
        PlaybackResumeStore.ResumePoint point = resumeStore.get(entry.stableKey(), System.currentTimeMillis());
        if (point != null) {
            heroEyebrow.setText("• " + DlnaMediaFormat.progressLabel(point.positionMs, point.durationMs));
            heroEyebrow.setTextColor(TvUi.NOW);
        } else {
            String year = DlnaMediaFormat.year(media.date);
            String duration = entry.getDurationMs() > 0L ? DlnaMediaFormat.duration(entry.getDurationMs()) : "";
            heroEyebrow.setText(DlnaMediaFormat.join(java.util.Arrays.asList(year, duration), " · "));
            heroEyebrow.setTextColor(0x99FFFFFF);
        }
        heroTitle.setText(entry.getName());
        heroMeta.setText(DlnaMediaFormat.mediaLine(media));
        showBackdrop(entry);
    }

    /**
     * Fondo ambiente: reduce la miniatura 16 veces y la vuelve a ampliar. Así se pierde
     * el detalle y queda como luz de fondo, no como imagen nítida a pantalla completa.
     */
    private static Bitmap ambient(Bitmap source) {
        if (source == null || source.getWidth() < 2 || source.getHeight() < 2) return source;
        int smallWidth = Math.max(1, source.getWidth() / 16);
        int smallHeight = Math.max(1, source.getHeight() / 16);
        Bitmap small = Bitmap.createScaledBitmap(source, smallWidth, smallHeight, true);
        return Bitmap.createScaledBitmap(small, source.getWidth(), source.getHeight(), true);
    }

    /** Fondo: la miniatura del video marcado con cruce de 500 ms, o un degradado si no hay. */
    private void showBackdrop(VideoItem entry) {
        String token = entry.stableKey() + ":" + backdropFlip;
        backdropToken = token;
        thumbnailRepository.load(entry, (bitmap, ignoredSource) -> {
            if (!token.equals(backdropToken)) return;
            // Cruce: la capa oculta recibe la imagen y la visible se desvanece.
            ImageView incoming = backdropFlip % 2 == 0 ? backdropA : backdropB;
            ImageView outgoing = incoming == backdropA ? backdropB : backdropA;
            incoming.setImageBitmap(ambient(bitmap));
            incoming.setBackground(bitmap == null ? TvUi.gradientFor(entry.getName()) : null);
            incoming.animate().alpha(1f).setDuration(500L).start();
            outgoing.animate().alpha(0f).setDuration(500L).start();
            backdropFlip++;
        });
    }

    private void openEntry(VideoItem entry) {
        PlaybackResumeStore.ResumePoint point = resumeStore.get(entry.stableKey(), System.currentTimeMillis());
        if (point != null) {
            openDetail(entry, point);
        } else {
            startPlayer(entry, 0L);
        }
    }

    // ---------------------------------------------------------------- detalle

    private void openDetail(VideoItem entry, PlaybackResumeStore.ResumePoint point) {
        detailEntry = entry;
        detailPoint = point;
        returnScreen = Screen.LIBRARY;
        MediaInfo media = entry.getMedia();
        String folder = selectedFolder == null ? "" : selectedFolder.getName();
        String server = currentServer == null ? "" : currentServer.getFriendlyName();
        detailCrumbs.setText(DlnaMediaFormat.join(java.util.Arrays.asList(server, folder), " · ")
                .toUpperCase(Locale.ROOT));
        String year = DlnaMediaFormat.year(media.date);
        String duration = DlnaMediaFormat.duration(entry.getDurationMs());
        detailEyebrow.setText(DlnaMediaFormat.join(java.util.Arrays.asList(year, duration), " · "));
        detailTitle.setText(entry.getName());

        detailFacts.removeAllViews();
        addFact(detailFacts, "Imagen", DlnaMediaFormat.qualityLabel(media.height()), 0);
        addFact(detailFacts, "Formato", media.codecs, 34);
        String file = DlnaMediaFormat.join(java.util.Arrays.asList(
                DlnaMediaFormat.size(media.sizeBytes),
                DlnaMediaFormat.mbps(media.bitrateBytesPerSecond)), " · ");
        addFact(detailFacts, "Archivo", file, 34);

        int percent = point == null ? 0 : DlnaMediaFormat.percent(point.positionMs, point.durationMs);
        LinearLayout.LayoutParams barParams = (LinearLayout.LayoutParams) detailBarTrack.getLayoutParams();
        barParams.width = TvUi.dp(this, 300);
        detailBar.getLayoutParams().width = TvUi.dp(this, 300) * percent / 100;
        detailBar.requestLayout();
        if (point != null) {
            detailProgress.setText(DlnaMediaFormat.duration(point.positionMs) + " de "
                    + DlnaMediaFormat.duration(point.durationMs) + " · quedan "
                    + DlnaMediaFormat.remaining(point.durationMs - point.positionMs)
                    + " · se recuerda " + retentionPhrase());
            detailResume.setText("Reanudar desde " + DlnaMediaFormat.duration(point.positionMs));
        } else {
            detailProgress.setText("Sin empezar");
            detailResume.setText("Reproducir");
        }
        detailFile.setText(DlnaMediaFormat.join(java.util.Arrays.asList(server, folder, entry.getName()),
                " / "));
        showScreen(Screen.DETAIL);
        showBackdrop(entry);
        detailResume.post(() -> detailResume.requestFocus());
    }

    private long detailStart() {
        return detailPoint == null ? 0L : detailPoint.positionMs;
    }

    private void addFact(LinearLayout row, String label, String value, int leftDp) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.rightMargin = TvUi.dp(this, leftDp);
        column.setLayoutParams(params);
        TextView name = TvUi.text(this, label.toUpperCase(Locale.ROOT), 11f, TvUi.INK_3, false);
        name.setLetterSpacing(0.16f);
        column.addView(name);
        TextView content = TvUi.text(this, value == null || value.isEmpty() ? "—" : value,
                15f, TvUi.WHITE, false);
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        contentParams.topMargin = TvUi.dp(this, 4);
        column.addView(content, contentParams);
        row.addView(column);
    }

    private String retentionPhrase() {
        switch (settings.resumeRetention()) {
            case PlayerSettings.RETENTION_DAY:
                return "por 1 día";
            case PlayerSettings.RETENTION_FOREVER:
                return "siempre";
            default:
                return "por 1 hora";
        }
    }

    // ---------------------------------------------------------------- reproductor

    private void startPlayer(VideoItem entry, long startMs) {
        if (entry == null || entry.getUri() == null) return;
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra(PlayerActivity.EXTRA_URI, entry.getUri().toString());
        intent.putExtra(PlayerActivity.EXTRA_TITLE, entry.getName());
        intent.putExtra(PlayerActivity.EXTRA_DURATION, entry.getDurationMs());
        intent.putExtra(PlayerActivity.EXTRA_RESUME_KEY, entry.stableKey());
        intent.putExtra(PlayerActivity.EXTRA_START_MS, startMs);
        intent.putExtra(PlayerActivity.EXTRA_RETENTION_MS, settings.resumeRetentionMs());
        intent.putExtra(PlayerActivity.EXTRA_SERVER, currentServer == null ? "" : currentServer.getFriendlyName());
        intent.putExtra(PlayerActivity.EXTRA_FOLDER, selectedFolder == null ? "" : selectedFolder.getName());
        Uri uri = entry.getUri();
        intent.putExtra(PlayerActivity.EXTRA_HOST, uri.getHost() == null ? "" : uri.getHost());
        int index = videos.indexOf(entry);
        if (index >= 0 && index + 1 < videos.size()) {
            VideoItem next = videos.get(index + 1);
            intent.putExtra(PlayerActivity.EXTRA_NEXT_TITLE, next.getName());
            intent.putExtra(PlayerActivity.EXTRA_NEXT_DURATION, next.getDurationMs());
        }
        playingKey = entry.stableKey();
        thumbnailRepository.pause();
        startActivityForResult(intent, REQUEST_PLAYER);
    }

    /**
     * Vuelve del reproductor. Si el video terminó y hay otro en la carpeta, sigue con él; si no,
     * la biblioteca queda con el foco en el video que se reprodujo.
     */
    private void handlePlayerResult(int resultCode, Intent data) {
        String key = data == null ? "" : data.getStringExtra(PlayerActivity.EXTRA_RESULT_KEY);
        if (key == null || key.isEmpty()) key = playingKey;
        boolean ended = resultCode == RESULT_OK
                && data != null
                && data.getBooleanExtra(PlayerActivity.EXTRA_RESULT_ENDED, false);
        if (ended) {
            VideoItem next = videoAfter(key);
            if (next != null) {
                startPlayer(next, -1L);
                return;
            }
            showScreen(Screen.LIBRARY);
            renderHeader();
        }
        pendingFocusKey = key;
    }

    /**
     * Grilla que desplaza por filas completas: la fila de la tarjeta con foco queda alineada al
     * borde superior del área de la grilla (el padding de arriba), sin restos de la fila anterior.
     * Si ya está alineada, no se mueve (así el desplazamiento horizontal no salta).
     */
    private static final class RowGridLayoutManager extends GridLayoutManager {
        RowGridLayoutManager(android.content.Context context, int spanCount) {
            super(context, spanCount);
        }

        @Override
        public boolean requestChildRectangleOnScreen(@NonNull RecyclerView parent, @NonNull View child,
                                                     @NonNull Rect rect, boolean immediate,
                                                     boolean focusedChildVisible) {
            if (parent.isComputingLayout()) return false;
            int visibleTop = parent.getPaddingTop();
            int top = getDecoratedTop(child);
            if (top == visibleTop) return false;
            parent.scrollBy(0, top - visibleTop);
            return true;
        }
    }

    // ---------------------------------------------------------------- opciones

    private void showOptions() {
        returnScreen = screen;
        showScreen(Screen.OPTIONS);
        View first = optionsScreen.show(0);
        first.post(first::requestFocus);
        adapter.refresh();
        updateThumbnailStats();
    }

    private void closeOptions() {
        if (screen != Screen.OPTIONS) return;
        Screen back = returnScreen == Screen.OPTIONS ? Screen.LIBRARY : returnScreen;
        if (back == Screen.SERVERS) {
            showScreen(Screen.SERVERS);
        } else if (back == Screen.DETAIL) {
            showScreen(Screen.DETAIL);
        } else {
            showLibrary(false);
        }
        enterImmersiveMode();
    }

    private void updateThumbnailStats() {
        thumbnailCacheExecutor.submit(() -> {
            long[] stats = thumbnailRepository.cacheStats();
            mainHandler.post(() -> {
                if (isFinishing()) return;
                optionsScreen.setThumbnailStats(stats[0], stats[1]);
            });
        });
    }

    private void showClearThumbnailsDialog() {
        Dialog dialog = new Dialog(this);
        clearDialog = dialog;
        dialog.setContentView(R.layout.dialog_clear_thumbnails);
        dialog.setCanceledOnTouchOutside(false);
        Button cancel = dialog.findViewById(R.id.cancel_clear_thumbnails);
        Button clear = dialog.findViewById(R.id.confirm_clear_thumbnails);
        cancel.setOnClickListener(view -> dialog.dismiss());
        clear.setOnClickListener(view -> {
            dialog.dismiss();
            thumbnailCacheExecutor.submit(() -> {
                thumbnailRepository.clearAll();
                mainHandler.post(() -> {
                    if (isFinishing()) return;
                    adapter.refresh();
                    updateThumbnailStats();
                });
            });
        });
        dialog.setOnShowListener(ignored -> clear.requestFocus());
        dialog.setOnDismissListener(ignored -> {
            if (clearDialog == dialog) clearDialog = null;
            if (!isFinishing()) enterImmersiveMode();
        });
        dialog.show();
        styleDialog(dialog, 0.68f);
    }

    private void styleDialog(Dialog dialog, float dim) {
        Window window = dialog.getWindow();
        if (window == null) return;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.width = WindowManager.LayoutParams.WRAP_CONTENT;
        attributes.height = WindowManager.LayoutParams.WRAP_CONTENT;
        attributes.dimAmount = dim;
        window.setAttributes(attributes);
    }

    // ---------------------------------------------------------------- teclas y atrás

    @Override
    @SuppressLint("GestureBackNavigation")
    public boolean dispatchKeyEvent(KeyEvent event) {
        // ◀ desde la primera columna de la grilla vuelve a la carpeta abierta (punto verde).
        if (event.getAction() == KeyEvent.ACTION_DOWN
                && event.getKeyCode() == KeyEvent.KEYCODE_DPAD_LEFT
                && screen == Screen.LIBRARY && selectedFolder != null) {
            View focused = getCurrentFocus();
            RecyclerView.ViewHolder holder = focused == null
                    ? null
                    : grid.findContainingViewHolder(focused);
            if (holder != null && holder.getBindingAdapterPosition() % 3 == 0) {
                focusFolderRow(selectedFolder.getId());
                return true;
            }
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            int keyCode = event.getKeyCode();
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                handleBackAction();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_SETTINGS) {
                if (screen == Screen.OPTIONS) closeOptions();
                else showOptions();
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
        if (exitDialog != null && exitDialog.isShowing()) return;
        if (clearDialog != null && clearDialog.isShowing()) return;
        switch (screen) {
            case OPTIONS:
                closeOptions();
                break;
            case DETAIL:
                showLibrary(false);
                break;
            case SERVERS:
                if (currentServer != null && !library.getServerUdn().isBlank()) {
                    showScreen(Screen.LIBRARY);
                    renderGrid();
                } else {
                    showExitDialog();
                }
                break;
            default:
                if (!levels.isRoot()) popLevel();
                else showExitDialog();
                break;
        }
    }

    private void showExitDialog() {
        if (exitDialog != null && exitDialog.isShowing()) return;
        Dialog dialog = new Dialog(this);
        exitDialog = dialog;
        dialog.setContentView(R.layout.dialog_exit);
        dialog.setCanceledOnTouchOutside(false);
        Button stayButton = dialog.findViewById(R.id.stay_button);
        Button exitButton = dialog.findViewById(R.id.exit_button);
        stayButton.setOnClickListener(view -> dialog.dismiss());
        exitButton.setOnClickListener(view -> {
            dialog.dismiss();
            finishAndRemoveTask();
        });
        dialog.setOnShowListener(ignored -> exitButton.requestFocus());
        dialog.setOnDismissListener(ignored -> {
            if (exitDialog == dialog) exitDialog = null;
            if (!isFinishing()) enterImmersiveMode();
        });
        dialog.show();
        styleDialog(dialog, 0.68f);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PLAYER) {
            handlePlayerResult(resultCode, data);
            return;
        }
        if (appUpdater != null) appUpdater.onActivityResult(requestCode);
    }

    @Override
    protected void onResume() {
        super.onResume();
        thumbnailRepository.resume();
        resumeStore = new PlaybackResumeStore(this, settings.resumeRetentionMs());
        adapter.setResume(resumeStore);
        // Recargar la grilla quita el foco de sus tarjetas: el marco se esconde y, si hay una
        // tarjeta pendiente (al volver del reproductor), el foco vuelve a ella.
        TvUi.hide(focusRing);
        adapter.refresh();
        String key = pendingFocusKey;
        pendingFocusKey = null;
        if (key != null && screen == Screen.LIBRARY) focusGridEntry(key);
        if (appUpdater != null) appUpdater.onHostResume();
        enterImmersiveMode();
    }

    @Override
    protected void onPause() {
        thumbnailRepository.pause();
        if (appUpdater != null) appUpdater.onHostPause();
        super.onPause();
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

    private boolean isDebuggable() {
        return (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    @Override
    protected void onDestroy() {
        discoveryGeneration++;
        browseGeneration++;
        mainHandler.removeCallbacksAndMessages(null);
        if (exitDialog != null) exitDialog.dismiss();
        if (clearDialog != null) clearDialog.dismiss();
        if (appUpdater != null) appUpdater.destroy();
        thumbnailRepository.destroy();
        discovery.close();
        dlnaExecutor.shutdownNow();
        updateExecutor.shutdownNow();
        thumbnailCacheExecutor.shutdownNow();
        super.onDestroy();
    }
}
