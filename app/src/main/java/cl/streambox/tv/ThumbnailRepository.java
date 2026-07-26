package cl.streambox.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import android.util.LruCache;

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

    private static final String TAG = "VibeThumbnails";
    private static final int WIDTH = 480;
    private static final int HEIGHT = 270;

    private final Context context;
    private final File cacheDirectory;
    private final File legacyCacheDirectory;
    private final Handler mainHandler;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final LruCache<String, Bitmap> memoryCache =
            new LruCache<String, Bitmap>(16 * 1024) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return Math.max(1, value.getAllocationByteCount() / 1024);
                }
            };

    ThumbnailRepository(Context context, Handler mainHandler) {
        this.context = context.getApplicationContext();
        this.mainHandler = mainHandler;
        cacheDirectory = context.getDir("video_thumbnails", Context.MODE_PRIVATE);
        legacyCacheDirectory = new File(context.getCacheDir(), "video_thumbnails");
        ensureDirectory(cacheDirectory);
    }

    void load(VideoItem video, Callback callback) {
        String cacheName = cacheName(video);
        Bitmap memoryBitmap;
        synchronized (memoryCache) {
            memoryBitmap = memoryCache.get(cacheName);
        }
        if (memoryBitmap != null && !memoryBitmap.isRecycled()) {
            mainHandler.post(() -> callback.onLoaded(memoryBitmap));
            return;
        }

        executor.submit(() -> {
            Bitmap bitmap = loadOrCreate(video, cacheName);
            if (bitmap != null) {
                synchronized (memoryCache) {
                    memoryCache.put(cacheName, bitmap);
                }
            }
            mainHandler.post(() -> callback.onLoaded(bitmap));
        });
    }

    void destroy() {
        executor.shutdownNow();
        synchronized (memoryCache) {
            memoryCache.evictAll();
        }
    }

    private Bitmap loadOrCreate(VideoItem video, String cacheName) {
        File cached = new File(cacheDirectory, cacheName);
        Bitmap bitmap = decode(cached);
        if (bitmap != null) return bitmap;

        File legacy = new File(legacyCacheDirectory, legacyCacheName(video));
        bitmap = decode(legacy);
        if (bitmap != null) {
            save(cached, bitmap);
            return bitmap;
        }

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            String scheme = video.getUri().getScheme();
            if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                Map<String, String> headers = new HashMap<>();
                headers.put("User-Agent", "VibeDLNA/0.3.3");
                headers.put("transferMode.dlna.org", "Streaming");
                retriever.setDataSource(video.getUri().toString(), headers);
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
        } catch (Exception error) {
            Log.w(TAG, "No se pudo crear la miniatura de " + video.getName(), error);
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

    private static Bitmap decode(File file) {
        if (!file.isFile() || file.length() <= 0L) return null;
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
        if (bitmap == null && !file.delete()) {
            Log.w(TAG, "No se pudo eliminar una miniatura dañada: " + file);
        }
        return bitmap;
    }

    private static void save(File destination, Bitmap bitmap) {
        ensureDirectory(destination.getParentFile());
        File temporary = new File(
                destination.getParentFile(),
                destination.getName() + ".tmp"
        );
        try {
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 86, output)) {
                    throw new IllegalStateException("Bitmap.compress devolvió false");
                }
                output.flush();
                output.getFD().sync();
            }
            if (destination.exists() && !destination.delete()) {
                throw new IllegalStateException("No se pudo reemplazar la miniatura anterior");
            }
            if (!temporary.renameTo(destination)) {
                throw new IllegalStateException("No se pudo finalizar la miniatura");
            }
        } catch (Exception error) {
            if (temporary.exists() && !temporary.delete()) {
                Log.w(TAG, "No se pudo eliminar una miniatura temporal: " + temporary);
            }
            Log.w(TAG, "No se pudo guardar la miniatura: " + destination, error);
        }
    }

    private static void ensureDirectory(File directory) {
        if (directory != null && !directory.exists() && !directory.mkdirs()) {
            Log.w(TAG, "No se pudo crear el directorio: " + directory);
        }
    }

    private static String cacheName(VideoItem video) {
        Uri uri = video.getUri();
        String identity = uri.getHost()
                + ":" + uri.getPath()
                + ":" + video.getName()
                + ":" + video.getDurationMs()
                + ":" + video.getLastModified();
        return sha256(identity) + ".jpg";
    }

    private static String legacyCacheName(VideoItem video) {
        return sha256(video.getUri() + ":" + video.getLastModified()) + ".jpg";
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
