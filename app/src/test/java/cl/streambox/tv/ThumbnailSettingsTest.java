package cl.streambox.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ThumbnailSettingsTest {
    @Test
    public void serverModeFallsBackToHalfwayFrame() {
        ThumbnailSettings settings =
                new ThumbnailSettings(ThumbnailSettings.Mode.SERVER);
        assertTrue(settings.prefersServerArtwork());
        assertEquals(50, settings.generatedPercentage());
    }

    @Test
    public void generatedModesExposeSelectedPercentage() {
        ThumbnailSettings settings =
                new ThumbnailSettings(ThumbnailSettings.Mode.GENERATED_75);
        assertFalse(settings.prefersServerArtwork());
        assertEquals(75, settings.generatedPercentage());
    }

    @Test
    public void cacheIdentityIgnoresVolatileNetworkAddress() {
        String first = ThumbnailCacheKey.name("uuid:server", "video-42", "frame-50");
        String second = ThumbnailCacheKey.name("uuid:server", "video-42", "frame-50");
        String changedMode = ThumbnailCacheKey.name("uuid:server", "video-42", "frame-75");
        assertEquals(first, second);
        assertNotEquals(first, changedMode);
    }

    @Test
    public void serverArtworkCacheIdentityUsesAdvertisedThumbnailPath() {
        VideoItem first = VideoItem.video(
                "uuid:server",
                "video-42",
                "0",
                "Video",
                android.net.Uri.parse("http://192.168.1.20:43123/media/video/file.mp4"),
                android.net.Uri.parse("http://192.168.1.20:43123/thumbnail/first.jpg"),
                "video/mp4",
                1_000L
        );
        VideoItem sameThumbnailOnNewServerAddress = VideoItem.video(
                "uuid:server",
                "video-42",
                "0",
                "Video",
                android.net.Uri.parse("http://192.168.1.20:43124/media/video/file.mp4"),
                android.net.Uri.parse("http://192.168.1.20:43124/thumbnail/first.jpg"),
                "video/mp4",
                1_000L
        );
        VideoItem second = VideoItem.video(
                "uuid:server",
                "video-42",
                "0",
                "Video",
                android.net.Uri.parse("http://192.168.1.20:43124/media/video/file.mp4"),
                android.net.Uri.parse("http://192.168.1.20:43124/thumbnail/second.jpg"),
                "video/mp4",
                1_000L
        );
        ThumbnailSettings server = new ThumbnailSettings(ThumbnailSettings.Mode.SERVER);
        assertEquals(
                ThumbnailRepository.cacheName(first, server),
                ThumbnailRepository.cacheName(sameThumbnailOnNewServerAddress, server)
        );
        assertNotEquals(
                ThumbnailRepository.cacheName(first, server),
                ThumbnailRepository.cacheName(second, server)
        );
        assertNotEquals(
                ThumbnailRepository.cacheName(first, server),
                ThumbnailRepository.cacheName(
                        first,
                        new ThumbnailSettings(ThumbnailSettings.Mode.GENERATED_50)
                )
        );
    }
}
