package cl.streambox.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class CardLayoutMathTest {
    @Test
    public void calculatesCardWidthInsideRecyclerViewContent() {
        assertEquals(451, CardLayoutMath.cardWidth(1920, 30, 30, 7, 4));
    }

    @Test
    public void neverOverflowsFourColumnsWhenWidthIsSmall() {
        assertEquals(234, CardLayoutMath.cardWidth(1280, 60, 60, 28, 4));
    }

    @Test
    public void choosesHorizontalSpacingThatFitsThreeRows() {
        int spacing = CardLayoutMath.horizontalSpacingForThreeRows(
                1280,
                720,
                45,
                84,
                45,
                33,
                4,
                3,
                11,
                5,
                9,
                9,
                45
        );
        int width = CardLayoutMath.cardWidth(1280, 45, 45, spacing, 4);
        int rowStride = CardLayoutMath.videoCardHeight(width, 9, 9, 45) + 10;
        assertEquals(22, spacing);
        assertTrue(rowStride * 3 <= 603);
        int previousWidth = CardLayoutMath.cardWidth(1280, 45, 45, spacing - 1, 4);
        int previousStride = CardLayoutMath.videoCardHeight(previousWidth, 9, 9, 45) + 10;
        assertTrue(previousStride * 3 > 603);
    }

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
