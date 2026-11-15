package cl.streambox.tv;

/**
 * Cálculos de la barra ondulada Material 3 (sin Android, para probarlos en la JVM): cuánta
 * amplitud tiene la onda en cada instante, dónde está el corte entre lo visto y lo que falta,
 * y si la vista necesita seguir dibujando cuadros.
 *
 * <p>Medidas en dp del diseño (mockup 1280 × 720). {@link WavyProgressBar} las convierte a px.
 */
final class WavyProgressMath {
    /** Amplitud de la onda cuando se reproduce. */
    static final float WAVE_AMPLITUDE_DP = 3f;
    /** Grosor de la onda, de la línea que falta y del punto final. */
    static final float STROKE_DP = 2.5f;
    /** Corte entre la parte vista y la que falta. */
    static final float CUT_GAP_DP = 7f;
    /** Ondas completas a lo ancho de la barra. */
    static final float WAVES_ACROSS = 18f;
    /** Tiempo para que la onda se aplane (pausa) o vuelva a su amplitud (reproducir). */
    static final float TRANSITION_MS = 300f;
    /** Velocidad con que la onda se desplaza mientras reproduce, en radianes por segundo. */
    static final float PHASE_RAD_PER_SECOND = 6f;

    private WavyProgressMath() {
    }

    /** Fracción visible de {@code value}: entre 0 y 1. */
    static float clampFraction(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    /**
     * Amplitud relativa (0 a 1) tras {@code elapsedMs}: sube a 1 al reproducir y baja a 0 en
     * pausa, en {@link #TRANSITION_MS}, a ritmo constante.
     */
    static float amplitudeAfter(float current, boolean playing, long elapsedMs) {
        float target = playing ? 1f : 0f;
        float step = Math.max(0L, elapsedMs) / TRANSITION_MS;
        if (current < target) return Math.min(target, current + step);
        return Math.max(target, current - step);
    }

    /** Desplazamiento de la onda (en px) de la coordenada {@code x} con la fase dada. */
    static float waveOffset(float x, float width, float phase, float amplitudePx) {
        if (width <= 0f) return 0f;
        double angle = 2d * Math.PI * WAVES_ACROSS * x / width - phase;
        return amplitudePx * (float) Math.sin(angle);
    }

    /** Fase de la onda tras {@code elapsedMs}; solo avanza mientras se reproduce. */
    static float advancePhase(float phase, boolean playing, long elapsedMs) {
        if (!playing) return phase;
        return phase + PHASE_RAD_PER_SECOND * Math.max(0L, elapsedMs) / 1_000f;
    }

    /**
     * Fin visible de la parte vista (px): el corte queda {@code gapPx / 2} antes del punto de
     * avance. Sin nada visto no hay corte, y la parte vista queda vacía.
     */
    static float playedEnd(float width, float fraction, float gapPx) {
        return Math.max(0f, clampFraction(fraction) * width - gapPx / 2f);
    }

    /**
     * Comienzo visible de la parte que falta (px): {@code gapPx / 2} después del avance. Sin nada
     * visto empieza en 0, sin corte; al final de la barra queda en el borde.
     */
    static float remainingStart(float width, float fraction, float gapPx) {
        float center = clampFraction(fraction) * width;
        if (center - gapPx / 2f <= 0f) return center;
        return Math.min(width, center + gapPx / 2f);
    }

    /**
     * Si la vista debe dibujar otro cuadro: mientras se reproduce (la onda se mueve) o mientras
     * la onda aún se está aplanando. En pausa ya plana, no hay nada que animar.
     */
    static boolean needsFrame(float amplitude, boolean playing) {
        return playing || amplitude > 0f;
    }
}
