package cl.streambox.tv;

import android.net.Uri;

final class VideoItem {
    private final Uri uri;
    private final String name;
    private final String mimeType;
    private final long durationMs;
    private final long lastModified;

    VideoItem(Uri uri, String name, String mimeType, long durationMs, long lastModified) {
        this.uri = uri;
        this.name = name;
        this.mimeType = mimeType;
        this.durationMs = durationMs;
        this.lastModified = lastModified;
    }

    Uri getUri() {
        return uri;
    }

    String getName() {
        return name;
    }

    String getMimeType() {
        return mimeType;
    }

    long getDurationMs() {
        return durationMs;
    }

    long getLastModified() {
        return lastModified;
    }
}
