package cl.streambox.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.os.Handler;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class ThumbnailRepository {
    interface Callback {
        void onLoaded(Bitmap bitmap);
    }

    private static final int WIDTH = 480;
    private static final int HEIGHT = 270;

    private final Context context;
    private final File cacheDirectory;
    private final Handler mainHandler;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    ThumbnailRepository(Context context, Handler mainHandler) {
        this.context = context.getApplicationContext();
        this.mainHandler = mainHandler;
        cacheDirectory = new File(context.getCacheDir(), "video_thumbnails");
        if (!cacheDirectory.exists()) cacheDirectory.mkdirs();
    }

    void load(VideoItem video, Callback callback) {
        executor.submit(() -> {
            Bitmap bitmap = loadOrCreate(video);
            mainHandler.post(() -> callback.onLoaded(bitmap));
        });
    }

    void destroy() {
        executor.shutdownNow();
    }

    private Bitmap loadOrCreate(VideoItem video) {
        File cached = new File(
                cacheDirectory,
                sha256(video.getUri() + ":" + video.getLastModified()) + ".jpg"
        );
        Bitmap bitmap = BitmapFactory.decodeFile(cached.getAbsolutePath());
        if (bitmap != null) return bitmap;

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            String scheme = video.getUri().getScheme();
            if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                Map<String, String> headers = new HashMap<>();
                headers.put("User-Agent", "VibeDLNA/0.2");
                headers.put("transferMode.dlna.org", "Streaming");
                retriever.setDataSource(
                        video.getUri().toString(),
                        headers
                );
            } else {
                retriever.setDataSource(context, video.getUri());
            }
            long durationMs = video.getDurationMs();
            if (durationMs <= 0) {
                String value = retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_DURATION
                );
                if (value != null) durationMs = Long.parseLong(value);
            }
            long midpointUs = Math.max(0, durationMs * 500L);
            Bitmap source = retriever.getFrameAtTime(
                    midpointUs,
                    MediaMetadataRetriever.OPTION_CLOSEST
            );
            if (source == null) return null;
            bitmap = centerCrop(source, WIDTH, HEIGHT);
            if (bitmap != source) source.recycle();
            save(cached, bitmap);
            return bitmap;
        } catch (Exception ignored) {
            return null;
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
                // Nada que liberar.
            }
        }
    }

    private static Bitmap centerCrop(Bitmap source, int targetWidth, int targetHeight) {
        float scale = Math.max(
                targetWidth / (float) source.getWidth(),
                targetHeight / (float) source.getHeight()
        );
        int scaledWidth = Math.max(targetWidth, Math.round(source.getWidth() * scale));
        int scaledHeight = Math.max(targetHeight, Math.round(source.getHeight() * scale));
        Bitmap scaled = Bitmap.createScaledBitmap(source, scaledWidth, scaledHeight, true);
        int left = Math.max(0, (scaledWidth - targetWidth) / 2);
        int top = Math.max(0, (scaledHeight - targetHeight) / 2);
        Bitmap cropped = Bitmap.createBitmap(scaled, left, top, targetWidth, targetHeight);
        if (scaled != source && scaled != cropped) scaled.recycle();
        return cropped;
    }

    private static void save(File destination, Bitmap bitmap) {
        try (FileOutputStream output = new FileOutputStream(destination)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 86, output);
        } catch (Exception ignored) {
            // La miniatura seguirá disponible en memoria durante esta sesión.
        }
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format(Locale.ROOT, "%02x", item & 0xff));
            }
            return result.toString();
        } catch (Exception ignored) {
            return Integer.toHexString(value.hashCode());
        }
    }
}
