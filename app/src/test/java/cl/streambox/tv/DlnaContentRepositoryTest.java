package cl.streambox.tv;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class DlnaContentRepositoryTest {
    @Test
    public void parsesDlnaDuration() {
        assertEquals(
                3_723_000L,
                DlnaContentRepository.durationMillis("01:02:03.500")
        );
    }

    @Test
    public void rejectsInvalidDuration() {
        assertEquals(0L, DlnaContentRepository.durationMillis(""));
        assertEquals(0L, DlnaContentRepository.durationMillis("12:34"));
        assertEquals(0L, DlnaContentRepository.durationMillis("desconocida"));
    }
}
