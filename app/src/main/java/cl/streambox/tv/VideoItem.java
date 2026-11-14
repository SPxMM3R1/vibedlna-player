package cl.streambox.tv;

import android.net.Uri;

final class VideoItem {
    private final String serverUdn;
    private final String id;
    private final String parentId;
    private final String name;
    private final boolean container;
    private final Uri uri;
    private final Uri artworkUri;
    private final String mimeType;
    private final long durationMs;
    private final MediaInfo media;

    private VideoItem(
            String serverUdn,
            String id,
            String parentId,
            String name,
            boolean container,
            Uri uri,
            Uri artworkUri,
            String mimeType,
            long durationMs,
            MediaInfo media
    ) {
        this.serverUdn = serverUdn;
        this.id = id;
        this.parentId = parentId;
        this.name = name;
        this.container = container;
        this.uri = uri;
        this.artworkUri = artworkUri;
        this.mimeType = mimeType;
        this.durationMs = durationMs;
        this.media = media == null ? MediaInfo.NONE : media;
    }

    static VideoItem container(String serverUdn, String id, String parentId, String name) {
        return new VideoItem(serverUdn, id, parentId, name, true, null, null, null, 0,
                MediaInfo.NONE);
    }

    static VideoItem video(
            String serverUdn,
            String id,
            String parentId,
            String name,
            Uri uri,
            Uri artworkUri,
            String mimeType,
            long durationMs,
            MediaInfo media
    ) {
        return new VideoItem(
                serverUdn,
                id,
                parentId,
                name,
                false,
                uri,
                artworkUri,
                mimeType,
                durationMs,
                media
        );
    }

    String getServerUdn() {
        return serverUdn;
    }

    String getId() {
        return id;
    }

    String getParentId() {
        return parentId;
    }

    String getName() {
        return name;
    }

    boolean isContainer() {
        return container;
    }

    Uri getUri() {
        return uri;
    }

    Uri getArtworkUri() {
        return artworkUri;
    }

    String getMimeType() {
        return mimeType;
    }

    long getDurationMs() {
        return durationMs;
    }

    /** Datos técnicos anunciados; {@link MediaInfo#NONE} si no llegaron. */
    MediaInfo getMedia() {
        return media;
    }

    long getLastModified() {
        return 0;
    }

    String stableKey() {
        return (container ? "container:" : "video:") + serverUdn + ":" + id;
    }
}
