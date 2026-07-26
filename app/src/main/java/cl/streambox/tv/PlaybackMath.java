package cl.streambox.tv;

final class PlaybackMath {
    private PlaybackMath() {
    }

    static long clampSeekPosition(long currentMs, long deltaMs, long durationMs) {
        long target;
        try {
            target = Math.addExact(currentMs, deltaMs);
        } catch (ArithmeticException ignored) {
            target = deltaMs > 0L ? Long.MAX_VALUE : 0L;
        }
        target = Math.max(0L, target);
        return durationMs > 0L ? Math.min(target, durationMs) : target;
    }

    static int progress(long positionMs, long durationMs, int maximum) {
        if (durationMs <= 0L || maximum <= 0) return 0;
        double ratio = Math.max(0d, Math.min(1d, positionMs / (double) durationMs));
        return (int) Math.round(ratio * maximum);
    }
}
