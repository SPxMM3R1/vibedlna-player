package cl.streambox.tv;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.view.animation.PathInterpolator;

/**
 * Lenguaje visual «Flotante minimalista» (DISENO.md de VibeM3U) para el Player:
 * colores, resorte y los elementos de foco que viajan entre opciones.
 */
final class TvUi {
    static final int WHITE = Color.WHITE;
    /** Texto normal: blanco al 62 %. */
    static final int INK_2 = 0x9EFFFFFF;
    /** Etiquetas: blanco al 42 %. */
    static final int INK_3 = 0x6BFFFFFF;
    /** No disponible: blanco al 25 %. */
    static final int INK_4 = 0x40FFFFFF;
    /** En uso / elegido / en línea. */
    static final int LIVE = 0xFF5BE9A0;
    /** Avance / activo. */
    static final int NOW = 0xFF00B8D9;
    /** Fondo de tarjeta sin miniatura. */
    static final int CARD_BG = 0xFF12161C;
    static final int PAGE_BG = 0xFF0B0D10;

    static final PathInterpolator SPRING = new PathInterpolator(0.34f, 1.42f, 0.64f, 1f);
    static final PathInterpolator SPRING_SOFT = new PathInterpolator(0.34f, 1.3f, 0.64f, 1f);
    static final long TRAVEL_MS = 420L;

    private static final int[][] PALETTE = {
            {0xFF3A2A4A, 0xFF1A1224, 0xFF0B0812},
            {0xFF24406A, 0xFF0F1D33, 0xFF070D1A},
            {0xFF5A3A22, 0xFF2A1A0E, 0xFF120A05},
            {0xFF22503E, 0xFF10261C, 0xFF06120C},
            {0xFF4A2630, 0xFF241218, 0xFF10060A},
    };

    private TvUi() {
    }

    /**
     * Factor de escala del diseño: las medidas están pensadas para 1280×720 dp. Una TV
     * de 1080p con densidad 2 tiene 960×540 dp y se reduce a 0,75; una de 720p, 1.
     */
    private static float scale = 1f;

    /** Calcula el factor de escala a partir de la pantalla actual. Llamar al crear la actividad. */
    static void configureScale(Context context) {
        android.util.DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        float widthDp = metrics.widthPixels / metrics.density;
        float heightDp = metrics.heightPixels / metrics.density;
        float factor = Math.min(widthDp / 1280f, heightDp / 720f);
        scale = Math.max(0.5f, Math.min(1.5f, factor));
    }

    static float scale() {
        return scale;
    }

