package cl.streambox.tv;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Formato de los datos que el servidor anuncia (contrato §7 A). Casi todo es
 * puro Java para poder probarlo en la JVM; solo {@link #supportedDecoderMimes()}
 * consulta el sistema.
 */
final class DlnaMediaFormat {
    private static final String SEPARATOR = " · ";

    /** Códec legible de dc:description → tipo MIME que lo decodifica. */
    private static final Map<String, String> MIME_BY_CODEC = new HashMap<>();

    static {
        MIME_BY_CODEC.put("H.264", "video/avc");
        MIME_BY_CODEC.put("HEVC", "video/hevc");
        MIME_BY_CODEC.put("VP9", "video/x-vnd.on2.vp9");
        MIME_BY_CODEC.put("AV1", "video/av01");
        MIME_BY_CODEC.put("MPEG-2", "video/mpeg2");
        MIME_BY_CODEC.put("AAC", "audio/mp4a-latm");
        MIME_BY_CODEC.put("AC3", "audio/ac3");
        MIME_BY_CODEC.put("E-AC3", "audio/eac3");
        MIME_BY_CODEC.put("DTS", "audio/vnd.dts");
        MIME_BY_CODEC.put("TrueHD", "audio/true-hd");
        MIME_BY_CODEC.put("FLAC", "audio/flac");
        MIME_BY_CODEC.put("Opus", "audio/opus");
        MIME_BY_CODEC.put("MP3", "audio/mpeg");
    }

    private DlnaMediaFormat() {
    }

    /** Alto de «WxH»; 0 si el texto no sirve. */
    static int heightOf(String resolution) {
        if (resolution == null) return 0;
        String[] parts = resolution.toLowerCase(Locale.ROOT).split("x", 2);
        if (parts.length != 2) return 0;
        try {
            return Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    /**
     * Etiqueta de calidad por alto: ≥2000 → 4K, ≥1000 → 1080p, ≥700 → 720p,
     * si no «&lt;alto&gt;p». Sin alto, vacío.
     */
    static String qualityLabel(int height) {
        if (height <= 0) return "";
        if (height >= 2000) return "4K";
        if (height >= 1000) return "1080p";
        if (height >= 700) return "720p";
        return height + "p";
    }

    /** «M:SS» hasta una hora y «H:MM:SS» desde entonces. */
    static String duration(long milliseconds) {
        long total = Math.max(0L, milliseconds) / 1_000L;
        long hours = total / 3_600L;
        long minutes = (total % 3_600L) / 60L;
        long seconds = total % 60L;
        if (hours > 0) {
            return String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.ROOT, "%d:%02d", minutes, seconds);
    }

    /** «1 h 33 min» o «45 min». */
    static String remaining(long milliseconds) {
        long millis = Math.max(0L, milliseconds);
        if (millis < 60_000L) {
            // Bajo un minuto se dan segundos: «quedan 17 s».
            return ((millis + 999L) / 1_000L) + " s";
        }
        long minutes = (millis + 30_000L) / 60_000L;
        if (minutes >= 60L) {
            return String.format(Locale.ROOT, "%d h %d min", minutes / 60L, minutes % 60L);
        }
        return minutes + " min";
    }

    /** Porcentaje visto, 0..100. Con duración desconocida, 0. */
    static int percent(long positionMs, long durationMs) {
        if (durationMs <= 0L) return 0;
        // Redondeo al más cercano: 1:13:02 de 2:46:00 da 44 %, como en el diseño.
        long value = (Math.max(0L, positionMs) * 100L + durationMs / 2L) / durationMs;
        return (int) Math.max(0L, Math.min(100L, value));
    }

    /**
     * Primera letra en mayúscula, sin tocar el resto. Para los tramos que la app escribe (no los
     * nombres que trae el servidor, que se muestran tal cual).
     */
    static String capitalizeFirst(String text) {
        if (text == null || text.isEmpty()) return text == null ? "" : text;
        int first = text.codePointAt(0);
        int length = Character.charCount(first);
        return new String(Character.toChars(Character.toUpperCase(first)))
                + text.substring(length);
    }

    /** Tamaño en GB o MB con coma decimal («22,6 GB»). Vacío si no se conoce. */
    static String size(long bytes) {
        if (bytes <= 0L) return "";
        double gigabytes = bytes / (1024d * 1024d * 1024d);
        if (gigabytes >= 1d) return decimal(gigabytes) + " GB";
        double megabytes = bytes / (1024d * 1024d);
        return megabytes >= 10d
                ? String.format(Locale.ROOT, "%.0f MB", megabytes)
                : decimal(megabytes) + " MB";
    }

    /** Tasa en Mbps con coma decimal («18,2 Mbps»). Vacío si no se conoce. */
    static String mbps(long bytesPerSecond) {
        if (bytesPerSecond <= 0L) return "";
        return decimal(bytesPerSecond * 8d / 1_000_000d) + " Mbps";
    }

    /** Año de dc:date («2024-05-01» → «2024»), o vacío. */
    static String year(String date) {
        if (date == null || date.length() < 4) return "";
        String head = date.substring(0, 4);
        for (int i = 0; i < head.length(); i++) {
            if (!Character.isDigit(head.charAt(i))) return "";
        }
        return head;
    }

    /**
     * Canales en palabras, como el rótulo de pistas: «2.0» → «Estéreo», «1.0» → «Mono». «5.1» y
     * «7.1» quedan igual. Solo cambia esas dos marcas, con sus límites.
     */
    static String channelWords(String codecs) {
        if (codecs == null || codecs.isEmpty()) return codecs == null ? "" : codecs;
        return codecs
                .replaceAll("(?<![\\d.])1\\.0(?![\\d.])", "Mono")
                .replaceAll("(?<![\\d.])2\\.0(?![\\d.])", "Estéreo");
    }

    /** Calidad · códecs · tasa · tamaño, solo con lo que exista. */
    static String mediaLine(MediaInfo info) {
        List<String> parts = new ArrayList<>();
        String quality = qualityLabel(info.height());
        if (!quality.isEmpty()) parts.add(quality);
        if (!info.codecs.isEmpty()) parts.add(channelWords(info.codecs));
        String rate = mbps(info.bitrateBytesPerSecond);
        if (!rate.isEmpty()) parts.add(rate);
        String fileSize = size(info.sizeBytes);
        if (!fileSize.isEmpty()) parts.add(fileSize);
        return join(parts, " · ");
    }

    /**
     * Códecs de dc:description que la TV no puede decodificar. Un códec sin
     * nombre conocido no se marca: no se inventa un fallo.
     */
    static List<String> unsupportedCodecs(String description, Set<String> supportedMimes) {
        List<String> missing = new ArrayList<>();
        if (description == null || description.isBlank()) return missing;
        for (String part : description.split("·")) {
            String token = part.trim().split(" ")[0];
            for (Map.Entry<String, String> known : MIME_BY_CODEC.entrySet()) {
                if (!known.getKey().equalsIgnoreCase(token)) continue;
                if (!supportedMimes.contains(known.getValue().toLowerCase(Locale.ROOT))) {
                    missing.add(known.getKey());
                }
            }
        }
        return missing;
    }

    /** Tipos MIME que esta TV puede decodificar, en minúsculas. */
    static Set<String> supportedDecoderMimes() {
        Set<String> types = new HashSet<>();
        for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
            if (info.isEncoder()) continue;
            for (String type : info.getSupportedTypes()) {
                types.add(type.toLowerCase(Locale.ROOT));
            }
        }
        return types;
    }

    static String join(List<String> parts, String separator) {
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isEmpty()) continue;
            if (out.length() > 0) out.append(separator);
            out.append(part);
        }
        return out.toString();
    }

    /** Separador usado en dc:description y en las líneas de la interfaz. */
    static String separator() {
        return SEPARATOR;
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value).replace('.', ',');
    }
}
