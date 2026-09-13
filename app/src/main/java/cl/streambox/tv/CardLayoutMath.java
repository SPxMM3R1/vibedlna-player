package cl.streambox.tv;

final class CardLayoutMath {
    static final int GRID_COLUMNS = 4;
    static final int GRID_VISIBLE_ROWS = 3;
    static final int MIN_HORIZONTAL_SPACING_DP = 7;
    static final int VERTICAL_SPACING_DP = 3;
    static final int CARD_PADDING_DP = 3;
    static final int VIDEO_TITLE_HEIGHT_DP = 30;

    private CardLayoutMath() {
    }

    static int cardWidth(
            int parentWidth,
            int paddingStart,
            int paddingEnd,
            int itemSpacing,
            int columns
    ) {
        if (columns <= 0) return 1;
        int contentWidth = parentWidth - paddingStart - paddingEnd;
        int totalSpacing = itemSpacing * 2 * columns;
        int calculated = (contentWidth - totalSpacing) / columns;
        // A fixed span count must never overflow the RecyclerView. Overflow
        // cuts the last column and makes every card taller than necessary.
        return Math.max(1, calculated);
    }

    static int thumbnailHeight(int outerCardWidth, int horizontalCardPadding) {
        int contentWidth = Math.max(1, outerCardWidth - horizontalCardPadding);
        return Math.max(1, Math.round(contentWidth * 9f / 16f));
    }

    static int videoCardHeight(
            int outerCardWidth,
            int cardHorizontalPadding,
            int cardVerticalPadding,
            int titleHeight
    ) {
        return thumbnailHeight(outerCardWidth, cardHorizontalPadding)
                + cardVerticalPadding
                + titleHeight;
    }

    static int horizontalSpacingForThreeRows(
            int parentWidth,
            int parentHeight,
            int paddingStart,
            int paddingTop,
            int paddingEnd,
            int paddingBottom,
            int columns,
            int visibleRows,
            int minimumHorizontalSpacing,
            int verticalSpacing,
            int cardHorizontalPadding,
            int cardVerticalPadding,
            int titleHeight
    ) {
        int minimumSpacing = Math.max(0, minimumHorizontalSpacing);
        if (columns <= 0 || visibleRows <= 0 || parentWidth <= 0 || parentHeight <= 0) {
            return minimumSpacing;
        }

        int contentWidth = Math.max(1, parentWidth - paddingStart - paddingEnd);
        int availableHeight = Math.max(1, parentHeight - paddingTop - paddingBottom);
        int maxSpacing = Math.max(
                minimumSpacing,
                (contentWidth - columns) / (2 * columns)
        );
        int bestSpacing = minimumSpacing;
        int smallestSlack = Integer.MAX_VALUE;

        for (int spacing = minimumSpacing; spacing <= maxSpacing; spacing++) {
            int width = cardWidth(
                    parentWidth,
                    paddingStart,
                    paddingEnd,
                    spacing,
                    columns
            );
            int rowStride = videoCardHeight(
                    width,
                    cardHorizontalPadding,
                    cardVerticalPadding,
                    titleHeight
            ) + (verticalSpacing * 2);
            int totalRowsHeight = rowStride * visibleRows;
            if (totalRowsHeight <= availableHeight) {
                int slack = availableHeight - totalRowsHeight;
                if (slack < smallestSlack) {
                    bestSpacing = spacing;
                    smallestSlack = slack;
                }
            }
        }
        return bestSpacing;
    }
}
