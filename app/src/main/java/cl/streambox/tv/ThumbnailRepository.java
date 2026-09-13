package cl.streambox.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;

/**
 * Consumes only artwork announced by a DLNA server.
 *
 * <p>This class may cache and decode an announced image, but it never derives
 * an artwork URL, extracts a frame from a video, or asks a server to generate
 * a missing image.</p>
 */
final class ThumbnailRepository {
    interface Callback {
        void onLoaded(Bitmap bitmap, Source source);
    }

    enum Source {
        SERVER("SERVIDOR"),
        NONE("SIN MINIATURA");

        final String label;

        Source(String label) {
            this.label = label;
        }
    }

    private static final class LoadedThumbnail {
        final Bitmap bitmap;
        final Source source;

        LoadedThumbnail(Bitmap bitmap, Source source) {
            this.bitmap = bitmap;
            this.source = source;
        }
    }

    private static final class PendingThumbnail {
        final List<Callback> callbacks = new ArrayList<>();
        Future<?> future;
    }

    private static final String TAG = "VibeThumbnails";
    private static final String ARTWORK_VARIANT = "server-artwork-";
    private static final int CONNECT_TIMEOUT_MS = 8_000;
    private static final int READ_TIMEOUT_MS = 15_000;
    private static final String USER_AGENT = "VibeDLNA Player/0.3.12";

    private final File cacheDirectory;
    private final File legacyCacheDirectory;
    private final Handler mainHandler;
    private final Object executorLock = new Object();
    private ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Map<String, PendingThumbnail> pending = new HashMap<>();
    private final Map<String, Integer> revisions = new HashMap<>();
    private final Object diskLock = new Object();
    private final LruCache<String, LoadedThumbnail> memoryCache =
            new LruCache<String, LoadedThumbnail>(16 * 1024) {
                @Override
                protected int sizeOf(String key, LoadedThumbnail value) {
                    return value.bitmap == null
                            ? 1
                            : Math.max(1, value.bitmap.getAllocationByteCount() / 1024);
                }
            };

    private volatile boolean destroyed;
    private volatile boolean paused;
    private int cacheEpoch;
    private int requestGeneration;

    ThumbnailRepository(Context context, Handler mainHandler) {
        Context appContext = context.getApplicationContext();
        this.mainHandler = mainHandler;
        cacheDirectory = appContext.getDir("video_thumbnails", Context.MODE_PRIVATE);
        legacyCacheDirectory = new File(appContext.getCacheDir(), "video_thumbnails");
        ensureDirectory(cacheDirectory);
    }

    /**
     * Returns the identity used by the adapter to reject a late callback.
     * The identity includes the exact artwork URL announced by the server.
     */
    String requestKey(VideoItem video) {
        String name = cacheName(video);
        synchronized (diskLock) {
            return name + ":" + cacheEpoch + ":" + revision(name)
                    + ":" + requestGeneration;
        }
    }

