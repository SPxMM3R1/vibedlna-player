package cl.streambox.tv;

import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

final class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.VideoHolder> {
    interface Listener {
        void onEntrySelected(VideoItem entry);
    }

    private static final int PROGRESS_MAX = 1_000;

    private final List<VideoItem> entries = new ArrayList<>();
    private final ThumbnailRepository thumbnails;
    private final PlaybackResumeStore resumeStore;
    private final Listener listener;

    VideoAdapter(
            ThumbnailRepository thumbnails,
            PlaybackResumeStore resumeStore,
            Listener listener
    ) {
        this.thumbnails = thumbnails;
        this.resumeStore = resumeStore;
        this.listener = listener;
        setHasStableIds(true);
    }

    void submit(List<VideoItem> newEntries) {
        entries.clear();
        entries.addAll(newEntries);
        notifyDataSetChanged();
    }

    void refreshThumbnails() {
        notifyDataSetChanged();
    }

    @Override
    public long getItemId(int position) {
        return entries.get(position).stableKey().hashCode();
    }

    @NonNull
    @Override
    public VideoHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_video, parent, false);
        int parentWidth = parent.getMeasuredWidth();
        if (parentWidth <= 0) {
            parentWidth = parent.getResources().getDisplayMetrics().widthPixels;
        }
        int parentHeight = parent.getMeasuredHeight();
        if (parentHeight <= 0) {
            parentHeight = parent.getResources().getDisplayMetrics().heightPixels;
        }
        int horizontalSpacing = CardLayoutMath.horizontalSpacingForThreeRows(
                parentWidth,
                parentHeight,
                parent.getPaddingLeft(),
                parent.getPaddingTop(),
                parent.getPaddingRight(),
                parent.getPaddingBottom(),
                CardLayoutMath.GRID_COLUMNS,
                CardLayoutMath.GRID_VISIBLE_ROWS,
                dp(parent, CardLayoutMath.MIN_HORIZONTAL_SPACING_DP),
                dp(parent, CardLayoutMath.VERTICAL_SPACING_DP),
                dp(parent, CardLayoutMath.CARD_PADDING_DP) * 2,
                dp(parent, CardLayoutMath.CARD_PADDING_DP) * 2,
                dp(parent, CardLayoutMath.VIDEO_TITLE_HEIGHT_DP)
        );
        int width = CardLayoutMath.cardWidth(
                parentWidth,
                parent.getPaddingLeft(),
                parent.getPaddingRight(),
                horizontalSpacing,
                CardLayoutMath.GRID_COLUMNS
        );
        view.setLayoutParams(new RecyclerView.LayoutParams(
                width,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        View thumbnailFrame = view.findViewById(R.id.thumbnail_frame);
        ViewGroup.LayoutParams thumbnailLayout = thumbnailFrame.getLayoutParams();
        thumbnailLayout.height = CardLayoutMath.thumbnailHeight(
                width,
                dp(parent, CardLayoutMath.CARD_PADDING_DP) * 2
        );
        thumbnailFrame.setLayoutParams(thumbnailLayout);
        return new VideoHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VideoHolder holder, int position) {
        VideoItem entry = entries.get(position);
        String token = entry.stableKey() + ":" + thumbnails.requestKey(entry);
        holder.thumbnail.setTag(token);
        holder.thumbnail.setImageDrawable(null);
        holder.title.setText(entry.getName());
        holder.folderTitle.setText(entry.getName());
        holder.itemView.setOnClickListener(view -> listener.onEntrySelected(entry));
        holder.itemView.setOnFocusChangeListener((view, focused) -> {
            // Foco blanco sólido con texto oscuro, como las filas de VibeM3U.
            int textColor = view.getContext().getColor(focused ? R.color.ink : R.color.white);
            holder.title.setTextColor(textColor);
            holder.folderTitle.setTextColor(textColor);
            view.animate()
                    .scaleX(focused ? 1.025f : 1f)
                    .scaleY(focused ? 1.025f : 1f)
                    .setDuration(120)
                    .start();
        });

        if (entry.isContainer()) {
            holder.thumbnailFrame.setVisibility(View.GONE);
            holder.title.setVisibility(View.GONE);
            holder.folderRow.setVisibility(View.VISIBLE);
            return;
        }

        holder.thumbnailFrame.setVisibility(View.VISIBLE);
        holder.title.setVisibility(View.VISIBLE);
        holder.folderRow.setVisibility(View.GONE);
        holder.placeholder.setImageResource(R.drawable.ic_play_outline);
        holder.placeholder.setVisibility(View.VISIBLE);
        bindDuration(holder, entry);
        bindResumeProgress(holder, entry);
        thumbnails.load(entry, (bitmap, ignoredSource) -> {
            if (!token.equals(holder.thumbnail.getTag())) return;
            holder.thumbnail.setImageBitmap(bitmap);
            holder.placeholder.setVisibility(bitmap == null ? View.VISIBLE : View.GONE);
        });
    }

    private static void bindDuration(VideoHolder holder, VideoItem entry) {
        long durationMs = entry.getDurationMs();
        if (durationMs <= 0L) {
            holder.duration.setVisibility(View.GONE);
            return;
        }
        holder.duration.setText(PlaybackUiFormatter.timecode(durationMs));
        holder.duration.setVisibility(View.VISIBLE);
    }

    private void bindResumeProgress(VideoHolder holder, VideoItem entry) {
        PlaybackResumeStore.ResumePoint point = resumeStore.get(
                entry.stableKey(),
                System.currentTimeMillis()
        );
        long durationMs = point == null || point.durationMs <= 0L
                ? entry.getDurationMs()
                : point.durationMs;
        int progress = point == null
                ? 0
                : PlaybackMath.progress(point.positionMs, durationMs, PROGRESS_MAX);
        if (progress <= 0) {
            holder.progressTrack.setVisibility(View.GONE);
            return;
        }
        setWeight(holder.progressFill, progress);
        setWeight(holder.progressRest, PROGRESS_MAX - progress);
        holder.progressTrack.setVisibility(View.VISIBLE);
    }

    private static void setWeight(View view, int weight) {
        view.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight));
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    private static int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }

    static final class VideoHolder extends RecyclerView.ViewHolder {
        final ImageView thumbnail;
        final ImageView placeholder;
        final TextView title;
        final TextView duration;
        final View progressTrack;
        final View progressFill;
        final View progressRest;
        final View thumbnailFrame;
        final View folderRow;
        final TextView folderTitle;

        VideoHolder(@NonNull View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.video_thumbnail);
            placeholder = itemView.findViewById(R.id.video_placeholder);
            title = itemView.findViewById(R.id.video_title);
            duration = itemView.findViewById(R.id.video_duration);
            progressTrack = itemView.findViewById(R.id.video_progress_track);
            progressFill = itemView.findViewById(R.id.video_progress_fill);
            progressRest = itemView.findViewById(R.id.video_progress_rest);
            thumbnailFrame = itemView.findViewById(R.id.thumbnail_frame);
            folderRow = itemView.findViewById(R.id.folder_row);
            folderTitle = itemView.findViewById(R.id.folder_title);
        }
    }
}
