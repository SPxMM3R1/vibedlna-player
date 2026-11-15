package cl.streambox.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class CardLayoutMathTest {
    @Test
    public void gridIsFourColumnsOfCardsWithDesignGaps() {
        assertEquals(4, CardLayoutMath.GRID_COLUMNS);
        assertEquals(228, CardLayoutMath.CARD_WIDTH_DP);
        assertEquals(24, CardLayoutMath.COLUMN_GAP_DP);
        assertEquals(984, CardLayoutMath.GRID_WIDTH_DP);
        assertEquals(252, CardLayoutMath.columnPitchDp());
    }

    @Test
    public void cardsStartOnTheColumnPitchInsideTheGrid() {
        for (int column = 0; column < CardLayoutMath.GRID_COLUMNS; column++) {
            int cellStart = column * CardLayoutMath.cellWidthDp();
            int cardLeft = cellStart + CardLayoutMath.insetLeftDp(column);
            assertEquals(252 * column, cardLeft);
        }
    }

    @Test
    public void insetsFillEachCellAroundItsCard() {
        for (int column = 0; column < CardLayoutMath.GRID_COLUMNS; column++) {
            int inside = CardLayoutMath.insetLeftDp(column)
                    + CardLayoutMath.CARD_WIDTH_DP
                    + CardLayoutMath.insetRightDp(column);
            assertEquals(CardLayoutMath.cellWidthDp(), inside);
        }
        assertEquals(0, CardLayoutMath.insetLeftDp(0));
        assertEquals(0, CardLayoutMath.insetRightDp(3));
    }

    @Test
    public void lastColumnEndsExactlyAtTheRightEdgeOfTheGrid() {
        int lastLeft = 3 * CardLayoutMath.columnPitchDp();
        assertEquals(CardLayoutMath.GRID_WIDTH_DP, lastLeft + CardLayoutMath.CARD_WIDTH_DP);
    }

    @Test
    public void gridStartsAtTheMockupPositionWithRoomForFocusGrowth() {
        // La tarjeta empieza en x = 248; la vista de la grilla empieza antes, con margen para
        // que la tarjeta con foco (crece 6 % a cada lado) no se corte a la izquierda.
        assertEquals(248, CardLayoutMath.GRID_LEFT_DP);
        assertEquals(236, CardLayoutMath.GRID_VIEW_LEFT_DP);
        assertEquals(CardLayoutMath.GRID_LEFT_DP, CardLayoutMath.GRID_VIEW_LEFT_DP
                + CardLayoutMath.GRID_SIDE_PAD_DP);
        assertEquals(CardLayoutMath.GRID_WIDTH_DP + 24, CardLayoutMath.GRID_VIEW_WIDTH_DP);
        assertTrue(CardLayoutMath.GRID_SIDE_PAD_DP >= 12);
    }

    @Test
    public void thumbnailIsSixteenByNineOfTheCardWidth() {
        assertEquals(128, CardLayoutMath.THUMB_HEIGHT_DP);
        assertEquals(128, CardLayoutMath.thumbnailHeight(228));
        assertEquals(1, CardLayoutMath.thumbnailHeight(0));
    }

    @Test
    public void cardIncludesNameAndSizeBelowTheThumbnail() {
        assertEquals(163, CardLayoutMath.CARD_HEIGHT_DP);
        assertEquals(177, CardLayoutMath.ROW_STRIDE_DP);
    }

    @Test
    public void threeFullRowsFitBelowTheHeaderWithPadding() {
        int available = CardLayoutMath.SCREEN_HEIGHT_DP - CardLayoutMath.GRID_TOP_DP;
        int rows = 3 * (CardLayoutMath.THUMB_HEIGHT_DP + CardLayoutMath.NAME_TOP_DP
                + CardLayoutMath.NAME_LINE_DP + CardLayoutMath.SIZE_TOP_DP
                + CardLayoutMath.SIZE_LINE_DP + CardLayoutMath.ROW_GAP_DP);
        assertEquals(531, rows);
        assertTrue(rows + CardLayoutMath.GRID_TOP_PAD_DP <= available);
        assertEquals(543, CardLayoutMath.GRID_VIEW_HEIGHT_DP);
        assertTrue(CardLayoutMath.GRID_VIEW_HEIGHT_DP <= available);
    }

    @Test
    public void lastVisibleRowEndsInsideTheViewAndTheFourthRowStaysOut() {
        int pad = CardLayoutMath.GRID_TOP_PAD_DP;
        int stride = CardLayoutMath.ROW_STRIDE_DP;
        int thirdRowBottom = pad + 2 * stride + CardLayoutMath.CARD_HEIGHT_DP;
        assertTrue(thirdRowBottom + CardLayoutMath.ROW_GAP_DP <= CardLayoutMath.GRID_VIEW_HEIGHT_DP);
        int fourthRowTop = pad + 3 * stride;
        assertTrue(fourthRowTop >= CardLayoutMath.GRID_VIEW_HEIGHT_DP);
    }

    @Test
    public void columnFillsRowByRow() {
        assertEquals(0, CardLayoutMath.columnOf(0));
        assertEquals(3, CardLayoutMath.columnOf(3));
        assertEquals(0, CardLayoutMath.columnOf(4));
        assertEquals(2, CardLayoutMath.columnOf(10));
    }
}
