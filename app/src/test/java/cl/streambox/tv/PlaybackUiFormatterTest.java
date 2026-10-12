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
}
