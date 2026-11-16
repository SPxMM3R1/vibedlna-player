package cl.streambox.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

/**
 * Barra de avance recta (mockup): 3 dp de grosor con las puntas redondeadas. Lo visto va en cian
 * ({@link TvUi#NOW}), lo que falta en blanco al 25 % y, en la posición actual, un punto blanco de
 * 9 dp.
 *
 * <p>La fracción la calcula {@link PlaybackMath#fraction}. Las medidas van en dp sin escalar, como
 * el resto del reproductor.
 */
public final class StraightProgressBar extends View {
    private static final int REMAINING_COLOR = 0x40FFFFFF;
    private static final float TRACK_DP = 3f;
    private static final float DOT_DP = 9f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private float fraction;

    public StraightProgressBar(Context context) {
        this(context, null);
    }

    public StraightProgressBar(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /** Fija cuánto se ha visto: la posición y la duración del video. */
    public void setProgress(long positionMs, long durationMs) {
        float value = PlaybackMath.fraction(positionMs, durationMs);
        if (value == fraction) return;
        fraction = value;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        if (width <= 0f || height <= 0f) return;

        float track = dp(TRACK_DP);
        float radius = track / 2f;
        float top = (height - track) / 2f;
        float bottom = top + track;
        float dot = dp(DOT_DP);
        float played = fraction * width;
        // El punto se mantiene entero dentro de la vista, también al inicio y al final.
        float dotX = Math.max(dot / 2f, Math.min(width - dot / 2f, played));

        paint.setColor(REMAINING_COLOR);
        rect.set(0f, top, width, bottom);
        canvas.drawRoundRect(rect, radius, radius, paint);

        if (played > 0f) {
            paint.setColor(TvUi.NOW);
            rect.set(0f, top, played, bottom);
            canvas.drawRoundRect(rect, radius, radius, paint);
        }

        paint.setColor(Color.WHITE);
        canvas.drawCircle(dotX, height / 2f, dot / 2f, paint);
    }

    private float dp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }
}
