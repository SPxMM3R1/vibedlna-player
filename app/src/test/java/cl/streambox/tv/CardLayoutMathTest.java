package cl.streambox.tv;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class CardLayoutMathTest {
    @Test
    public void calculatesExactSixteenByNineHeightAfterCardPadding() {
        assertEquals(162, CardLayoutMath.thumbnailHeight(294, 6));
        assertEquals(180, CardLayoutMath.thumbnailHeight(326, 6));
    }

    @Test
    public void alwaysReturnsAUsableHeight() {
        assertEquals(1, CardLayoutMath.thumbnailHeight(0, 6));
    }
}
