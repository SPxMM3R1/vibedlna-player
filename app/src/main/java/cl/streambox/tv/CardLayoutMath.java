package cl.streambox.tv;

final class CardLayoutMath {
    private CardLayoutMath() {
    }

    static int thumbnailHeight(int outerCardWidth, int horizontalCardPadding) {
        int contentWidth = Math.max(1, outerCardWidth - horizontalCardPadding);
        return Math.max(1, Math.round(contentWidth * 9f / 16f));
    }
}
