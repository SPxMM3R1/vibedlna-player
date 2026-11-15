package cl.streambox.tv;

/**
 * Geometría de la grilla de videos de la Biblioteca (mockup 4 × 3, 1280 × 720 dp): 4 columnas de
 * tarjetas de 228 dp, 24 dp entre columnas y 18 dp entre filas, desde x = 248 hasta el margen
 * derecho de 48 dp. Todas las medidas son dp del diseño; {@link TvUi#dp} las escala.
 *
 * <p>RecyclerView reparte el ancho en celdas iguales; los márgenes de cada celda llevan la
 * tarjeta a su posición exacta, con paso de columna (tarjeta + separación).
 */
final class CardLayoutMath {
    static final int GRID_COLUMNS = 4;
    static final int CARD_WIDTH_DP = 228;
    static final int COLUMN_GAP_DP = 24;
    static final int ROW_GAP_DP = 18;
    static final int GRID_WIDTH_DP = GRID_COLUMNS * CARD_WIDTH_DP + (GRID_COLUMNS - 1) * COLUMN_GAP_DP;
    /** Miniatura 16:9 de la tarjeta: 228 × 9 / 16, redondeado. */
    static final int CARD_HEIGHT_DP = thumbnailHeight(CARD_WIDTH_DP);

    private CardLayoutMath() {
    }

    /** Alto de una miniatura 16:9 de {@code width} px (o dp): nunca menos de 1. */
    static int thumbnailHeight(int width) {
        return Math.max(1, Math.round(width * 9f / 16f));
    }

    /** Paso entre el inicio de una columna y el de la siguiente: tarjeta más separación. */
    static int columnPitchDp() {
        return CARD_WIDTH_DP + COLUMN_GAP_DP;
    }

    /** Ancho de la celda que RecyclerView asigna a cada columna. */
    static int cellWidthDp() {
        return GRID_WIDTH_DP / GRID_COLUMNS;
    }

    /** Margen izquierdo de la celda de {@code column}: lleva su tarjeta al paso de columna. */
    static int insetLeftDp(int column) {
        return column * (columnPitchDp() - cellWidthDp());
    }

    /** Margen derecho de la celda de {@code column}: lo que queda tras la tarjeta. */
    static int insetRightDp(int column) {
        return cellWidthDp() - CARD_WIDTH_DP - insetLeftDp(column);
    }

    /** Columna (0 a 3) de la posición {@code index} de la grilla, que llena fila a fila. */
    static int columnOf(int index) {
        return index % GRID_COLUMNS;
    }
}
