package cl.streambox.tv;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Tarjetas 228 × 128 dp de la grilla 4 × 3 de la biblioteca (mockup): miniatura con calidad
 * arriba a la izquierda, duración abajo a la derecha y raya cian de avance al pie. Bajo la
 * miniatura, el nombre (una línea) y el tamaño.
 */
final class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.VideoHolder> {
    interface Listener {
        /** La tarjeta recibió el foco; {@code thumbFrame} es el marco para el marco blanco. */
        void onTileFocused(VideoItem entry, View thumbFrame);

        /** OK sobre la tarjeta. */
        void onTileSelected(VideoItem entry);
    }

    static final int CARD_WIDTH_DP = CardLayoutMath.CARD_WIDTH_DP;
    static final int CARD_HEIGHT_DP = CardLayoutMath.CARD_HEIGHT_DP;
    /** Crecimiento y subida de la tarjeta con foco; el marco de foco usa los mismos valores. */
    static final float FOCUS_SCALE = 1.06f;
    static final int FOCUS_LIFT_DP = 4;

    private final List<VideoItem> entries = new ArrayList<>();
    private final ThumbnailRepository thumbnails;
    private PlaybackResumeStore resume;
    private final Listener listener;

    VideoAdapter(ThumbnailRepository thumbnails, PlaybackResumeStore resume, Listener listener) {
        this.thumbnails = thumbnails;
        this.resume = resume;
        this.listener = listener;
        setHasStableIds(true);
    }

    /** Cambia el almacén de avance (p. ej. al cambiar la retención en Opciones). */
    void setResume(PlaybackResumeStore store) {
        resume = store;
        notifyDataSetChanged();
    }

    void submit(List<VideoItem> newEntries) {
        entries.clear();
        entries.addAll(newEntries);
        notifyDataSetChanged();
    }

    List<VideoItem> entries() {
        return entries;
    }

    void refresh() {
        notifyDataSetChanged();
    }

    @Override
    public long getItemId(int position) {
        return entries.get(position).stableKey().hashCode();
    }

    @NonNull
    @Override
    public VideoHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context context = parent.getContext();
        LinearLayout tile = new LinearLayout(context);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setFocusable(true);
        tile.setClipChildren(false);
        tile.setLayoutParams(new RecyclerView.LayoutParams(
                dp(context, CARD_WIDTH_DP),
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        FrameLayout frame = new FrameLayout(context);
        frame.setBackgroundColor(TvUi.CARD_BG);
        LinearLayout.LayoutParams frameParams = new LinearLayout.LayoutParams(
                dp(context, CARD_WIDTH_DP),
                dp(context, CARD_HEIGHT_DP)
        );
        frame.setLayoutParams(frameParams);
        frame.setClipToOutline(true);
        frame.setBackground(roundedBackground(context, TvUi.CARD_BG));

        ImageView thumb = new ImageView(context);
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        frame.addView(thumb, match(context));

        TextView none = TvUi.text(context, "SIN MINIATURA", 11f, TvUi.INK_4, false);
        none.setLetterSpacing(0.2f);
        none.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams noneParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        );
        frame.addView(none, noneParams);

        TextView quality = TvUi.text(context, "", 10f, TvUi.WHITE, true);
        quality.setShadowLayer(6f, 0f, 1f, 0xD9000000);
        quality.setLetterSpacing(0.06f);
        frame.addView(quality, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START
        ) {{
            leftMargin = dp(context, 7);
            topMargin = dp(context, 6);
        }});

