package cl.streambox.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class WavyProgressMathTest {
    private static final float DELTA = 0.001f;

    @Test
    public void amplitudeRisesToFullInThreeHundredMilliseconds() {
        assertEquals(0.5f, WavyProgressMath.amplitudeAfter(0f, true, 150L), DELTA);
        assertEquals(1f, WavyProgressMath.amplitudeAfter(0f, true, 300L), DELTA);
        assertEquals(1f, WavyProgressMath.amplitudeAfter(0.9f, true, 500L), DELTA);
    }

    @Test
    public void amplitudeFlattensToZeroInThreeHundredMilliseconds() {
        assertEquals(0.5f, WavyProgressMath.amplitudeAfter(1f, false, 150L), DELTA);
        assertEquals(0f, WavyProgressMath.amplitudeAfter(1f, false, 300L), DELTA);
        assertEquals(0f, WavyProgressMath.amplitudeAfter(0.2f, false, 1_000L), DELTA);
    }

    @Test
    public void amplitudeKeepsItsValueWhenNoTimePasses() {
        assertEquals(0.4f, WavyProgressMath.amplitudeAfter(0.4f, true, 0L), DELTA);
        assertEquals(0.4f, WavyProgressMath.amplitudeAfter(0.4f, false, -5L), DELTA);
    }

    @Test
    public void cutSeparatesPlayedFromRemainingBySevenDp() {
        float width = 300f;
        float gap = 7f;
        assertEquals(146.5f, WavyProgressMath.playedEnd(width, 0.5f, gap), DELTA);
        assertEquals(153.5f, WavyProgressMath.remainingStart(width, 0.5f, gap), DELTA);
        assertEquals(153.5f - 146.5f, gap, DELTA);
    }

    @Test
    public void nothingPlayedMeansNoCutAtTheStart() {
        assertEquals(0f, WavyProgressMath.playedEnd(300f, 0f, 7f), DELTA);
        assertEquals(0f, WavyProgressMath.remainingStart(300f, 0f, 7f), DELTA);
    }

    @Test
    public void fullyPlayedLeavesNoRemainingPart() {
        assertEquals(296.5f, WavyProgressMath.playedEnd(300f, 1f, 7f), DELTA);
        assertEquals(300f, WavyProgressMath.remainingStart(300f, 1f, 7f), DELTA);
    }

    @Test
    public void fractionOutsideZeroToOneIsClamped() {
        assertEquals(WavyProgressMath.playedEnd(300f, 1f, 7f),
                WavyProgressMath.playedEnd(300f, 3f, 7f), DELTA);
        assertEquals(0f, WavyProgressMath.playedEnd(300f, -1f, 7f), DELTA);
    }

    @Test
    public void waveReachesItsAmplitudeAtAQuarterWave() {
        float width = 300f;
        float quarterWave = width / (4f * WavyProgressMath.WAVES_ACROSS);
        assertEquals(3f, WavyProgressMath.waveOffset(quarterWave, width, 0f, 3f), DELTA);
        assertEquals(0f, WavyProgressMath.waveOffset(0f, width, 0f, 3f), DELTA);
        assertEquals(0f, WavyProgressMath.waveOffset(width / WavyProgressMath.WAVES_ACROSS,
                width, 0f, 3f), DELTA);
    }

    @Test
    public void flatWaveWhenAmplitudeIsZero() {
        assertEquals(0f, WavyProgressMath.waveOffset(37f, 300f, 1.3f, 0f), DELTA);
    }

    @Test
    public void phaseMovesOnlyWhilePlaying() {
        assertEquals(6f, WavyProgressMath.advancePhase(0f, true, 1_000L), DELTA);
        assertEquals(0f, WavyProgressMath.advancePhase(0f, false, 1_000L), DELTA);
    }

    @Test
    public void framesAreNeededWhilePlayingOrStillFlattening() {
        assertTrue(WavyProgressMath.needsFrame(0f, true));
        assertTrue(WavyProgressMath.needsFrame(0.5f, false));
        assertFalse(WavyProgressMath.needsFrame(0f, false));
    }
}
