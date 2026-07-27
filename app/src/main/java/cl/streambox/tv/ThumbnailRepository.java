package cl.streambox.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.LruCache;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
    private final Map<String, List<Callback>> inFlight = new HashMap<>();
    private final Map<String, Integer> revisions = new HashMap<>();
    private final Object diskLock = new Object();
    private final LruCache<String, Bitmap> memoryCache =
            new LruCache<String, Bitmap>(16 * 1024) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return Math.max(1, value.getAllocationByteCount() / 1024);
                }
            };

    private volatile ThumbnailSettings settings;
    private volatile boolean destroyed;
    private int cacheEpoch;

    ThumbnailRepository(
            Context context,
            Handler mainHandler,
            ThumbnailSettings initialSettings
    ) {
        this.context = context.getApplicationContext();
        this.mainHandler = mainHandler;
        settings = initialSettings;
        cacheDirectory = context.getDir("video_thumbnails", Context.MODE_PRIVATE);
        legacyCacheDirectory = new File(context.getCacheDir(), "video_thumbnails");
        ensureDirectory(cacheDirectory);
    }

    void setSettings(ThumbnailSettings updatedSettings) {
        settings = updatedSettings;
    }

    ThumbnailSettings getSettings() {
        return settings;
    }

    String requestKey(VideoItem video) {
        String name = cacheName(video, settings);
        synchronized (diskLock) {
            return name + ":" + cacheEpoch + ":" + revision(name);
        }
    }

    void load(VideoItem video, Callback callback) {
        ThumbnailSettings requestSettings = settings;
        String cacheName = cacheName(video, requestSettings);
        int requestEpoch;
        int requestRevision;
        synchronized (diskLock) {
            requestEpoch = cacheEpoch;
            requestRevision = revision(cacheName);
        }
        String workKey = cacheName + ":" + requestEpoch + ":" + requestRevision;
        Bitmap memoryBitmap;
        synchronized (memoryCache) {
            memoryBitmap = memoryCache.get(workKey);
        }
        if (memoryBitmap != null && !memoryBitmap.isRecycled()) {
            post(callback, memoryBitmap);
            return;
        }

        synchronized (inFlight) {
            List<Callback> callbacks = inFlight.get(workKey);
            if (callbacks != null) {
                if (callback != null) callbacks.add(callback);
                return;
            }
            callbacks = new ArrayList<>();
            if (callback != null) callbacks.add(callback);
            inFlight.put(workKey, callbacks);
        }

        executor.submit(() -> {
            Bitmap bitmap = loadOrCreate(
                    video,
                    requestSettings,
                    cacheName,
                    requestEpoch,
                    requestRevision
            );
            if (bitmap != null) {
                synchronized (memoryCache) {
                    memoryCache.put(workKey, bitmap);
                }
            }
            List<Callback> callbacks;
            synchronized (inFlight) {
                callbacks = inFlight.remove(workKey);
            }
            if (callbacks == null || callbacks.isEmpty()) return;
            Bitmap loaded = bitmap;
            mainHandler.post(() -> {
                if (destroyed) return;
                for (Callback item : callbacks) item.onLoaded(loaded);
            });
        });
    }

    void prefetch(List<VideoItem> videos) {
        for (VideoItem video : videos) {
            if (!video.isContainer()) load(video, null);
        }
    }

    void evict(VideoItem video) {
        for (ThumbnailSettings.Mode mode : ThumbnailSettings.Mode.values()) {
            String name = cacheName(video, new ThumbnailSettings(mode));
            synchronized (diskLock) {
                revisions.put(name, revision(name) + 1);
                File file = new File(cacheDirectory, name);
                if (file.exists() && !file.delete()) {
                    Log.w(TAG, "No se pudo borrar " + file);
                }
            }
        }
        synchronized (memoryCache) {
            memoryCache.evictAll();
        }
    }

    void clearAll() {
        synchronized (memoryCache) {
            memoryCache.evictAll();
        }
        synchronized (diskLock) {
            cacheEpoch++;
            revisions.clear();
            File[] files = cacheDirectory.listFiles();
            deleteFiles(files);
            deleteFiles(legacyCacheDirectory.listFiles());
        }
    }

    void destroy() {
        destroyed = true;
        executor.shutdownNow();
        synchronized (inFlight) {
            inFlight.clear();
        }
        synchronized (memoryCache) {
            memoryCache.evictAll();
        }
    }

    private Bitmap loadOrCreate(
            VideoItem video,
            ThumbnailSettings requestSettings,
            String cacheName,
            int requestEpoch,
            int requestRevision
    ) {
        File cached = new File(cacheDirectory, cacheName);
        Bitmap bitmap;
        synchronized (diskLock) {
            bitmap = requestIsCurrent(cacheName, requestEpoch, requestRevision)
                    ? decode(cached)
                    : null;
        }
        if (bitmap != null) return bitmap;

        if (requestSettings.prefersServerArtwork() && video.getArtworkUri() != null) {
            bitmap = downloadArtwork(video.getArtworkUri());
            if (bitmap != null) {
                Bitmap cropped = centerCrop(bitmap, WIDTH, HEIGHT);
                if (cropped != bitmap) bitmap.recycle();
                saveIfCurrent(cached, cropped, cacheName, requestEpoch, requestRevision);
                return cropped;
            }
        }
        if (requestSettings.generatedPercentage() == 50) {
            File legacy = new File(legacyCacheDirectory, legacyCacheName(video));
            bitmap = decode(legacy);
            if (bitmap != null) {
                saveIfCurrent(cached, bitmap, cacheName, requestEpoch, requestRevision);
                return bitmap;
            }
        }
        return createFrame(
                video,
                requestSettings.generatedPercentage(),
                cached,
                cacheName,
                requestEpoch,
                requestRevision
        );
    }

    private Bitmap downloadArtwork(Uri artworkUri) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(artworkUri.toString()).openConnection();
            connection.setConnectTimeout(8_000);
            connection.setReadTimeout(15_000);
            connection.setRequestProperty("User-Agent", "VibeDLNA/0.3.3");
            connection.setRequestProperty("transferMode.dlna.org", "Interactive");
            connection.connect();
            if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) {
                return null;
            }
            try (InputStream input = connection.getInputStream()) {
                return BitmapFactory.decodeStream(input);
            }
        } catch (Exception error) {
            Log.w(TAG, "No se pudo descargar " + artworkUri, error);
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private Bitmap createFrame(
            VideoItem video,
            int percentage,
            File destination,
            String cacheName,
            int requestEpoch,
            int requestRevision
    ) {
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
            long frameUs = Math.max(0L, durationMs * percentage * 10L);
            Bitmap source = retriever.getFrameAtTime(
                    frameUs,
                    MediaMetadataRetriever.OPTION_CLOSEST
            );
            if (source == null) return null;
            Bitmap bitmap = centerCrop(source, WIDTH, HEIGHT);
            if (bitmap != source) source.recycle();
            saveIfCurrent(
                    destination,
                    bitmap,
                    cacheName,
                    requestEpoch,
                    requestRevision
            );
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

    private void post(Callback callback, Bitmap bitmap) {
        if (callback == null) return;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            if (!destroyed) callback.onLoaded(bitmap);
            return;
        }
        mainHandler.post(() -> {
            if (!destroyed) callback.onLoaded(bitmap);
        });
    }

    private void saveIfCurrent(
            File destination,
            Bitmap bitmap,
            String cacheName,
            int requestEpoch,
            int requestRevision
    ) {
        synchronized (diskLock) {
            if (!requestIsCurrent(cacheName, requestEpoch, requestRevision)) return;
            save(destination, bitmap);
        }
    }

    private boolean requestIsCurrent(
            String cacheName,
            int requestEpoch,
            int requestRevision
    ) {
        return requestEpoch == cacheEpoch && requestRevision == revision(cacheName);
    }

    private int revision(String cacheName) {
        Integer value = revisions.get(cacheName);
        return value == null ? 0 : value;
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
        File temporary = new File(destination.getParentFile(), destination.getName() + ".tmp");
        File backup = new File(destination.getParentFile(), destination.getName() + ".bak");
        try {
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 86, output)) {
                    throw new IllegalStateException("Bitmap.compress devolvió false");
                }
                output.flush();
                output.getFD().sync();
            }
            if (backup.exists() && !backup.delete()) {
                throw new IllegalStateException("No se pudo limpiar el respaldo anterior");
            }
            if (destination.exists() && !destination.renameTo(backup)) {
                throw new IllegalStateException("No se pudo respaldar la miniatura anterior");
            }
            if (!temporary.renameTo(destination)) {
                if (backup.exists()) backup.renameTo(destination);
                throw new IllegalStateException("No se pudo finalizar la miniatura");
            }
            if (backup.exists() && !backup.delete()) {
                Log.w(TAG, "No se pudo borrar el respaldo " + backup);
            }
        } catch (Exception error) {
            if (temporary.exists() && !temporary.delete()) {
                Log.w(TAG, "No se pudo eliminar " + temporary);
            }
            Log.w(TAG, "No se pudo guardar " + destination, error);
        }
    }

    private static void ensureDirectory(File directory) {
        if (directory != null && !directory.exists() && !directory.mkdirs()) {
            Log.w(TAG, "No se pudo crear el directorio: " + directory);
        }
    }

    private static void deleteFiles(File[] files) {
        if (files == null) return;
        for (File file : files) {
            if (file.isFile() && !file.delete()) {
                Log.w(TAG, "No se pudo borrar " + file);
            }
        }
    }

    static String cacheName(VideoItem video, ThumbnailSettings settings) {
        return ThumbnailCacheKey.name(
                video.getServerUdn(),
                video.getId(),
                settings.cacheVariant()
        );
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
