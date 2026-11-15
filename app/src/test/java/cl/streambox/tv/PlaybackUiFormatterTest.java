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
    public void audioTrackLabelJoinsLanguageCodecAndChannels() {
        assertEquals("Original · AAC · Estéreo",
                PlaybackUiFormatter.audioTrackLabel("und", "AAC", 2));
        assertEquals("spa · E-AC-3 · 5.1",
                PlaybackUiFormatter.audioTrackLabel("spa", "E-AC-3", 6));
        assertEquals("Original · Mono",
                PlaybackUiFormatter.audioTrackLabel(null, "—", 1));
    }
}
