package cl.streambox.tv;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class PlaybackResumeStoreTest {
    @Test
    public void pointIsUsableUntilTheSixtyMinuteBoundary() {
        long savedAt = 1_000_000L;
        PlaybackResumeStore.ResumePoint point =
                new PlaybackResumeStore.ResumePoint(60_000L, 600_000L, savedAt);

        assertTrue(PlaybackResumeStore.isUsable(
                point,
                savedAt + PlaybackResumeStore.RETENTION_MS - 1L
        ));
        assertFalse(PlaybackResumeStore.isUsable(
                point,
                savedAt + PlaybackResumeStore.RETENTION_MS
        ));
    }

    @Test
    public void completedOrTinyPointsAreNotResumed() {
        assertFalse(PlaybackResumeStore.isUsable(
                new PlaybackResumeStore.ResumePoint(2_000L, 600_000L, 1_000L),
                2_000L
        ));
        assertFalse(PlaybackResumeStore.isUsable(
                new PlaybackResumeStore.ResumePoint(580_000L, 600_000L, 1_000L),
                2_000L
        ));
    }
}
