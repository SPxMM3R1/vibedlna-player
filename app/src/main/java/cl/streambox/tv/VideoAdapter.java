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

final class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.VideoHolder> {
    interface Listener {
        void onEntrySelected(VideoItem entry);
    }

    private final List<VideoItem> entries = new ArrayList<>();
    private final ThumbnailRepository thumbnails;
    private final Listener listener;

    VideoAdapter(ThumbnailRepository thumbnails, Listener listener) {
        this.thumbnails = thumbnails;
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
            parentWidth = parent.getResources().getDisplayMetrics().widthPixels - dp(parent, 60);
        }
        int width = Math.max(dp(parent, 180), (parentWidth - dp(parent, 56)) / 4);
        view.setLayoutParams(new RecyclerView.LayoutParams(
                width,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        View thumbnailFrame = view.findViewById(R.id.thumbnail_frame);
        ViewGroup.LayoutParams thumbnailLayout = thumbnailFrame.getLayoutParams();
        thumbnailLayout.height = CardLayoutMath.thumbnailHeight(width, dp(parent, 6));
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
        holder.itemView.setOnClickListener(view -> listener.onEntrySelected(entry));
        holder.itemView.setOnFocusChangeListener((view, focused) -> view.animate()
                .scaleX(focused ? 1.025f : 1f)
                .scaleY(focused ? 1.025f : 1f)
                .setDuration(120)
                .start());

        if (entry.isContainer()) {
            holder.placeholder.setImageResource(R.drawable.ic_folder_large);
            holder.placeholder.setVisibility(View.VISIBLE);
            return;
        }

        holder.placeholder.setImageResource(R.drawable.ic_play_outline);
        holder.placeholder.setVisibility(View.VISIBLE);
        thumbnails.load(entry, bitmap -> {
            if (!token.equals(holder.thumbnail.getTag())) return;
            holder.thumbnail.setImageBitmap(bitmap);
            holder.placeholder.setVisibility(bitmap == null ? View.VISIBLE : View.GONE);
        });
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

        VideoHolder(@NonNull View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.video_thumbnail);
            placeholder = itemView.findViewById(R.id.video_placeholder);
            title = itemView.findViewById(R.id.video_title);
        }
    }
}
