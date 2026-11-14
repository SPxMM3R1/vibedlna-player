package cl.streambox.tv;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Almacén privado del punto donde quedó cada video.
 *
 * <p>No es un historial: cada entrada vence según la retención elegida en
 * Opciones (1 hora, 1 día o siempre), se borra al llegar al final del video y,
 * en «siempre», se conservan como máximo {@link #MAX_ENTRIES} entradas (se
 * borran las más antiguas).</p>
 */
final class PlaybackResumeStore {
    static final long RETENTION_MS = 60L * 60L * 1_000L;
    static final int MAX_ENTRIES = 200;
    private static final long MINIMUM_POSITION_MS = 5_000L;
    private static final long COMPLETION_TAIL_MS = 30_000L;
    private static final String PREFS = "temporary_playback_resume";
    private static final String PREFIX = "resume_";

    private final SharedPreferences preferences;
    private final long retentionMs;

    PlaybackResumeStore(Context context) {
        this(context, RETENTION_MS);
    }

    PlaybackResumeStore(Context context, long retentionMs) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.retentionMs = retentionMs;
        prune(System.currentTimeMillis());
    }

    ResumePoint get(String stableKey, long nowMs) {
        String key = storageKey(stableKey);
        String value = preferences.getString(key, null);
        ResumePoint point = parse(value);
        if (point == null || !isUsable(point, nowMs, retentionMs)) {
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
        if (duration > 0L && position >= Math.max(0L, duration - completionTail(duration))) {
            clear(stableKey);
            return;
        }
        preferences.edit()
                .putString(storageKey(stableKey), position + "|" + duration + "|" + nowMs)
                .apply();
        enforceLimit();
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
            if (point != null && isUsable(point, nowMs, retentionMs)) continue;
            if (editor == null) editor = preferences.edit();
            editor.remove(key);
        }
        if (editor != null) editor.apply();
    }

    /** Quita las entradas más antiguas cuando hay más de {@link #MAX_ENTRIES}. */
    private void enforceLimit() {
        List<Map.Entry<String, ?>> stored = new ArrayList<>();
        for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
            if (entry.getKey().startsWith(PREFIX)) stored.add(entry);
        }
        if (stored.size() <= MAX_ENTRIES) return;
        Collections.sort(stored, (left, right) -> Long.compare(
                savedAt(left.getValue()),
                savedAt(right.getValue())
        ));
        SharedPreferences.Editor editor = preferences.edit();
        for (int i = 0; i < stored.size() - MAX_ENTRIES; i++) {
            editor.remove(stored.get(i).getKey());
        }
        editor.apply();
    }

    private static long savedAt(Object value) {
        ResumePoint point = parse(value == null ? null : value.toString());
        return point == null ? 0L : point.savedAtMs;
    }

    static boolean isUsable(ResumePoint point, long nowMs) {
        return isUsable(point, nowMs, RETENTION_MS);
    }

    static boolean isUsable(ResumePoint point, long nowMs, long retentionMs) {
        return point != null
                && point.positionMs >= MINIMUM_POSITION_MS
                && nowMs >= point.savedAtMs
                && nowMs - point.savedAtMs < retentionMs
                && (point.durationMs <= 0L
                || point.positionMs < Math.max(0L, point.durationMs - completionTail(point.durationMs)));
    }

    /**
     * Cola final que cuenta como «visto del todo»: 30 s, o el 5 % de la duración si el
     * video es más corto (un clip de 20 s no debe borrarse al pausarlo a los 11 s).
     */
    static long completionTail(long durationMs) {
        return Math.min(COMPLETION_TAIL_MS, Math.max(0L, durationMs) / 20L);
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
