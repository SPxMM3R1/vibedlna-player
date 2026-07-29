package cl.streambox.tv;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Map;

/**
 * Small, private store for the temporary resume point of a video.
 *
 * <p>Entries are intentionally short lived. They are not a viewing history:
 * an entry is removed once it expires, reaches the end of the item, or is
 * cleared by the player.</p>
 */
final class PlaybackResumeStore {
    static final long RETENTION_MS = 60L * 60L * 1_000L;
    private static final long MINIMUM_POSITION_MS = 5_000L;
    private static final long COMPLETION_TAIL_MS = 30_000L;
    private static final String PREFS = "temporary_playback_resume";
    private static final String PREFIX = "resume_";

    private final SharedPreferences preferences;

    PlaybackResumeStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prune(System.currentTimeMillis());
    }

    ResumePoint get(String stableKey, long nowMs) {
        String key = storageKey(stableKey);
        String value = preferences.getString(key, null);
        ResumePoint point = parse(value);
        if (point == null || !isUsable(point, nowMs)) {
            if (value != null) preferences.edit().remove(key).apply();
            return null;
        }
        return point;
    }

    void save(String stableKey, long positionMs, long durationMs, long nowMs) {
        if (stableKey == null || stableKey.isBlank()) return;
        long position = Math.max(0L, positionMs);
        long duration = Math.max(0L, durationMs);
        if (position < MINIMUM_POSITION_MS) {
            return;
        }
        if (duration > 0L && position >= Math.max(0L, duration - COMPLETION_TAIL_MS)) {
            clear(stableKey);
            return;
        }
        preferences.edit()
                .putString(storageKey(stableKey), position + "|" + duration + "|" + nowMs)
                .apply();
    }

    void clear(String stableKey) {
        if (stableKey == null || stableKey.isBlank()) return;
        preferences.edit().remove(storageKey(stableKey)).apply();
    }

    void prune(long nowMs) {
        SharedPreferences.Editor editor = null;
        for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith(PREFIX)) continue;
            ResumePoint point = parse(entry.getValue() == null ? null : entry.getValue().toString());
            if (point != null && isUsable(point, nowMs)) continue;
            if (editor == null) editor = preferences.edit();
            editor.remove(key);
        }
        if (editor != null) editor.apply();
    }

    static boolean isUsable(ResumePoint point, long nowMs) {
        return point != null
                && point.positionMs >= MINIMUM_POSITION_MS
                && nowMs >= point.savedAtMs
                && nowMs - point.savedAtMs < RETENTION_MS
                && (point.durationMs <= 0L
                || point.positionMs < Math.max(0L, point.durationMs - COMPLETION_TAIL_MS));
    }

    private static ResumePoint parse(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            String[] parts = value.split("\\|", -1);
            if (parts.length != 3) return null;
            return new ResumePoint(
                    Long.parseLong(parts[0]),
                    Long.parseLong(parts[1]),
                    Long.parseLong(parts[2])
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String storageKey(String stableKey) {
        return PREFIX + ThumbnailCacheKey.name("resume", stableKey, "v1").replace(".jpg", "");
    }

    static final class ResumePoint {
        final long positionMs;
        final long durationMs;
        final long savedAtMs;

        ResumePoint(long positionMs, long durationMs, long savedAtMs) {
            this.positionMs = positionMs;
            this.durationMs = durationMs;
            this.savedAtMs = savedAtMs;
        }
    }
}
