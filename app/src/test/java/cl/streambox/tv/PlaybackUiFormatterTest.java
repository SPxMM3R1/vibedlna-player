package cl.streambox.tv;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Locale;
import java.util.TimeZone;

public final class PlaybackUiFormatterTest {
    @Test
    public void keepsAccumulatedMinutesForLongVideos() {
        assertEquals("75:20", PlaybackUiFormatter.elapsed(4_520_000L));
        assertEquals(
                "75:20 / 102:10",
                PlaybackUiFormatter.positionAndDuration(4_520_000L, 6_130_000L)
        );
    }

    @Test
    public void calculatesWallClockEndingTime() {
        Locale locale = Locale.forLanguageTag("es-CL");
        TimeZone previous = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            assertEquals(
                    "Terminar\u00e1 a las 22:30",
                    PlaybackUiFormatter.endingAt(
                            21L * 3_600_000L,
                            0L,
                            90L * 60_000L,
                            locale
                    )
            );
        } finally {
            TimeZone.setDefault(previous);
        }
    }

    @Test
    public void usesFriendlyCodecNames() {
        assertEquals("H.264", PlaybackUiFormatter.friendlyCodec("video/avc", "avc1.640028"));
        assertEquals("H.265", PlaybackUiFormatter.friendlyCodec("video/hevc", "hvc1"));
        assertEquals("AAC", PlaybackUiFormatter.friendlyCodec("audio/mp4a-latm", "mp4a.40.2"));
        assertEquals("E-AC-3", PlaybackUiFormatter.friendlyCodec("audio/eac3", null));
    }

    @Test
    public void undeterminedOrEmptyAudioLanguageIsOriginal() {
        assertEquals("Original", PlaybackUiFormatter.audioLanguage("und"));
        assertEquals("Original", PlaybackUiFormatter.audioLanguage("UND"));
        assertEquals("Original", PlaybackUiFormatter.audioLanguage(""));
        assertEquals("Original", PlaybackUiFormatter.audioLanguage("   "));
        assertEquals("Original", PlaybackUiFormatter.audioLanguage(null));
        assertEquals("spa", PlaybackUiFormatter.audioLanguage("spa"));
    }

    @Test
    public void audioChannelsUseWordsForMonoAndStereo() {
        assertEquals("Mono", PlaybackUiFormatter.audioChannels(1));
        assertEquals("Estéreo", PlaybackUiFormatter.audioChannels(2));
        assertEquals("5.1", PlaybackUiFormatter.audioChannels(6));
        assertEquals("7.1", PlaybackUiFormatter.audioChannels(8));
        assertEquals("3 ch", PlaybackUiFormatter.audioChannels(3));
        assertEquals("", PlaybackUiFormatter.audioChannels(0));
    }

    @Test
    public void networkAndHttpErrorsSayTheConnectionFailed() {
        String connection = "No se pudo conectar con el PC";
        assertEquals(connection, PlaybackUiFormatter.playbackErrorMessage(
                "ERROR_CODE_IO_NETWORK_CONNECTION_FAILED", -1));
        assertEquals(connection, PlaybackUiFormatter.playbackErrorMessage(
                "ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT", -1));
        assertEquals(connection, PlaybackUiFormatter.playbackErrorMessage(
                "ERROR_CODE_IO_BAD_HTTP_STATUS", 500));
    }

    @Test
    public void missingFileSaysTheFileIsGone() {
        String gone = "El archivo ya no está en el PC";
        assertEquals(gone, PlaybackUiFormatter.playbackErrorMessage(
                "ERROR_CODE_IO_FILE_NOT_FOUND", -1));
        assertEquals(gone, PlaybackUiFormatter.playbackErrorMessage(
                "ERROR_CODE_IO_BAD_HTTP_STATUS", 404));
    }

    @Test
    public void parsingErrorsSayTheFileIsDamaged() {
        assertEquals("Este archivo está dañado o incompleto",
                PlaybackUiFormatter.playbackErrorMessage(
                        "ERROR_CODE_PARSING_CONTAINER_MALFORMED", -1));
    }

    @Test
    public void decoderErrorsSayTheFormatIsNotSupported() {
        String format = "La TV no puede reproducir este formato";
        assertEquals(format, PlaybackUiFormatter.playbackErrorMessage(
                "ERROR_CODE_DECODER_INIT_FAILED", -1));
        assertEquals(format, PlaybackUiFormatter.playbackErrorMessage(
                "ERROR_CODE_DECODING_FORMAT_UNSUPPORTED", -1));
    }

    @Test
    public void anyOtherErrorGetsTheGenericMessage() {
        String generic = "No se pudo reproducir este video";
        assertEquals(generic, PlaybackUiFormatter.playbackErrorMessage(
                "ERROR_CODE_UNSPECIFIED", -1));
        assertEquals(generic, PlaybackUiFormatter.playbackErrorMessage(null, -1));
    }

    @Test
    public void audioTrackLabelJoinsLanguageCodecAndChannels() {
        assertEquals("Original · AAC · Estéreo",
                PlaybackUiFormatter.audioTrackLabel("und", "AAC", 2));
        assertEquals("spa · E-AC-3 · 5.1",
                PlaybackUiFormatter.audioTrackLabel("spa", "E-AC-3", 6));
        assertEquals("Original · Mono",
                PlaybackUiFormatter.audioTrackLabel(null, "—", 1));
    }
}