        // Duración en píldora oscura (mockup): 10 sp en negrita semibold.
        TextView duration = TvUi.text(context, "", 10f, TvUi.WHITE, true);
        duration.setBackground(durationPill(context));
        duration.setPadding(dp(context, 5), dp(context, 1), dp(context, 5), dp(context, 1));
        frame.addView(duration, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.END
        ) {{
            rightMargin = dp(context, 7);
            bottomMargin = dp(context, 6);
        }});

        View progressTrack = new View(context);
        progressTrack.setBackgroundColor(0x2EFFFFFF);
        frame.addView(progressTrack, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(context, 3),
                Gravity.BOTTOM
        ));
        View progress = new View(context);
        progress.setBackgroundColor(TvUi.NOW);
        frame.addView(progress, new FrameLayout.LayoutParams(0, dp(context, 3), Gravity.BOTTOM));

        tile.addView(frame);

        // Nombre en una línea con «…» (mockup: 12,5 sp, 7 dp arriba) y tamaño debajo (10 sp).
        TextView name = TvUi.text(context, "", 12.5f, TvUi.INK_2, false);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                dp(context, CARD_WIDTH_DP),
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        nameParams.topMargin = dp(context, 7);
        tile.addView(name, nameParams);

        TextView sub = TvUi.text(context, "", 10f, TvUi.INK_3, false);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                dp(context, CARD_WIDTH_DP),
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        subParams.topMargin = dp(context, 1);
        tile.addView(sub, subParams);

        return new VideoHolder(tile, frame, thumb, none, quality, duration, progress, name, sub);
    }

    @Override
    public void onBindViewHolder(@NonNull VideoHolder holder, int position) {
        VideoItem entry = entries.get(position);
        Context context = holder.itemView.getContext();
        MediaInfo media = entry.getMedia();
        holder.name.setText(entry.getName());
        holder.quality.setText(DlnaMediaFormat.qualityLabel(media.height()));
        holder.duration.setText(entry.getDurationMs() > 0L
                ? DlnaMediaFormat.duration(entry.getDurationMs())
                : "");
        holder.sub.setText(DlnaMediaFormat.size(media.sizeBytes));

        PlaybackResumeStore.ResumePoint point = resume.get(
                entry.stableKey(),
                System.currentTimeMillis()
        );
        int percent = point == null ? 0 : DlnaMediaFormat.percent(point.positionMs, point.durationMs);
        if (point != null && point.durationMs > 0L) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) holder.progress.getLayoutParams();
            params.width = dp(context, CARD_WIDTH_DP) * percent / 100;
            holder.progress.setLayoutParams(params);
            holder.progress.setVisibility(View.VISIBLE);
        } else {
            holder.progress.setVisibility(View.GONE);
        }

        String token = entry.stableKey() + ":" + thumbnails.requestKey(entry);
        holder.thumb.setTag(token);
        holder.thumb.setImageDrawable(null);
        holder.none.setVisibility(View.VISIBLE);
        thumbnails.load(entry, (bitmap, ignoredSource) -> {
            if (!token.equals(holder.thumb.getTag())) return;
            holder.thumb.setImageBitmap(bitmap);
            holder.none.setVisibility(bitmap == null ? View.VISIBLE : View.GONE);
        });

        TvUi.clickable(holder.itemView, () -> listener.onTileSelected(entry));
        holder.itemView.setOnFocusChangeListener((view, focused) -> {
            holder.name.setTextColor(focused ? TvUi.WHITE : TvUi.INK_2);
            holder.name.setTypeface(null, focused ? Typeface.BOLD : Typeface.NORMAL);
            view.setPivotX(view.getWidth() / 2f);
            view.setPivotY(dp(view.getContext(), CARD_HEIGHT_DP));
            view.animate()
                    .scaleX(focused ? FOCUS_SCALE : 1f)
                    .scaleY(focused ? FOCUS_SCALE : 1f)
                    .translationY(focused ? -dp(view.getContext(), FOCUS_LIFT_DP) : 0f)
                    .setDuration(350L)
                    .setInterpolator(TvUi.SPRING)
                    .start();
            if (focused) listener.onTileFocused(entry, holder.thumbFrame);
        });
        // Una tarjeta con foco que se vuelve a enlazar sigue con su negrita y su color.
        boolean focused = holder.itemView.hasFocus();
        holder.name.setTextColor(focused ? TvUi.WHITE : TvUi.INK_2);
        holder.name.setTypeface(null, focused ? Typeface.BOLD : Typeface.NORMAL);
    }

    /**
     * Repite el camino de foco de una tarjeta (negrita, escala, marco y video de fondo). Sirve
     * al restaurar el foco: si el marco se calculó antes del layout, así se vuelve a calcular.
     */
    static void replayFocus(View itemView) {
        if (itemView == null || !itemView.hasFocus()) return;
        View.OnFocusChangeListener listener = itemView.getOnFocusChangeListener();
        if (listener != null) listener.onFocusChange(itemView, true);
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    private static int dp(Context context, int value) {
        return TvUi.dp(context, value);
    }

    private static FrameLayout.LayoutParams match(Context context) {
        return new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        );
    }

    /** Fondo de la duración: negro al 72 % con esquinas de 4 dp, como el mockup. */
    private static GradientDrawable durationPill(Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(0xB8000000);
        drawable.setCornerRadius(dp(context, 4));
        return drawable;
    }

    private static GradientDrawable roundedBackground(Context context, int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, 4));
        return drawable;
    }

    static final class VideoHolder extends RecyclerView.ViewHolder {
        final FrameLayout thumbFrame;
        final ImageView thumb;
        final TextView none;
        final TextView quality;
        final TextView duration;
        final View progress;
        final TextView name;
        final TextView sub;

        VideoHolder(
                @NonNull View itemView,
                FrameLayout thumbFrame,
                ImageView thumb,
                TextView none,
                TextView quality,
                TextView duration,
                View progress,
                TextView name,
                TextView sub
        ) {
            super(itemView);
            this.thumbFrame = thumbFrame;
            this.thumb = thumb;
            this.none = none;
            this.quality = quality;
            this.duration = duration;
            this.progress = progress;
            this.name = name;
            this.sub = sub;
        }
    }
}
