package cl.streambox.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

/**
 * Barra de avance ondulada Material 3, determinada: la parte vista es una onda cian que se
 * desplaza mientras se reproduce; la que falta, una línea recta blanca al 28 %; entre las dos,
 * un corte de 7 dp; y al final, un punto blanco al 55 % del mismo grosor.
 *
 * <p>En pausa la amplitud baja a 0 en unos 300 ms y vuelve igual al reanudar. Solo dibuja
 * cuadros nuevos mientras está visible y hace falta (reproduciendo, o aplanándose).
 */
public final class WavyProgressBar extends View {
    private static final int WAVE_COLOR = TvUi.NOW;
    private static final int REMAINING_COLOR = 0x47FFFFFF;
    private static final int END_COLOR = 0x8CFFFFFF;
    /** Tope del tiempo entre cuadros: tras volver a mostrarla no salta de golpe. */
    private static final long MAX_FRAME_MS = 100L;

    private final Paint wavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint remainingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint endPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    private float fraction;
    private boolean playing;
    private float amplitude;
    private float phase;
    private long lastFrameMs = -1L;

    public WavyProgressBar(Context context) {
        this(context, null);
    }

    public WavyProgressBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        float stroke = dp(WavyProgressMath.STROKE_DP);
        wavePaint.setStyle(Paint.Style.STROKE);
        wavePaint.setStrokeCap(Paint.Cap.ROUND);
        wavePaint.setStrokeJoin(Paint.Join.ROUND);
        wavePaint.setStrokeWidth(stroke);
        wavePaint.setColor(WAVE_COLOR);
        remainingPaint.setStyle(Paint.Style.STROKE);
        remainingPaint.setStrokeCap(Paint.Cap.ROUND);
        remainingPaint.setStrokeWidth(stroke);
        remainingPaint.setColor(REMAINING_COLOR);
        endPaint.setStyle(Paint.Style.FILL);
        endPaint.setColor(END_COLOR);
    }

    /** Fija cuánto se ha visto: la posición y la duración del video. */
    public void setProgress(long positionMs, long durationMs) {
        float value = durationMs > 0L
                ? (float) Math.max(0L, positionMs) / (float) durationMs
                : 0f;
        fraction = WavyProgressMath.clampFraction(value);
        invalidate();
    }

    /** Reproduciendo, la onda tiene su amplitud y se mueve; en pausa se aplana. */
    public void setPlaying(boolean value) {
        if (playing == value) return;
        playing = value;
        lastFrameMs = -1L;
        invalidate();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        // Al volver a mostrarla el reloj empieza de nuevo: no salta por el tiempo oculto.
        lastFrameMs = -1L;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        if (width <= 0f || height <= 0f) return;

        long now = SystemClock.uptimeMillis();
        long elapsed = lastFrameMs < 0L ? 0L : Math.min(MAX_FRAME_MS, now - lastFrameMs);
        lastFrameMs = now;
        amplitude = WavyProgressMath.amplitudeAfter(amplitude, playing, elapsed);
        phase = WavyProgressMath.advancePhase(phase, playing, elapsed);

        float middle = height / 2f;
        float cap = wavePaint.getStrokeWidth() / 2f;
        float gap = dp(WavyProgressMath.CUT_GAP_DP);
        float amplitudePx = dp(WavyProgressMath.WAVE_AMPLITUDE_DP) * amplitude;

        // Parte vista: onda desde el inicio hasta el corte. La tapa redonda no pasa del corte.
        float playedEnd = WavyProgressMath.playedEnd(width, fraction, gap);
        float waveEnd = playedEnd - cap;
        if (waveEnd > 0f) {
            float step = dp(2f);
            path.reset();
            for (float x = 0f; ; x += step) {
                float at = Math.min(x, waveEnd);
                float y = middle + WavyProgressMath.waveOffset(at, width, phase, amplitudePx);
                if (x == 0f) path.moveTo(at, y);
                else path.lineTo(at, y);
                if (at >= waveEnd) break;
            }
            canvas.drawPath(path, wavePaint);
        }

        // Parte que falta: recta, desde el corte hasta antes del punto final.
        float dotRadius = wavePaint.getStrokeWidth() / 2f;
        float dotCenter = width - dotRadius;
        float remainingStart = WavyProgressMath.remainingStart(width, fraction, gap) + cap;
        float remainingEnd = dotCenter - dotRadius;
        if (remainingStart < remainingEnd) {
            canvas.drawLine(remainingStart, middle, remainingEnd, middle, remainingPaint);
        }

        // Punto final, del mismo grosor de la línea.
        canvas.drawCircle(dotCenter, middle, dotRadius, endPaint);

        if (isShown() && WavyProgressMath.needsFrame(amplitude, playing)) {
            postInvalidateOnAnimation();
        }
    }

    private float dp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }
}
