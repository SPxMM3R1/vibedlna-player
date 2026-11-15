package cl.streambox.tv;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class CardLayoutMathTest {
    @Test
    public void gridIsFourColumnsOfCardsWithDesignGaps() {
        assertEquals(4, CardLayoutMath.GRID_COLUMNS);
        assertEquals(228, CardLayoutMath.CARD_WIDTH_DP);
        assertEquals(24, CardLayoutMath.COLUMN_GAP_DP);
        assertEquals(18, CardLayoutMath.ROW_GAP_DP);
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
    public void thumbnailIsSixteenByNineOfTheCardWidth() {
        assertEquals(128, CardLayoutMath.CARD_HEIGHT_DP);
        assertEquals(128, CardLayoutMath.thumbnailHeight(228));
        assertEquals(1, CardLayoutMath.thumbnailHeight(0));
    }

    @Test
    public void columnFillsRowByRow() {
        assertEquals(0, CardLayoutMath.columnOf(0));
        assertEquals(3, CardLayoutMath.columnOf(3));
        assertEquals(0, CardLayoutMath.columnOf(4));
        assertEquals(2, CardLayoutMath.columnOf(10));
    }
}
