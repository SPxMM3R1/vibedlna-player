package cl.streambox.tv;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class VideoLibraryRepository {
    private static final int MAX_DEPTH = 16;
    private static final int MAX_VIDEOS = 2_000;

    private final Context context;

    VideoLibraryRepository(Context context) {
        this.context = context.getApplicationContext();
    }

    List<VideoItem> scan(Uri treeUri, String sortMode) {
        DocumentFile root = DocumentFile.fromTreeUri(context, treeUri);
        List<VideoItem> result = new ArrayList<>();
        if (root == null || !root.exists() || !root.canRead()) return result;
        collect(root, result, 0);
        sort(result, sortMode);
        return result;
    }

    private void collect(DocumentFile folder, List<VideoItem> result, int depth) {
        if (depth > MAX_DEPTH || result.size() >= MAX_VIDEOS) return;
        DocumentFile[] children;
        try {
            children = folder.listFiles();
        } catch (Exception ignored) {
            return;
        }

        for (DocumentFile child : children) {
            if (result.size() >= MAX_VIDEOS) return;
            if (child.isDirectory()) {
                collect(child, result, depth + 1);
            } else if (child.isFile() && isVideo(child)) {
                String name = child.getName();
                result.add(new VideoItem(
                        child.getUri(),
                        name == null || name.isBlank() ? "Video" : removeExtension(name),
                        child.getType(),
                        readDuration(child.getUri()),
                        Math.max(0, child.lastModified())
                ));
            }
        }
    }

    private boolean isVideo(DocumentFile file) {
        String mimeType = file.getType();
        if (mimeType != null && mimeType.startsWith("video/")) return true;
        String name = file.getName();
        if (name == null) return false;
        String normalized = name.toLowerCase(Locale.ROOT);
        return normalized.endsWith(".mp4")
                || normalized.endsWith(".mkv")
                || normalized.endsWith(".avi")
                || normalized.endsWith(".mov")
                || normalized.endsWith(".webm")
                || normalized.endsWith(".m4v")
                || normalized.endsWith(".ts")
                || normalized.endsWith(".mpeg")
                || normalized.endsWith(".mpg");
    }

    private long readDuration(Uri uri) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, uri);
            String duration = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION
            );
            return duration == null ? 0 : Math.max(0, Long.parseLong(duration));
        } catch (Exception ignored) {
            return 0;
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
                // Nada que liberar.
            }
        }
    }

    private static void sort(List<VideoItem> videos, String sortMode) {
        Collections.sort(videos, new Comparator<VideoItem>() {
            @Override
            public int compare(VideoItem left, VideoItem right) {
                if (LibraryPreferences.SORT_DATE.equals(sortMode)) {
                    int dateOrder = Long.compare(right.getLastModified(), left.getLastModified());
                    if (dateOrder != 0) return dateOrder;
                }
                return left.getName().compareToIgnoreCase(right.getName());
            }
        });
    }

    private static String removeExtension(String name) {
        int separator = name.lastIndexOf('.');
        return separator > 0 ? name.substring(0, separator) : name;
    }
}
