package cl.streambox.tv;

final class CardLayoutMath {
    private CardLayoutMath() {
    }

    static int cardWidth(
            int parentWidth,
            int paddingStart,
            int paddingEnd,
            int itemSpacing,
            int columns,
            int minimumWidth
    ) {
        if (columns <= 0) return Math.max(1, minimumWidth);
        int contentWidth = parentWidth - paddingStart - paddingEnd;
        int totalSpacing = itemSpacing * 2 * columns;
        int calculated = (contentWidth - totalSpacing) / columns;
        return Math.max(1, Math.max(minimumWidth, calculated));
    }

    static int thumbnailHeight(int outerCardWidth, int horizontalCardPadding) {
        int contentWidth = Math.max(1, outerCardWidth - horizontalCardPadding);
        return Math.max(1, Math.round(contentWidth * 9f / 16f));
    }
}
