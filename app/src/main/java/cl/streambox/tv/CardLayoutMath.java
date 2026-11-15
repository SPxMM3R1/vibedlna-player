package cl.streambox.tv;

/**
 * Geometría de la grilla de videos de la Biblioteca (mockup 4 × 3, 1280 × 720 dp): 4 columnas de
 * tarjetas de 228 dp, 24 dp entre columnas, y 3 filas completas bajo el encabezado. Todas las
 * medidas son dp del diseño; {@link TvUi#dp} las escala.
 *
 * <p>Las tarjetas empiezan en x = 248 (la grilla es algo más ancha, con un margen de
 * {@link #GRID_SIDE_PAD_DP} a cada lado, para que la tarjeta con foco crezca sin cortarse). El alto
 * de la grilla es exactamente el de tres filas con su padding superior: la cuarta fila queda fuera.
 *
 * <p>RecyclerView reparte el ancho en celdas iguales; los márgenes de cada celda llevan la
 * tarjeta a su posición exacta, con paso de columna (tarjeta más separación).
 */
final class CardLayoutMath {
    static final int GRID_COLUMNS = 4;
    static final int CARD_WIDTH_DP = 228;
    static final int COLUMN_GAP_DP = 24;
    /** Ancho de las tarjetas más su separación: 4 × 228 + 3 × 24. */
    static final int GRID_WIDTH_DP = GRID_COLUMNS * CARD_WIDTH_DP + (GRID_COLUMNS - 1) * COLUMN_GAP_DP;

    /** Miniatura 16:9 de la tarjeta: 228 × 9 / 16, redondeado. */
    static final int THUMB_HEIGHT_DP = thumbnailHeight(CARD_WIDTH_DP);
    /** Nombre bajo la miniatura: 7 dp de separación y una línea de 15 dp (12,5 sp). */
    static final int NAME_TOP_DP = 7;
    static final int NAME_LINE_DP = 15;
    /** Tamaño bajo el nombre: 1 dp de separación y una línea de 12 dp (10 sp). */
    static final int SIZE_TOP_DP = 1;
    static final int SIZE_LINE_DP = 12;
    /** Tarjeta completa: miniatura, nombre y tamaño. */
    static final int CARD_HEIGHT_DP = THUMB_HEIGHT_DP + NAME_TOP_DP + NAME_LINE_DP + SIZE_TOP_DP + SIZE_LINE_DP;
    /** Separación entre filas (de la tarjeta de arriba a la de abajo). */
    static final int ROW_GAP_DP = 14;
    /** Paso de una fila a la siguiente. */
    static final int ROW_STRIDE_DP = CARD_HEIGHT_DP + ROW_GAP_DP;

    /** La biblioteca: la grilla empieza en x = 248 y y = 168; la pantalla mide 720 dp de alto. */
    static final int GRID_LEFT_DP = 248;
    static final int GRID_TOP_DP = 168;
    static final int SCREEN_HEIGHT_DP = 720;
    /** Margen lateral de la grilla y margen superior, para la tarjeta con foco (crece 6 %, sube 4 dp). */
    static final int GRID_SIDE_PAD_DP = 12;
    static final int GRID_TOP_PAD_DP = 12;
    /** Filas completas que caben: se ven las tres, con nombre y tamaño. */
    static final int VISIBLE_ROWS = 3;

    /** Ancho de la vista de la grilla: el de las tarjetas más el margen a cada lado. */
    static final int GRID_VIEW_WIDTH_DP = GRID_WIDTH_DP + 2 * GRID_SIDE_PAD_DP;
    /** Borde izquierdo de la vista de la grilla. */
    static final int GRID_VIEW_LEFT_DP = GRID_LEFT_DP - GRID_SIDE_PAD_DP;
    /** Alto de la vista: el padding superior y las tres filas, con la separación de cada una. */
    static final int GRID_VIEW_HEIGHT_DP = GRID_TOP_PAD_DP + VISIBLE_ROWS * ROW_STRIDE_DP;

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
