package cl.streambox.tv;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.window.OnBackInvokedDispatcher;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int FOLDER_REQUEST = 1001;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService libraryExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService updateExecutor = Executors.newSingleThreadExecutor();
    private final List<VideoItem> videos = new ArrayList<>();

    private RecyclerView videoGrid;
    private View emptyState;
    private TextView emptyTitle;
    private TextView emptyDescription;
    private ProgressBar libraryProgress;
    private Button chooseFolderButton;
    private TextView folderLabel;
    private TextView videoCount;
    private TextView clock;
    private View optionsScrim;
    private View optionsPanel;
    private Button folderOption;
    private Button rescanOption;
    private Button sortOption;
    private TextView folderValue;
    private TextView sortValue;

    private LibraryPreferences preferences;
    private VideoLibraryRepository libraryRepository;
    private ThumbnailRepository thumbnailRepository;
    private VideoAdapter adapter;
    private AppUpdater appUpdater;
    private Dialog exitDialog;
    private int scanGeneration;

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
        setContentView(R.layout.activity_main);
        preferences = new LibraryPreferences(this);
        libraryRepository = new VideoLibraryRepository(this);
        thumbnailRepository = new ThumbnailRepository(this, mainHandler);
        bindViews();
        configureGrid();
        configureActions();
        registerBackCallback();
        enterImmersiveMode();

        appUpdater = new AppUpdater(this, updateExecutor, mainHandler);
        appUpdater.checkForUpdates();
        updateClock.run();

        Uri folderUri = preferences.getFolderUri();
        if (folderUri == null) {
            showFolderPrompt();
        } else {
            scanLibrary(folderUri);
        }
    }

    private void bindViews() {
        videoGrid = findViewById(R.id.video_grid);
        emptyState = findViewById(R.id.empty_state);
        emptyTitle = findViewById(R.id.empty_title);
        emptyDescription = findViewById(R.id.empty_description);
        libraryProgress = findViewById(R.id.library_progress);
        chooseFolderButton = findViewById(R.id.choose_folder_button);
        folderLabel = findViewById(R.id.folder_label);
        videoCount = findViewById(R.id.video_count);
        clock = findViewById(R.id.clock);
        optionsScrim = findViewById(R.id.options_scrim);
        optionsPanel = findViewById(R.id.options_panel);
        folderOption = findViewById(R.id.folder_option);
        rescanOption = findViewById(R.id.rescan_option);
        sortOption = findViewById(R.id.sort_option);
        folderValue = findViewById(R.id.folder_value);
        sortValue = findViewById(R.id.sort_value);
    }

    private void configureGrid() {
        adapter = new VideoAdapter(thumbnailRepository, this::openVideo);
        videoGrid.setLayoutManager(new GridLayoutManager(this, 4));
        videoGrid.setAdapter(adapter);
        videoGrid.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(
                    @NonNull Rect outRect,
                    @NonNull View view,
                    @NonNull RecyclerView parent,
                    @NonNull RecyclerView.State state
            ) {
                int spacing = dp(7);
                outRect.set(spacing, spacing, spacing, spacing);
            }
        });
    }

    private void configureActions() {
        chooseFolderButton.setOnClickListener(view -> openFolderPicker());
        folderOption.setOnClickListener(view -> openFolderPicker());
        rescanOption.setOnClickListener(view -> {
            closeOptions();
            Uri folderUri = preferences.getFolderUri();
            if (folderUri == null) openFolderPicker();
            else scanLibrary(folderUri);
        });
        sortOption.setOnClickListener(view -> {
            preferences.toggleSortMode();
            updateOptionValues();
            closeOptions();
            Uri folderUri = preferences.getFolderUri();
            if (folderUri != null) scanLibrary(folderUri);
        });
    }

    private void scanLibrary(Uri folderUri) {
        int generation = ++scanGeneration;
        emptyState.setVisibility(View.VISIBLE);
        emptyTitle.setText(R.string.loading_library);
        emptyDescription.setVisibility(View.GONE);
        libraryProgress.setVisibility(View.VISIBLE);
        chooseFolderButton.setVisibility(View.GONE);
        videoGrid.setVisibility(View.GONE);
        folderLabel.setText(getString(R.string.folder_label, preferences.getFolderName()));
        videoCount.setText(getString(R.string.video_count, 0));

        libraryExecutor.submit(() -> {
            List<VideoItem> found;
            try {
                found = libraryRepository.scan(folderUri, preferences.getSortMode());
            } catch (Exception ignored) {
                found = null;
            }
            List<VideoItem> result = found;
            mainHandler.post(() -> {
                if (generation != scanGeneration || isFinishing()) return;
                if (result == null) {
                    showLibraryError();
                    return;
                }
                videos.clear();
                videos.addAll(result);
                adapter.submit(videos);
                videoCount.setText(getString(R.string.video_count, videos.size()));
                if (videos.isEmpty()) {
                    emptyState.setVisibility(View.VISIBLE);
                    emptyTitle.setText(R.string.empty_library_title);
                    emptyDescription.setText(R.string.library_error);
                    emptyDescription.setVisibility(View.VISIBLE);
                    libraryProgress.setVisibility(View.GONE);
                    chooseFolderButton.setVisibility(View.VISIBLE);
                    chooseFolderButton.requestFocus();
                } else {
                    emptyState.setVisibility(View.GONE);
                    videoGrid.setVisibility(View.VISIBLE);
                    videoGrid.post(() -> {
                        RecyclerView.ViewHolder holder =
                                videoGrid.findViewHolderForAdapterPosition(0);
                        if (holder != null) holder.itemView.requestFocus();
                    });
                }
                updateOptionValues();
            });
        });
    }

    private void showFolderPrompt() {
        folderLabel.setText(getString(R.string.folder_label, "Sin seleccionar"));
        videoCount.setText(getString(R.string.video_count, 0));
        videoGrid.setVisibility(View.GONE);
        emptyState.setVisibility(View.VISIBLE);
        emptyTitle.setText(R.string.empty_library_title);
        emptyDescription.setText(R.string.empty_library_description);
        emptyDescription.setVisibility(View.VISIBLE);
        libraryProgress.setVisibility(View.GONE);
        chooseFolderButton.setVisibility(View.VISIBLE);
        chooseFolderButton.requestFocus();
        updateOptionValues();
    }

    private void showLibraryError() {
        videoGrid.setVisibility(View.GONE);
        emptyState.setVisibility(View.VISIBLE);
        emptyTitle.setText(R.string.library_error);
        emptyDescription.setText(R.string.empty_library_description);
        emptyDescription.setVisibility(View.VISIBLE);
        libraryProgress.setVisibility(View.GONE);
        chooseFolderButton.setVisibility(View.VISIBLE);
        chooseFolderButton.requestFocus();
    }

    private void openFolderPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(intent, FOLDER_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (appUpdater != null && appUpdater.onActivityResult(requestCode)) return;
        if (requestCode != FOLDER_REQUEST || resultCode != RESULT_OK
                || data == null || data.getData() == null) return;

        Uri folderUri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(
                    folderUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );
        } catch (SecurityException ignored) {
            // El permiso temporal sigue siendo válido durante esta sesión.
        }
        DocumentFile folder = DocumentFile.fromTreeUri(this, folderUri);
        String name = folder == null ? "Videos" : folder.getName();
        preferences.setFolder(folderUri, name);
        closeOptions();
        scanLibrary(folderUri);
    }

    private void openVideo(VideoItem video) {
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra(PlayerActivity.EXTRA_URI, video.getUri().toString());
        intent.putExtra(PlayerActivity.EXTRA_TITLE, video.getName());
        intent.putExtra(PlayerActivity.EXTRA_DURATION, video.getDurationMs());
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(intent);
    }

    private void showOptions() {
        updateOptionValues();
        optionsScrim.setVisibility(View.VISIBLE);
        optionsPanel.setVisibility(View.VISIBLE);
        folderOption.requestFocus();
    }

    private void closeOptions() {
        if (optionsPanel.getVisibility() != View.VISIBLE) return;
        optionsPanel.setVisibility(View.GONE);
        optionsScrim.setVisibility(View.GONE);
        if (!videos.isEmpty()) {
            videoGrid.requestFocus();
        } else {
            chooseFolderButton.requestFocus();
        }
        enterImmersiveMode();
    }

    private void updateOptionValues() {
        Uri folderUri = preferences.getFolderUri();
        folderValue.setText(folderUri == null
                ? getString(R.string.select)
                : preferences.getFolderName());
        sortValue.setText(LibraryPreferences.SORT_DATE.equals(preferences.getSortMode())
                ? R.string.sort_date
                : R.string.sort_name);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            int keyCode = event.getKeyCode();
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                handleBackAction();
                return true;
            }
            if (optionsPanel.getVisibility() != View.VISIBLE
                    && (keyCode == KeyEvent.KEYCODE_MENU
                    || keyCode == KeyEvent.KEYCODE_SETTINGS)) {
                showOptions();
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
        if (optionsPanel.getVisibility() == View.VISIBLE) closeOptions();
        else showExitDialog();
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
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.width = WindowManager.LayoutParams.WRAP_CONTENT;
            attributes.height = WindowManager.LayoutParams.WRAP_CONTENT;
            attributes.dimAmount = 0.68f;
            window.setAttributes(attributes);
        }
        dialog.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (appUpdater != null) appUpdater.onHostResume();
        enterImmersiveMode();
    }

    @Override
    protected void onPause() {
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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        scanGeneration++;
        mainHandler.removeCallbacksAndMessages(null);
        if (exitDialog != null) exitDialog.dismiss();
        if (appUpdater != null) appUpdater.destroy();
        thumbnailRepository.destroy();
        libraryExecutor.shutdownNow();
        updateExecutor.shutdownNow();
        super.onDestroy();
    }
}
