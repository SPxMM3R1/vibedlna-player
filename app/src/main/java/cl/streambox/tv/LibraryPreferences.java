package cl.streambox.tv;

import android.content.Context;
import android.content.SharedPreferences;

final class LibraryPreferences {
    private static final String PREFS = "dlna_settings";
    private static final String KEY_SERVER_UDN = "server_udn";
    private static final String KEY_SERVER_NAME = "server_name";
    private static final String KEY_CONTAINER_ID = "container_id";
    private static final String KEY_CONTAINER_NAME = "container_name";
    private static final String KEY_THUMBNAIL_MODE = "thumbnail_mode";
    private static final String KEY_THUMBNAIL_PERCENT = "thumbnail_percent";

    private final SharedPreferences preferences;

    LibraryPreferences(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    String getServerUdn() {
        return preferences.getString(KEY_SERVER_UDN, "");
    }

    String getServerName() {
        return preferences.getString(KEY_SERVER_NAME, "");
    }

    String getContainerId() {
        return preferences.getString(KEY_CONTAINER_ID, "0");
    }

    String getContainerName() {
        return preferences.getString(KEY_CONTAINER_NAME, "Inicio");
    }

    void selectServer(DlnaServer server) {
        preferences.edit()
                .putString(KEY_SERVER_UDN, server.getUdn())
                .putString(KEY_SERVER_NAME, server.getFriendlyName())
                .putString(KEY_CONTAINER_ID, "0")
                .putString(KEY_CONTAINER_NAME, "Inicio")
                .apply();
    }

    void selectContainer(String id, String name) {
        preferences.edit()
                .putString(KEY_CONTAINER_ID, id)
                .putString(KEY_CONTAINER_NAME, name)
                .apply();
    }

    ThumbnailSettings getThumbnailSettings() {
        String value = preferences.getString(
                KEY_THUMBNAIL_MODE,
                ThumbnailSettings.Mode.SERVER.name()
        );
        try {
            return new ThumbnailSettings(ThumbnailSettings.Mode.valueOf(value));
        } catch (Exception ignored) {
            return new ThumbnailSettings(ThumbnailSettings.Mode.SERVER);
        }
    }

    void setThumbnailMode(ThumbnailSettings.Mode mode) {
        preferences.edit()
                .putString(KEY_THUMBNAIL_MODE, mode.name())
                .apply();
    }

    int getThumbnailPercentage() {
        int stored = preferences.getInt(KEY_THUMBNAIL_PERCENT, 0);
        if (stored == 25 || stored == 50 || stored == 75) return stored;
        ThumbnailSettings settings = getThumbnailSettings();
        int migrated = settings.generatedPercentage();
        setThumbnailPercentage(migrated);
        return migrated;
    }

    void setThumbnailPercentage(int percentage) {
        int normalized = percentage == 25 || percentage == 75 ? percentage : 50;
        preferences.edit().putInt(KEY_THUMBNAIL_PERCENT, normalized).apply();
    }

    void setThumbnailServerArtwork(boolean enabled) {
        if (enabled) {
            setThumbnailMode(ThumbnailSettings.Mode.SERVER);
        } else {
            setThumbnailMode(ThumbnailSettings.generated(getThumbnailPercentage()).mode());
        }
    }
}
