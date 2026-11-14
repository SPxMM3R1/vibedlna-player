package cl.streambox.tv;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Preferencias de la pantalla Opciones (0.4.0). Cada valor tiene un índice
 * estable para que el orden de las opciones no dependa del idioma.
 */
final class PlayerSettings {
    static final int SORT_NAME = 0;
    static final int SORT_RECENT = 1;
    static final int SORT_SIZE = 2;

    static final int RETENTION_HOUR = 0;
    static final int RETENTION_DAY = 1;
    static final int RETENTION_FOREVER = 2;

    static final long HOUR_MS = 60L * 60L * 1_000L;
    static final long DAY_MS = 24L * HOUR_MS;

    private static final String PREFS = "player_settings";
    private static final String KEY_SORT = "sort_order";
    private static final String KEY_UNPLAYABLE = "show_unplayable";
    private static final String KEY_RETENTION = "resume_retention";
    private static final String KEY_OPEN_LAST = "open_last_server";
    private static final String SEEN_PREFIX = "seen_";

    private final SharedPreferences preferences;

    PlayerSettings(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    int sortOrder() {
        return preferences.getInt(KEY_SORT, SORT_NAME);
    }

    void setSortOrder(int value) {
        preferences.edit().putInt(KEY_SORT, value).apply();
    }

    boolean showUnplayable() {
        return preferences.getBoolean(KEY_UNPLAYABLE, false);
    }

    void setShowUnplayable(boolean value) {
        preferences.edit().putBoolean(KEY_UNPLAYABLE, value).apply();
    }

    int resumeRetention() {
        return preferences.getInt(KEY_RETENTION, RETENTION_HOUR);
    }

    void setResumeRetention(int value) {
        preferences.edit().putInt(KEY_RETENTION, value).apply();
    }

    /** Duración de «Recordar dónde quedaste»; Long.MAX_VALUE = siempre. */
    long resumeRetentionMs() {
        return retentionMs(resumeRetention());
    }

    static long retentionMs(int option) {
        switch (option) {
            case RETENTION_DAY:
                return DAY_MS;
            case RETENTION_FOREVER:
                return Long.MAX_VALUE;
            default:
                return HOUR_MS;
        }
    }

    boolean openAtLastServer() {
        return preferences.getBoolean(KEY_OPEN_LAST, true);
    }

    void setOpenAtLastServer(boolean value) {
        preferences.edit().putBoolean(KEY_OPEN_LAST, value).apply();
    }

    /** Última vez que se anunció el servidor, para decir «no responde desde las…». */
    void markSeen(String udn, long atMs) {
        if (udn == null || udn.isBlank()) return;
        preferences.edit().putLong(SEEN_PREFIX + udn, atMs).apply();
    }

    long lastSeen(String udn) {
        if (udn == null || udn.isBlank()) return 0L;
        return preferences.getLong(SEEN_PREFIX + udn, 0L);
    }
}