    void load(VideoItem video, Callback callback) {
        if (video == null || video.isContainer()) return;

        Uri artworkUri = video.getArtworkUri();
        int requestEpoch;
        int requestRevision;
        int requestGeneration;
        String cacheName = cacheName(video);
        synchronized (diskLock) {
            requestEpoch = cacheEpoch;
            requestRevision = revision(cacheName);
            requestGeneration = this.requestGeneration;
        }

        if (!isRemoteArtwork(artworkUri)) {
            post(callback, new LoadedThumbnail(null, Source.NONE), requestGeneration);
            return;
        }

        String workKey = cacheName + ":" + requestEpoch + ":" + requestRevision;
        LoadedThumbnail memoryThumbnail;
        synchronized (memoryCache) {
            memoryThumbnail = memoryCache.get(workKey);
        }
        if (memoryThumbnail != null
                && memoryThumbnail.bitmap != null
                && !memoryThumbnail.bitmap.isRecycled()) {
            post(callback, memoryThumbnail, requestGeneration);
            return;
        }

        PendingThumbnail request;
        synchronized (pending) {
            PendingThumbnail existing = pending.get(workKey);
            if (existing != null) {
                if (callback != null) existing.callbacks.add(callback);
                return;
            }
            request = new PendingThumbnail();
            if (callback != null) request.callbacks.add(callback);
            pending.put(workKey, request);
        }

        ExecutorService worker;
        synchronized (executorLock) {
            if (paused || destroyed) {
                removePending(workKey, request);
                return;
            }
            worker = executor;
        }
        try {
            Future<?> future = worker.submit(() -> {
                if (!isCurrentPending(workKey, request)
                        || paused
                        || destroyed
                        || !isGenerationCurrent(requestGeneration)) {
                    removePending(workKey, request);
                    return;
                }

                LoadedThumbnail thumbnail = loadAnnouncedArtwork(
                        artworkUri,
                        cacheName,
                        requestEpoch,
                        requestRevision,
                        requestGeneration
                );
                if (!isCurrentPending(workKey, request)
                        || paused
                        || destroyed
                        || !isGenerationCurrent(requestGeneration)
                        || !requestIsCurrent(cacheName, requestEpoch, requestRevision)) {
                    recycle(thumbnail);
                    removePending(workKey, request);
                    return;
                }

                if (thumbnail.bitmap != null) {
                    synchronized (memoryCache) {
                        memoryCache.put(workKey, thumbnail);
                    }
                }
                List<Callback> callbacks = takeCallbacks(workKey, request);
                if (callbacks.isEmpty()) return;
                mainHandler.post(() -> {
                    if (destroyed || paused || !isGenerationCurrent(requestGeneration)) return;
                    for (Callback item : callbacks) {
                        item.onLoaded(thumbnail.bitmap, thumbnail.source);
                    }
                });
            });
            synchronized (pending) {
                if (pending.get(workKey) == request) {
                    request.future = future;
                } else {
                    future.cancel(true);
                }
            }
        } catch (RejectedExecutionException rejected) {
            removePending(workKey, request);
        }
    }

    void resetForFolder() {
        synchronized (diskLock) {
            requestGeneration++;
        }
        cancelPending();
    }

    void pause() {
        paused = true;
        synchronized (diskLock) {
            requestGeneration++;
        }
        cancelPending();
        synchronized (executorLock) {
            executor.shutdownNow();
        }
    }

    void resume() {
        synchronized (executorLock) {
            if (destroyed) return;
            if (executor.isShutdown()) {
                executor = Executors.newFixedThreadPool(2);
            }
            paused = false;
        }
    }

    void clearAll() {
        cancelPending();
        synchronized (memoryCache) {
            memoryCache.evictAll();
        }
        synchronized (diskLock) {
            cacheEpoch++;
            requestGeneration++;
            revisions.clear();
            deleteFiles(cacheDirectory.listFiles());
            deleteFiles(legacyCacheDirectory.listFiles());
        }
    }

    void destroy() {
        destroyed = true;
        synchronized (diskLock) {
            requestGeneration++;
        }
        cancelPending();
        synchronized (executorLock) {
            executor.shutdownNow();
        }
        synchronized (memoryCache) {
            memoryCache.evictAll();
        }
    }

    private LoadedThumbnail loadAnnouncedArtwork(
            Uri artworkUri,
            String cacheName,
            int requestEpoch,
            int requestRevision,
            int requestGeneration
    ) {
        File cached = new File(cacheDirectory, cacheName);
        Bitmap bitmap;
        synchronized (diskLock) {
            bitmap = requestIsCurrent(cacheName, requestEpoch, requestRevision)
                    ? decode(cached)
                    : null;
        }
        if (bitmap != null) return new LoadedThumbnail(bitmap, Source.SERVER);

        bitmap = downloadArtwork(artworkUri);
        if (bitmap == null) return new LoadedThumbnail(null, Source.NONE);

        saveIfCurrent(
                cached,
                bitmap,
                cacheName,
                requestEpoch,
                requestRevision,
                requestGeneration
        );
        return new LoadedThumbnail(bitmap, Source.SERVER);
    }

