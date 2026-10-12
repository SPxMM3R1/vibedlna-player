package cl.streambox.tv;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class PlaybackUiFormatter {
    private PlaybackUiFormatter() {
    }

    static String elapsed(long milliseconds) {
        long totalSeconds = Math.max(0L, milliseconds) / 1_000L;
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return String.format(Locale.ROOT, "%02d:%02d", minutes, seconds);
    }

    static String positionAndDuration(long positionMs, long durationMs) {
        return elapsed(Math.min(Math.max(0L, positionMs), durationMs))
                + " / "
                + elapsed(durationMs);
    }

    static String endingAt(long nowMs, long positionMs, long durationMs, Locale locale) {
        long remaining = Math.max(0L, durationMs - Math.max(0L, positionMs));
        String value = new SimpleDateFormat("HH:mm", locale)
                .format(new Date(nowMs + remaining));
        return "Terminar\u00e1 a las " + value;
    }

    static String friendlyCodec(String mimeType, String codecs) {
        String source = ((mimeType == null ? "" : mimeType)
                + " "
                + (codecs == null ? "" : codecs)).toLowerCase(Locale.ROOT);
        if (source.contains("hevc") || source.contains("h265") || source.contains("hvc1")) {
            return "H.265";
        }
        if (source.contains("avc") || source.contains("h264")) return "H.264";
        if (source.contains("av01") || source.contains("av1")) return "AV1";
        if (source.contains("vp9") || source.contains("vp09")) return "VP9";
        if (source.contains("eac3") || source.contains("e-ac-3")) return "E-AC-3";
        if (source.contains("ac3") || source.contains("ac-3")) return "AC-3";
        if (source.contains("opus")) return "Opus";
        if (source.contains("aac") || source.contains("mp4a") || source.contains("latm")) {
            return "AAC";
        }
        return "\u2014";
    }
}
