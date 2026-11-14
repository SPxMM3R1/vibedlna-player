package cl.streambox.tv;

/**
 * Datos técnicos de un video que el servidor anuncia (contrato §7 A). Cada campo
 * puede faltar: un valor vacío o cero significa «no informado».
 */
final class MediaInfo {
    static final MediaInfo NONE = new MediaInfo("", 0L, 0L, 0L, "", "");

    /** «3840x2160», o vacío. */
    final String resolution;
    /** Bytes por segundo (res@bitrate). */
    final long bitrateBytesPerSecond;
    /** Canales de audio (res@nrAudioChannels). */
    final long audioChannels;
    /** Tamaño del archivo en bytes (res@size). */
    final long sizeBytes;
    /** Fecha de dc:date tal como llega, o vacío. */
    final String date;
    /** Texto legible de dc:description, p. ej. «HEVC · E-AC3 5.1». */
    final String codecs;

    MediaInfo(
            String resolution,
            long bitrateBytesPerSecond,
            long audioChannels,
            long sizeBytes,
            String date,
            String codecs
    ) {
        this.resolution = resolution == null ? "" : resolution;
        this.bitrateBytesPerSecond = Math.max(0L, bitrateBytesPerSecond);
        this.audioChannels = Math.max(0L, audioChannels);
        this.sizeBytes = Math.max(0L, sizeBytes);
        this.date = date == null ? "" : date;
        this.codecs = codecs == null ? "" : codecs;
    }

    /** Alto en píxeles, o 0 si no se conoce. */
    int height() {
        return DlnaMediaFormat.heightOf(resolution);
    }
}