    private Bitmap downloadArtwork(Uri artworkUri) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(artworkUri.toString()).openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("transferMode.dlna.org", "Interactive");
            connection.connect();
            if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) {
                return null;
            }
            try (InputStream input = connection.getInputStream()) {
                return BitmapFactory.decodeStream(input);
            }
        } catch (Exception error) {
            Log.w(TAG, "No se pudo descargar la miniatura anunciada " + artworkUri, error);
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void post(Callback callback, LoadedThumbnail thumbnail, int generation) {
        if (callback == null) return;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            if (!destroyed && !paused && isGenerationCurrent(generation)) {
                callback.onLoaded(thumbnail.bitmap, thumbnail.source);
            }
            return;
        }
        mainHandler.post(() -> {
            if (!destroyed && !paused && isGenerationCurrent(generation)) {
                callback.onLoaded(thumbnail.bitmap, thumbnail.source);
            }
        });
    }

    private void cancelPending() {
        List<Future<?>> futures = new ArrayList<>();
        synchronized (pending) {
            for (PendingThumbnail request : pending.values()) {
                if (request.future != null) futures.add(request.future);
            }
            pending.clear();
        }
        for (Future<?> future : futures) {
            future.cancel(true);
        }
    }

    private boolean isCurrentPending(String workKey, PendingThumbnail request) {
        synchronized (pending) {
            return pending.get(workKey) == request;
        }
    }

    private void removePending(String workKey, PendingThumbnail request) {
        synchronized (pending) {
            if (pending.get(workKey) == request) pending.remove(workKey);
        }
    }

    private List<Callback> takeCallbacks(String workKey, PendingThumbnail request) {
        synchronized (pending) {
            if (pending.get(workKey) != request) return new ArrayList<>();
            pending.remove(workKey);
            return new ArrayList<>(request.callbacks);
        }
    }

    private boolean requestIsCurrent(
            String cacheName,
            int requestEpoch,
            int requestRevision
    ) {
        return requestEpoch == cacheEpoch && requestRevision == revision(cacheName);
    }

    private boolean isGenerationCurrent(int generation) {
        synchronized (diskLock) {
            return generation == requestGeneration;
        }
    }

    private int revision(String cacheName) {
        Integer value = revisions.get(cacheName);
        return value == null ? 0 : value;
    }

    private void saveIfCurrent(
            File destination,
            Bitmap bitmap,
            String cacheName,
            int requestEpoch,
            int requestRevision,
            int requestGeneration
    ) {
        synchronized (diskLock) {
            if (!requestIsCurrent(cacheName, requestEpoch, requestRevision)
                    || requestGeneration != this.requestGeneration) return;
            save(destination, bitmap);
        }
    }

    static boolean isRemoteArtwork(Uri artworkUri) {
        if (artworkUri == null) return false;
        String scheme = artworkUri.getScheme();
        if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            return false;
        }
        String path = artworkUri.getPath();
        return path == null
                || !path.toLowerCase(Locale.ROOT).contains("/thumbnail/request");
    }

    static String cacheName(VideoItem video) {
        Uri artworkUri = video.getArtworkUri();
        String artwork = artworkUri == null ? "" : artworkUri.toString();
        String variant = artwork.isBlank()
                ? "no-artwork"
                : ARTWORK_VARIANT + sha256(artwork);
        return ThumbnailCacheKey.name(
                video.getServerUdn(),
                video.getId(),
                variant
        );
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

    private static void recycle(LoadedThumbnail thumbnail) {
        if (thumbnail != null
                && thumbnail.bitmap != null
                && !thumbnail.bitmap.isRecycled()) {
            thumbnail.bitmap.recycle();
        }
    }
}
