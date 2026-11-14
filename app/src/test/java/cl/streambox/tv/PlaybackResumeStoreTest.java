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

    @Test
    public void shortClipsKeepAResumePointBeforeTheEnd() {
        // Un clip de 20 s pausado a los 11 s debe conservarse; al final de 20 s, no.
        assertTrue(PlaybackResumeStore.isUsable(
                new PlaybackResumeStore.ResumePoint(11_000L, 20_000L, 1_000L),
                2_000L
        ));
        assertFalse(PlaybackResumeStore.isUsable(
                new PlaybackResumeStore.ResumePoint(19_500L, 20_000L, 1_000L),
                2_000L
        ));
    }

    @Test
    public void dayRetentionKeepsPointForADayOnly() {
        long savedAt = 1_000_000L;
        PlaybackResumeStore.ResumePoint point =
                new PlaybackResumeStore.ResumePoint(60_000L, 600_000L, savedAt);
        long day = 24L * 60L * 60L * 1_000L;

        assertTrue(PlaybackResumeStore.isUsable(point, savedAt + day - 1L, day));
        assertFalse(PlaybackResumeStore.isUsable(point, savedAt + day, day));
    }

    @Test
    public void foreverRetentionNeverExpires() {
        long savedAt = 1_000_000L;
        PlaybackResumeStore.ResumePoint point =
                new PlaybackResumeStore.ResumePoint(60_000L, 600_000L, savedAt);
        long fourYears = 4L * 365L * 24L * 60L * 60L * 1_000L;

        assertTrue(PlaybackResumeStore.isUsable(point, savedAt + fourYears, Long.MAX_VALUE));
    }
}