    static int dp(Context context, float value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value * scale,
                context.getResources().getDisplayMetrics()
        ));
    }

    static TextView text(Context context, String value, float sp, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp * scale);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        view.setSingleLine(true);
        view.setTypeface(null, bold ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        return view;
    }

    /** Foco de texto: blanco en negrita y crece 7 % desde la izquierda. */
    static void setFocusedText(TextView view, boolean focused) {
        view.setTextColor(focused ? WHITE : INK_2);
        view.setTypeface(null, focused
                ? android.graphics.Typeface.BOLD
                : android.graphics.Typeface.NORMAL);
        view.setPivotX(0f);
        view.animate()
                .scaleX(focused ? 1.07f : 1f)
                .scaleY(focused ? 1.07f : 1f)
                .setDuration(350L)
                .setInterpolator(SPRING)
                .start();
    }

    /**
     * Mueve la línea de foco bajo {@code target} (ancho del objetivo, 2 dp de alto,
     * 6 dp por debajo). {@code mover} debe estar dentro de {@code host}, un FrameLayout.
     */
    static void underline(View mover, View target, View host, Context context) {
        underline(mover, target, host, context, 6, 1f);
    }

    /**
     * Línea de foco a {@code gapDp} bajo {@code target}. {@code widthScale} es la escala con que
     * el texto crece al enfocarse: la línea mide su ancho final, no el de reposo.
     */
    static void underline(View mover, View target, View host, Context context,
                          int gapDp, float widthScale) {
        // Se calcula después del siguiente layout: al recibir el foco la vista puede
        // aún no tener posición y la línea quedaría en (0, 0).
        target.post(() -> {
            // Si el foco ya salió de la fila, una línea pendiente no debe volver a mostrarse.
            if (!target.isAttachedToWindow() || !hasFocusWithin(target)) {
                hide(mover);
                return;
            }
            int[] at = new int[2];
            int[] origin = new int[2];
            target.getLocationInWindow(at);
            host.getLocationInWindow(origin);
            float x = at[0] - origin[0];
            float y = at[1] - origin[1] + target.getHeight() + dp(context, gapDp);
            setSize(mover, Math.round(target.getWidth() * widthScale), dp(context, 2));
            travel(mover, x, y);
            mover.setAlpha(1f);
        });
    }

    /** Marco blanco de 2 dp de grosor alrededor de {@code target} (6 dp fuera). */
    static void ring(View mover, View target, View host, Context context) {
        target.post(() -> {
            // Una tarjeta ya quitada de la grilla no tiene posición: el marco se esconde en vez
            // de quedar en (0, 0), sobre el encabezado.
            if (!target.isAttachedToWindow() || !hasFocusWithin(target)) {
                hide(mover);
                return;
            }
            int[] at = new int[2];
            int[] origin = new int[2];
            target.getLocationInWindow(at);
            host.getLocationInWindow(origin);
            int pad = dp(context, 6);
            float x = at[0] - origin[0] - pad;
            float y = at[1] - origin[1] - pad;
            setSize(mover, target.getWidth() + pad * 2, target.getHeight() + pad * 2);
            travel(mover, x, y);
            mover.setAlpha(1f);
        });
    }

    /**
     * Acción de una fila o tarjeta: el clic y el OK del control (DPAD_CENTER) o ENTER la
     * disparan. El OK se atiende aquí y se consume, así que no se ejecuta dos veces.
     */
    static void clickable(View view, Runnable action) {
        view.setOnClickListener(ignored -> action.run());
        view.setOnKeyListener((ignored, keyCode, event) -> {
            if (keyCode != KeyEvent.KEYCODE_DPAD_CENTER && keyCode != KeyEvent.KEYCODE_ENTER) {
                return false;
            }
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                action.run();
            }
            return true;
        });
    }

    static void hide(View mover) {
        mover.setAlpha(0f);
    }

    /** True si {@code view} o uno de sus contenedores es la vista con foco. */
    static boolean hasFocusWithin(View view) {
        View current = view;
        while (current != null) {
            if (current.isFocused()) return true;
            current = current.getParent() instanceof View ? (View) current.getParent() : null;
        }
        return false;
    }

    private static void setSize(View mover, int width, int height) {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) mover.getLayoutParams();
        if (params.width == width && params.height == height) return;
        params.width = width;
        params.height = height;
        mover.setLayoutParams(params);
    }

    private static void travel(View mover, float x, float y) {
        if (reduceMotion(mover.getContext())) {
            mover.setTranslationX(x);
            mover.setTranslationY(y);
            return;
        }
        mover.animate()
                .translationX(x)
                .translationY(y)
                .setDuration(TRAVEL_MS)
                .setInterpolator(SPRING_SOFT)
                .start();
    }

    /** Animaciones apagadas en el sistema (escala 0). */
    static boolean reduceMotion(Context context) {
        try {
            return Settings.Global.getFloat(
                    context.getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f
            ) == 0f;
        } catch (Exception ignored) {
            return false;
        }
    }

    /** Degradado oscuro estable para un texto (fondo sin miniatura). */
    static Drawable gradientFor(String seed) {
        int index = Math.floorMod(seed == null ? 0 : seed.hashCode(), PALETTE.length);
        int[] colors = PALETTE[index];
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{colors[0], colors[1], colors[2]}
        );
        return drawable;
    }

    /** Coloca un TextView en un FrameLayout en la posición indicada (dp). */
    static void place(View view, Context context, int leftDp, int topDp, int widthDp) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                widthDp < 0 ? FrameLayout.LayoutParams.WRAP_CONTENT : dp(context, widthDp),
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        params.leftMargin = dp(context, leftDp);
        params.topMargin = dp(context, topDp);
        view.setLayoutParams(params);
    }

    static ViewGroup.MarginLayoutParams margins(View view, int left, int top, int right, int bottom) {
        ViewGroup.MarginLayoutParams params = view.getLayoutParams() instanceof ViewGroup.MarginLayoutParams
                ? (ViewGroup.MarginLayoutParams) view.getLayoutParams()
                : new ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(left, top, right, bottom);
        view.setLayoutParams(params);
        return params;
    }
}
