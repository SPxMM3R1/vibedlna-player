package cl.streambox.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

public final class DlnaMediaFormatTest {
    @Test
    public void qualityLabelFollowsHeightThresholds() {
        assertEquals("4K", DlnaMediaFormat.qualityLabel(2160));
        assertEquals("4K", DlnaMediaFormat.qualityLabel(2000));
        assertEquals("1080p", DlnaMediaFormat.qualityLabel(1080));
        assertEquals("1080p", DlnaMediaFormat.qualityLabel(1000));
        assertEquals("720p", DlnaMediaFormat.qualityLabel(720));
        assertEquals("576p", DlnaMediaFormat.qualityLabel(576));
        assertEquals("", DlnaMediaFormat.qualityLabel(0));
    }

    @Test
    public void readsHeightFromResolution() {
        assertEquals(2160, DlnaMediaFormat.heightOf("3840x2160"));
        assertEquals(0, DlnaMediaFormat.heightOf("desconocida"));
        assertEquals(0, DlnaMediaFormat.heightOf(null));
    }

    @Test
    public void formatsDurationsWithAndWithoutHours() {
        assertEquals("52:10", DlnaMediaFormat.duration(3_130_000L));
        assertEquals("2:46:00", DlnaMediaFormat.duration(9_960_000L));
        assertEquals("0:05", DlnaMediaFormat.duration(5_000L));
    }

    @Test
    public void formatsRemainingTimeInHoursOrMinutes() {
        assertEquals("1 h 33 min", DlnaMediaFormat.remaining(5_580_000L));
        assertEquals("45 min", DlnaMediaFormat.remaining(2_700_000L));
        assertEquals("17 s", DlnaMediaFormat.remaining(17_000L));
        assertEquals("0 s", DlnaMediaFormat.remaining(0L));
    }

    @Test
    public void formatsSizeAndBitrateWithComma() {
        // 2 275 000 bytes/s equivalen a 18,2 Mbps (contrato §A: bitrate en bytes/s).
        assertEquals("18,2 Mbps", DlnaMediaFormat.mbps(2_275_000L));
        assertEquals("22,6 GB", DlnaMediaFormat.size(24_270_000_000L));
        assertEquals("", DlnaMediaFormat.size(0L));
        assertEquals("", DlnaMediaFormat.mbps(0L));
    }

    @Test
    public void takesYearOnlyFromAFourDigitDate() {
        assertEquals("2024", DlnaMediaFormat.year("2024-05-01"));
        assertEquals("", DlnaMediaFormat.year("ayer"));
        assertEquals("", DlnaMediaFormat.year(""));
    }

    @Test
    public void progressLabelShowsPercentAndRemaining() {
        assertEquals("Viste el 44 % · quedan 1 h 33 min",
                DlnaMediaFormat.progressLabel(4_380_000L, 9_960_000L));
        assertEquals(44, DlnaMediaFormat.percent(4_380_000L, 9_960_000L));
        assertEquals("Visto hasta 1:13:00", DlnaMediaFormat.progressLabel(4_380_000L, 0L));
    }

    @Test
    public void mediaLineKeepsOnlyKnownParts() {
        MediaInfo info = new MediaInfo("3840x2160", 2_275_000L, 6L,
                24_270_000_000L, "2024", "HEVC · E-AC3 5.1");
        assertEquals("4K · HEVC · E-AC3 5.1 · 18,2 Mbps · 22,6 GB",
                DlnaMediaFormat.mediaLine(info));
        assertEquals("", DlnaMediaFormat.mediaLine(MediaInfo.NONE));
    }

    @Test
    public void reportsUnsupportedCodecsOnly() {
        Set<String> decoders = new HashSet<>(Arrays.asList(
                "video/hevc", "video/avc", "audio/eac3", "audio/mp4a-latm"));
        assertTrue(DlnaMediaFormat.unsupportedCodecs("HEVC · E-AC3 5.1", decoders).isEmpty());
        assertEquals(Collections.singletonList("DTS"),
                DlnaMediaFormat.unsupportedCodecs("H.264 · DTS 5.1", decoders));
        assertTrue(DlnaMediaFormat.unsupportedCodecs("", decoders).isEmpty());
    }

    @Test
    public void percentStaysInsideZeroAndHundred() {
        assertEquals(0, DlnaMediaFormat.percent(-5L, 1000L));
        assertEquals(100, DlnaMediaFormat.percent(5000L, 1000L));
    }
}
