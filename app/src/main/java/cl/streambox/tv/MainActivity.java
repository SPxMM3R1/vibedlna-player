package cl.streambox.tv;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService dlnaExecutor = Executors.newFixedThreadPool(2);
    private final ExecutorService updateExecutor = Executors.newSingleThreadExecutor();
    private final List<VideoItem> entries = new ArrayList<>();
    private final List<DlnaServer> availableServers = new ArrayList<>();
    private final Map<String, String> containerNames = new HashMap<>();

    private RecyclerView videoGrid;
    private View emptyState;
    private TextView emptyTitle;
    private TextView emptyDescription;
    private ProgressBar libraryProgress;
    private Button searchButton;
    private TextView folderLabel;
    private TextView entryCount;
    private TextView clock;
    private Button optionsButton;
    private View optionsScrim;
    private View optionsPanel;
    private Button serverOption;
    private Button discoverOption;
    private TextView serverValue;
    private TextView folderValue;

    private LibraryPreferences preferences;
    private DlnaDiscovery discovery;
    private DlnaContentRepository contentRepository;
    private ThumbnailRepository thumbnailRepository;
    private VideoAdapter adapter;
    private AppUpdater appUpdater;
    private DlnaServer currentServer;
    private String currentContainerId = "0";
    private String currentContainerName = "Inicio";
    private String currentParentId;
    private Dialog serverDialog;
    private Dialog exitDialog;
    private int discoveryGeneration;
    private int browseGeneration;

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
        discovery = new DlnaDiscovery(this);
        contentRepository = new DlnaContentRepository(discovery);
        thumbnailRepository = new ThumbnailRepository(this, mainHandler);
        bindViews();
        configureGrid();
        configureActions();
        registerBackCallback();
        enterImmersiveMode();

        appUpdater = new AppUpdater(this, updateExecutor, mainHandler);
        appUpdater.checkForUpdates();
        updateClock.run();
        discoverServers(true);
    }

    private void bindViews() {
        videoGrid = findViewById(R.id.video_grid);
        emptyState = findViewById(R.id.empty_state);
        emptyTitle = findViewById(R.id.empty_title);
        emptyDescription = findViewById(R.id.empty_description);
        libraryProgress = findViewById(R.id.library_progress);
        searchButton = findViewById(R.id.choose_folder_button);
        folderLabel = findViewById(R.id.folder_label);
        entryCount = findViewById(R.id.video_count);
        clock = findViewById(R.id.clock);
        optionsButton = findViewById(R.id.options_button);
        optionsScrim = findViewById(R.id.options_scrim);
        optionsPanel = findViewById(R.id.options_panel);
        serverOption = findViewById(R.id.server_option);
        discoverOption = findViewById(R.id.discover_option);
        serverValue = findViewById(R.id.server_value);
        folderValue = findViewById(R.id.folder_value);
    }

    private void configureGrid() {
        adapter = new VideoAdapter(thumbnailRepository, this::openEntry);
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
        searchButton.setOnClickListener(view -> discoverServers(false));
        optionsButton.setOnClickListener(view -> showOptions());
        serverOption.setOnClickListener(view -> {
            closeOptions();
            if (availableServers.isEmpty()) discoverServers(false);
            else showServerDialog();
        });
        discoverOption.setOnClickListener(view -> {
            closeOptions();
            discoverServers(false);
        });
    }

    private void discoverServers(boolean automatic) {
        int generation = ++discoveryGeneration;
        showLoading(
                R.string.searching_servers,
                R.string.searching_servers_description
        );
        dlnaExecutor.submit(() -> {
            List<DlnaServer> found = discovery.discover(5_000L);
            mainHandler.post(() -> {
                if (generation != discoveryGeneration || isFinishing()) return;
                availableServers.clear();
                availableServers.addAll(found);
                String savedUdn = preferences.getServerUdn();
                DlnaServer saved = findServer(savedUdn);

                if (automatic && saved != null) {
                    selectAvailableServer(saved, false);
                    return;
                }
                if (availableServers.isEmpty()) {
                    showNoServers();
                    return;
                }
                if (automatic && !savedUdn.isBlank()) {
                    emptyTitle.setText(R.string.saved_server_unavailable);
                    emptyDescription.setText(R.string.no_servers_description);
                    libraryProgress.setVisibility(View.GONE);
                    searchButton.setVisibility(View.VISIBLE);
                }
                showServerDialog();
            });
        });
    }

    private DlnaServer findServer(String udn) {
        if (udn == null || udn.isBlank()) return null;
        for (DlnaServer server : availableServers) {
            if (udn.equals(server.getUdn())) return server;
        }
        return null;
    }

    private void showServerDialog() {
        if (availableServers.isEmpty()) {
            showNoServers();
            return;
        }
        if (serverDialog != null && serverDialog.isShowing()) return;

        Dialog dialog = new Dialog(this);
        serverDialog = dialog;
        dialog.setContentView(R.layout.dialog_dlna_servers);
        dialog.setCanceledOnTouchOutside(false);
        LinearLayout container = dialog.findViewById(R.id.server_options);
        Button focusTarget = null;
        String savedUdn = preferences.getServerUdn();
        for (DlnaServer server : availableServers) {
            Button button = createServerButton(server.getFriendlyName());
            button.setOnClickListener(view -> {
                dialog.dismiss();
                selectAvailableServer(server, true);
            });
            container.addView(button);
            if (focusTarget == null || server.getUdn().equals(savedUdn)) {
                focusTarget = button;
            }
        }
        Button initialFocus = focusTarget;
        dialog.setOnShowListener(ignored -> {
            if (initialFocus != null) initialFocus.requestFocus();
        });
        dialog.setOnDismissListener(ignored -> {
            if (serverDialog == dialog) serverDialog = null;
            if (!isFinishing()) enterImmersiveMode();
        });
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.width = WindowManager.LayoutParams.WRAP_CONTENT;
            attributes.height = WindowManager.LayoutParams.WRAP_CONTENT;
            attributes.dimAmount = 0.72f;
            window.setAttributes(attributes);
        }
        dialog.show();
    }

    private Button createServerButton(String name) {
        Button button = new Button(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
        );
        params.bottomMargin = dp(8);
        button.setLayoutParams(params);
        button.setBackgroundResource(R.drawable.focus_button_compact);
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        button.setIncludeFontPadding(false);
        button.setMinHeight(0);
        button.setMinWidth(0);
        button.setPadding(dp(14), 0, dp(14), 0);
        button.setText(name);
        button.setTextColor(getColor(R.color.white));
        button.setTextSize(11);
        button.setAllCaps(false);
        return button;
    }

    private void selectAvailableServer(DlnaServer server, boolean resetFolder) {
        currentServer = server;
        if (resetFolder || !server.getUdn().equals(preferences.getServerUdn())) {
            preferences.selectServer(server);
        }
        String containerId = resetFolder ? "0" : preferences.getContainerId();
        String containerName = resetFolder ? "Inicio" : preferences.getContainerName();
        browseContainer(containerId, containerName);
    }

    private void browseContainer(String containerId, String containerName) {
        if (currentServer == null) return;
        int generation = ++browseGeneration;
        showLoading(
                R.string.loading_remote_folder,
                R.string.searching_servers_description
        );
        DlnaServer server = currentServer;
        dlnaExecutor.submit(() -> {
            DlnaContentRepository.BrowseResult result;
            try {
                result = contentRepository.browse(server, containerId);
            } catch (Exception ignored) {
                result = null;
            }
            DlnaContentRepository.BrowseResult loaded = result;
            mainHandler.post(() -> {
                if (generation != browseGeneration || isFinishing()
                        || currentServer != server) return;
                if (loaded == null) {
                    showBrowseError();
                    return;
                }
                currentContainerId = containerId;
                currentContainerName = containerName;
                currentParentId = loaded.parentId;
                containerNames.put(containerId, containerName);
                preferences.selectContainer(containerId, containerName);
                showEntries(loaded.entries);
            });
        });
    }

    private void showEntries(List<VideoItem> loaded) {
        entries.clear();
        entries.addAll(loaded);
        for (VideoItem entry : entries) {
            if (entry.isContainer()) containerNames.put(entry.getId(), entry.getName());
        }
        Collections.sort(entries, new Comparator<VideoItem>() {
            @Override
            public int compare(VideoItem left, VideoItem right) {
                if (left.isContainer() != right.isContainer()) {
                    return left.isContainer() ? -1 : 1;
                }
                return left.getName().compareToIgnoreCase(right.getName());
            }
        });
        adapter.submit(entries);
        updateHeader();
        updateOptionValues();
        libraryProgress.setVisibility(View.GONE);
        searchButton.setVisibility(View.GONE);
        if (entries.isEmpty()) {
            videoGrid.setVisibility(View.GONE);
            emptyState.setVisibility(View.VISIBLE);
            emptyTitle.setText(R.string.empty_remote_folder);
            emptyDescription.setText(R.string.privacy_note);
            emptyDescription.setVisibility(View.VISIBLE);
        } else {
            emptyState.setVisibility(View.GONE);
            videoGrid.setVisibility(View.VISIBLE);
            videoGrid.post(this::focusFirstEntry);
        }
    }

    private void openEntry(VideoItem entry) {
        if (entry.isContainer()) {
            browseContainer(entry.getId(), entry.getName());
            return;
        }
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra(PlayerActivity.EXTRA_URI, entry.getUri().toString());
        intent.putExtra(PlayerActivity.EXTRA_TITLE, entry.getName());
        intent.putExtra(PlayerActivity.EXTRA_DURATION, entry.getDurationMs());
        startActivity(intent);
    }

    private void navigateUp() {
        if (currentServer == null || "0".equals(currentContainerId)) {
            showExitDialog();
            return;
        }
        String parent = currentParentId == null || currentParentId.isBlank()
                ? "0"
                : currentParentId;
        String parentName = "0".equals(parent)
                ? "Inicio"
                : containerNames.get(parent);
        browseContainer(parent, parentName == null ? "Carpeta" : parentName);
    }

    private void showLoading(int titleResource, int descriptionResource) {
        videoGrid.setVisibility(View.GONE);
        emptyState.setVisibility(View.VISIBLE);
        emptyTitle.setText(titleResource);
        emptyDescription.setText(descriptionResource);
        emptyDescription.setVisibility(View.VISIBLE);
        libraryProgress.setVisibility(View.VISIBLE);
        searchButton.setVisibility(View.GONE);
        entryCount.setText(getString(R.string.entry_count, 0));
    }

    private void showNoServers() {
        currentServer = null;
        entries.clear();
        adapter.submit(entries);
        folderLabel.setText(R.string.select_server);
        entryCount.setText(getString(R.string.entry_count, 0));
        videoGrid.setVisibility(View.GONE);
        emptyState.setVisibility(View.VISIBLE);
        emptyTitle.setText(R.string.no_servers);
        emptyDescription.setText(R.string.no_servers_description);
        emptyDescription.setVisibility(View.VISIBLE);
        libraryProgress.setVisibility(View.GONE);
        searchButton.setVisibility(View.VISIBLE);
        searchButton.requestFocus();
        updateOptionValues();
    }

    private void showBrowseError() {
        videoGrid.setVisibility(View.GONE);
        emptyState.setVisibility(View.VISIBLE);
        emptyTitle.setText(R.string.remote_folder_error);
        emptyDescription.setText(R.string.no_servers_description);
        emptyDescription.setVisibility(View.VISIBLE);
        libraryProgress.setVisibility(View.GONE);
        searchButton.setVisibility(View.VISIBLE);
        searchButton.requestFocus();
    }

    private void updateHeader() {
        if (currentServer == null) {
            folderLabel.setText(R.string.select_server);
        } else {
            folderLabel.setText(getString(
                    R.string.source_label,
                    currentServer.getFriendlyName(),
                    currentContainerName
            ));
        }
        entryCount.setText(getString(R.string.entry_count, entries.size()));
    }

    private void showOptions() {
        updateOptionValues();
        optionsScrim.setVisibility(View.VISIBLE);
        optionsPanel.setVisibility(View.VISIBLE);
        serverOption.requestFocus();
    }

    private void closeOptions() {
        if (optionsPanel.getVisibility() != View.VISIBLE) return;
        optionsPanel.setVisibility(View.GONE);
        optionsScrim.setVisibility(View.GONE);
        if (!entries.isEmpty()) focusFirstEntry();
        else searchButton.requestFocus();
        enterImmersiveMode();
    }

    private boolean focusFirstEntry() {
        RecyclerView.ViewHolder holder = videoGrid.findViewHolderForAdapterPosition(0);
        if (holder == null) return videoGrid.requestFocus();
        return holder.itemView.requestFocus();
    }

    private void updateOptionValues() {
        String serverName = currentServer == null
                ? preferences.getServerName()
                : currentServer.getFriendlyName();
        serverValue.setText(serverName == null || serverName.isBlank()
                ? getString(R.string.select_server)
                : serverName);
        folderValue.setText(currentContainerName == null
                ? preferences.getContainerName()
                : currentContainerName);
    }

    @Override
    @SuppressLint("GestureBackNavigation")
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
            if (optionsPanel.getVisibility() != View.VISIBLE
                    && keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                View focused = getCurrentFocus();
                RecyclerView.ViewHolder holder = focused == null
                        ? null
                        : videoGrid.findContainingViewHolder(focused);
                if (holder != null
                        && holder.getBindingAdapterPosition() >= 0
                        && holder.getBindingAdapterPosition() < 4) {
                    optionsButton.requestFocus();
                    return true;
                }
            }
            if (optionsPanel.getVisibility() != View.VISIBLE
                    && keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                    && getCurrentFocus() == optionsButton
                    && !entries.isEmpty()) {
                focusFirstEntry();
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
        if (optionsPanel.getVisibility() == View.VISIBLE) {
            closeOptions();
        } else if (serverDialog != null && serverDialog.isShowing()) {
            serverDialog.dismiss();
        } else {
            navigateUp();
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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (appUpdater != null) appUpdater.onActivityResult(requestCode);
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
        discoveryGeneration++;
        browseGeneration++;
        mainHandler.removeCallbacksAndMessages(null);
        if (serverDialog != null) serverDialog.dismiss();
        if (exitDialog != null) exitDialog.dismiss();
        if (appUpdater != null) appUpdater.destroy();
        thumbnailRepository.destroy();
        discovery.close();
        dlnaExecutor.shutdownNow();
        updateExecutor.shutdownNow();
        super.onDestroy();
    }
}
