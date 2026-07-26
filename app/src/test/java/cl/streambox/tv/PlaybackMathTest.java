package cl.streambox.tv;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class PlaybackMathTest {
    @Test
    public void seekNeverLeavesMediaBounds() {
        assertEquals(0L, PlaybackMath.clampSeekPosition(5_000L, -10_000L, 60_000L));
        assertEquals(60_000L, PlaybackMath.clampSeekPosition(55_000L, 10_000L, 60_000L));
        assertEquals(25_000L, PlaybackMath.clampSeekPosition(15_000L, 10_000L, 60_000L));
    }

    @Test
    public void progressUsesRequestedScale() {
        assertEquals(0, PlaybackMath.progress(10_000L, 0L, 1_000));
        assertEquals(500, PlaybackMath.progress(30_000L, 60_000L, 1_000));
        assertEquals(1_000, PlaybackMath.progress(90_000L, 60_000L, 1_000));
    }
}
