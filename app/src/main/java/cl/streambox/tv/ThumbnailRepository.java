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
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;

final class ThumbnailRepository {
    interface Callback {
        void onLoaded(Bitmap bitmap, Source source);
    }

    enum Source {
        SERVER("SERVIDOR"),
        LOCAL("LOCAL"),
        SERVER_UNAVAILABLE("SERVIDOR NO DISPONIBLE"),
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
    private static final int WIDTH = 480;
    private static final int HEIGHT = 270;
    private static final int MAX_PREFETCH_ITEMS = 24;
    private static final String USER_AGENT = "VibeDLNA/0.3.11";
    private static final String SERVER_FALLBACK_VARIANT = "server-fallback-50";
    private static final String SERVER_ARTWORK_VARIANT = "server-artwork-";

    private final Context context;
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
                    return Math.max(1, value.bitmap.getAllocationByteCount() / 1024);
                }
            };

    private volatile ThumbnailSettings settings;
    private volatile boolean destroyed;
    private volatile boolean paused;
    private int cacheEpoch;
    private int requestGeneration;

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
        settings = updatedSettings == null
                ? new ThumbnailSettings(ThumbnailSettings.Mode.SERVER)
                : updatedSettings;
        synchronized (diskLock) {
            requestGeneration++;
        }
        cancelPending();
    }

    ThumbnailSettings getSettings() {
        return settings;
    }

    String requestKey(VideoItem video) {
        String name = cacheName(video, settings);
        synchronized (diskLock) {
            return name + ":" + cacheEpoch + ":" + revision(name)
                    + ":" + requestGeneration;
        }
    }

    void load(VideoItem video, Callback callback) {
        ThumbnailSettings requestSettings = settings;
        String cacheName = cacheName(video, requestSettings);
        int requestEpoch;
        int requestRevision;
        int requestGeneration;
        synchronized (diskLock) {
            requestEpoch = cacheEpoch;
            requestRevision = revision(cacheName);
            requestGeneration = this.requestGeneration;
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
                LoadedThumbnail thumbnail = loadOrCreate(
                        video,
                        requestSettings,
                        cacheName,
                        requestEpoch,
                        requestRevision
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

    void prefetch(List<VideoItem> videos) {
        if (paused || destroyed) return;
        int scheduled = 0;
        for (VideoItem video : videos) {
            if (video.isContainer()) continue;
            load(video, null);
            if (++scheduled >= MAX_PREFETCH_ITEMS) break;
        }
    }

    void resetForFolder() {
        synchronized (diskLock) {
            requestGeneration++;
        }
        cancelPending();
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

    void evict(VideoItem video) {
        Set<String> names = new HashSet<>();
        for (ThumbnailSettings.Mode mode : ThumbnailSettings.Mode.values()) {
            names.add(cacheName(video, new ThumbnailSettings(mode)));
        }
        names.add(serverFallbackCacheName(video));
        for (String name : names) {
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
        cancelPending();
        synchronized (memoryCache) {
            memoryCache.evictAll();
        }
        synchronized (diskLock) {
            cacheEpoch++;
            requestGeneration++;
            revisions.clear();
            File[] files = cacheDirectory.listFiles();
            deleteFiles(files);
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

    private LoadedThumbnail loadOrCreate(
            VideoItem video,
            ThumbnailSettings requestSettings,
            String cacheName,
            int requestEpoch,
            int requestRevision
    ) {
        File cached = new File(cacheDirectory, cacheName);
        Bitmap bitmap;
        boolean serverOnly = requestSettings.prefersServerArtwork();
        boolean hasServerArtwork = serverOnly
                && video.getArtworkUri() != null;
        synchronized (diskLock) {
            bitmap = (!serverOnly || hasServerArtwork)
                    && requestIsCurrent(cacheName, requestEpoch, requestRevision)
                    ? decode(cached)
                    : null;
        }
        if (bitmap != null) return new LoadedThumbnail(bitmap, hasServerArtwork
                ? Source.SERVER : Source.LOCAL);

        if (serverOnly) {
            if (!hasServerArtwork) {
                return new LoadedThumbnail(null, Source.SERVER_UNAVAILABLE);
            }

            bitmap = downloadServerArtwork(video);
            if (bitmap != null) {
                Bitmap cropped = centerCrop(bitmap, WIDTH, HEIGHT);
                if (cropped != bitmap) bitmap.recycle();
                saveIfCurrent(
                        cached,
                        cropped,
                        cacheName,
                        requestEpoch,
                        requestRevision,
                        requestGeneration
                );
                return new LoadedThumbnail(cropped, Source.SERVER);
            }
            return new LoadedThumbnail(null, Source.SERVER_UNAVAILABLE);
        }

        if (requestSettings.generatedPercentage() == 50) {
            File legacy = new File(legacyCacheDirectory, legacyCacheName(video));
            bitmap = decode(legacy);
            if (bitmap != null) {
                saveIfCurrent(
                        cached,
                        bitmap,
                        cacheName,
                        requestEpoch,
                        requestRevision,
                        requestGeneration
                );
                return new LoadedThumbnail(bitmap, Source.LOCAL);
            }
        }
        bitmap = createFrame(
                video,
                requestSettings.generatedPercentage(),
                cached,
                cacheName,
                requestEpoch,
                requestRevision
        );
        return new LoadedThumbnail(bitmap, bitmap == null ? Source.NONE : Source.LOCAL);
    }

    private Bitmap downloadServerArtwork(VideoItem video) {
        Bitmap bitmap = downloadArtwork(video.getArtworkUri());
        if (bitmap != null) return bitmap;

        // Older VibeDLNA versions announced the hash URL only when the file
        // was already cached. Retry through the ObjectID request endpoint so
        // those servers can generate it after a restart as well.
        Uri requestUri = DlnaContentRepository.deriveThumbnailRequestUri(video.getUri());
        if (requestUri == null
                || requestUri.toString().equals(video.getArtworkUri().toString())) {
            return null;
        }
        return downloadArtwork(requestUri);
    }

    private Bitmap downloadArtwork(Uri artworkUri) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(artworkUri.toString()).openConnection();
            connection.setConnectTimeout(8_000);
            connection.setReadTimeout(60_000);
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
                headers.put("User-Agent", USER_AGENT);
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
                    requestRevision,
                    requestGeneration
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

    private boolean requestIsCurrent(
            String cacheName,
            int requestEpoch,
            int requestRevision
    ) {
        synchronized (diskLock) {
            return requestEpoch == cacheEpoch && requestRevision == revision(cacheName);
        }
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

    private static void recycle(LoadedThumbnail thumbnail) {
        if (thumbnail != null
                && thumbnail.bitmap != null
                && !thumbnail.bitmap.isRecycled()) {
            thumbnail.bitmap.recycle();
        }
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
        String variant = settings.cacheVariant();
        if (settings.prefersServerArtwork() && video.getArtworkUri() != null) {
            variant = SERVER_ARTWORK_VARIANT
                    + sha256(serverArtworkIdentity(video.getArtworkUri()));
        }
        return ThumbnailCacheKey.name(
                video.getServerUdn(),
                video.getId(),
                variant
        );
    }

    private static String serverFallbackCacheName(VideoItem video) {
        return ThumbnailCacheKey.name(
                video.getServerUdn(),
                video.getId(),
                SERVER_FALLBACK_VARIANT
        );
    }

    private static String serverArtworkIdentity(Uri artworkUri) {
        return serverArtworkIdentity(artworkUri.toString());
    }

    static String serverArtworkIdentity(String artworkUrl) {
        try {
            URI uri = URI.create(artworkUrl);
            String path = uri.getRawPath();
            if (path == null || path.isBlank()) return artworkUrl;
            String query = uri.getRawQuery();
            return query == null || query.isBlank() ? path : path + "?" + query;
        } catch (Exception ignored) {
            return artworkUrl;
        }
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
