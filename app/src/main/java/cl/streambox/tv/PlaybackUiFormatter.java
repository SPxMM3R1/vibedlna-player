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

    /**
     * Línea de tiempo del OSD: «0:42 / 1:30 · Quedan 48 min · En pausa». Sin duración conocida
     * solo va la posición. Cada tramo empieza con mayúscula.
     */
    static String timeLine(long positionMs, long durationMs, boolean playing) {
        long position = Math.max(0L, positionMs);
        java.util.List<String> parts = new java.util.ArrayList<>();
        if (durationMs > 0L) {
            long shown = Math.min(position, durationMs);
            parts.add(DlnaMediaFormat.duration(shown) + " / " + DlnaMediaFormat.duration(durationMs));
            parts.add("Quedan " + DlnaMediaFormat.remaining(durationMs - shown));
        } else {
            parts.add(DlnaMediaFormat.duration(position));
        }
        if (!playing) parts.add("En pausa");
        return DlnaMediaFormat.join(parts, " · ");
    }

    /** Ayuda del OSD: el OK dice lo que hará, «Pausa» al reproducir y «Reproducir» en pausa. */
    static String helpLine(boolean playing) {
        return "◀ ▶ 10 s · ▲ Audio y subtítulos · OK " + (playing ? "Pausa" : "Reproducir");
    }

    /** «Desde VibeDLNA Prueba · 192.168.3.4»; vacío si no hay ni servidor ni host. */
    static String fromLine(String server, String host) {
        String source = DlnaMediaFormat.join(java.util.Arrays.asList(server, host), " · ");
        return source.isEmpty() ? "" : "Desde " + source;
    }

    static String endingAt(long nowMs, long positionMs, long durationMs, Locale locale) {
        long remaining = Math.max(0L, durationMs - Math.max(0L, positionMs));
        String value = new SimpleDateFormat("HH:mm", locale)
                .format(new Date(nowMs + remaining));
        return "Terminar\u00e1 a las " + value;
    }

    /** Idiomas conocidos (código ISO 639 de dos o tres letras) con su nombre en español. */
    private static final java.util.Map<String, String> LANGUAGE_NAMES = new java.util.HashMap<>();

    static {
        String[][] names = {
                {"spa", "es", "Español"}, {"eng", "en", "Inglés"}, {"fra fre", "fr", "Francés"},
                {"deu ger", "de", "Alemán"}, {"ita", "it", "Italiano"}, {"por", "pt", "Portugués"},
                {"jpn", "ja", "Japonés"}, {"zho chi", "zh", "Chino"}, {"kor", "ko", "Coreano"},
                {"rus", "ru", "Ruso"}, {"cat", "ca", "Catalán"}, {"nld dut", "nl", "Neerlandés"},
        };
        for (String[] row : names) {
            for (String code : row[0].split(" ")) LANGUAGE_NAMES.put(code, row[2]);
            LANGUAGE_NAMES.put(row[1], row[2]);
        }
    }

    /**
     * Nombre en español del idioma de una pista («spa» o «es-419» → «Español»). Si el código no
     * se conoce, se muestra en mayúsculas («XYZ»); sin código, vacío.
     */
    static String languageName(String language) {
        if (language == null) return "";
        String value = language.trim().toLowerCase(java.util.Locale.ROOT);
        if (value.isEmpty()) return "";
        String primary = value.split("[-_]", 2)[0];
        String name = LANGUAGE_NAMES.get(primary);
        return name != null ? name : primary.toUpperCase(java.util.Locale.ROOT);
    }

    /**
     * Idioma de una pista de audio: «und» o vacío significa «Original»; si no, el nombre en español
     * («Español») o el código en mayúsculas.
     */
    static String audioLanguage(String language) {
        if (language == null) return "Original";
        String value = language.trim();
        if (value.isEmpty() || "und".equalsIgnoreCase(value)) return "Original";
        return languageName(value);
    }

    /** Canales de una pista de audio: 1 = Mono, 2 = Estéreo, 6 = 5.1, 8 = 7.1, el resto «N ch». */
    static String audioChannels(int channels) {
        switch (channels) {
            case 1:
                return "Mono";
            case 2:
                return "Estéreo";
            case 6:
                return "5.1";
            case 8:
                return "7.1";
            default:
                return channels > 0 ? channels + " ch" : "";
        }
    }

    /** «Original · AAC · Estéreo»: lo que muestra el panel ▲ para una pista de audio. */
    static String audioTrackLabel(String language, String codec, int channels) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        parts.add(audioLanguage(language));
        parts.add("—".equals(codec) ? "" : codec);
        parts.add(audioChannels(channels));
        return DlnaMediaFormat.join(parts, " · ");
    }

    /**
     * Mensaje de error para quien mira la TV. {@code errorCodeName} es el código técnico de Media3
     * (el log lo conserva); {@code httpStatus} es la respuesta HTTP si la hubo, o -1.
     */
    static String playbackErrorMessage(String errorCodeName, int httpStatus) {
        String code = errorCodeName == null ? "" : errorCodeName.replace("ERROR_CODE_", "");
        if (httpStatus == 404 || "IO_FILE_NOT_FOUND".equals(code)) {
            return "El archivo ya no está en el PC";
        }
        if (code.startsWith("IO_") || code.startsWith("NETWORK_CONNECTION_")
                || "BAD_HTTP_STATUS".equals(code)) {
            return "No se pudo conectar con el PC";
        }
        if (code.startsWith("PARSING_")) {
            return "Este archivo está dañado o incompleto";
        }
        if (code.startsWith("DECODER_") || code.startsWith("DECODING_")) {
            return "La TV no puede reproducir este formato";
        }
        return "No se pudo reproducir este video";
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
