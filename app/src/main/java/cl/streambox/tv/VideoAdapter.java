package cl.streambox.tv;

import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.VideoHolder> {
    interface Listener {
        void onVideoSelected(VideoItem video);
    }

    private final List<VideoItem> videos = new ArrayList<>();
    private final ThumbnailRepository thumbnails;
    private final Listener listener;

    VideoAdapter(ThumbnailRepository thumbnails, Listener listener) {
        this.thumbnails = thumbnails;
        this.listener = listener;
        setHasStableIds(true);
    }

    void submit(List<VideoItem> newVideos) {
        videos.clear();
        videos.addAll(newVideos);
        notifyDataSetChanged();
    }

    @Override
    public long getItemId(int position) {
        return videos.get(position).getUri().toString().hashCode();
    }

    @NonNull
    @Override
    public VideoHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_video, parent, false);
        int width = Math.max(1, (parent.getMeasuredWidth() - dp(parent, 54)) / 4);
        view.setLayoutParams(new RecyclerView.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT));
        return new VideoHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VideoHolder holder, int position) {
        VideoItem video = videos.get(position);
        String token = video.getUri().toString();
        holder.thumbnail.setTag(token);
        holder.thumbnail.setImageDrawable(null);
        holder.placeholder.setVisibility(View.VISIBLE);
        holder.title.setText(video.getName());
        holder.duration.setText(formatDuration(video.getDurationMs()));
        holder.itemView.setOnClickListener(view -> listener.onVideoSelected(video));
        holder.itemView.setOnFocusChangeListener((view, focused) -> view.animate()
                .scaleX(focused ? 1.025f : 1f)
                .scaleY(focused ? 1.025f : 1f)
                .setDuration(120)
                .start());

        thumbnails.load(video, bitmap -> {
            if (!token.equals(holder.thumbnail.getTag())) return;
            holder.thumbnail.setImageBitmap(bitmap);
            holder.placeholder.setVisibility(bitmap == null ? View.VISIBLE : View.GONE);
        });
    }

    @Override
    public int getItemCount() {
        return videos.size();
    }

    private static String formatDuration(long durationMs) {
        if (durationMs <= 0) return "--:--";
        long totalSeconds = durationMs / 1_000L;
        long hours = totalSeconds / 3_600L;
        long minutes = (totalSeconds % 3_600L) / 60L;
        long seconds = totalSeconds % 60L;
        return hours > 0
                ? String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds)
                : String.format(Locale.ROOT, "%02d:%02d", minutes, seconds);
    }

    private static int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }

    static final class VideoHolder extends RecyclerView.ViewHolder {
        final ImageView thumbnail;
        final ImageView placeholder;
        final TextView title;
        final TextView duration;

        VideoHolder(@NonNull View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.video_thumbnail);
            placeholder = itemView.findViewById(R.id.video_placeholder);
            title = itemView.findViewById(R.id.video_title);
            duration = itemView.findViewById(R.id.video_duration);
        }
    }
}
